package com.atharchive.feature.tasmee

import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** How a revealed word got revealed; words revealed by being said correctly carry no mark. */
enum class TasmeeMark { Missed, Hinted }

/** Words before [cursor] are revealed. */
data class TasmeeProgress(val cursor: Int = 0, val marks: Map<Int, TasmeeMark> = emptyMap())

/** The text to recite, one list of words per line, and the line the reader last stood at. */
data class TasmeeText(val lines: List<List<String>>, val start: Int)

/** The model's alphabet: an id per letter, the CTC blank and the word delimiter. */
class TasmeeVocab(private val ids: Map<Char, Int>, val blank: Int, val delimiter: Int) {
    val size = maxOf(ids.values.max(), blank, delimiter) + 1

    private val letters = ids.entries.associate { (c, id) -> id to c }

    /** What the model heard, best letter per frame: for the log while تسميع is tuned. */
    fun greedy(frames: List<FloatArray>): String = buildString {
        var prev = -1
        for (f in frames) {
            val id = f.indices.maxBy { f[it] }
            if (id != prev && id != blank) append(if (id == delimiter) ' ' else letters[id] ?: "")
            prev = id
        }
    }

    /** The model also writes harakat; a held vowel is many such frames, which belong between letters. */
    val harakat = ids.filterKeys(::isHaraka).values.toIntArray()

    /** A written word as the model spells it: its letters without harakat; empty when nothing is said for it (punctuation, numbers). */
    fun spell(word: String): IntArray =
        word.filter { !isHaraka(it) && it != '|' && it != '-' }.mapNotNull { ids[it] }.toIntArray()

    private fun isHaraka(c: Char) = c in '\u064B'..'\u0653' || c == '\u0670' || c == '\u06E5' || c == '\u06E6'
}

/** Reads the vocab file exported with the model: {"vocab": {letter: id}, "blank": id, "delimiter": letter}. */
fun loadVocab(json: String): TasmeeVocab {
    val o = kotlinx.serialization.json.Json.parseToJsonElement(json).jsonObject
    val ids = o.getValue("vocab").jsonObject.filterKeys { it.length == 1 }.map { (k, v) -> k[0] to v.jsonPrimitive.int }.toMap()
    return TasmeeVocab(ids, o.getValue("blank").jsonPrimitive.int, ids.getValue(o.getValue("delimiter").jsonPrimitive.content[0]))
}

/**
 * Follows a recitation of [words] through the model's per-frame letter log-probs (one frame per
 * 20 ms). The frames since the last revealed word are force-aligned (CTC Viterbi) to the next
 * expected words, starting at whichever of them the reciter is at; a word is revealed once the alignment has moved past it, and marked missed when
 * its letters scored below [Cutoff], i.e. a different word was said or it was skipped.
 * Pure: no Android, so the JVM test drives it with recorded frames.
 */
class TasmeeTracker(private val vocab: TasmeeVocab, words: List<String>) {
    private val spelled = words.map(vocab::spell)
    private val frames = ArrayList<FloatArray>()
    var progress = TasmeeProgress()
        private set

    fun restart(from: TasmeeProgress) {
        progress = from.copy(cursor = skipSilent(from.cursor))
        frames.clear()
    }

    /** Reveals the next word without it being said. */
    fun hint() {
        val at = skipSilent(progress.cursor)
        if (at < spelled.size) restart(progress.copy(cursor = at + 1, marks = progress.marks + (at to TasmeeMark.Hinted)))
    }

    val done get() = skipSilent(progress.cursor) >= spelled.size

