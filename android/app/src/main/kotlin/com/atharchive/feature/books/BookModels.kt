package com.atharchive.feature.books

import com.atharchive.feature.downloads.TransferUi


enum class BookSort(val label: String) {
    Died("وفاة المؤلف"),
    Title("العنوان أ–ي"),
    Newest("الأحدث إضافة"),
    Author("المؤلف"),
    Pages("عدد الصفحات"),
}

sealed interface BookDownloadUi {
    val sizeLabel: String

    data class Available(override val sizeLabel: String) : BookDownloadUi
    data class Downloading(
        override val sizeLabel: String,
        val transfer: com.atharchive.feature.downloads.TransferUi,
    ) : BookDownloadUi

    data class Downloaded(override val sizeLabel: String) : BookDownloadUi
}

data class BookUi(
    val id: String,
    val title: String,
    val author: String,
    /** First entry of the catalog's `topics[]`, for display only. */
    val discipline: String,
    /** مرجع · متن · كتاب — the real work-type axis (main-plan.md §4.2). */
    val kind: String,
    val download: BookDownloadUi,
    val recentRank: Int? = null,
    val saved: Boolean = false,
    val readingPosition: String? = null,
    val readingProgress: Float? = null,
    val readingProgressLabel: String? = null,
    val topics: List<String> = listOf(discipline),
    val died: Int? = null,
    val hasAudio: Boolean = false,
    val publishedAt: String = "",
    /** Last printed page; with [volumes] > 1 the count is shown as volumes instead. */
    val pages: Int? = null,
    val volumes: Int? = null,
    /** "lesson"/"issue": [volumes] counts lessons or issues, not printed volumes. */
    val volumeUnit: String? = null,
    /** Which collection the row comes from: an author's page tabs by it. */
    val coll: String = "book",
)

private fun downloadOf(sizeLabel: String, downloaded: Boolean, downloading: Boolean, transfer: TransferUi?) = when {
    downloaded -> BookDownloadUi.Downloaded(sizeLabel)
    downloading || transfer != null -> BookDownloadUi.Downloading(sizeLabel, transfer ?: TransferUi.Running(0f, ""))
    else -> BookDownloadUi.Available(sizeLabel)
}

/** A poem as a row of the books list: an author's page and المتون list every kind of work alike. */
fun com.atharchive.feature.poetry.PoemUi.asBook() = BookUi(
    id = id, title = title, author = poet, discipline = topic, kind = kind,
    download = downloadOf(sizeLabel, downloaded, downloading, transfer),
    recentRank = recentRank, saved = saved, topics = topics.ifEmpty { listOf(topic) }, died = died, hasAudio = hasAudio, coll = "poem",
)

fun com.atharchive.feature.articles.ArticleUi.asBook() = BookUi(
    id = id, title = title, author = author, discipline = topics.firstOrNull().orEmpty(), kind = "مقال",
    download = downloadOf(sizeLabel, downloaded, downloading, transfer),
    recentRank = recentRank, saved = saved, topics = topics, died = died, hasAudio = hasAudio, coll = "article",
)

fun com.atharchive.feature.issues.IssueUi.asBook() = BookUi(
    id = id, title = question, author = scholar, discipline = topic, kind = "مسألة",
    download = downloadOf(sizeLabel, downloaded, false, transfer),
    recentRank = recentRank, saved = saved, topics = topics.ifEmpty { listOf(topic) }, died = died, hasAudio = hasAudio, coll = "question",
)

data class BooksUiState(
    val archiveCountLabel: String,
    val books: List<BookUi>,
    val loaded: Boolean = true,
)

val BooksFixture = BooksUiState(
    archiveCountLabel = "١٬٢٣٩ كتابًا",
    books = listOf(
        BookUi(
            id = "aqida-tahawiyya",
            title = "العقيدة الطحاوية",
            author = "أبو جعفر الطحاوي",
            discipline = "العقيدة",
            kind = "متن",
            download = BookDownloadUi.Available("13.9 MB"),
            recentRank = 1,
            saved = true,
            readingPosition = "الباب الثالث · ص ١٣١",
            readingProgress = 0.46f,
            readingProgressLabel = "٤٦٪",
        ),
        BookUi(
            id = "al-istiqaama",
            title = "الاستقامة",
            author = "أحمد بن عبد الحليم ابن تيمية",
            discipline = "العقيدة",
            kind = "مرجع",
            download = BookDownloadUi.Downloaded("8.4 MB"),
            recentRank = 2,
            readingPosition = "الفصل الثاني · ص ٧٤",
            readingProgress = 0.24f,
            readingProgressLabel = "٢٤٪",
        ),
        BookUi(
            id = "jami-bayan-al-ilm",
            title = "جامع بيان العلم وفضله",
            author = "ابن عبد البر",
            discipline = "العلم وآدابه",
            kind = "مرجع",
            download = BookDownloadUi.Available("18.2 MB"),
            saved = true,
        ),
        BookUi(
            id = "sharh-hilyat-talib-al-ilm",
            title = "شرح حلية طالب العلم",
            author = "محمد بن صالح العثيمين",
            discipline = "العلم وآدابه",
            kind = "كتاب",
            download = BookDownloadUi.Downloaded("12.6 MB"),
            recentRank = 3,
            readingPosition = "المجلس الأول · ص ٣٩",
            readingProgress = 0.12f,
            readingProgressLabel = "١٢٪",
        ),
        BookUi(
            id = "iqtida-al-sirat",
            title = "اقتضاء الصراط المستقيم",
            author = "أحمد بن عبد الحليم ابن تيمية",
            discipline = "العقيدة",
            kind = "مرجع",
            download = BookDownloadUi.Downloading(
                sizeLabel = "21.7 MB",
                transfer = com.atharchive.feature.downloads.TransferUi.Running(0.64f, "٦٤٪"),
            ),
            recentRank = 4,
            saved = true,
            readingPosition = "الباب السابع · ص ٢١٨",
            readingProgress = 0.64f,
            readingProgressLabel = "٦٤٪",
        ),
        BookUi(
            id = "rawdat-al-uqala",
            title = "روضة العقلاء ونزهة الفضلاء",
            author = "ابن حبان البستي",
            discipline = "الآداب",
            kind = "مرجع",
            download = BookDownloadUi.Available("9.6 MB"),
        ),
        BookUi(
            id = "khalq-afal-al-ibad",
            title = "خلق أفعال العباد",
            author = "محمد بن إسماعيل البخاري",
            discipline = "العقيدة",
            kind = "متن",
            download = BookDownloadUi.Available("4.7 MB"),
            saved = true,
        ),
    ),
)
