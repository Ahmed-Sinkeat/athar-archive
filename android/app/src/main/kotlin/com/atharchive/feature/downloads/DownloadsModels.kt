package com.atharchive.feature.downloads

/**
 * التنزيلات — the screen for what M6 actually stores.
 *
 * Two kinds of bytes live on the device and they behave differently, so the screen
 * never merges them:
 *  - **مثبّت** — pinned by an explicit download. Never evicted. Removing it is a
 *    decision the reader makes.
 *  - **مؤقّت** — imported frames kept because you read them. Evicted by LRU under the
 *    budget, and cleared on request. Pins survive both.
 *
 * That distinction is the whole reason this screen exists rather than a single number
 * in الإعدادات.
 */

/** The budgets M6 offers, with the byte values the repository actually stores. */
enum class CacheBudget(val label: String, val bytes: Long) {
    Mb500("٥٠٠ ميغابايت", 500L * 1024 * 1024),
    Gb2("٢ غيغابايت", 2L * 1024 * 1024 * 1024),
    Gb5("٥ غيغابايت", 5L * 1024 * 1024 * 1024),
    Gb10("١٠ غيغابايت", 10L * 1024 * 1024 * 1024),
    Gb20("٢٠ غيغابايت", 20L * 1024 * 1024 * 1024),
    Unlimited("بلا حد", Long.MAX_VALUE),
    ;

    companion object {
        /** Nearest match, so a stored value from an older build still selects something. */
        fun fromBytes(bytes: Long): CacheBudget =
            entries.minByOrNull { kotlin.math.abs(it.bytes - bytes) } ?: Gb2
    }
}

sealed interface TransferUi {
    data class Running(val progress: Float, val label: String) : TransferUi
    data class Paused(val progress: Float, val label: String) : TransferUi
    /** Resume is exact-prefix, so a failure never costs the bytes already verified. */
    data class Failed(val reason: String, val progress: Float) : TransferUi
}

data class DownloadUi(
    val id: String,
    val title: String,
    /** كتاب / قصيدة / تسجيل / مسألة — what kind of thing is on disk. */
    val kindLabel: String,
    val sizeLabel: String,
    /** null once the package is verified and imported. */
    val transfer: TransferUi? = null,
    /** What removing it frees; summed for «حذف (n)». */
    val bytes: Long = 0,
)

/**
 * The budget, not the device.
 *
 * How full the disk is belongs to Settings; what this screen governs is the ceiling the
 * cache is allowed to reach and how close it is to it — the one number tuning changes.
 */
data class StorageUi(
    /** How many items are pinned. Pins are counted, not weighed: none of them evict. */
    val pinnedCountLabel: String,
    val cacheLabel: String,
    val budget: CacheBudget,
    /** Cache over budget, 0 when unbounded. */
    val cacheFraction: Float,
) {
    val unlimited: Boolean get() = budget == CacheBudget.Unlimited
}

data class DownloadsUiState(
    val storage: StorageUi,
    val transfers: List<DownloadUi>,
    val downloaded: List<DownloadUi>,
    val bulk: List<BulkRowUi> = emptyList(),
)

val DownloadsFixture = DownloadsUiState(
    storage = StorageUi(
        pinnedCountLabel = "٦ عناصر",
        cacheLabel = "640 MB",
        budget = CacheBudget.Gb2,
        cacheFraction = 0.31f,
    ),
    transfers = listOf(
        DownloadUi(
            id = "t1",
            title = "مدارج السالكين بين منازل إياك نعبد",
            kindLabel = "كتاب",
            sizeLabel = "42.6 MB",
            transfer = TransferUi.Running(progress = 0.62f, label = "٪٦٢"),
        ),
        DownloadUi(
            id = "t2",
            title = "الدرس الخامس: إثبات صفات الله تعالى",
            kindLabel = "تسجيل",
            sizeLabel = "18.6 MB",
            transfer = TransferUi.Paused(progress = 0.34f, label = "٪٣٤"),
        ),
        DownloadUi(
            id = "t3",
            title = "روضة العقلاء ونزهة الفضلاء",
            kindLabel = "كتاب",
            sizeLabel = "9.6 MB",
            transfer = TransferUi.Failed(reason = "انقطع الاتصال", progress = 0.18f),
        ),
    ),
    downloaded = listOf(
        DownloadUi("d1", "العقيدة الطحاوية", "كتاب", "13.9 MB"),
        DownloadUi("d2", "الاستقامة", "كتاب", "8.4 MB"),
        DownloadUi("d3", "شرح حلية طالب العلم", "كتاب", "12.6 MB"),
        DownloadUi("d4", "بانت سعاد فقلبي اليوم متبول", "قصيدة", "1.2 MB"),
        DownloadUi("d5", "شرح نونية ابن القيم", "تسجيل", "16.2 MB"),
        DownloadUi("d6", "حكم صيام يوم السبت تطوعًا", "مسألة", "0.4 MB"),
    ),
)