    /** Adds frames; true when more words were revealed. */
    fun feed(newFrames: List<FloatArray>): Boolean {
        // A haraka frame counts as a gap between letters: the blank takes the harakat's probability too.
        for (f in newFrames) {
            var sum = kotlin.math.exp(f[vocab.blank].toDouble())
            for (h in vocab.harakat) sum += kotlin.math.exp(f[h].toDouble())
            frames += f.copyOf().also { it[vocab.blank] = kotlin.math.ln(sum).toFloat() }
        }
        val window = ArrayList<Int>()
        var m = skipSilent(progress.cursor)
        while (m < spelled.size && window.size < Window) { if (spelled[m].isNotEmpty()) window += m; m++ }
        if (window.isEmpty()) return false
        val (last, scores) = align(window)
        // Settled: the path left the word a moment ago, so later frames won't pull it back.
        // The newest one waits for the word after it, so a guess about where the reciter is never gets
        // revealed, unless a pause followed it or the text ends there.
        val settled = window.indices.filter { last[it] in 0 until frames.size - Settle }
            .let { it.dropLast(if (it.isNotEmpty() && scores[it.last()] < Sure && last[it.last()] >= frames.size - Pause && window[it.last()] < window.last()) 1 else 0) }
            // Only real speech moves on: up to the newest word heard clearly. Without one (noise,
            // silence, someone else talking) the path's guesses reveal nothing.
            .let { s -> s.take(s.indexOfLast { scores[it] >= Cutoff } + 1) }
        if (settled.isEmpty()) {
            // 30 s with nothing recognised drops the oldest audio; a pause past that never resyncs mid-word
            if (frames.size > MaxFrames) frames.subList(0, frames.size - MaxFrames / 3).clear()
            return false
        }
        val marks = progress.marks.toMutableMap()
        // Words the path jumped over were skipped; words it forced through scored as not said.
        for (k in 0..settled.last()) if (last[k] < 0 || scores[k] < Cutoff) marks[window[k]] = TasmeeMark.Missed
        progress = TasmeeProgress(skipSilent(window[settled.last()] + 1), marks)
        frames.subList(0, last[settled.last()] + 1).clear()
        return true
    }

    /** Each window word's last frame (-1 when not reached) and its mean letter log-prob. */
    private fun align(window: List<Int>): Pair<IntArray, FloatArray> {
        val tokens = ArrayList<Int>()
        val owner = ArrayList<Int>()
        val entry = ArrayList<Pair<Int, Int>>() // (state, word) where a path may begin
        tokens += vocab.blank; owner += -1
        window.forEachIndexed { k, w ->
            if (k > 0) { tokens += vocab.delimiter; owner += -1; tokens += vocab.blank; owner += -1 }
            val first = tokens.size
            for (j in maxOf(0, first - 2)..first) entry += j to k
            for (c in spelled[w]) { tokens += c; owner += k; tokens += vocab.blank; owner += -1 }
        }
        val s = tokens.size
        val t = frames.size
        val skip = BooleanArray(s) { it >= 2 && tokens[it] != vocab.blank && tokens[it] != tokens[it - 2] }
        var score = FloatArray(s) { Neg }
        // The reciter may be ahead of the cursor (a skipped line, a start a little off): the path
        // may begin at any window word, at a cost per word jumped so it only jumps when it must.
        for ((j, k) in entry) score[j] = maxOf(score[j], frames[0][tokens[j]] - k * SkipCost)
        val back = ByteArray(t * s)
        for (f in 1 until t) {
            val em = frames[f]
            val next = FloatArray(s)
            for (j in 0 until s) {
                var best = score[j]; var from = 0
                if (j >= 1 && score[j - 1] > best) { best = score[j - 1]; from = 1 }
                if (skip[j] && score[j - 2] > best) { best = score[j - 2]; from = 2 }
                next[j] = best + em[tokens[j]]
                back[f * s + j] = from.toByte()
            }
            score = next
        }
        // The speaker is somewhere inside the window: the path ends at whichever state scores best.
        var at = score.indices.maxBy { score[it] }
        val last = IntArray(window.size) { -1 }
        val sum = FloatArray(window.size)
        val count = IntArray(window.size)
        for (f in t - 1 downTo 0) {
            val k = owner[at]
            if (k >= 0) { if (last[k] < 0) last[k] = f; sum[k] += frames[f][tokens[at]]; count[k]++ }
            at -= back[f * s + at]
        }
        return last to FloatArray(window.size) { if (count[it] > 0) sum[it] / count[it] else Neg }
    }

    private fun skipSilent(from: Int): Int {
        var c = from
        while (c < spelled.size && spelled[c].isEmpty()) c++
        return c
    }

    companion object {
        /** Mean letter log-prob below which a word was not said: between the worst correct (−3.9) and best wrong (−4.9) on the test recitations. */
        const val Cutoff = -4.5f
        private const val Window = 40
        private const val SkipCost = 4f
        private const val Settle = 10
        private const val Pause = 75
        /** A word heard this clearly shows at once; a doubtful one waits for the word after it. */
        private const val Sure = -1.5f
        private const val MaxFrames = 1500
        private const val Neg = -1e9f
    }
}
