package com.atharchive.data

import com.atharchive.core.data.repository.persons
import com.atharchive.ui.components.arabicDigits
import com.atharchive.feature.audio.AudioUi
import androidx.work.WorkInfo
import com.atharchive.feature.downloads.DownloadUi
import com.atharchive.feature.downloads.TransferUi
import com.atharchive.feature.downloads.BulkCandidate
import com.atharchive.feature.downloads.BulkRowUi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atharchive.core.data.annotation.Annotation
import com.atharchive.core.data.annotation.AnnotationKind
import com.atharchive.core.data.annotation.AnnotationRepository
import com.atharchive.core.data.annotation.AttachmentState
import com.atharchive.core.data.archive.UserDataArchive
import com.atharchive.core.data.archive.UserDataImportReport
import com.atharchive.core.data.db.content.ContentEntity
import com.atharchive.core.data.db.user.LibraryEntryEntity
import com.atharchive.core.data.repository.ContentSearchRepository
import com.atharchive.core.data.repository.ContentSearchSchema
import com.atharchive.core.data.repository.PersonalLibraryRepository
import com.atharchive.core.data.repository.toCatalogEntry
import com.atharchive.feature.issues.IssueUi
import com.atharchive.feature.issues.IssuesUiState
import com.atharchive.feature.articles.ArticleUi
import com.atharchive.feature.articles.ArticlesUiState
import com.atharchive.feature.books.BookDownloadUi
import com.atharchive.feature.books.BookUi
import com.atharchive.feature.books.BooksUiState
import com.atharchive.feature.poetry.PoemUi
import com.atharchive.feature.poetry.PoetryUiState
import com.atharchive.feature.kannashah.ExcerptUi
import com.atharchive.feature.kannashah.KannashahUiState
import com.atharchive.feature.reader.InBookHit
import com.atharchive.feature.reader.InBookSearch
import com.atharchive.feature.reader.ReaderSelection
import com.atharchive.feature.search.SearchFilters
import com.atharchive.feature.search.SearchHitUi
import com.atharchive.feature.search.SearchResultType
import com.atharchive.feature.search.SearchUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import com.atharchive.feature.search.SearchMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

const val BookOpenTraceName = "AtharBookOpen"

sealed interface ContentSyncUiState {
    data object LocalReady : ContentSyncUiState
    data object Checking : ContentSyncUiState
    data object UpToDate : ContentSyncUiState
    data object Disabled : ContentSyncUiState
    /** [manual]: the reader asked for this check, so the failure is worth interrupting them for. */
    data class Failed(val message: String, val manual: Boolean = false) : ContentSyncUiState
}

