package com.atharchive.feature.search

/**
 * Search is an information-retrieval surface, not a content list with a box on top.
 *
 * Three rules shape this model:
 *
 * 1. **One relevance-ranked stream.** The unit of ranking is the *passage*: a book sits
 *    where its best passage ranked, with its other passages one tap away under it, so one
 *    book cannot fill the page and none of its hits is hidden for good.
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
    Died("وفاة المؤلف"),
}

/** How the words of the query must appear. */
enum class SearchMode(val label: String, val api: String) {
    All("كل الكلمات", "all"),
    Any("بعض الكلمات", "any"),
    Phrase("عبارة متتالية", "phrase"),
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
    /** Every highlighted range in [excerpt] (online snippets mark each word). */
    val matches: List<IntRange> = listOf(matchStart until matchEnd),
    val died: Int? = null,
    /** Set for an online hit: the site's url for the passage (its chapter), used to locate it and for «المزيد». */
    val url: String? = null,
)

data class SearchFilters(
    val types: Set<SearchResultType> = emptySet(),
    val field: SearchField = SearchField.FullText,
    val authors: Set<String> = emptySet(),
    val sort: SearchSort = SearchSort.Relevance,
    /** For [SearchSort.Died]: latest death first. */
    val descending: Boolean = false,
    val mode: SearchMode = SearchMode.All,
    /** Search only what is on the phone, even when online. */
    val downloadedOnly: Boolean = false,
)

/** Where the passages came from, said above the results. */
enum class SearchScope { Online, Offline, Downloaded }

/** «Who talks about this most»: an author and how many passages of theirs match. */
data class AuthorCountUi(val name: String, val count: Int)

data class SearchUiState(
    val query: String = "",
    val filters: SearchFilters = SearchFilters(),
    val recentQueries: List<String>,
    val scope: SearchScope = SearchScope.Offline,
    /** Matching passages in the whole library (online only; null until the count arrives). */
    val total: Int? = null,
    val totalCapped: Boolean = false,
    val topAuthors: List<AuthorCountUi> = emptyList(),
    val searching: Boolean = false,
    val hasMore: Boolean = false,
    val directMatches: List<DirectMatchUi>,
    val hits: List<SearchHitUi>,
    val availableAuthors: List<String>,
)

val SearchFixture = SearchUiState(
    recentQueries = listOf("الإخلاص", "ابن تيمية", "طلب العلم", "العقيدة الطحاوية", "مكارم الأخلاق"),
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
    // Results 1 and 2: same book, different passages; the screen shows the second under the first.
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
