package com.atharchive.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import com.atharchive.core.data.content.AudioReference
import com.atharchive.core.data.db.content.ChapterEntity
import com.atharchive.core.data.db.content.ContentAvailability
import com.atharchive.core.data.db.content.ContentBlockEntity
import com.atharchive.core.data.db.content.ContentEntity
import com.atharchive.core.data.db.content.ContentTransferState
import com.atharchive.core.data.db.content.FootnoteEntity
import com.atharchive.core.data.db.user.LibraryEntryEntity
import com.atharchive.core.data.db.user.LibraryStatus
import com.atharchive.core.data.db.user.CollectionItemEntity
import com.atharchive.core.data.db.user.PinnedDownloadEntity
import com.atharchive.core.data.db.user.ReadingHistoryEntity
import com.atharchive.core.data.db.user.ReadingPositionEntity
import com.atharchive.core.data.db.user.UserCollectionEntity
import com.atharchive.core.data.repository.ReaderPrefetchSession
import com.atharchive.core.data.repository.ContentSearchField
import com.atharchive.core.data.repository.ContentSearchFilter
import com.atharchive.core.data.repository.ContentSearchRepository
import com.atharchive.core.data.repository.ContentSearchRequest
import com.atharchive.core.data.repository.ContentSearchSort
import com.atharchive.core.data.repository.PersonalLibraryRepository
import com.atharchive.core.data.repository.StoredBlockAttributes
import com.atharchive.core.data.repository.attributes
import com.atharchive.core.data.repository.toCatalogEntry
import com.atharchive.feature.articles.ArticleUi
import com.atharchive.feature.articles.ArticlesUiState
import com.atharchive.feature.books.BookDownloadUi
import com.atharchive.feature.books.BookUi
import com.atharchive.feature.books.BooksUiState
import com.atharchive.feature.poemreader.PoemAudioUi
import com.atharchive.feature.poemreader.PoemReaderUiState
import com.atharchive.feature.poemreader.VerseCue
import com.atharchive.feature.poemreader.VerseUi
import com.atharchive.feature.poetry.PoemUi
import com.atharchive.feature.poetry.PoetryUiState
import com.atharchive.feature.library.LibraryCollectionUi
import com.atharchive.feature.library.LibraryShelf
import com.atharchive.feature.library.LibraryUiState
import com.atharchive.feature.library.LibraryWorkUi
import com.atharchive.feature.reader.BookAudioUi
import com.atharchive.feature.reader.ReaderBlock
import com.atharchive.feature.reader.ReaderUiState
import com.atharchive.feature.reader.TocEntry
import com.atharchive.feature.search.DirectMatchUi
import com.atharchive.feature.search.SearchField
import com.atharchive.feature.search.SearchFilters
import com.atharchive.feature.search.SearchHitUi
import com.atharchive.feature.search.SearchResultType
import com.atharchive.feature.search.SearchSort
import com.atharchive.feature.search.SearchUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ContentSyncUiState {
    data object LocalReady : ContentSyncUiState
    data object Checking : ContentSyncUiState
    data object UpToDate : ContentSyncUiState
    data object Disabled : ContentSyncUiState
    data class Failed(val message: String) : ContentSyncUiState
}

