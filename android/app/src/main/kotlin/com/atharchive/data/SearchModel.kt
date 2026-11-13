package com.atharchive.data

import com.atharchive.core.data.repository.exactMatch
import com.atharchive.core.data.repository.persons
import com.atharchive.ui.components.arabicDigits
import androidx.tracing.Trace
import com.atharchive.core.data.repository.BlockSearchHit
import com.atharchive.core.data.db.content.ContentEntity
import com.atharchive.core.data.repository.ContentSearchField
import com.atharchive.core.data.repository.ContentSearchFilter
import com.atharchive.core.data.repository.ContentSearchRepository
import com.atharchive.core.data.repository.ContentSearchRequest
import com.atharchive.core.data.repository.ContentSearchSort
import com.atharchive.core.data.repository.PersonalLibraryRepository
import com.atharchive.feature.reader.InBookHit
import com.atharchive.feature.reader.InBookSearch
import com.atharchive.feature.search.DirectMatchUi
import com.atharchive.feature.search.SearchField
import com.atharchive.feature.search.SearchFilters
import com.atharchive.feature.search.SearchHitUi
import com.atharchive.feature.search.SearchResultType
import com.atharchive.feature.search.SearchSort
import com.atharchive.feature.search.SearchUiState
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import com.atharchive.feature.search.AuthorCountUi
import com.atharchive.feature.search.SearchScope
import com.atharchive.feature.search.SearchMode
import com.atharchive.core.ArNormalize
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch


private const val SearchDebounceMs = 60L
private const val OnlineSearchPauseMs = 150L
private const val FacetsWaitMs = 1_500L
/** Before the first page shows: the counts and authors row usually land with it rather than push it down after. */
private const val PartialFacetsWaitMs = 300L
private const val SearchPageSize = 40
// a list search keeps the best 3000; the largest list (المقالات) is ~2000, raise it with the catalog.
private const val CatalogMatchLimit = 3000
private const val SearchLatencyTraceName = "AtharSearchKeystrokeToResults"

private data class SearchInput(
    val query: String,
    val filters: SearchFilters,
    val catalog: SearchCatalog,
)

private class SearchCatalog(entities: List<ContentEntity>) {
    val byId = entities.associateBy { it.id }
    val slugByName = entities.filter { it.person != null && it.personName != null }.associate { it.personName!! to it.person!! }
    val nameBySlug = slugByName.entries.associate { (name, slug) -> slug to name }
    val authors = slugByName.keys.sortedBy(ArNormalize::normalize)
    val idsByTitle = entities.filter { it.coll == "book" }.groupBy({ it.title }, { it.id })
    val books = idsByTitle.keys.sortedBy(ArNormalize::normalize)
}

/** «القرن ٨» → 8. */
/**
 * A poem passage's blocks, 16 more each side: the index places it by its letters, and the باب
 * headings and prose it leaves out move an edge by a few blocks (rarely ~25-40; measured over every
 * poem 2026-11-14). The best match by the snippet's words is still taken inside it.
 */
/**
 * Of the occurrences in [text] of the words at [start]..[end], the one whose surroundings share most
 * of [words] (an online hit's snippet); ties keep the earliest. A word said five times in a long
 * paragraph used to land on the first of them whichever the hit was.
 */
internal fun nearestOccurrence(text: String, start: Int, end: Int, words: Set<String>): Pair<Int, Int> {
    if (words.isEmpty() || start !in 0 until end || end > text.length) return start to end
    val mapped = com.atharchive.core.ArNormalize.normalizeWithMap(text)
    val term = com.atharchive.core.ArNormalize.normalize(text.substring(start, end))
    if (term.isEmpty()) return start to end
    var best = start to end
    var bestScore = -1
    var at = mapped.text.indexOf(term)
    while (at >= 0) {
        val around = mapped.text.substring((at - NearContext).coerceAtLeast(0), (at + term.length + NearContext).coerceAtMost(mapped.text.length))
        val have = around.split(' ', '\n').toHashSet()
        val score = words.count { it in have }
        if (score > bestScore) {
            bestScore = score
            val range = mapped.sourceRange(at, at + term.length)
            best = range.first to range.last + 1
        }
        at = mapped.text.indexOf(term, at + 1)
    }
    return best
}