/** A failed sync, in words a reader can act on (the raw cause goes to logcat). */
internal fun syncFailureReason(error: Throwable): String {
    val causes = generateSequence(error) { it.cause }.toList()
    return when {
        causes.any { it is java.net.UnknownHostException || it is java.net.ConnectException } -> "لا يوجد اتصال بالخادم"
        causes.any { it is java.net.SocketTimeoutException || it is java.io.InterruptedIOException } -> "الاتصال بطيء جدًا"
        causes.any { it is javax.net.ssl.SSLException } -> "تعذّر الاتصال الآمن بالخادم"
        causes.any { it is com.atharchive.core.data.content.ContentIntegrityException } -> "وصلت بيانات غير سليمة"
        else -> "تعذّر الوصول إلى الخادم"
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@HiltViewModel
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class AtharContentViewModel @Inject constructor(
    private val savedState: androidx.lifecycle.SavedStateHandle,
    private val content: ContentAccess,
    val playback: AudioPlayback,
    private val downloadScheduler: ContentDownloadScheduler,
    val dictionary: Dictionary,
    private val presetStore: SearchPresetStore,
) : ViewModel() {

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val errors = _errors.asSharedFlow()
    private val errorHandler = CoroutineExceptionHandler { _, error ->
        _errors.tryEmit(error.message ?: "تعذّرت العملية؛ أعد المحاولة")
    }
    private val userDao = content.userDatabase.userDataDao()
    private val catalogDao = content.contentDatabase.catalogDao()
    private val importDao = content.contentDatabase.importDao()
    private val downloadsModel = DownloadsModel(
        viewModelScope, content, downloadScheduler, errorHandler, _errors, { audioModel.recordingCatalog }, { audioModel.audioSources },
    )
    private val pinIds: Flow<Set<String>> get() = downloadsModel.pinIds
    private val retainedPins: Flow<Set<String>> get() = downloadsModel.retainedPins
    private val localStates: StateFlow<LocalStates> get() = downloadsModel.localStates
    private val jobIndex: StateFlow<Map<String, TransferJob>> get() = downloadsModel.jobIndex
    private fun <S, R> Flow<S>.withTransfers(
        kind: String, rows: (S) -> List<R>, id: (R) -> String, rebuild: (S, List<R>) -> S, apply: (R, TransferUi) -> R,
    ): Flow<S> = with(downloadsModel) { withTransfers(kind, rows, id, rebuild, apply) }

    // Downloads live in [DownloadsModel]; the screens still reach them through here.
    val pinnedCount: StateFlow<Int> get() = downloadsModel.pinnedCount
    val downloads: StateFlow<List<DownloadUi>> get() = downloadsModel.downloads
    val bulkDownloads: StateFlow<List<BulkRowUi>> get() = downloadsModel.bulkDownloads
    fun worksRow(label: String, ids: Set<String>) = downloadsModel.worksRow(label, ids)
    fun downloadRemaining(row: BulkRowUi, mobileData: Boolean) = downloadsModel.downloadRemaining(row, mobileData)
    fun downloadAll(items: List<BulkCandidate>, wifiOnly: Boolean = false) = downloadsModel.downloadAll(items, wifiOnly)
    fun pauseResumeMany(items: List<BulkCandidate>, pause: Boolean, wifiOnly: Boolean = false) = downloadsModel.pauseResumeMany(items, pause, wifiOnly)
    fun toggleAudioDownload(audioId: String) = downloadsModel.toggleAudioDownload(audioId)
    fun transferOf(entityId: String) = downloadsModel.transferOf(entityId)
    fun downloadForSearch(entityId: String) = downloadsModel.downloadForSearch(entityId)
    fun toggleDownload(entityId: String) = downloadsModel.toggleDownload(entityId)
    fun pauseResumeDownload(entityId: String) = downloadsModel.pauseResumeDownload(entityId)
    fun removeDownloads(ids: List<String>) = downloadsModel.removeDownloads(ids)
    fun removeDownload(entityId: String) = downloadsModel.removeDownload(entityId)

    private val library = userDao.observeLibraryEntries()
    /**
     * What the books list draws from positions: whole-percent progress and recent order. A scroll
     * stop that moves neither (almost all of them) rebuilds nothing.
     */
    private val listPositions = userDao.observeReadingPositions().map { rows ->
        rows.sortedByDescending { it.updatedAt }.mapIndexed { rank, p -> p.entityId to ((p.progressPct * 100).roundToInt() to rank) }.toMap()
    }.distinctUntilChanged()
    private val personalLibrary = PersonalLibraryRepository(content.userDatabase)
    private val annotationRepository = AnnotationRepository(content.userDatabase, content.contentDatabase)
    private val annotations = annotationRepository.observe()
    private val userDataArchive = UserDataArchive(content.userDatabase)
    private val contentSearch = ContentSearchRepository(content.contentDatabase)
    private val readingHistory = personalLibrary.observeHistory()
    /** entityId -> 0 for the most recently opened; audio ids are "audio:<id>". */
    private val recentRanks = readingHistory.map { rows ->
        rows.groupBy { it.entityId }.mapValues { (_, r) -> r.maxOf { it.openedAt } }
            .entries.sortedByDescending { it.value }.mapIndexed { i, e -> e.key to i }.toMap()
    }
    private val searchModel = SearchModel(viewModelScope, savedState, content, contentSearch, dictionary, presetStore, personalLibrary, errorHandler)
    // Search lives in [SearchModel]; the screens still reach it through here.
    val searchPresets get() = searchModel.searchPresets
    fun saveSearchPreset(name: String) = searchModel.saveSearchPreset(name)
    fun deleteSearchPreset(name: String) = searchModel.deleteSearchPreset(name)
    val searchQuery: StateFlow<String> get() = searchModel.searchQuery
    val search: StateFlow<SearchUiState> get() = searchModel.search
    val recentQueries: StateFlow<List<String>> get() = searchModel.recentQueries
    suspend fun catalogMatches(query: String, collections: Set<String>) = searchModel.catalogMatches(query, collections)
    suspend fun searchSection(type: SearchResultType, query: String) = searchModel.searchSection(type, query)
    suspend fun searchSectionLocal(type: SearchResultType, query: String) = searchModel.searchSectionLocal(type, query)
    suspend fun onlinePassage(hit: SearchHitUi) = searchModel.onlinePassage(hit)
    suspend fun locateOnlineHit(hit: SearchHitUi, query: String) = searchModel.locateOnlineHit(hit, query)
    suspend fun locateOnline(entityId: String, url: String?, query: String, snippet: String = "", pg: String? = null) =
        searchModel.locateOnline(entityId, url, query, snippet, pg)
    suspend fun searchInBook(
        entityId: String,
        query: String,
        mode: com.atharchive.feature.search.SearchMode = com.atharchive.feature.search.SearchMode.All,
        onPartial: (InBookSearch) -> Unit = {},
    ) = searchModel.searchInBook(entityId, query, mode, onPartial)
    suspend fun inBookPassage(entityId: String, hit: InBookHit) = searchModel.inBookPassage(entityId, hit)
    fun rememberSearch(query: String) = searchModel.rememberSearch(query)
    fun loadMoreSearch() = searchModel.loadMoreSearch()
    fun setSearchQuery(query: String) = searchModel.setSearchQuery(query)
    fun reportSearchResultsDrawn(query: String) = searchModel.reportSearchResultsDrawn(query)
    fun setSearchFilters(filters: SearchFilters) = searchModel.setSearchFilters(filters)
    fun rememberCurrentSearch() = searchModel.rememberCurrentSearch()
    fun clearRecentSearches() = searchModel.clearRecentSearches()

    private val readerModel = ReaderModel(viewModelScope, content, personalLibrary, annotationRepository, errorHandler, _errors, ::refreshCacheBytes)
    // The reader lives in [ReaderModel]; the screens still reach it through here.
    fun readerState(entityId: String) = readerModel.readerState(entityId)
    suspend fun footnoteBodies(entityId: String, ids: List<String>) = readerModel.footnoteBodies(entityId, ids)
    fun readerWindow(entityId: String) = readerModel.readerWindow(entityId)
    suspend fun selectionBlocks(entityId: String, range: IntRange) = readerModel.selectionBlocks(entityId, range)
    suspend fun savedPoemOrdinal(entityId: String, annotationId: String) = readerModel.savedPoemOrdinal(entityId, annotationId)
    fun poemReaderState(entityId: String) = readerModel.poemReaderState(entityId)
    fun openReader(entityId: String, targetOrdinal: Int? = null) = readerModel.openReader(entityId, targetOrdinal)
    suspend fun textVersion(entityId: String) = readerModel.textVersion(entityId)
    suspend fun findPrintedPage(entityId: String, page: Int, volume: Int?) = readerModel.findPrintedPage(entityId, page, volume)
    suspend fun printedPlace(entityId: String, ordinal: Int) = readerModel.printedPlace(entityId, ordinal)
    suspend fun findHeadingAnchor(entityId: String, anchor: String) = readerModel.findHeadingAnchor(entityId, anchor)
    suspend fun saveSelection(entityId: String, selection: ReaderSelection, kind: AnnotationKind = AnnotationKind.Highlight) =
        readerModel.saveSelection(entityId, selection, kind)
    suspend fun saveBlock(entityId: String, ordinal: Int) = readerModel.saveBlock(entityId, ordinal)
    suspend fun savedBlockId(entityId: String, ordinal: Int) = readerModel.savedBlockId(entityId, ordinal)
    suspend fun removeSavedBlock(entityId: String, ordinal: Int) = readerModel.removeSavedBlock(entityId, ordinal)
    suspend fun toggleBookmark(entityId: String, ordinal: Int) = readerModel.toggleBookmark(entityId, ordinal)
    fun onReaderPosition(entityId: String, ordinal: Int, offset: Int = 0) = readerModel.onReaderPosition(entityId, ordinal, offset)
    suspend fun editionToOpen(entityId: String) = readerModel.editionToOpen(entityId)
    fun chooseEdition(primaryId: String, editionId: String) = readerModel.chooseEdition(primaryId, editionId)
    fun closeReader(entityId: String) = readerModel.closeReader(entityId)

    private val _syncState = MutableStateFlow<ContentSyncUiState>(ContentSyncUiState.LocalReady)
    private val _cacheBytes = MutableStateFlow(0L)
    val syncState: StateFlow<ContentSyncUiState> = _syncState
    val storageStatus = content.storageStatus
    val openProgress = content.openProgress
    val cacheBytes: StateFlow<Long> = _cacheBytes
    val cacheBudgetBytes: StateFlow<Long> = content.cacheBudgetBytes

    val favouriteIds: StateFlow<Set<String>> = personalLibrary.observeFavouriteIds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val kannashahState: StateFlow<KannashahUiState> = combine(
        annotations,
        catalogDao.observeAllEntities().pausedDuringUpdate(),
    ) { saved, catalog ->
        val sourceById = catalog.associateBy(ContentEntity::id)
        // A list, not a sequence: a poem's verse number is read from its block (a suspend call).
        val excerpts = saved
            .filter { it.kind != AnnotationKind.Bookmark }
            .map { annotation ->
                val source = sourceById[annotation.entityId]
                ExcerptUi(
                    id = annotation.id,
                    entityId = annotation.entityId,
                    sourceColl = source?.coll ?: "book",
                    text = annotation.quotedText,
                    sourceTitle = source?.title ?: annotation.entityId,
                    sourceAuthor = source?.personName.orEmpty(),
                    sourceUrl = if (source != null) PlayStoreUrl else "",
                    locationLabel = when (annotation.state) {
                        AttachmentState.Orphaned -> "فقد موضعه"
                        AttachmentState.Approximate -> annotation.printedPage?.let {
                            "قرب ص ${arabicNumber(it)}"
                        } ?: "موضع تقريبي"
                        AttachmentState.Exact -> annotation.printedPage?.let { "ص ${arabicNumber(it)}" }
                            ?: if (source?.coll != "poem") null else importDao.blockAt(annotation.entityId, annotation.start.ordinalHint)
                                ?.safeAttributes()?.n?.let { "البيت ${arabicNumber(it)}" }
                    },
                    topics = source?.topicsCsv?.split(',')?.filter(String::isNotBlank)?.map(::topicName).orEmpty(),
                    comment = annotation.noteText,
                    sourceOrdinal = annotation.start.ordinalHint,
                    addedOrder = annotation.updatedAt,
                    kind = annotation.kind.storageValue,
                    attachmentState = annotation.state.storageValue,
                )
            }
        KannashahUiState(
            headline = if (excerpts.isEmpty()) "" else
                "${arabicCount(excerpts.size, "", "مقتطف واحد", "مقتطفان", "مقتطفات", "مقتطفًا")} من " +
                "${arabicCount(excerpts.map(ExcerptUi::entityId).distinct().size, "", "مصدر واحد", "مصدرين", "مصادر", "مصدرًا")}",
            excerpts = excerpts,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), KannashahUiState())

    val persons = catalogDao.observeGeneration().pausedDuringUpdate().map { generation ->
        generation?.persons().orEmpty()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val personWorks = catalogDao.observeCatalog().pausedDuringUpdate().map { entities -> entities.associateBy { it.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val books: StateFlow<BooksUiState> = combine(
        // the 2,465-row list follows a position only when its percent or the recent order moves, at most every 3 s
        catalogDao.observeCollectionLean("book").pausedDuringUpdate(), pinIds, retainedPins, library, combine(listPositions.throttled(3_000), localStates, ::Pair),
    ) { entities, pinnedIds, retainedIds, shelves, (placed, states) ->
        val shelfIds = shelves.mapTo(hashSetOf(), LibraryEntryEntity::entityId)
        BooksUiState(
            archiveCountLabel = arabicCount(entities.size, "الكتب", "كتاب واحد", "كتابين", "كتب", "كتابًا"),
            books = entities.map {
                it.toBookUi(
                    pinned = it.id in pinnedIds,
                    retained = it.id in retainedIds,
                    complete = it.id in states.complete,
                    saved = it.id in shelfIds,
                    progressPercent = placed[it.id]?.first,
                    recentRank = placed[it.id]?.second,
                )
            },
        )
    }.withTransfers("كتاب", BooksUiState::books, BookUi::id, { state, rows -> state.copy(books = rows) }) { book, transfer ->
        (book.download as? BookDownloadUi.Downloading)?.let { book.copy(download = it.copy(transfer = transfer)) } ?: book
    }.flowOn(Dispatchers.Default).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        BooksUiState("٠ كتاب", emptyList(), loaded = false),
    )

    val articles: StateFlow<ArticlesUiState> = combine(
        catalogDao.observeCollectionLean("article").pausedDuringUpdate(), pinIds, retainedPins, library, combine(recentRanks, localStates, ::Pair),
    ) { entities, pinnedIds, retainedIds, shelves, (ranks, states) ->
        val shelfIds = shelves.mapTo(hashSetOf(), LibraryEntryEntity::entityId)
        ArticlesUiState(
            countLabel = arabicCount(entities.size, "المقالات", "مقال واحد", "مقالين", "مقالات", "مقالًا"),
            articles = entities.map { entity ->
                ArticleUi(
                    id = entity.id,
                    title = entity.title,
                    author = entity.personName.orEmpty(),
                    dateLabel = entity.publishedAt?.take(4)?.let { "${arabicNumber(it)}م" }.orEmpty(),
                    excerpt = entity.excerpt.orEmpty(),
                    downloaded = entity.id in retainedIds && entity.id in states.complete,
                    downloading = entity.id in pinnedIds &&
                        entity.id !in retainedIds &&
                        entity.id !in states.failed,
                    saved = entity.id in shelfIds,
                    recentRank = ranks[entity.id],
                    sizeLabel = byteSize(entity.pkgSize),
                    topics = entity.topicsCsv.split(',').filter(String::isNotBlank).map(::topicName),
                    died = entity.died,
                    hasAudio = entity.audioJson != "[]",
                )
            },
        )
    }.withTransfers("مقال", ArticlesUiState::articles, ArticleUi::id, { state, rows -> state.copy(articles = rows) }) { row, transfer ->
        row.copy(transfer = transfer)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, ArticlesUiState("٠ مقال", emptyList(), loaded = false)) // hot from app start so the tab opens filled; a download tick only re-copies the rows downloading

    private val audioModel = AudioModel(viewModelScope, content, playback, personalLibrary, errorHandler, persons, library, recentRanks, jobIndex)
    // Audio lives in [AudioModel]; the screens still reach it through here.
    val audio get() = audioModel.audio
    fun playWorkAudio(entityId: String, audioId: String) = audioModel.playWorkAudio(entityId, audioId)
    fun playAudio(audioId: String, queue: List<AudioUi> = audio.value.recordings) = audioModel.playAudio(audioId, queue)

    val issues: StateFlow<IssuesUiState> = combine(
        catalogDao.observeCollectionLean("question").pausedDuringUpdate(), retainedPins, library, combine(recentRanks, localStates, ::Pair),
    ) { entities, retainedIds, shelves, (ranks, states) ->
        val shelfIds = shelves.mapTo(hashSetOf(), LibraryEntryEntity::entityId)
        IssuesUiState(
            countLabel = arabicCount(entities.size, "المسائل", "مسألة واحدة", "مسألتين", "مسائل", "مسألة"),
            issues = entities.map { entity ->
                IssueUi(
                    id = entity.id,
                    question = entity.title,
                    answerExcerpt = entity.excerpt.orEmpty(),
                    scholar = entity.personName.orEmpty(),
                    topic = topicName(entity.topicsCsv.substringBefore(',')).ifBlank { "" },
                    sizeLabel = byteSize(entity.pkgSize),
                    downloaded = entity.id in retainedIds && entity.id in states.complete,
                    saved = entity.id in shelfIds,
                    recentRank = ranks[entity.id],
                    topics = entity.topicsCsv.split(',').filter(String::isNotBlank).map(::topicName),
                    died = entity.died,
                    hasAudio = entity.audioJson != "[]",
                )
            },
        )
    }.withTransfers("مسألة", IssuesUiState::issues, IssueUi::id, { state, rows -> state.copy(issues = rows) }) { row, transfer ->
        row.copy(transfer = transfer)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, IssuesUiState("٠ مسألة", emptyList(), loaded = false)) // hot from app start so the tab opens filled; a download tick only re-copies the rows downloading

    val poetry: StateFlow<PoetryUiState> = combine(
        // Decoded once per catalog change, not on every pin, shelf or progress change.
        catalogDao.observeCollection("poem").pausedDuringUpdate().map { list -> list.map { it to it.toCatalogEntry() } },
        pinIds, retainedPins, library, combine(recentRanks, localStates, ::Pair),
    ) { decoded, pinnedIds, retainedIds, shelves, (ranks, states) ->
        val shelfIds = shelves.mapTo(hashSetOf(), LibraryEntryEntity::entityId)
        PoetryUiState(
            countLabel = arabicCount(decoded.size, "القصائد", "قصيدة واحدة", "قصيدتين", "قصائد", "قصيدة"),
            poems = decoded.map { (entity, entry) ->
                PoemUi(
                    id = entity.id,
                    title = entity.title,
                    poet = entity.personName.orEmpty(),
                    openingVerses = entry.openingVerses,
                    topic = topicName(entity.topicsCsv.substringBefore(',')).ifBlank { "" },
                    downloaded = entity.id in retainedIds && entity.id in states.complete,
                    downloading = entity.id in pinnedIds &&
                        entity.id !in retainedIds &&
                        entity.id !in states.failed,
                    saved = entity.id in shelfIds,
                    recentRank = ranks[entity.id],
                    verseCountLabel = entry.verseCount?.takeIf { it > 0 }?.let { arabicCount(it, "", "بيت واحد", "بيتان", "أبيات", "بيتًا") },
                    sizeLabel = byteSize(entity.pkgSize),
                    topics = entity.topicsCsv.split(',').filter(String::isNotBlank).map(::topicName),
                    died = entity.died,
                    era = entity.era.orEmpty(),
                    hasAudio = entity.audioJson != "[]",
                    kind = entity.kind ?: "قصيدة",
                )
            },
        )
    }.withTransfers("قصيدة", PoetryUiState::poems, PoemUi::id, { state, rows -> state.copy(poems = rows) }) { row, transfer ->
        row.copy(transfer = transfer)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, PoetryUiState("٠ قصيدة", emptyList(), loaded = false)) // hot from app start so the tab opens filled; a download tick only re-copies the rows downloading

    init {
        viewModelScope.launch(errorHandler) {
            // This is local disk work only and returns immediately when the content DB is
            // already healthy. UI Flows are active before either rebuild or sync begins.
            runCatching { content.rebuildRetainedContent() }
            downloadsModel.recoverPendingDownloads()
            refreshCacheBytes()
            sync()
        }
        // Installs from before 8.11: give the search index word positions, a chunk at a time.
        viewModelScope.launch(errorHandler) { ContentSearchSchema.addPositions(content.contentDatabase) }
    }

    private var syncJob: kotlinx.coroutines.Job? = null

    /** 0..1 while a new catalog is downloaded and applied; the app shows it with a cancel button. */
    val catalogUpdate: StateFlow<Float?> = content.catalogUpdate

    /**
     * Not collected while a catalog applies: every slice commit re-ran each list query over the
     * whole catalog (measured: an 8-minute update at ~6 cores on a Redmi 12). The lists keep
     * their last value and refresh once when the update ends.
     */
    private fun <T> kotlinx.coroutines.flow.Flow<T>.pausedDuringUpdate(): kotlinx.coroutines.flow.Flow<T> =
        content.catalogUpdate.map { it != null }.distinctUntilChanged().flatMapLatest { updating ->
            if (updating) kotlinx.coroutines.flow.emptyFlow() else this
        }

    /** Stops a running catalog update; the next launch resumes it. */
    fun cancelSync() {
        syncJob?.cancel()
        _syncState.value = ContentSyncUiState.LocalReady
    }

    /**
     * Automatic checks (launch, retry buttons) stay quiet and retry a slow or dropped link twice;
     * the saved library works without them. Only [manual] checks, from Settings, surface a failure.
     */
    fun sync(manual: Boolean = false) {
        if (!content.configured) {
            _syncState.value = ContentSyncUiState.Disabled
            return
        }
        syncJob?.cancel()
        syncJob = viewModelScope.launch(errorHandler) {
            _syncState.value = ContentSyncUiState.Checking
            val retryDelaysMs = if (manual) emptyList() else listOf(15_000L, 60_000L)
            for (attempt in 0..retryDelaysMs.size) {
                try {
                    content.sync()
                    downloadsModel.recoverPendingDownloads()
                    _syncState.value = ContentSyncUiState.UpToDate
                    return@launch
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    android.util.Log.w("AtharContent", "sync attempt ${attempt + 1} failed", error)
                    if (attempt == retryDelaysMs.size) {
                        _syncState.value = ContentSyncUiState.Failed(syncFailureReason(error), manual)
                    } else {
                        kotlinx.coroutines.delay(retryDelaysMs[attempt])
                    }
                }
            }
        }
    }

    val retainedEntityIds: StateFlow<Set<String>> = content.retainedEntityIds


    override fun onCleared() {
        searchModel.finishActiveSearchTrace()
        super.onCleared()
    }

    /** The work read most recently as (id, collection), or null on a fresh install. */
    suspend fun lastReadWork(): Pair<String, String>? {
        val entity = catalogDao.entity(userDao.lastReadId() ?: return null) ?: return null
        return entity.id to entity.coll
    }

    /** The person page that lists this work, if any. */
    suspend fun authorOf(entityId: String): String? =
        catalogDao.observeGeneration().first()?.persons().orEmpty().firstOrNull { entityId in it.works }?.id

    suspend fun isPoem(entityId: String): Boolean = catalogDao.entity(entityId)?.coll == "poem"

    fun toggleFavourite(entityId: String) {
        viewModelScope.launch(errorHandler) { personalLibrary.toggleFavourite(entityId) }
    }

    /**
     * A recording is saved by saving its poem or book (one way to save a work; it then shows
     * under that section's المفضّلة). Moves any old "audio:<id>" favourites across.
     */
    fun foldAudioFavourites(recordings: List<com.atharchive.feature.audio.AudioUi>) {
        viewModelScope.launch(errorHandler) {
            val bySource = recordings.associate { "audio:${it.id}" to it.sourceEntityId }
            val old = personalLibrary.observeFavouriteIds().first().filter { it in bySource }
            if (old.isEmpty()) return@launch
            personalLibrary.foldIntoFavourites(old.mapNotNull(bySource::get))
            old.forEach { personalLibrary.toggleFavourite(it) }
        }
    }

    /** One-time fold of the old adhkar prefs ids; returns true once they can be dropped. */
    suspend fun foldFavourites(extraIds: Collection<String>): Boolean =
        runCatching { personalLibrary.foldIntoFavourites(extraIds) }.isSuccess


    suspend fun deleteAnnotation(id: String) = annotationRepository.delete(id)

    suspend fun editAnnotation(id: String, noteText: String?, colorId: Int = 0) =
        annotationRepository.edit(id, noteText, colorId, kind = AnnotationKind.Note)

    /** «إرسال»: saved books into one file for another phone; returns how many went in. */
    suspend fun exportSharedBooks(ids: Collection<String>, output: OutputStream): Int = content.exportShared(ids, output)

    /** «استقبال كتب»: each verified book imports from the file, no network needed; returns how many. */
    suspend fun importSharedBooks(input: InputStream): Int {
        val targets = content.importShared(input)
        targets.forEach { downloadScheduler.enqueue(it.entityId, it.collection, replace = true, offline = true) }
        return targets.size
    }

    /** Whether there is anything of the reader's own (a note or highlight) to lose with the phone. */
    suspend fun hasNotes(): Boolean = annotations.first().isNotEmpty()

    suspend fun exportUserData(output: OutputStream, extras: Map<String, String> = emptyMap()) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { userDataArchive.exportTo(output, extras) }
    }

    suspend fun importUserData(input: InputStream): UserDataImportReport {
        val report = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { userDataArchive.importFrom(input) }
        runCatching { personalLibrary.foldIntoFavourites(emptyList()) }
        annotations.first().map(Annotation::entityId).distinct().forEach { entityId ->
            runCatching { annotationRepository.reattachEntity(entityId) }
        }
        return report
    }


    suspend fun clearCache() {
        content.clearCache()
        _cacheBytes.value = content.cacheBytes()
    }

    fun refreshCacheBytes() {
        viewModelScope.launch(errorHandler) { _cacheBytes.value = content.cacheBytes() }
    }

    fun setCacheBudget(bytes: Long) {
        viewModelScope.launch(errorHandler) {
            content.setCacheBudget(bytes)
            _cacheBytes.value = content.cacheBytes()
        }
    }

    private fun ContentEntity.toBookUi(
        pinned: Boolean,
        retained: Boolean,
        complete: Boolean,
        saved: Boolean,
        progressPercent: Int?,
        recentRank: Int?,
    ): BookUi {
        val progress = progressPercent?.let { (it / 100f).coerceIn(0f, 1f) }
        val download = when {
            pinned && !retained ->
                BookDownloadUi.Downloading(
                    sizeLabel = byteSize(pkgSize),
                    transfer = TransferUi.Running(0f, ""),
                )
            retained && complete -> BookDownloadUi.Downloaded(byteSize(pkgSize))
            else -> BookDownloadUi.Available(byteSize(pkgSize))
        }
        return BookUi(
            id = id,
            title = title,
            author = personName.orEmpty(),
            discipline = topicName(topicsCsv.substringBefore(',')).ifBlank { "" },
            kind = kind ?: "كتاب",
            download = download,
            saved = saved,
            topics = topicsCsv.split(',').filter(String::isNotBlank).map(::topicName),
            died = died,
            hasAudio = audioJson != "[]",
            publishedAt = publishedAt.orEmpty(),
            pages = pageTo,
            volumes = volumes,
            volumeUnit = volumeUnit,
            recentRank = recentRank,
            readingProgress = progress,
            readingProgressLabel = progress?.let { "${arabicNumber((it * 100).toInt())}٪" },
        )
    }
}

internal fun duration(seconds: Int): String =
    "${arabicNumber(seconds / 60).padStart(2, '٠')}:${arabicNumber(seconds % 60).padStart(2, '٠')}"

/** The app's one byte formatter: Arabic digits and units, e.g. «٣٫١ ك.ب». */
// Integer tenths, not String.format: list rows call this thousands of times per rebuild, and the
// formatter was 9% of the CPU of a bulk download (2026-11-10 profile).
internal fun byteSize(bytes: Long): String {
    val (unitBytes, unit) = when {
        bytes >= 1L shl 30 -> (1L shl 30) to "غ.ب"
        bytes >= 1L shl 20 -> (1L shl 20) to "م.ب"
        bytes >= 1L shl 10 -> (1L shl 10) to "ك.ب"
        else -> return "${arabicNumerals(bytes.toString())} بايت"
    }
    val tenths = (bytes * 10 + unitBytes / 2) / unitBytes
    return "${arabicNumerals("${tenths / 10}\u066B${tenths % 10}")} $unit"
}

private fun arabicNumerals(s: String) = String(CharArray(s.length) { val c = s[it]; if (c in '0'..'9') '\u0660' + (c - '0') else c })

/**
 * Arabic count phrase with correct noun agreement: 1 and 2 use the singular/dual form, 3–10
 * the plural, 11–99 the accusative singular; above 100 the last two digits decide.
 */
internal fun arabicCount(n: Int, zero: String, one: String, two: String, few: String, many: String): String {
    val r = n % 100
    return when {
        n == 0 -> zero
        n == 1 -> one
        n == 2 -> two
        r in 3..10 -> "${arabicNumber(n)} $few"
        r in 11..99 -> "${arabicNumber(n)} $many"
        else -> "${arabicNumber(n)} $one".substringBefore(" واحد") // 100, 101, 102: genitive singular
    }
}

internal fun arabicNumber(value: Any): String = arabicDigits(value.toString())

/** One download as the lists show it: WorkManager's state plus the live bytes kept in memory. */
internal data class TransferJob(val state: WorkInfo.State, val error: String?, val downloaded: Long, val total: Long?, val queued: Boolean, val wifiOnly: Boolean)

/** Works stored complete and works whose last transfer failed, by id. */
internal data class LocalStates(val complete: Set<String>, val failed: Set<String>)

private val AnnotationKind.storageValue: String
    get() = name.lowercase()

private val AttachmentState.storageValue: String
    get() = name.lowercase()
