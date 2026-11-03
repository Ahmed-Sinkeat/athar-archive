package com.atharchive.data

import android.content.Context
import com.atharchive.BuildConfig
import com.atharchive.core.ArNormalize
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.lang.ref.SoftReference
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton

/** One dictionary's entry for a word: where it is in the book and a clause of it. */
data class MeaningEntry(val source: String, val bookId: String, val ordinal: Int, val snippet: String)

/** A selected word, the root(s) it was traced to, and the entries in display order. */
data class Meaning(val word: String, val roots: List<String>, val entries: List<MeaningEntry>)

/**
 * «معنى»: a single word looked up in the archive's lexicons (scripts/gen-app-dict.ts).
 *
 * The lexicons are one zip, downloaded once on request («تنزيل المعجم») and read in place —
 * no unpacking, offline from then on. The index (roots + the forms each lexicon lists) is
 * read once and held softly; a root's entries are read on demand.
 */
@Singleton
class Dictionary @Inject constructor(
    @ApplicationContext context: Context,
    private val httpClient: OkHttpClient,
) {
    private val file = File(context.filesDir, "dict.zip")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _installed = MutableStateFlow(file.isFile)
    val installed: StateFlow<Boolean> = _installed
    /** Bytes so far and total while downloading; null otherwise. */
    private val _progress = MutableStateFlow<Pair<Long, Long>?>(null)
    val progress: StateFlow<Pair<Long, Long>?> = _progress
    private val _failed = MutableStateFlow(false)
    val failed: StateFlow<Boolean> = _failed
    private var download: Job? = null

    private var index = SoftReference<DictionaryLookup.Index?>(null)
    private var zip: ZipFile? = null
    private val lock = Mutex()
    private val zipLock = Mutex()

    /** Starts the download unless one is running; it outlives the popup that asked for it. */
    @Synchronized fun startDownload() {
        if (download?.isActive == true || _installed.value) return
        _failed.value = false
        download = scope.launch {
            val part = File(file.path + ".part")
            try {
                // ponytail: unsigned (plain HTTPS), checked only by opening it; sign it like the catalog if it ever carries more than text
                val url = BuildConfig.ATHAR_CONTENT_BASE_URL.trim().toHttpUrl().resolve("dict/dict.zip")!!
                httpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    check(response.isSuccessful) { "HTTP ${response.code}" }
                    val body = response.body ?: error("empty")
                    val total = body.contentLength()
                    body.byteStream().use { input -> part.outputStream().use { out ->
                        val buf = ByteArray(64 shl 10)
                        var done = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            _progress.value = done to total
                        }
                    } }
                }
                ZipFile(part).use { requireNotNull(it.getEntry("index.json")) }
                check(part.renameTo(file))
                _installed.value = true
            } catch (e: Exception) {
                android.util.Log.w("AtharDictionary", "download failed", e)
                part.delete()
                _failed.value = true
            } finally {
                _progress.value = null
            }
        }
    }

    suspend fun meaning(word: String): Meaning? = withContext(Dispatchers.IO) {
        if (!_installed.value) return@withContext null
        val index = index() ?: return@withContext null
        val roots = DictionaryLookup.roots(word, index)
        if (roots.isEmpty()) return@withContext null
        val entries = roots.flatMap { root ->
            val list = read("r/${DictionaryLookup.fileName(root)}.json")?.let(::JSONArray) ?: return@flatMap emptyList()
            (0 until list.length()).map { list.getJSONObject(it) }.distinctBy { it.getInt("s") }.map {
                val source = index.sources[it.getInt("s")]
                it.getInt("s") to MeaningEntry(source.second, source.first, it.getInt("o"), DictionaryLookup.snippet(it.getString("x"), word))
            }
        }.sortedBy { it.first }.map { it.second }
        val shown = word.trim { !it.isLetter() && it !in '\u064B'..'\u0652' }
        Meaning(shown, roots, entries).takeIf { entries.isNotEmpty() }
    }

    /**
     * «بالجذر»: the word's root(s) and every form the lexicons list under them, to search for any
     * of them. Null when the lexicons aren't on the phone or the word has no root.
     */
    suspend fun rootForms(word: String): Pair<List<String>, List<String>>? = withContext(Dispatchers.IO) {
        if (!_installed.value) return@withContext null
        val index = index() ?: return@withContext null
        val roots = DictionaryLookup.roots(word, index).takeIf { it.isNotEmpty() } ?: return@withContext null
        // ponytail: a scan of every listed form (~100k) per search; an inverse map if it shows in traces
        val forms = index.forms.entries.filter { (_, rs) -> rs.any { it in roots } }.map { it.key }
        roots to (forms + roots).distinct()
    }

    private suspend fun index(): DictionaryLookup.Index? = index.get() ?: lock.withLock {
        index.get() ?: read("index.json")?.let { DictionaryLookup.parseIndex(JSONObject(it)) }?.also { index = SoftReference(it) }
    }

    // ponytail: the zip stays open for the process (one descriptor); close it if that ever matters
    private suspend fun read(name: String): String? = zipLock.withLock {
        val z = zip ?: ZipFile(file).also { zip = it }
        z.getEntry(name)?.let { e -> z.getInputStream(e).bufferedReader().use { it.readText() } }
    }
}

