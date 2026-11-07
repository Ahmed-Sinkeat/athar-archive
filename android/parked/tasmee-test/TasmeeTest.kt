package com.atharchive.feature.tasmee

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class TasmeeTest {
    // A toy alphabet: blank 0, delimiter 1, then the letters.
    private val letters = "بسماللهرحنيعد"
    private val vocab = TasmeeVocab(letters.withIndex().associate { (i, c) -> c to i + 2 }, blank = 0, delimiter = 1)

    /** Confident frames for [text]: each letter twice, blank between, delimiter between words, silence around. */
    private fun say(text: String): List<FloatArray> {
        fun frame(id: Int) = FloatArray(vocab.size) { if (it == id) -0.05f else -8f }
        val out = ArrayList<FloatArray>()
        text.split(' ').forEachIndexed { k, w ->
            if (k > 0) out += frame(1)
            for (c in w) { val id = letters.indexOf(c) + 2; out += frame(id); out += frame(id); out += frame(0) }
        }
        repeat(90) { out += frame(0) } // a pause: the last word is revealed without waiting for the next
        return out
    }

    @Test fun saidWordsRevealAWrongOneIsMissedPunctuationPassesSilently() {
        val t = TasmeeTracker(vocab, "بِسْمِ اللَّهِ ، الرَّحْمَنِ الرَّحِيمِ".split(' '))
        assertTrue(t.feed(say("بسم الله")))
        assertEquals(3, t.progress.cursor) // past the comma
        t.feed(say("بسم الرحيم")) // a different word where الرحمن belongs
        assertEquals(5, t.progress.cursor)
        assertEquals(mapOf(3 to TasmeeMark.Missed), t.progress.marks)
        assertTrue(t.done)
    }

    @Test fun otherSpeechRevealsNothing() {
        val t = TasmeeTracker(vocab, "بسم الله الرحمن".split(' '))
        t.feed(say("عدد يعد دعد"))
        assertEquals(0, t.progress.cursor)
    }

    @Test fun silenceRevealsNothingAndHintSkipsAWord() {
        val t = TasmeeTracker(vocab, "بسم الله".split(' '))
        assertEquals(false, t.feed(say("").drop(1)))
        t.hint()
        assertEquals(TasmeeProgress(1, mapOf(0 to TasmeeMark.Hinted)), t.progress)
        t.feed(say("الله"))
        assertTrue(t.done)
    }

    /**
     * Real recitations: the model's frames for 3 minutes of each recording, dumped by the PC test
     * (scratchpad ctc.py --dump) into ~/.cache/athar-tasmee; skipped where they are absent. Fed in
     * 2 s steps as the app does, with every 10th expected word replaced by a different word.
     */
    @Test fun realRecitationsRevealAndCatchWrongWords() {
        val dir = File(System.getProperty("user.home"), ".cache/athar-tasmee")
        val ids = listOf("nawaqid-al-islam", "al-manzumah-al-bayquniyyah", "al-qawaid-al-arba").filter { File(dir, "$it.em").exists() }
        assumeTrue(ids.isNotEmpty() && File(dir, "tasmee-vocab.json").exists())
        val real = loadVocab(File(dir, "tasmee-vocab.json").readText())
        for (id in ids) {
            val lines = File(dir, "$id.words").readLines()
            val v = lines[0].toInt()
            val truth = lines.drop(1)
            val bytes = ByteBuffer.wrap(File(dir, "$id.em").readBytes()).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
            val frames = List(bytes.limit() / v) { f -> FloatArray(v) { bytes.get(f * v + it) } }
            val swapped = truth.indices.filter { it in 5 until truth.size - 5 && (it - 5) % 10 == 0 }
            val words = truth.mapIndexed { i, w -> if (i in swapped) truth.first { it.length in w.length - 2..w.length + 2 && it != w && it.take(2) != w.take(2) } else w }
            val t = TasmeeTracker(real, words)
            frames.chunked(100).forEachIndexed { i, c -> val before = t.progress.cursor; t.feed(c)
                if (System.getenv("TASMEE_DEBUG") == id) println("${i * 2}s ${before}->${t.progress.cursor} " + (before until t.progress.cursor).joinToString(" ") { k -> (if (k in t.progress.marks) "✗" else "") + (if (k in swapped) "*" else "") + words[k] }) }
            val reached = t.progress.cursor
            val missed = t.progress.marks.keys
            val caught = swapped.count { it < reached && it in missed }.toFloat() / swapped.count { it < reached }
            // From the first word heard: recordings open with a header the reciter never says.
            val first = (0 until reached).first { it !in missed }
            val falseMiss = (first until reached).count { it !in swapped && it in missed }.toFloat() / (reached - first)
            println("$id reached $reached/${words.size} caught ${"%.0f".format(caught * 100)}% wrong, ${"%.0f".format(falseMiss * 100)}% right ones marked missed")
            assertTrue("$id stalled at $reached", reached >= words.size * 0.9)
            assertTrue("$id caught $caught", caught >= 0.85f)
            assertTrue("$id falseMiss $falseMiss", falseMiss <= 0.12f)
        }
    }
}