/**
 * The downloads screen reads the catalogue rather than a store of its own: a transfer
 * is a catalogue entry mid-flight, and a download is one that finished. Deriving it
 * here means the two lists can never disagree with the section they came from.
 */
fun downloadsUiState(
    books: List<com.atharchive.feature.books.BookUi>,
    articles: List<com.atharchive.feature.articles.ArticleUi>,
    poems: List<com.atharchive.feature.poetry.PoemUi>,
    storage: StorageUi,
): DownloadsUiState {
    val transfers = buildList {
        books.forEach { book ->
            val state = book.download
            if (state is com.atharchive.feature.books.BookDownloadUi.Downloading) {
                add(
                    DownloadUi(
                        id = book.id,
                        title = book.title,
                        kindLabel = book.kind,
                        sizeLabel = state.sizeLabel,
                        transfer = state.transfer,
                    ),
                )
            }
        }
        articles.filter { it.downloading }.forEach {
            add(DownloadUi(it.id, it.title, "مقال", "—", TransferUi.Running(0f, "…")))
        }
        poems.filter { it.downloading }.forEach {
            add(DownloadUi(it.id, it.title, "قصيدة", it.sizeLabel, TransferUi.Running(0f, "…")))
        }
    }
    val downloaded = buildList {
        books.forEach { book ->
            val state = book.download
            if (state is com.atharchive.feature.books.BookDownloadUi.Downloaded) {
                add(DownloadUi(book.id, book.title, book.kind, state.sizeLabel))
            }
        }
        articles.filter { it.downloaded }.forEach {
            add(DownloadUi(it.id, it.title, "مقال", "—"))
        }
        poems.filter { it.downloaded }.forEach {
            add(DownloadUi(it.id, it.title, "قصيدة", it.sizeLabel))
        }
    }
    return DownloadsUiState(storage = storage, transfers = transfers, downloaded = downloaded)
}

/** One not-yet-downloaded thing «تنزيل الكل» can fetch: a work's text package or one recording. */
data class BulkCandidate(val coll: String, val audio: Boolean, val id: String, val bytes: Long)

data class BulkRowUi(val label: String, val items: List<BulkCandidate>) {
    val bytes: Long = items.sumOf { it.bytes }
}

private val BulkSections = listOf("book" to "الكتب", "poem" to "القصائد", "article" to "المقالات")

/**
 * The whole library, then each section, split into نصوص / صوت. Callers pass only what is not
 * downloaded yet, so a finished row simply disappears. Adhkar ship inside the app: no row.
 */
fun bulkRows(candidates: List<BulkCandidate>): List<BulkRowUi> = buildList {
    fun group(name: String, all: List<BulkCandidate>, combined: Boolean) {
        // A recording shared by two works is still one file.
        val items = all.distinctBy { it.audio to it.id }
        val (audio, text) = items.partition { it.audio }
        if (combined && text.isNotEmpty() && audio.isNotEmpty()) add(BulkRowUi(name, items))
        if (text.isNotEmpty()) add(BulkRowUi("$name · نصوص", text))
        if (audio.isNotEmpty()) add(BulkRowUi("$name · صوت", audio))
    }
    group("المكتبة كاملة", candidates, combined = true)
    BulkSections.forEach { (coll, name) -> group(name, candidates.filter { it.coll == coll }, combined = false) }
}