/**
 * The word → root rules, kept apart from the network so they can be tested. [key] mirrors gen-app-dict.ts dictKey;
 * [roots] is the phone's own analyser (the build places headwords with the simpler resolveRoot).
 */
internal object DictionaryLookup {
    class Index(val sources: List<Pair<String, String>>, val roots: Set<String>, val forms: Map<String, List<String>>) {
        /** How many listed forms point at a root: «بكي» has 15, the stray heading «تبك» one. Breaks ties toward real roots. */
        val weight: Map<String, Int> by lazy { HashMap<String, Int>().apply { forms.values.forEach { l -> l.forEach { merge(it, 1, Int::plus) } } } }
    }

    fun parseIndex(json: JSONObject): Index {
        val sources = json.getJSONArray("sources").let { a -> (0 until a.length()).map { a.getJSONObject(it).let { s -> s.getString("id") to s.getString("title") } } }
        val roots = json.getJSONArray("roots").let { a -> HashSet<String>(a.length() * 2).apply { for (i in 0 until a.length()) add(a.getString(i)) } }
        val formsJson = json.getJSONObject("forms")
        val forms = HashMap<String, List<String>>(formsJson.length() * 2)
        formsJson.keys().forEach { k -> forms[k] = formsJson.getJSONArray(k).let { a -> List(a.length()) { a.getString(it) } } }
        return Index(sources, roots, forms)
    }

    private val HAMZA = Regex("[ءؤئ]")
    /** normalizeArabic with every hamza seat folded to alef, letters only. */
    fun key(s: String): String = ArNormalize.normalize(s).replace(HAMZA, "ا").filter { it in 'ء'..'ي' }

    /** Root file name: the root's code points in hex, so the path is plain ASCII. */
    fun fileName(root: String) = root.map { it.code.toString(16).padStart(4, '0') }.joinToString("")

    private val STOP = ("عليه عليها عليهم فيه فيها إلى على من عن في ولا لا ما هذا هذه ذلك التي الذي الذين الله إن أن " +
        "ثم أو هو هي هم هما أنا نحن إذا إذ لم لن قد كل بعض بين عند مع حتى لكن بل وهو وهي فلا ومن وما")
        .split(' ').map(::key).toSet()
    // A proclitic, optionally after و/ف: «وبالأجر», «فسيكفيكهم».
    private val PRE = listOf("", "بال", "كال", "لل", "ال", "ب", "ل", "ك", "س").let { p -> p + p.flatMap { listOf("و$it", "ف$it") } }.distinct().map(::key)
    private val SUF = listOf("", "هما", "كما", "تما", "ها", "هم", "هن", "كم", "كن", "نا", "ون", "ين", "ان", "ات", "وا", "ه", "ي", "ك", "ت").map(::key)
    private val AUG = key("سالتمونيه").toSet()
    private const val WEAK = "اوي"
    private val AL = key("ال")

    /**
     * The roots a word belongs to: the likeliest, and a second when the spelling alone can't tell
     * («تقوى»: وقي or قوي). Every way of trimming clitics and the present-tense prefix gives a stem;
     * each root the lexicons list for it, or hidden in it, is scored by what it leaves unexplained
     * ([fit]), minus a bonus for being listed and for how common it is. Tuned on ~200 words (95% first
     * pick right on the held-out half, 86% before).
     */
    fun roots(word: String, index: Index): List<String> {
        val w = key(word)
        if (w.length < 2 || w in STOP) return emptyList()
        val score = HashMap<String, Double>()
        for ((stem, strip) in stems(w)) {
            val listed = (index.forms[stem].orEmpty() + index.forms[AL + stem].orEmpty()).toSet()
            for (r in listed + rootsInside(stem, index)) {
                if (r.length < 3) continue
                val fit = fit(r, stem) ?: continue
                val weight = index.weight[r] ?: 0
                val cost = strip + fit - (if (r in listed) 1.0 else 0.0) + (if (weight == 0) 1.0 else 0.0) - 0.8 * kotlin.math.ln(1.0 + weight)
                if (cost < (score[r] ?: Double.MAX_VALUE)) score[r] = cost
            }
        }
        val ranked = score.entries.sortedBy { it.value }
        val best = ranked.firstOrNull()?.value ?: return emptyList()
        val picked = ArrayList<String>(2)
        for ((r, cost) in ranked) {
            if (cost > best + 1.0 || picked.size == 2) break
            if (picked.none { weakEqual(it, r) }) picked += r
        }
        // A lexicon may head the same root «دعا» where another heads it «دعو»: read both.
        return picked.flatMap { p -> ranked.filter { (r, cost) -> r == p || (weakEqual(r, p) && cost <= score.getValue(p) + 0.5) }.map { it.key } }
    }

