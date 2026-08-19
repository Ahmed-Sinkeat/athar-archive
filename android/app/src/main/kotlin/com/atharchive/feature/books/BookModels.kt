package com.atharchive.feature.books

import com.atharchive.ui.components.AtharTab

enum class BooksTab(val key: String, val label: String) {
    All("all", "الكل"),
    Recent("recent", "الأخيرة"),
    Downloaded("downloaded", "المحمّلة"),
    MyList("mylist", "قائمتي"),
    ;

    companion object {
        val tabs: List<AtharTab> = entries.map { AtharTab(it.key, it.label) }
        fun fromKey(key: String): BooksTab = entries.first { it.key == key }
    }
}

enum class BookSort(val label: String) {
    Newest("الأحدث"),
    Title("العنوان أ–ي"),
    Author("المؤلف"),
}

sealed interface BookDownloadUi {
    val sizeLabel: String

    data class Available(override val sizeLabel: String) : BookDownloadUi
    data class Downloading(
        override val sizeLabel: String,
        val progress: Float,
        val progressLabel: String,
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
)

data class BooksUiState(
    val archiveCountLabel: String,
    val books: List<BookUi>,
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
                progress = 0.64f,
                progressLabel = "٦٤٪",
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
