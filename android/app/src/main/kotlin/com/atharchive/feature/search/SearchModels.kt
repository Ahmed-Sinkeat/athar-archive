package com.atharchive.feature.search

/**
 * Search is an information-retrieval surface, not a content list with a box on top.
 *
 * Three rules shape this model:
 *
 * 1. **One relevance-ranked stream.** Results are never grouped by source or type by
 *    default, and the unit of ranking is the *passage*, not the book — four strong hits
 *    from one book outrank one weak hit from four books, and all four are shown.
 * 2. **Filters are not navigation.** There are no permanent content-type tabs; the user
 *    must never choose where to search before seeing results.
 * 3. **Local full text is a scope, not an error.** The catalog remains complete while
 *    passage hits come from readable blocks already indexed on this device.
 */

enum class SearchResultType(val key: String, val label: String) {
    Book("book", "كتاب"),
    Poem("poem", "قصيدة"),
    Article("article", "مقال"),
    Issue("issue", "مسألة"),
}

/** Which part of a document the query is matched against. */
enum class SearchField(val label: String) {
    FullText("النص الكامل"),
    Title("العنوان"),
    Author("المؤلف"),
    Topic("الموضوع"),
}

enum class SearchSort(val label: String) {
    Relevance("الأكثر صلة"),
    Newest("الأحدث"),
}

/**
 * A strong match on an entity itself rather than on text inside one — a topic, a book
 * title, an author. Surfaced above the stream only when the match is unambiguous.
 */
data class DirectMatchUi(
    val id: String,
    val title: String,
    val kindLabel: String,
    val contextLabel: String,
    val type: SearchResultType,
)

/**
 * One passage. [matchStart]/[matchEnd] are UTF-16 offsets into [excerpt] as stored, so the
 * highlight lands on the vocalised match including its diacritics rather than on a
 * normalised copy of it (main-plan.md §10.2).
 */
data class SearchHitUi(
    val id: String,
    val entityId: String,
    val ordinal: Int,
    val excerpt: String,
    val matchStart: Int,
    val matchEnd: Int,
    /** UTF-16 offsets in the complete source block, used by tap-to-paragraph. */
    val sourceMatchStart: Int = matchStart,
    val sourceMatchEnd: Int = matchEnd,
    val sourceTitle: String,
    val sourceAuthor: String,
    val locationLabel: String?,
    val type: SearchResultType,
)

data class SearchFilters(
    val types: Set<SearchResultType> = emptySet(),
    val field: SearchField = SearchField.FullText,
    val sources: Set<String> = emptySet(),
    val authors: Set<String> = emptySet(),
    val sort: SearchSort = SearchSort.Relevance,
) {
    val isActive: Boolean
        // `this.` is required: a bare `field` inside a getter is the backing field.
        get() = types.isNotEmpty() || sources.isNotEmpty() || authors.isNotEmpty() ||
            this.field != SearchField.FullText || sort != SearchSort.Relevance
}

data class SearchUiState(
    val query: String = "",
    val filters: SearchFilters = SearchFilters(),
    val recentQueries: List<String>,
    /** Full text is local in v1; catalog metadata still covers every catalogued work. */
    val fullTextLocalOnly: Boolean = true,
    val searching: Boolean = false,
    val directMatches: List<DirectMatchUi>,
    val hits: List<SearchHitUi>,
    val availableSources: List<String>,
    val availableAuthors: List<String>,
    /** Set when search was opened from inside one work: "الاستقامة ×". */
    val scopedToSource: String? = null,
)

val SearchFixture = SearchUiState(
    recentQueries = listOf("الإخلاص", "ابن تيمية", "طلب العلم", "العقيدة الطحاوية", "مكارم الأخلاق"),
    availableSources = listOf(
        "الاستقامة",
        "جامع بيان العلم وفضله",
        "شرح حلية طالب العلم",
        "مدارج السالكين",
    ),
    availableAuthors = listOf(
        "ابن تيمية",
        "ابن عبد البر",
        "محمد بن صالح العثيمين",
        "ابن قيم الجوزية",
    ),
    directMatches = listOf(
        DirectMatchUi(
            id = "topic-ikhlas",
            title = "باب الإخلاص",
            kindLabel = "موضوع",
            contextLabel = "في ٤ كتب",
            type = SearchResultType.Book,
        ),
    ),
    // Note results 1 and 2: same book, different passages, both kept and independently
    // ranked. Collapsing them into "الاستقامة — نتيجتان" would hide the second-best hit.
    hits = listOf(
        SearchHitUi(
            id = "hit-istiqama-74",
            entityId = "al-istiqaama",
            ordinal = 74,
            excerpt = "فأصل العمل الإخلاص ومتابعة السنة جميعًا",
            matchStart = 14,
            matchEnd = 21,
            sourceTitle = "الاستقامة",
            sourceAuthor = "ابن تيمية",
            locationLabel = "الفصل الثاني · ص ٧٤",
            type = SearchResultType.Book,
        ),
        SearchHitUi(
            id = "hit-istiqama-121",
            entityId = "al-istiqaama",
            ordinal = 121,
            excerpt = "ولا يكون العمل صالحًا إلا بالإخلاص لله",
            matchStart = 26,
            matchEnd = 34,
            sourceTitle = "الاستقامة",
            sourceAuthor = "ابن تيمية",
            locationLabel = "باب العبادة · ص ١٢١",
            type = SearchResultType.Book,
        ),
        SearchHitUi(
            id = "hit-jami-53",
            entityId = "jami-bayan-al-ilm",
            ordinal = 53,
            excerpt = "الإخلاص أصل في طلب العلم والعمل به",
            matchStart = 0,
            matchEnd = 8,
            sourceTitle = "جامع بيان العلم وفضله",
            sourceAuthor = "ابن عبد البر",
            locationLabel = "ص ٥٣",
            type = SearchResultType.Book,
        ),
        SearchHitUi(
            id = "hit-madarij-312",
            entityId = "madarij-salikin",
            ordinal = 312,
            excerpt = "أخلصت لله أمرًا فأقبلت روحي … ونلتُ رضاه والجنةَ دارَ قرار",
            matchStart = 0,
            matchEnd = 6,
            sourceTitle = "مدارج السالكين",
            sourceAuthor = "ابن قيم الجوزية",
            locationLabel = "ص ٣١٢",
            type = SearchResultType.Poem,
        ),
        SearchHitUi(
            id = "hit-hilya-48",
            entityId = "sharh-hilyat-talib-al-ilm",
            ordinal = 48,
            excerpt = "وما كان لله دام واتصل، والإخلاص سر بين العبد وربه",
            matchStart = 22,
            matchEnd = 30,
            sourceTitle = "شرح حلية طالب العلم",
            sourceAuthor = "محمد بن صالح العثيمين",
            locationLabel = "ص ٤٨",
            type = SearchResultType.Book,
        ),
        SearchHitUi(
            id = "hit-article-ikhlas",
            entityId = "article-ikhlas",
            ordinal = 0,
            excerpt = "الإخلاص شرط في قبول العمل، فلا يقبل الله من العمل إلا ما كان خالصًا لوجهه",
            matchStart = 0,
            matchEnd = 8,
            sourceTitle = "الإخلاص وأثره في قبول العمل",
            sourceAuthor = "صالح بن فوزان الفوزان",
            locationLabel = null,
            type = SearchResultType.Article,
        ),
    ),
)