    private fun weakEqual(a: String, b: String) = a.length == b.length && a.indices.all { a[it] == b[it] || (a[it] in WEAK && b[it] in WEAK) }

    /** Each stem with its cost: a proclitic 1 (a bare ب/ل/ك 2, they are rarely clitics), a suffix 1, then the present-tense letter 1. */
    private fun stems(w: String): Map<String, Double> {
        val out = HashMap<String, Double>().apply { put(w, 0.0) }
        for (p in PRE) if (w.startsWith(p)) {
            val s = w.substring(p.length)
            for (x in SUF) if (s.endsWith(x) && s.length - x.length >= 2) {
                val cost = (if (p.length == 1 && p[0] in "بلك") 2.0 else if (p.isNotEmpty()) 1.0 else 0.0) + (if (x.isNotEmpty()) 1.0 else 0.0)
                out.merge(s.dropLast(x.length), cost, ::minOf)
            }
        }
        for ((s, cost) in out.toList()) if (s.length >= 3 && s[0] in "يتنا") out.merge(s.drop(1), cost + 1, ::minOf)
        return out
    }

    /**
     * What reading root [r] inside [s] leaves unexplained, or null if it can't be read there. Root letters in order;
     * a weak or hamza root letter may be missing or written as another weak letter («قال» قول), a first one as ت
     * («اتقى» وقي), a doubled last one missing («ظن» ظنن): 1 each. Every leftover letter must be an augment: 1 each.
     */
    private fun fit(r: String, s: String): Int? {
        val memo = HashMap<Int, Int?>()
        fun go(i: Int, j: Int): Int? = memo.getOrPut(i * 32 + j) {
            if (i == r.length) return@getOrPut if (s.substring(j).all { it in AUG }) s.length - j else null
            var best: Int? = null
            fun take(v: Int?, add: Int) { if (v != null && (best == null || v + add < best!!)) best = v + add }
            if (j < s.length) {
                if (s[j] == r[i]) take(go(i + 1, j + 1), 0)
                else if (r[i] in WEAK && (s[j] in WEAK || (i == 0 && s[j] == 'ت'))) take(go(i + 1, j + 1), 1)
                if (s[j] in AUG) take(go(i, j + 1), 1)
            }
            if (r[i] in WEAK || (i == r.length - 1 && i > 0 && r[i] == r[i - 1])) take(go(i + 1, j), 1)
            best
        }
        return go(0, 0)
    }

    /** Known roots hidden in [stem]: 3 or 4 of its letters, with a weak letter restored or swapped. */
    private fun rootsInside(stem: String, index: Index): Set<String> {
        if (stem.length > 9) return emptySet()
        val shapes = mutableListOf(stem)
        if (stem.length <= 3) for (pos in 0..stem.length) for (x in WEAK) shapes += stem.substring(0, pos) + x + stem.substring(pos)
        if (stem.length == 2) shapes += stem + stem[1]
        if (stem[0] == 'ت') shapes += "و" + stem.substring(1)
        val out = HashSet<String>()
        for (f in shapes) for (size in 3..minOf(4, f.length)) subsequences(f, size) { base -> variants(base) { if (it in index.roots) out += it } }
        return out
    }

    private fun variants(base: String, each: (String) -> Unit) {
        fun go(i: Int, acc: String) {
            if (i == base.length) return each(acc)
            if (base[i] in WEAK) for (x in WEAK) go(i + 1, acc + x) else go(i + 1, acc + base[i])
        }
        go(0, "")
    }

    private fun subsequences(f: String, size: Int, each: (String) -> Unit) {
        fun go(from: Int, picked: String) {
            if (picked.length == size) return each(picked)
            for (i in from..f.length - (size - picked.length)) go(i + 1, picked + f[i])
        }
        go(0, "")
    }

    private val CLAUSE_END = charArrayOf('.', '،', '؛', '\n', ':')

    /** A clause of the entry, around the word itself when the entry names it, else its opening. */
    fun snippet(text: String, word: String, max: Int = 190): String {
        val n = ArNormalize.normalizeWithMap(text)
        val k = key(word)
        val hit = if (k.length < 2) -1 else n.text.replace(HAMZA, "ا").indexOf(k)
        var from = 0
        if (hit > 0) {
            val at = n.sourceRange(hit, hit + k.length).first
            val clause = text.lastIndexOfAny(CLAUSE_END, at - 1) + 1
            from = if (at - clause <= max / 3) clause else text.lastIndexOf(' ', at - max / 6).coerceAtLeast(0)
        }
        val body = text.substring(from).trimStart(' ', '\n', '.', '،', '؛', ':').replace('\n', ' ')
        val lead = if (from > 0) "… " else ""
        if (body.length <= max) return lead + body.trim()
        val cut = body.lastIndexOf(' ', max).takeIf { it > max / 2 } ?: max
        return lead + body.substring(0, cut).trim() + " …"
    }
}