/** Characters each side of an occurrence compared with the snippet (the site's snippets run ~70 before, ~100 after). */
private const val NearContext = 90

internal fun widenedPoemRange(pg: String): String? =
    pg.split('-').mapNotNull(String::toIntOrNull).takeIf { it.size == 2 }?.let { (from, to) -> "${(from - 16).coerceAtLeast(0)}-${to + 16}" }

private fun centuryNumber(label: String) = com.atharchive.data.AllCenturies.indexOf(label) + 1

private data class SearchTrace(val query: String, val cookie: Int)

private fun SearchField.toContentField(): ContentSearchField = when (this) {
    SearchField.FullText -> ContentSearchField.FullText
    SearchField.Title -> ContentSearchField.Title
    SearchField.Author -> ContentSearchField.Author
    SearchField.Topic -> ContentSearchField.Topic
}

private val SearchResultType.contentCollection: String
    get() = when (this) {
        SearchResultType.Book -> "book"
        SearchResultType.Poem -> "poem"
        SearchResultType.Article -> "article"
        SearchResultType.Issue -> "question"
    }

private fun String.toResultType(): SearchResultType = when (this) {
    "poem" -> SearchResultType.Poem
    "article" -> SearchResultType.Article
    "question" -> SearchResultType.Issue
    else -> SearchResultType.Book
}

/**
 * Search (3.1): the search screen's query, filters and results, local and online, in-book search,
 * locating an online hit in a book, recent searches and saved filter sets. Built by
 * [AtharContentViewModel] on its scope; the screens reach it through there.
 */