@HiltViewModel
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class AtharContentViewModel @Inject constructor(
    private val content: ContentAccess,
    private val downloadScheduler: ContentDownloadScheduler,
) : ViewModel() {
    private val userDao = content.userDatabase.userDataDao()
    private val catalogDao = content.contentDatabase.catalogDao()
    private val importDao = content.contentDatabase.importDao()
    private val pins = userDao.observePinnedDownloads()
    private val pinIds = pins.map { downloads ->
        downloads.mapTo(hashSetOf(), PinnedDownloadEntity::entityId)
    }
    private val retainedPins = combine(pins, content.retainedEntityIds) { downloads, retained ->
        downloads.asSequence()
            .map(PinnedDownloadEntity::entityId)
            .filterTo(hashSetOf()) { it in retained }
    }
    private val library = userDao.observeLibraryEntries()
    private val positions = userDao.observeReadingPositions()
    private val personalLibrary = PersonalLibraryRepository(content.userDatabase)
    private val contentSearch = ContentSearchRepository(content.contentDatabase)
    private val userCollections = personalLibrary.observeCollections()
    private val collectionItems = personalLibrary.observeCollectionItems()
    private val readingHistory = personalLibrary.observeHistory()
    private val recentSearches = personalLibrary.observeRecentSearches()
    private val _searchQuery = MutableStateFlow("")
    private val _searchFilters = MutableStateFlow(SearchFilters())
    private val readerFlows = ConcurrentHashMap<String, Flow<ReaderUiState?>>()
    private val readerPaging = ConcurrentHashMap<String, Flow<PagingData<ReaderBlock>>>()
    private val poemFlows = ConcurrentHashMap<String, Flow<PoemReaderUiState?>>()
    private val prefetchSessions = ConcurrentHashMap<String, ReaderPrefetchSession>()
    private val prefetchedFrames = ConcurrentHashMap<String, Int>()

    private val _syncState = MutableStateFlow<ContentSyncUiState>(ContentSyncUiState.LocalReady)
    private val _cacheBytes = MutableStateFlow(0L)
    val syncState: StateFlow<ContentSyncUiState> = _syncState
    val storageStatus = content.storageStatus
    val cacheBytes: StateFlow<Long> = _cacheBytes
    val cacheBudgetBytes: StateFlow<Long> = content.cacheBudgetBytes
    val pinnedCount: StateFlow<Int> = pins.map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val libraryReading = combine(library, positions, retainedPins) { entries, savedPositions, retained ->
        LibraryReading(entries, savedPositions, retained)
    }
    private val libraryOrganization = combine(userCollections, collectionItems, readingHistory) {
            collections, items, history ->
        LibraryOrganization(collections, items, history)
    }

    val personalLibraryState: StateFlow<LibraryUiState> = combine(
        catalogDao.observeCatalog(),
        libraryReading,
        libraryOrganization,
    ) { entities, reading, organization ->
        val statusById = reading.entries.associateBy(LibraryEntryEntity::entityId)
        val positionById = reading.positions.associateBy(ReadingPositionEntity::entityId)
        val recentById = organization.history.groupBy { it.entityId }
            .mapValues { (_, rows) -> rows.maxOf { it.openedAt } }
        val collectionIdsByEntity = organization.items.groupBy { it.entityId }
            .mapValues { (_, rows) -> rows.mapTo(linkedSetOf()) { it.collectionId } }
        LibraryUiState(
            works = entities.map { entity ->
                val position = positionById[entity.id]
                LibraryWorkUi(
                    id = entity.id,
                    title = entity.title,
                    author = entity.personName.orEmpty(),
                    collection = entity.coll,
                    kind = entity.kind.orEmpty(),
                    status = statusById[entity.id]?.status.toLibraryShelf(),
                    progress = position?.progressPct?.toFloat()?.coerceIn(0f, 1f),
                    progressLabel = position?.let { "${arabicNumber((it.progressPct * 100).toInt())}٪" },
                    downloaded = entity.id in reading.retained && entity.availability == ContentAvailability.COMPLETE,
                    recentAt = recentById[entity.id] ?: position?.updatedAt,
                    collectionIds = collectionIdsByEntity[entity.id].orEmpty(),
                )
            },
            collections = organization.collections.map { collection ->
                LibraryCollectionUi(
                    id = collection.id,
                    title = collection.title,
                    itemCount = organization.items.count { it.collectionId == collection.id },
                )
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    val search: StateFlow<SearchUiState> = combine(
        _searchQuery.debounce(120),
        _searchFilters,
        recentSearches,
        catalogDao.observeCatalog(),
    ) { query, filters, recent, catalog ->
        SearchInput(query, filters, recent.map { it.q }, catalog)
    }.mapLatest { input ->
        val clean = input.query.trim()
        val base = SearchUiState(
            query = input.query,
            filters = input.filters,
            recentQueries = input.recent,
            directMatches = emptyList(),
            hits = emptyList(),
            availableSources = input.catalog.map { it.title },
            availableAuthors = input.catalog.mapNotNull { it.personName }.distinct(),
        )
        if (clean.length < 2) return@mapLatest base
        val sourceIds = input.filters.sources.takeIf { it.isNotEmpty() }?.let { sources ->
            input.catalog.asSequence().filter { it.title in sources }.mapTo(linkedSetOf()) { it.id }
        }
        val result = contentSearch.search(
            ContentSearchRequest(
                query = clean,
                field = input.filters.field.toContentField(),
                sort = input.filters.sort.toContentSort(),
                filter = ContentSearchFilter(
                    collections = input.filters.types.mapTo(linkedSetOf()) { it.contentCollection },
                    entityIds = sourceIds,
                    authors = input.filters.authors,
                ),
            ),
        )
        base.copy(
            directMatches = result.catalog.map { hit ->
                DirectMatchUi(
                    id = hit.entityId,
                    title = hit.title,
                    kindLabel = hit.kind.ifBlank { hit.collection.toResultType().label },
                    contextLabel = hit.personName,
                    type = hit.collection.toResultType(),
                )
            },
            hits = result.blocks.map { hit ->
                SearchHitUi(
                    id = "${hit.entityId}:${hit.rowid}",
                    entityId = hit.entityId,
                    ordinal = hit.ordinal,
                    excerpt = hit.excerpt,
                    matchStart = hit.matchStart,
                    matchEnd = hit.matchEnd,
                    sourceMatchStart = hit.sourceMatchStart,
                    sourceMatchEnd = hit.sourceMatchEnd,
                    sourceTitle = hit.sourceTitle,
                    sourceAuthor = hit.sourceAuthor,
                    locationLabel = listOfNotNull(
                        hit.chapterTitle.ifBlank { null },
                        hit.printedPage?.let { "ص ${arabicNumber(it)}" },
                    ).joinToString(" · ").ifBlank { null },
                    type = hit.collection.toResultType(),
                )
            },
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        SearchUiState(
            recentQueries = emptyList(),
            directMatches = emptyList(),
            hits = emptyList(),
            availableSources = emptyList(),
            availableAuthors = emptyList(),
        ),
    )

    val books: StateFlow<BooksUiState> = combine(
        catalogDao.observeCollection("book"), pinIds, retainedPins, library, positions,
    ) { entities, pinnedIds, retainedIds, shelves, savedPositions ->
        val shelfIds = shelves.mapTo(hashSetOf(), LibraryEntryEntity::entityId)
        val positionById = savedPositions.associateBy(ReadingPositionEntity::entityId)
        BooksUiState(
            archiveCountLabel = "${arabicNumber(entities.size)} كتابًا",
            books = entities.map {
                it.toBookUi(
                    pinned = it.id in pinnedIds,
                    retained = it.id in retainedIds,
                    saved = it.id in shelfIds,
                    position = positionById[it.id],
                )
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BooksUiState("٠ كتاب", emptyList()))

    val articles: StateFlow<ArticlesUiState> = combine(
        catalogDao.observeCollection("article"), pinIds, retainedPins, library,
    ) { entities, pinnedIds, retainedIds, shelves ->
        val shelfIds = shelves.mapTo(hashSetOf(), LibraryEntryEntity::entityId)
        ArticlesUiState(
            countLabel = "${arabicNumber(entities.size)} مقالًا",
            articles = entities.map { entity ->
                ArticleUi(
                    id = entity.id,
                    title = entity.title,
                    author = entity.personName.orEmpty(),
                    dateLabel = entity.publishedAt?.take(4)?.let { "${arabicNumber(it)}م" }.orEmpty(),
                    excerpt = entity.excerpt.orEmpty(),
                    downloaded = entity.id in retainedIds && entity.availability == ContentAvailability.COMPLETE,
                    downloading = entity.id in pinnedIds &&
                        entity.id !in retainedIds &&
                        entity.transferState != ContentTransferState.FAILED,
                    saved = entity.id in shelfIds,
                )
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ArticlesUiState("٠ مقال", emptyList()))

    val poetry: StateFlow<PoetryUiState> = combine(
        catalogDao.observeCollection("poem"), pinIds, retainedPins, library,
    ) { entities, pinnedIds, retainedIds, shelves ->
        val shelfIds = shelves.mapTo(hashSetOf(), LibraryEntryEntity::entityId)
        PoetryUiState(
            countLabel = "${arabicNumber(entities.size)} قصيدة",
            poems = entities.map { entity ->
                val entry = entity.toCatalogEntry()
                PoemUi(
                    id = entity.id,
                    title = entity.title,
                    poet = entity.personName.orEmpty(),
                    openingVerses = entry.openingVerses,
                    topic = entity.topicsCsv.substringBefore(',').ifBlank { "—" },
                    downloaded = entity.id in retainedIds && entity.availability == ContentAvailability.COMPLETE,
                    downloading = entity.id in pinnedIds &&
                        entity.id !in retainedIds &&
                        entity.transferState != ContentTransferState.FAILED,
                    saved = entity.id in shelfIds,
                    verseCountLabel = "${arabicNumber(entity.pkgBlocks.coerceAtLeast(0))} بيتًا",
                    sizeLabel = byteSize(entity.pkgSize),
                )
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PoetryUiState("٠ قصيدة", emptyList()))

    init {
        viewModelScope.launch {
            // This is local disk work only and returns immediately when the content DB is
            // already healthy. UI Flows are active before either rebuild or sync begins.
            runCatching { content.rebuildRetainedContent() }
            recoverPendingDownloads()
            refreshCacheBytes()
            sync()
        }
    }

    fun sync() {
        if (!content.configured) {
            _syncState.value = ContentSyncUiState.Disabled
            return
        }
        viewModelScope.launch {
            _syncState.value = ContentSyncUiState.Checking
            _syncState.value = try {
                content.sync()
                recoverPendingDownloads()
                ContentSyncUiState.UpToDate
            } catch (error: Throwable) {
                ContentSyncUiState.Failed(error.message ?: "تعذّر تحديث الفهرس")
            }
        }
    }

    fun readerState(entityId: String): Flow<ReaderUiState?> = readerFlows.getOrPut(entityId) {
        combine(
            catalogDao.observeEntity(entityId),
            importDao.observeBlocks(entityId),
            importDao.observeChapters(entityId),
            importDao.observeFootnotes(entityId),
            userDao.observeReadingPosition(entityId),
        ) { entity, blocks, chapters, footnotes, position ->
            entity?.toReaderUi(blocks, chapters, footnotes, position)
        }
    }

    fun pagedReaderBlocks(entityId: String): Flow<PagingData<ReaderBlock>> =
        readerPaging.getOrPut(entityId) {
            content.pagedBlocks(entityId)
                .map { paging -> paging.map(ContentBlockEntity::toReaderBlock) }
                .cachedIn(viewModelScope)
        }

    fun poemReaderState(entityId: String): Flow<PoemReaderUiState?> = poemFlows.getOrPut(entityId) {
        combine(catalogDao.observeEntity(entityId), importDao.observeBlocks(entityId)) { entity, blocks ->
            entity?.toPoemReaderUi(blocks)
        }
    }

    fun openReader(entityId: String, targetOrdinal: Int? = null) {
        viewModelScope.launch {
            personalLibrary.recordOpened(entityId)
            val position = targetOrdinal ?: userDao.readingPosition(entityId)?.ordinalHint ?: 0
            runCatching { content.open(entityId, position) }
            refreshCacheBytes()
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query.take(10_000)
    }

    fun setSearchFilters(filters: SearchFilters) {
        _searchFilters.value = filters
    }

    fun rememberCurrentSearch() {
        viewModelScope.launch { personalLibrary.rememberSearch(_searchQuery.value) }
    }

    fun clearRecentSearches() {
        viewModelScope.launch { personalLibrary.clearRecentSearches() }
    }

    fun toggleReadLater(entityId: String) {
        viewModelScope.launch { personalLibrary.toggleReadLater(entityId) }
    }

    fun setLibraryStatus(entityId: String, shelf: LibraryShelf?) {
        val status = when (shelf) {
            LibraryShelf.ReadLater -> LibraryStatus.READ_LATER
            LibraryShelf.Reading -> LibraryStatus.READING
            LibraryShelf.Finished -> LibraryStatus.FINISHED
            null -> null
            else -> return
        }
        viewModelScope.launch { personalLibrary.setStatus(entityId, status) }
    }

    fun createCollection(title: String) {
        viewModelScope.launch { personalLibrary.createCollection(title) }
    }

    fun deleteCollection(collectionId: String) {
        viewModelScope.launch { personalLibrary.deleteCollection(collectionId) }
    }

    fun addToCollection(collectionId: String, entityId: String) {
        viewModelScope.launch { personalLibrary.addToCollection(collectionId, entityId) }
    }

    fun removeFromCollection(collectionId: String, entityId: String) {
        viewModelScope.launch { personalLibrary.removeFromCollection(collectionId, entityId) }
    }

    fun onReaderPosition(entityId: String, ordinal: Int) {
        viewModelScope.launch {
            val entity = catalogDao.entity(entityId) ?: return@launch
            val block = importDao.blockAt(entityId, ordinal) ?: return@launch
            userDao.saveReadingPosition(
                ReadingPositionEntity(
                    entityId = entityId,
                    chapterAnchor = block.chapterAnchor,
                    blockIdHi = block.blockIdHi,
                    blockIdLo = block.blockIdLo,
                    ordinalHint = ordinal,
                    offsetInBlock = 0,
                    progressPct = if (entity.pkgBlocks <= 1) 0.0 else ordinal.toDouble() / (entity.pkgBlocks - 1),
                    updatedAt = System.currentTimeMillis(),
                ),
            )

            val frame = importDao.frames(entityId).firstOrNull {
                ordinal in it.firstBlockOrdinal until (it.firstBlockOrdinal + it.blockCount)
            } ?: return@launch
            val distanceToEnd = frame.firstBlockOrdinal + frame.blockCount - ordinal
            if (distanceToEnd <= 40 && prefetchedFrames.put(entityId, frame.frameOrdinal) != frame.frameOrdinal) {
                prefetchSessions.remove(entityId)?.close()
                content.prefetchAdjacent(viewModelScope, entityId, ordinal)?.let {
                    prefetchSessions[entityId] = it
                }
            }
        }
    }

    fun closeReader(entityId: String) {
        prefetchSessions.remove(entityId)?.close()
        prefetchedFrames.remove(entityId)
    }

    fun toggleDownload(entityId: String) {
        viewModelScope.launch {
            val entity = catalogDao.entity(entityId) ?: return@launch
            if (content.isPinned(entityId)) {
                if (
                    entity.transferState == ContentTransferState.FAILED &&
                    entityId !in content.retainedEntityIds.value
                ) {
                    downloadScheduler.enqueue(entityId, entity.coll)
                } else {
                    downloadScheduler.cancel(entityId, entity.coll)
                    content.unpin(entityId)
                }
            } else {
                content.requestDownload(entityId)
                downloadScheduler.enqueue(entityId, entity.coll)
            }
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            content.clearCache()
            refreshCacheBytes()
        }
    }

    fun refreshCacheBytes() {
        viewModelScope.launch { _cacheBytes.value = content.cacheBytes() }
    }

    fun setCacheBudget(bytes: Long) {
        viewModelScope.launch {
            content.setCacheBudget(bytes)
            _cacheBytes.value = content.cacheBytes()
        }
    }

    private suspend fun recoverPendingDownloads() {
        content.reconcilePinnedDownloads().forEach { target ->
            downloadScheduler.enqueue(target.entityId, target.collection)
        }
    }

    private fun ContentEntity.toBookUi(
        pinned: Boolean,
        retained: Boolean,
        saved: Boolean,
        position: ReadingPositionEntity?,
    ): BookUi {
        val progress = position?.progressPct?.toFloat()?.coerceIn(0f, 1f)
        val download = when {
            pinned && !retained && transferState != ContentTransferState.FAILED ->
                BookDownloadUi.Downloading(
                    sizeLabel = byteSize(pkgSize),
                    progress = if (pkgBlocks == 0) 0f else importProgress.toFloat() / pkgBlocks,
                    progressLabel = "${arabicNumber(importProgress * 100 / pkgBlocks.coerceAtLeast(1))}٪",
                )
            retained && availability == ContentAvailability.COMPLETE -> BookDownloadUi.Downloaded(byteSize(pkgSize))
            else -> BookDownloadUi.Available(byteSize(pkgSize))
        }
        return BookUi(
            id = id,
            title = title,
            author = personName.orEmpty(),
            discipline = topicsCsv.substringBefore(',').ifBlank { "—" },
            kind = kind ?: "كتاب",
            download = download,
            saved = saved,
            readingPosition = position?.let { "${it.chapterAnchor} · ${arabicNumber(it.ordinalHint + 1)}" },
            readingProgress = progress,
            readingProgressLabel = progress?.let { "${arabicNumber((it * 100).toInt())}٪" },
        )
    }
}

private fun ContentEntity.toReaderUi(
    blocks: List<ContentBlockEntity>,
    chapters: List<ChapterEntity>,
    footnotes: List<FootnoteEntity>,
    position: ReadingPositionEntity?,
): ReaderUiState {
    val contentBlocks = blocks.map(ContentBlockEntity::toReaderBlock)
    val footnoteBlocks = footnotes.mapIndexed { index, note ->
        ReaderBlock.FootnoteBody("fn-${note.fnId}", arabicNumber(index + 1), note.text)
    }
    return ReaderUiState(
        bookTitle = title,
        author = personName.orEmpty(),
        blocks = contentBlocks + footnoteBlocks,
        toc = chapters.map { TocEntry(it.anchor, it.title, 2, it.firstOrdinal) },
        bookmarks = emptyList(),
        benefits = emptyList(),
        readingPositionIndex = position?.ordinalHint ?: 0,
        audio = audioReferences().map { BookAudioUi(it.id, title, duration(it.seconds)) },
        blockCount = pkgBlocks,
    )
}

private fun ContentEntity.toPoemReaderUi(blocks: List<ContentBlockEntity>): PoemReaderUiState =
    PoemReaderUiState(
        title = title,
        poet = personName.orEmpty(),
        verses = blocks.filter { it.type == "verse" }.mapIndexed { index, block ->
            val attrs = block.safeAttributes()
            VerseUi(
                id = block.stableId(),
                number = attrs.n ?: index + 1,
                sadr = attrs.s ?: block.text,
                ajz = attrs.j.orEmpty(),
            )
        },
        recordings = audioReferences().map { audio ->
            PoemAudioUi(
                id = audio.id,
                title = title,
                collection = "المنظومات",
                reciter = "",
                durationLabel = duration(audio.seconds),
                durationSeconds = audio.seconds,
                cues = audio.cues.map { VerseCue(it.v, it.t.toInt()) },
            )
        },
    )

private fun ContentEntity.audioReferences(): List<AudioReference> =
    runCatching { toCatalogEntry().audio }.getOrDefault(emptyList())

private fun ContentBlockEntity.toReaderBlock(): ReaderBlock {
    val id = stableId()
    val attrs = safeAttributes()
    return when {
        type.startsWith('h') -> ReaderBlock.Heading(
            id = id,
            level = type.drop(1).toIntOrNull() ?: 2,
            text = text,
            anchor = chapterAnchor,
            ordinal = ordinal,
        )
        type == "quote" -> ReaderBlock.Quote(id, text, ordinal)
        type == "verse" -> ReaderBlock.Verse(id, attrs.s ?: text, attrs.j, ordinal)
        type == "page" -> ReaderBlock.PageBreak(id, printedPage ?: 0, vol, ordinal)
        else -> ReaderBlock.Paragraph(id, text, attrs.footnotes, ordinal)
    }
}

private fun ContentBlockEntity.safeAttributes(): StoredBlockAttributes =
    runCatching { attributes() }.getOrDefault(StoredBlockAttributes())

private fun ContentBlockEntity.stableId(): String =
    blockIdHi.toULong().toString(16).padStart(16, '0') +
        blockIdLo.toULong().toString(16).padStart(16, '0')

private fun duration(seconds: Int): String =
    "${arabicNumber(seconds / 60).padStart(2, '٠')}:${arabicNumber(seconds % 60).padStart(2, '٠')}"

private fun byteSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

private fun arabicNumber(value: Any): String = value.toString().map { character ->
    if (character in '0'..'9') "٠١٢٣٤٥٦٧٨٩"[character - '0'] else character
}.joinToString("")

private data class LibraryReading(
    val entries: List<LibraryEntryEntity>,
    val positions: List<ReadingPositionEntity>,
    val retained: Set<String>,
)

private data class LibraryOrganization(
    val collections: List<UserCollectionEntity>,
    val items: List<CollectionItemEntity>,
    val history: List<ReadingHistoryEntity>,
)

private data class SearchInput(
    val query: String,
    val filters: SearchFilters,
    val recent: List<String>,
    val catalog: List<ContentEntity>,
)

private fun String?.toLibraryShelf(): LibraryShelf? = when (this) {
    LibraryStatus.READ_LATER -> LibraryShelf.ReadLater
    LibraryStatus.READING -> LibraryShelf.Reading
    LibraryStatus.FINISHED -> LibraryShelf.Finished
    else -> null
}

private fun SearchField.toContentField(): ContentSearchField = when (this) {
    SearchField.FullText -> ContentSearchField.FullText
    SearchField.Title -> ContentSearchField.Title
    SearchField.Author -> ContentSearchField.Author
    SearchField.Topic -> ContentSearchField.Topic
}

private fun SearchSort.toContentSort(): ContentSearchSort = when (this) {
    SearchSort.Relevance -> ContentSearchSort.Relevance
    SearchSort.Newest -> ContentSearchSort.Newest
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