internal class SearchModel(
    private val scope: kotlinx.coroutines.CoroutineScope,
    private val savedState: androidx.lifecycle.SavedStateHandle,
    private val content: ContentAccess,
    private val contentSearch: ContentSearchRepository,
    private val dictionary: Dictionary,
    private val presetStore: SearchPresetStore,
    private val personalLibrary: PersonalLibraryRepository,
    private val errorHandler: CoroutineExceptionHandler,
) {
    private val catalogDao = content.contentDatabase.catalogDao()
    private val importDao = content.contentDatabase.importDao()

    private fun <T> Flow<T>.pausedDuringUpdate(): Flow<T> =
        content.catalogUpdate.map { it != null }.distinctUntilChanged().flatMapLatest { updating ->
            if (updating) kotlinx.coroutines.flow.emptyFlow() else this
        }

    val searchPresets = presetStore.presets
    fun saveSearchPreset(name: String) = presetStore.save(name, _searchFilters.value)
    fun deleteSearchPreset(name: String) = presetStore.delete(name)
    private val recentSearches = personalLibrary.observeRecentSearches()
    private val _searchQuery = MutableStateFlow(savedState.get<String>("searchQuery").orEmpty())
    private val _searchFilters = MutableStateFlow(SearchFilters(
        types = savedState.get<ArrayList<String>>("searchTypes").orEmpty().mapNotNull { key -> SearchResultType.entries.find { it.name == key } }.toSet(),
        field = SearchField.entries.find { it.name == savedState.get<String>("searchField") } ?: SearchField.FullText,
        authors = savedState.get<ArrayList<String>>("searchAuthors").orEmpty().toSet(),
        sort = SearchSort.entries.find { it.name == savedState.get<String>("searchSort") } ?: SearchSort.Relevance,
        descending = savedState.get<Boolean>("searchDescending") ?: false,
        mode = SearchMode.entries.find { it.name == savedState.get<String>("searchMode") } ?: SearchMode.All,
        downloadedOnly = savedState.get<Boolean>("searchDownloadedOnly") ?: false,
        books = savedState.get<ArrayList<String>>("searchBooks").orEmpty().toSet(),
        centuries = savedState.get<ArrayList<String>>("searchCenturies").orEmpty().toSet(),
        sections = savedState.get<ArrayList<String>>("searchSections").orEmpty().toSet(),
        plain = savedState.get<Boolean>("searchPlain") ?: false,
    ))
    val searchQuery: StateFlow<String> = _searchQuery
    private val _searchLimit = MutableStateFlow(SearchPageSize)
    private val onlineSearch = OnlineSearch(content.httpClient)
    private var lastFacets: Pair<OnlineSearch.Query, OnlineSearch.Facets>? = null
    private val searchTraceSequence = AtomicInteger()
    private var activeSearchTrace: SearchTrace? = null
    // Room emits the catalog again after every update (and any entity write) though nothing a search
    // reads changed: each emission re-ran the search, emptied the passages and refilled them, the
    // list jumping back to its top under the reader. Shared with the section searches, which loaded
    // their section's whole rows, every column, per search.
    private val searchCatalog = catalogDao.observeCatalogLean().pausedDuringUpdate().distinctUntilChanged().map { SearchCatalog(it) }
        .shareIn(scope, SharingStarted.Eagerly, replay = 1)

    val search: StateFlow<SearchUiState> = combine(
        // Leave half of the §17 120 ms keystroke-to-results target for Room and drawing.
        _searchQuery.debounce(SearchDebounceMs),
        // «إخفاء التشكيل» is display only: toggling it must not search again.
        _searchFilters.map { it.copy(plain = false) }.distinctUntilChanged(),
        // The filter lists are built once per catalog change, not on every keystroke.
        searchCatalog,
    ) { query, filters, catalog ->
        SearchInput(query, filters, catalog)
    }.combine(_searchLimit) { input, limit -> input to limit }.transformLatest { (input, limit) ->
        val clean = input.query.trim()
        val base = SearchUiState(
            query = input.query,
            filters = input.filters,
            recentQueries = emptyList(),
            directMatches = emptyList(),
            hits = emptyList(),
            availableAuthors = input.catalog.authors,
            availableBooks = input.catalog.books,
            scope = if (input.filters.downloadedOnly) SearchScope.Downloaded else SearchScope.Online,
        )
        if (clean.length < 2) return@transformLatest emit(base)
        val catalog = input.catalog
        val filters = input.filters
        // «بالجذر»: the root's forms, searched as «بعض الكلمات»; without a root, the words as typed.
        val rootForms = if (filters.mode == SearchMode.Root) dictionary.rootForms(clean) else null
        val roots = if (filters.mode == SearchMode.Root) rootForms?.first.orEmpty() else null
        // Titles and authors always come from the catalog on the phone: complete, instant.
        val local = contentSearch.search(
            ContentSearchRequest(
                // الدقة offline: a phrase is the query quoted (verified for adjacency), «بعض الكلمات» joins with OR.
                query = rootForms?.second?.joinToString(" ") ?: if (filters.mode == SearchMode.Phrase || filters.mode == SearchMode.Exact) "\"${clean.replace("\"", "")}\"" else clean,
                anyWord = filters.mode == SearchMode.Any || rootForms != null,
                exact = filters.mode == SearchMode.Exact,
                field = filters.field.toContentField(),
                sort = if (filters.sort == SearchSort.Book) ContentSearchSort.BookOrder else ContentSearchSort.Relevance,
                filter = ContentSearchFilter(
                    collections = filters.types.mapTo(linkedSetOf()) { it.contentCollection },
                    authors = filters.authors,
                    entityIds = filters.books.takeIf { it.isNotEmpty() }?.flatMapTo(hashSetOf()) { catalog.idsByTitle[it].orEmpty() },
                    topics = topicsIn(filters.sections),
                    // several centuries search their whole span (٣ and ٨ → ٣ to ٨); per-century ranges if anyone asks
                    diedFrom = filters.centuries.minOfOrNull(::centuryNumber)?.let { (it - 1) * 100 + 1 },
                    diedTo = filters.centuries.maxOfOrNull(::centuryNumber)?.let { it * 100 },
                ),
            ),
            limit = limit + 1,
        )
        val direct = local.catalog.take(limit).map { hit ->
            DirectMatchUi(
                id = hit.entityId,
                title = hit.title,
                kindLabel = hit.kind.ifBlank { hit.collection.toResultType().label },
                contextLabel = hit.personName,
                type = hit.collection.toResultType(),
            )
        }
        fun localState(scope: SearchScope) = base.copy(
            scope = scope,
            roots = roots,
            hasMore = local.catalog.size > limit || local.blocks.size > limit,
            directMatches = direct,
            hits = local.blocks.take(limit)
                .let { hits -> if (filters.sort == SearchSort.Died) hits.sortedWith(compareBy(nullsLast(if (filters.descending) reverseOrder() else naturalOrder())) { catalog.byId[it.entityId]?.died }) else hits }
                .map { it.toHitUi(catalog) },
        )
        if (filters.downloadedOnly || filters.field != SearchField.FullText) // Titles/authors/topics: the catalog on the phone already covers the whole library.
            return@transformLatest emit(localState(if (filters.downloadedOnly) SearchScope.Downloaded else SearchScope.Online))
        // Titles show at once; passages wait for the whole library's answer and land in one go. Showing the
        // phone's own passages first, then swapping them for the library's, moved the list under the reader.
        // «المزيد» keeps the current list up until the longer one arrives.
        if (limit == SearchPageSize) emit(localState(SearchScope.Online).copy(searching = true, hasMore = false, hits = emptyList()))
        // a fixed pause before the network call; cancelled by the next keystroke
        delay(OnlineSearchPauseMs)
        val query = OnlineSearch.Query(
            q = rootForms?.second?.joinToString(" ") ?: clean,
            mode = if (rootForms != null) "root" else if (filters.mode == SearchMode.Root) "all" else filters.mode.api,
            field = "all",
            types = (filters.types.ifEmpty { SearchResultType.entries.toSet() }).mapTo(linkedSetOf()) { it.contentCollection },
            persons = filters.authors.mapNotNullTo(linkedSetOf()) { catalog.slugByName[it] },
            sort = when (filters.sort) { SearchSort.Relevance -> "relevance"; SearchSort.Book -> "book"; SearchSort.Died -> if (filters.descending) "death_desc" else "death" },
            books = filters.books.flatMapTo(linkedSetOf()) { catalog.idsByTitle[it].orEmpty() },
            centuries = filters.centuries.mapTo(linkedSetOf(), ::centuryNumber),
            topics = topicsIn(filters.sections),
        )
        val exact = if (filters.mode == SearchMode.Exact) clean.replace("\"", "") else null
        // the site has no exact mode; its phrase hits are kept when their snippet has the letters as typed (a hit whose snippet cut the phrase is dropped).
        fun exactOnly(hits: List<OnlineSearch.Hit>) = if (exact == null) hits else hits.filter { exactMatch(unmark(it.snippet).first, exact) != null }
        coroutineScope {
            // The counts are asked for beside the passages, not after them: they used to pop in a
            // request later (8.14). Same query, more pages: the counts don't change, so not asked again.
            val facetsAsync = async {
                lastFacets?.takeIf { it.first == query }?.second
                    ?: runCatching { onlineSearch.facets(query) }.getOrNull()?.also { lastFacets = query to it }
            }
            suspend fun counted(state: SearchUiState): SearchUiState {
                val facets = if (facetsAsync.isCompleted) facetsAsync.await() else null
                return if (facets == null) state else state.copy(
                    total = facets.total,
                    totalCapped = facets.capped,
                    topAuthors = facets.persons.mapNotNull { p -> catalog.nameBySlug[p.slug]?.let { AuthorCountUi(it, p.count) } }.take(8),
                )
            }
            var shownPartial = false
            val online = try {
                // The first page shows as it lands (~1 s a page); the rest is added under it, so what
                // is on screen never moves. Only for a new search: «المزيد» refetches from the top, and
                // its first pages would shorten the list already shown.
                onlineSearch.hits(query, limit) { partial, _ ->
                    if (limit != SearchPageSize) return@hits
                    kotlinx.coroutines.withTimeoutOrNull(PartialFacetsWaitMs) { facetsAsync.await() }
                    shownPartial = true
                    emit(counted(base.copy(
                        scope = SearchScope.Online, roots = roots, directMatches = direct, hasMore = false, searching = true,
                        hits = exactOnly(partial).mapIndexedNotNull { index, hit -> hit.toHitUi(index, catalog) },
                    )))
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled
            } catch (offline: Exception) { null }
            if (online == null) {
                facetsAsync.cancel()
                Analytics.hit("search", "offline")
                emit(localState(SearchScope.Offline))
                return@coroutineScope
            }
            val (hits, more) = online.let { (h, m) -> exactOnly(h) to m }
            Analytics.hit("search", if (hits.isEmpty()) "none" else "online")
            val state = base.copy(
                scope = SearchScope.Online,
                roots = roots,
                directMatches = direct,
                hasMore = more,
                // A work the site has and this app's catalog doesn't can't be opened: leave it out.
                hits = hits.mapIndexedNotNull { index, hit -> hit.toHitUi(index, catalog) },
            )
            // Passages, counts and top authors land together (the authors row used to push the list down
            // a moment later); only counts slower than this follow on their own.
            // (Not once the first page is up: the rest would wait on them.)
            if (shownPartial && !facetsAsync.isCompleted) {
                emit(state)
                facetsAsync.await()
            } else if (kotlinx.coroutines.withTimeoutOrNull(FacetsWaitMs) { facetsAsync.await() } == null) {
                emit(state)
                facetsAsync.await()
            }
            emit(counted(state))
        }
    }.combine(_searchFilters) { state, filters -> state.copy(filters = filters) }
        // Recents join after the search: opening a result saves its query, and as an input that re-ran
        // the whole search under the reader, emptying the list (back landed at its top, 8.12).
        .combine(recentSearches) { state, recent -> state.copy(recentQueries = recent.map { it.q }) }.stateIn(
        scope,
        // Kept while a result is open in the reader: on WhileSubscribed the way back re-ran the search
        // and the list rebuilt page by page under the reader. Stops only with the view model.
        SharingStarted.Eagerly,
        SearchUiState(
            recentQueries = emptyList(),
            directMatches = emptyList(),
            hits = emptyList(),
            availableAuthors = emptyList(),
        ),
    )

    private fun BlockSearchHit.toHitUi(catalog: SearchCatalog) = SearchHitUi(
        id = "$entityId:$rowid",
        entityId = entityId,
        ordinal = ordinal,
        excerpt = excerpt,
        matchStart = matchStart,
        matchEnd = matchEnd,
        sourceMatchStart = sourceMatchStart,
        sourceMatchEnd = sourceMatchEnd,
        sourceTitle = sourceTitle,
        sourceAuthor = sourceAuthor,
        locationLabel = listOfNotNull(
            chapterTitle.ifBlank { null },
            printedPage?.let { "ص ${arabicNumber(it)}" },
        ).joinToString(" · ").ifBlank { null },
        type = collection.toResultType(),
        died = catalog.byId[entityId]?.died,
    )

    /**
     * Passages inside one section (articles, poems, مسائل), for that section's own search box:
     * the whole library online, what is on the phone otherwise.
     */
    /**
     * The section lists' search: the main search's catalog match (same normalisation, «ال», word
     * order), best first, so a title found in البحث is found in الشعر or المتون too.
     */
    suspend fun catalogMatches(query: String, collections: Set<String>): List<String> =
        contentSearch.search(
            ContentSearchRequest(query, ContentSearchField.Catalog, filter = ContentSearchFilter(collections = collections)),
            limit = CatalogMatchLimit,
        ).catalog.map { it.entityId }

    suspend fun searchSection(type: SearchResultType, query: String): List<SearchHitUi> {
        val catalog = searchCatalog.first()
        val online = try {
            onlineSearch.hits(OnlineSearch.Query(query, "all", "text", setOf(type.contentCollection), emptySet(), "relevance"), 20).first
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled
        } catch (offline: Exception) { null }
        if (online != null) return online.mapIndexedNotNull { index, hit -> hit.toHitUi(index, catalog) }
        return localSectionHits(type, query, catalog)
    }

    private suspend fun localSectionHits(type: SearchResultType, query: String, known: SearchCatalog): List<SearchHitUi> {
        return contentSearch.search(
            ContentSearchRequest(query = query, filter = ContentSearchFilter(collections = setOf(type.contentCollection))),
            limit = 20,
        ).blocks.map { it.toHitUi(known) }
    }

    private fun OnlineSearch.Hit.toHitUi(index: Int, catalog: SearchCatalog): SearchHitUi? {
        val id = if (type == "book") book else android.net.Uri.decode(url.trimEnd('/').substringAfterLast('/'))
        val entity = catalog.byId[id] ?: return null
        val (text, marks) = unmark(snippet)
        val first = marks.firstOrNull() ?: 0..-1
        return SearchHitUi(
            id = "online:$index:$url",
            entityId = id,
            ordinal = -1,
            excerpt = text,
            matchStart = first.first,
            matchEnd = first.last + 1,
            sourceTitle = entity.title,
            sourceAuthor = entity.personName.orEmpty(),
            // The site's «صفحات 17-40» in Western digits reads 40-17 in an RTL line; the app's digits read in order.
            locationLabel = title.substringAfter(" — ", "").ifBlank { null }
                ?.let { arabicDigits(it.replace(Regex("""(\d)-(\d)"""), "$1–$2")) },
            type = entity.coll.toResultType(),
            matches = marks,
            died = entity.died,
            url = url,
            row = row,
            pg = pg,
        )
    }

    /** The whole passage behind an online hit, as written, for «المزيد». */
    suspend fun onlinePassage(hit: SearchHitUi): String? =
        hit.url?.let { url -> runCatching { onlineSearch.fullText(url, hit.row) }.getOrNull()?.ifBlank { null } }

    /**
     * Where an online hit sits in the app's own text: its chapter (a book url's slug is the chapter
     * anchor), then the first match of [query] in that chapter's loaded frame; an article, poem or
     * مسألة is searched from its start. Null ordinal: open it as usual.
     */
    suspend fun locateOnlineHit(hit: SearchHitUi, query: String): Triple<Int?, Int, Int> =
        locateOnline(hit.entityId, hit.url, query, hit.excerpt, if (hit.type == SearchResultType.Poem) hit.pg?.let(::widenedPoemRange) else hit.pg)

    /**
     * The block behind an online hit: its chapter (or the site's «--pages-85-124» piece of it) is
     * opened frame by frame, and of the blocks there that match, the one sharing most words with the
     * hit's own snippet wins. It used to take the chapter's first match: a tap on the fifth hit in
     * a 40-page piece landed on the first.
     */
    suspend fun locateOnline(entityId: String, url: String?, query: String, snippet: String = "", pg: String? = null, walk: Boolean = true): Triple<Int?, Int, Int> {
        val slug = url?.substringAfter("/$entityId/", "")?.let(android.net.Uri::decode).orEmpty()
        if (query.length < 2) return Triple(null, 0, 0)
        // The index knows the passage's blocks: only their frames are opened (online too), no chapter walk.
        pg?.split('-')?.mapNotNull(String::toIntOrNull)?.takeIf { it.size == 2 && it[0] <= it[1] }?.let { (from, to) ->
            // past the last block (a widened poem range) it would fail and walk the chapter instead
            val last = minOf(to, (catalogDao.entity(entityId)?.pkgBlocks ?: Int.MAX_VALUE) - 1)
            runCatching { bestMatch(entityId, from..last, query, snippetWords(snippet), frameLimit = 4) }
                .getOrElse { if (it is kotlinx.coroutines.CancellationException) throw it else null }
                ?.let { return Triple(it.ordinal, it.sourceMatchStart, it.sourceMatchEnd) }
        }
        if (!walk) return Triple(null, 0, 0)
        return runCatching {
            content.open(entityId, 0) // the first frame carries the chapter list
            val chapters = if (slug.isEmpty()) emptyList() else importDao.observeChapters(entityId).first()
            val index = chapters.indexOfFirst { it.anchor == slug.substringBefore("--pages-") }
            if (slug.isNotEmpty() && index < 0) return@runCatching Triple(null, 0, 0)
            val from = chapters.getOrNull(index)?.firstOrdinal ?: 0
            val to = chapters.getOrNull(index + 1)?.firstOrdinal?.minus(1)
                ?: ((catalogDao.entity(entityId)?.pkgBlocks ?: Int.MAX_VALUE) - 1)
            // at most 40 frames of one chapter walked; a longer chapter falls back to its best match so far.
            val best = bestMatch(entityId, from..to, query, snippetWords(snippet), frameLimit = 40)
            best?.let { Triple(it.ordinal, it.sourceMatchStart, it.sourceMatchEnd) } ?: Triple(from, 0, 0)
        }.getOrElse { if (it is kotlinx.coroutines.CancellationException) throw it else Triple(null, 0, 0) }
    }

    /**
     * Of the blocks in [range] that match [query], the one sharing most of [words] (the hit's own
     * snippet), opening at most [frameLimit] frames in order and stopping once one shares 80%.
     */
    private suspend fun bestMatch(
        entityId: String, range: IntRange, query: String, words: Set<String>, frameLimit: Int,
    ): com.atharchive.core.data.repository.BlockSearchHit? {
        var best: Pair<Int, com.atharchive.core.data.repository.BlockSearchHit>? = null
        var at = range.first
        var frames = 0
        while (at <= range.last && frames++ < frameLimit) {
            content.open(entityId, at)
            val frame = importDao.frames(entityId).firstOrNull { at in it.firstBlockOrdinal until it.firstBlockOrdinal + it.blockCount } ?: break
            val end = minOf(frame.firstBlockOrdinal + frame.blockCount - 1, range.last)
            val hits = contentSearch.search(
                ContentSearchRequest(query = query, sort = ContentSearchSort.BookOrder,
                    filter = ContentSearchFilter(entityIds = setOf(entityId)), ordinals = at..end),
                limit = 200,
            ).blocks
            // One read for the frame's matching blocks, not one query per hit.
            val texts = if (hits.isEmpty()) emptyMap()
                else importDao.blockWindow(entityId, hits.first().ordinal, hits.last().ordinal).associate { it.ordinal to it.text }
            hits.forEach { hit ->
                val text = texts[hit.ordinal].orEmpty()
                val score = snippetWords(text).let { have -> words.count { it in have } }
                if (best == null || score > best!!.first) {
                    // The FTS match is the block's first occurrence; the snippet's own is the one nearest its words.
                    val (start, end) = nearestOccurrence(text, hit.sourceMatchStart, hit.sourceMatchEnd, words)
                    best = score to hit.copy(sourceMatchStart = start, sourceMatchEnd = end)
                }
            }
            if (best != null && (words.isEmpty() || best!!.first * 5 >= words.size * 4)) break
            at = end + 1
        }
        return best?.second
    }

    private fun snippetWords(text: String): Set<String> =
        com.atharchive.core.ArNormalize.normalize(text).split(' ', '\n', '…').filterTo(HashSet()) { it.length >= 2 }

    /**
     * One FTS query over the blocks already on the phone. It used to open every frame in turn,
     * fetching the missing ones one range request at a time: minutes for a large book like
     * مصنف ابن أبي شيبة on a slow link. A partly saved book says so, and the reader offers a
     * whole-book download (one stream) instead.
     */
    suspend fun searchInBook(
        entityId: String,
        query: String,
        mode: com.atharchive.feature.search.SearchMode = com.atharchive.feature.search.SearchMode.All,
        /** The site's first pages as they land, before the whole answer. */
        onPartial: (InBookSearch) -> Unit = {},
    ): InBookSearch {
        if (query.trim().length < 2) return InBookSearch(emptyList())
        val total = catalogDao.entity(entityId)?.pkgBlocks ?: 0
        val saved = importDao.frames(entityId).sumOf { it.blockCount }
        // first 200 source-order hits; paginate if more results are needed.
        val hits = contentSearch.search(
            ContentSearchRequest(
                // As on the search screen: a phrase is the query quoted, «بعض الكلمات» joins with OR.
                query = if (mode == com.atharchive.feature.search.SearchMode.Phrase || mode == com.atharchive.feature.search.SearchMode.Exact) "\"${query.trim().replace("\"", "")}\"" else query,
                sort = ContentSearchSort.BookOrder,
                anyWord = mode == com.atharchive.feature.search.SearchMode.Any,
                exact = mode == com.atharchive.feature.search.SearchMode.Exact,
                filter = ContentSearchFilter(entityIds = setOf(entityId))),
            limit = 200,
        ).blocks.map { hit ->
            InBookHit(
                id = hit.rowid.toString(), blockIndex = hit.ordinal,
                chapterTitle = hit.chapterTitle, excerpt = hit.excerpt,
                matchStart = hit.matchStart, matchEnd = hit.matchEnd,
                pageLabel = hit.printedPage?.let { "ص ${arabicNumber(it)}" },
                sourceMatchStart = hit.sourceMatchStart, sourceMatchEnd = hit.sourceMatchEnd,
            )
        }
        val complete = total == 0 || saved >= total
        if (!complete) onlineInBook(entityId, query, mode) { onPartial(InBookSearch(it)) }?.let { return InBookSearch(it) }
        return InBookSearch(hits, complete, if (complete) 100 else (saved * 100 / total).coerceIn(0, 99))
    }

    /** The whole paragraph behind an in-book hit, for «المزيد»: the saved block, else the site's passage. */
    suspend fun inBookPassage(entityId: String, hit: InBookHit): String? = runCatching {
        if (hit.url == null) importDao.blockAt(entityId, hit.blockIndex)?.text
        else onlineSearch.fullText(hit.url, hit.row).ifBlank { null }
    }.getOrElse { if (it is kotlinx.coroutines.CancellationException) throw it else null }

    /** The site's index for one book (every chapter that matches); null offline. A hit is located on tap. */
    private suspend fun onlineInBook(
        entityId: String,
        query: String,
        mode: com.atharchive.feature.search.SearchMode,
        onPage: (List<InBookHit>) -> Unit,
    ): List<InBookHit>? = try {
        // The first page shows at once: the rest (up to five pages, one after another) fills in under it.
        onlineSearch.hits(OnlineSearch.Query(query, mode.api, "text", emptySet(), emptySet(), "relevance", books = setOf(entityId)), 100) { partial, _ ->
            onPage(partial.toInBookHits())
        }.first.toInBookHits()
    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled
    } catch (offline: Exception) { null }

    private fun List<OnlineSearch.Hit>.toInBookHits(): List<InBookHit> =
            mapIndexed { index, hit ->
                val (text, marks) = unmark(hit.snippet)
                val first = marks.firstOrNull() ?: 0..-1
                // The site splits a long chapter into «صفحات 85-124» pieces: the chapter's name comes from the url, the pages from the title.
                val place = hit.title.substringAfter(" — ", "")
                val pages = place.takeIf { it.startsWith("صفحات") }
                InBookHit(
                    id = "online:$index:${hit.url}", blockIndex = -1,
                    chapterTitle = if (pages == null) place else android.net.Uri.decode(hit.url.substringAfterLast('/').substringBefore("--pages-")).replace('-', ' '),
                    excerpt = text, matchStart = first.first, matchEnd = first.last + 1,
                    pageLabel = pages?.let { arabicDigits(it.replace(Regex("""(\d)-(\d)"""), "$1–$2")) },
                    url = hit.url, matches = marks, row = hit.row, pg = hit.pg,
                )
            }

    val recentQueries: StateFlow<List<String>> = recentSearches.map { rows -> rows.map { it.q } }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun rememberSearch(query: String) {
        scope.launch(errorHandler) { personalLibrary.rememberSearch(query) }
    }

    fun loadMoreSearch() { _searchLimit.value += SearchPageSize }

    fun setSearchQuery(query: String) {
        val clipped = query.take(10_000)
        if (clipped == _searchQuery.value) return
        finishActiveSearchTrace()
        if (clipped.trim().length >= 2) {
            val trace = SearchTrace(clipped, searchTraceSequence.incrementAndGet())
            Trace.beginAsyncSection(SearchLatencyTraceName, trace.cookie)
            activeSearchTrace = trace
        }
        savedState["searchQuery"] = clipped
        _searchLimit.value = SearchPageSize
        _searchQuery.value = clipped
    }

    /** Called by Compose after the frame containing results for [query] has been produced. */
    fun reportSearchResultsDrawn(query: String) {
        if (activeSearchTrace?.query == query) finishActiveSearchTrace()
    }

    fun setSearchFilters(filters: SearchFilters) {
        savedState["searchTypes"] = ArrayList(filters.types.map { it.name })
        savedState["searchField"] = filters.field.name
        savedState["searchMode"] = filters.mode.name
        savedState["searchDownloadedOnly"] = filters.downloadedOnly
        savedState["searchAuthors"] = ArrayList(filters.authors)
        savedState["searchSort"] = filters.sort.name
        savedState["searchDescending"] = filters.descending
        savedState["searchBooks"] = ArrayList(filters.books)
        savedState["searchCenturies"] = ArrayList(filters.centuries)
        savedState["searchSections"] = ArrayList(filters.sections)
        savedState["searchPlain"] = filters.plain
        _searchLimit.value = SearchPageSize
        _searchFilters.value = filters
    }

    fun rememberCurrentSearch() {
        scope.launch(errorHandler) { personalLibrary.rememberSearch(_searchQuery.value) }
    }

    fun clearRecentSearches() {
        scope.launch(errorHandler) { personalLibrary.clearRecentSearches() }
    }

    fun finishActiveSearchTrace() {
        val trace = activeSearchTrace ?: return
        Trace.endAsyncSection(SearchLatencyTraceName, trace.cookie)
        activeSearchTrace = null
    }
}
