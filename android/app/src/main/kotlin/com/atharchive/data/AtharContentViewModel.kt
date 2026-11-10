package com.atharchive.data

import com.atharchive.core.data.repository.persons
import com.atharchive.ui.components.arabicDigits
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.atharchive.feature.audio.AudioUi
import com.atharchive.feature.audio.AudioUiState
import com.atharchive.feature.audio.AudioSourceKind
import com.atharchive.feature.audio.NowPlayingUi
import androidx.work.WorkInfo
import com.atharchive.feature.downloads.DownloadUi
import com.atharchive.feature.downloads.TransferUi
import com.atharchive.feature.downloads.BulkCandidate
import com.atharchive.feature.downloads.BulkRowUi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tracing.Trace
import com.atharchive.core.data.annotation.Annotation
import com.atharchive.core.data.annotation.AnnotationBlockId
import com.atharchive.core.data.annotation.AnnotationCopyStyle
import com.atharchive.core.data.annotation.AnnotationEndpoint
import com.atharchive.core.data.annotation.AnnotationFilter
import com.atharchive.core.data.annotation.AnnotationKind
import com.atharchive.core.data.annotation.AnnotationRepository
import com.atharchive.core.data.annotation.AnnotationText
import com.atharchive.core.data.annotation.AnnotationTextBlock
import com.atharchive.core.data.annotation.AttachmentState
import com.atharchive.core.data.archive.UserDataArchive
import com.atharchive.core.data.archive.UserDataImportReport
import com.atharchive.core.data.repository.visitBookFrames
import com.atharchive.core.data.repository.spans
import com.atharchive.feature.reader.ReaderHighlight
import com.atharchive.core.data.content.AudioReference
import com.atharchive.core.data.db.content.ChapterEntity
import com.atharchive.core.data.db.content.ContentBlockEntity
import com.atharchive.core.data.db.content.ContentEntity
import com.atharchive.core.data.db.user.LibraryEntryEntity
import com.atharchive.core.data.db.user.ReadingPositionEntity
import com.atharchive.core.data.repository.ReaderPrefetchSession
import com.atharchive.core.data.repository.ContentSearchRepository
import com.atharchive.core.data.repository.ContentSearchSchema
import com.atharchive.core.data.repository.PersonalLibraryRepository
import com.atharchive.core.data.repository.StoredBlockAttributes
import com.atharchive.core.data.repository.attributes
import com.atharchive.core.data.repository.toCatalogEntry
import com.atharchive.feature.issues.IssueUi
import com.atharchive.feature.issues.IssuesUiState
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
import com.atharchive.feature.kannashah.ExcerptUi
import com.atharchive.feature.kannashah.KannashahUiState
import com.atharchive.feature.reader.InBookHit
import com.atharchive.feature.reader.InBookSearch
import com.atharchive.feature.reader.BookAudioUi
import com.atharchive.feature.reader.ReaderBlock
import com.atharchive.feature.reader.ReaderBenefit
import com.atharchive.feature.reader.ReaderBookmark
import com.atharchive.feature.reader.ReaderSelection
import com.atharchive.feature.reader.ReaderUiState
import com.atharchive.feature.reader.TocEntry
import com.atharchive.feature.search.SearchFilters
import com.atharchive.feature.search.SearchHitUi
import com.atharchive.feature.search.SearchResultType
import com.atharchive.feature.search.SearchUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.ConcurrentHashMap
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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.drop

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
        viewModelScope, content, downloadScheduler, errorHandler, _errors, { recordingCatalog }, { audioSources },
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
    private val readerFlows = ConcurrentHashMap<String, Flow<ReaderUiState?>>()
    private val readerJobs = ConcurrentHashMap<String, Job>()
    private val readerWindows = ConcurrentHashMap<String, com.atharchive.feature.reader.ReaderWindow>()
    private val poemFlows = ConcurrentHashMap<String, Flow<PoemReaderUiState?>>()
    private val prefetchSessions = ConcurrentHashMap<String, ReaderPrefetchSession>()
    private val prefetchedFrames = ConcurrentHashMap<String, Int>()

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

    // Only works with recordings are decoded, and only when the catalog changes, not on every download tick.
    private val audioSources = combine(catalogDao.observeEntitiesWithAudio().pausedDuringUpdate(), persons) { list, people ->
        val names = people.associate { it.id to it.name }
        list.map { entity -> entity to entity.audioReferences().map { it to it.reciters.mapNotNull(names::get) } }
    }
    private val recordingCatalog = combine(
        audioSources, library, content.downloadedAudio, recentRanks,
        combine(content.requestedAudio, content.pausedDownloads, jobIndex) { requested, paused, jobs -> Triple(requested, paused, jobs) },
    ) { catalog, shelves, downloaded, ranks, transfers ->
        val shelfIds = shelves.mapTo(hashSetOf(), LibraryEntryEntity::entityId)
        val recordings = catalog.flatMap { (source, refs) ->
            refs.map { (ref, reciterNames) ->
                AudioUi(
                    id = ref.id, title = ref.title ?: source.title, sourceTitle = source.title,
                    sourceKind = when (source.coll) { "poem" -> AudioSourceKind.Poem; "question" -> AudioSourceKind.Question; else -> AudioSourceKind.Book },
                    durationLabel = duration(ref.seconds), durationSeconds = ref.seconds,
                    sizeLabel = byteSize(ref.size), bytes = ref.size,
                    downloaded = ref.id in downloaded, saved = source.id in shelfIds,
                    recentRank = ranks["audio:${ref.id}"],
                    sourceEntityId = source.id,
                    reciters = reciterNames,
                    transfer = if (ref.id !in transfers.first || ref.id in downloaded) null else {
                        val job = transfers.third["content:audio:${ref.id}"]
                        val bytes = job?.downloaded ?: 0L
                        val progress = (bytes.toFloat() / ref.size.coerceAtLeast(1)).coerceIn(0f, 1f)
                        val label = "${arabicNumber((progress * 100).toInt())}٪"
                        when {
                            "audio:${ref.id}" in transfers.second -> TransferUi.Paused(progress, label)
                            job?.state == WorkInfo.State.FAILED -> TransferUi.Failed("تعذّر التنزيل", progress)
                            else -> TransferUi.Running(progress, label)
                        }
                    },
                )
            }
        }.distinctBy { it.id }
        AudioUiState(
            countLabel = arabicCount(recordings.size, "الصوتيات", "تسجيل واحد", "تسجيلين", "تسجيلات", "تسجيلًا"),
            nowPlaying = null,
            recordings = recordings,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AudioUiState("٠ تسجيل", null, emptyList()))

    val audio = combine(recordingCatalog, playback.state) { catalog, player ->
        val current = catalog.recordings.firstOrNull { it.id == player.id }
        catalog.copy(nowPlaying = current?.let {
            NowPlayingUi(it, duration((player.positionMs / 1000).toInt()), duration((player.durationMs.takeIf { n -> n > 0 } ?: it.durationSeconds * 1000L).div(1000).toInt()),
                (player.positionMs.toFloat() / (player.durationMs.takeIf { n -> n > 0 } ?: it.durationSeconds * 1000L).coerceAtLeast(1)).coerceIn(0f, 1f), player.playWhenReady)
        })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AudioUiState("٠ تسجيل", null, emptyList()))

    fun playWorkAudio(entityId: String, audioId: String) {
        playAudio(audioId, audio.value.recordings.filter { it.sourceEntityId == entityId })
    }

    fun playAudio(audioId: String, queue: List<AudioUi> = audio.value.recordings) {
        viewModelScope.launch(errorHandler) {
            if (playback.state.value.id == audioId && playback.state.value.playWhenReady) {
                playback.toggle()
                return@launch
            }
            personalLibrary.recordOpened("audio:$audioId")
            // The decoded catalog is already in memory while anything audio is on screen.
            val references = audioSources.first().flatMap { (_, refs) -> refs.map { it.first } }.associateBy { it.id }
            val items = queue.map { recording ->
                MediaItem.Builder().setMediaId(recording.id).setCustomCacheKey(recording.id).setUri(content.audioUri(requireNotNull(references[recording.id])))
                    .setMediaMetadata(MediaMetadata.Builder().setTitle(recording.title).setArtist(recording.speaker).build()).build()
            }
            playback.play(items, queue.indexOfFirst { it.id == audioId })
        }
    }

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

    /**
     * The book (entity, contents, editions) is built once per change of those; a saved reading position
     * or a new annotation only copies it. The text comes through [readerWindow] and footnotes by id.
     */
    fun readerState(entityId: String): Flow<ReaderUiState?> = readerFlows.getOrPut(entityId) {
        val book = combine(catalogDao.observeEntity(entityId), importDao.observeChapters(entityId)) { entity, chapters ->
            entity?.toReaderUi(chapters)?.copy(
                editions = catalogDao.editionsFor(entity.editionOf ?: entity.id).takeIf { it.size > 1 }.orEmpty()
                    .sortedBy { it.editionOf != null }.map {
                        com.atharchive.feature.reader.ReaderEdition(
                            it.id, it.edition?.takeIf(String::isNotBlank) ?: it.title,
                            current = it.id == entity.id, primary = it.editionOf == null,
                        )
                    },
            )
        }
        combine(
            book,
            userDao.observeReadingPosition(entityId).map { it?.ordinalHint to it?.offsetInBlock }.distinctUntilChanged(),
            annotationRepository.observe(AnnotationFilter(entityId = entityId)),
        ) { state, (ordinal, offset), saved ->
            state?.copy(readingPositionIndex = ordinal ?: 0, readingOffset = offset ?: 0)
                ?.withAnnotations(saved, saved.filter { it.kind == AnnotationKind.Bookmark && it.quotedText.isBlank() }
                    .associate { it.id to importDao.blockAt(entityId, it.start.ordinalHint)?.text.orEmpty() })
        }
    }

    /** Footnote bodies by id, for a page's notes block and the marker callout. */
    suspend fun footnoteBodies(entityId: String, ids: List<String>): Map<String, ReaderBlock.FootnoteBody> =
        ids.distinct().chunked(500).flatMap { importDao.footnotes(entityId, it) }.sortedBy { ids.indexOf(it.fnId) }
            .mapIndexed { index, note ->
                note.fnId to ReaderBlock.FootnoteBody("fn-${note.fnId}", note.marker ?: arabicNumber(index + 1), note.text, spans = note.spans())
            }.toMap()

    fun readerWindow(entityId: String): com.atharchive.feature.reader.ReaderWindow =
        readerWindows.getOrPut(entityId) {
            val job = SupervisorJob(viewModelScope.coroutineContext[Job])
            readerJobs[entityId] = job
            val scope = CoroutineScope(viewModelScope.coroutineContext + job)
            com.atharchive.feature.reader.ReaderWindow(
                scope = scope,
                total = { content.blockCount(entityId) },
                load = { range ->
                    val blocks = content.blockRange(entityId, range).map(ContentBlockEntity::toReaderBlock)
                    // A page footer gets its note bodies here, only for the pages loaded.
                    val notes = footnoteBodies(entityId, blocks.filterIsInstance<ReaderBlock.PageNotes>().flatMap { it.footnotes })
                    blocks.map { if (it is ReaderBlock.PageNotes) it.copy(notes = it.footnotes.mapNotNull(notes::get)) else it }
                },
            ).also { window ->
                // A finished or replaced download swaps the text under the reader.
                scope.launch { content.observePackage(entityId).drop(1).collect { window.invalidate() } }
            }
        }

    suspend fun selectionBlocks(entityId: String, range: IntRange): List<ReaderBlock> =
        content.blockRange(entityId, range).map(ContentBlockEntity::toReaderBlock)

    suspend fun savedPoemOrdinal(entityId: String, annotationId: String): Int {
        visitTextFrames(entityId) { false }
        return annotationRepository.reattach(annotationId).start.ordinalHint
    }

    fun poemReaderState(entityId: String): Flow<PoemReaderUiState?> = poemFlows.getOrPut(entityId) {
        combine(catalogDao.observeEntity(entityId), importDao.observeBlocks(entityId), userDao.observeReadingPosition(entityId),
            annotationRepository.observe(AnnotationFilter(entityId = entityId))) { entity, blocks, position, saved ->
            val (marks, benefits) = saved.partition { it.kind == AnnotationKind.Bookmark }
            val ordinals = blocks.associate { AnnotationBlockId(it.blockIdHi, it.blockIdLo) to it.ordinal }
            val reading = position?.let { ordinals[AnnotationBlockId(it.blockIdHi, it.blockIdLo)] }
            entity?.toPoemReaderUi(blocks)?.copy(
                readingOrdinal = reading ?: position?.ordinalHint ?: 0,
                readingPositionReady = position == null || reading != null || blocks.size >= entity.pkgBlocks,
                bookmarks = marks.mapNotNull { ordinals[it.start.blockId] },
                saved = benefits.mapNotNull { ordinals[it.start.blockId] },
            )
        }
    }

    fun openReader(entityId: String, targetOrdinal: Int? = null) {
        // Ended by the reader when its first page shows (the bookOpen benchmark reads it).
        Trace.beginAsyncSection(BookOpenTraceName, 0)
        viewModelScope.launch(errorHandler) {
            personalLibrary.recordOpened(entityId)
            val saved = userDao.readingPosition(entityId)
            val stable = saved?.let { importDao.blockByStableId(entityId, it.blockIdHi, it.blockIdLo) }
            val entity = catalogDao.entity(entityId) ?: return@launch
            Analytics.hit("book", entityId)
            val position = (targetOrdinal ?: stable?.ordinal ?: saved?.ordinalHint ?: 0)
                .coerceIn(0, (entity.pkgBlocks - 1).coerceAtLeast(0))
            if (targetOrdinal == null && saved != null && position != saved.ordinalHint) {
                userDao.saveReadingPosition(saved.copy(ordinalHint = position, chapterAnchor = stable?.chapterAnchor ?: saved.chapterAnchor))
            }
            try {
                content.open(entityId, position)
                if (entity.coll == "poem") visitTextFrames(entityId) { false }
            }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (error: Exception) { _errors.tryEmit(error.message ?: "تعذّر تحميل النص") }
            runCatching { annotationRepository.reattachEntity(entityId) }
            refreshCacheBytes()
        }
    }

    private suspend fun visitTextFrames(entityId: String, visit: suspend (IntRange) -> Boolean) {
        val total = requireNotNull(catalogDao.entity(entityId)?.pkgBlocks) { "تعذّر العثور على النص" }
        visitBookFrames(total, frameEnd = { ordinal ->
            content.open(entityId, ordinal)
            val frame = requireNotNull(importDao.frames(entityId).firstOrNull {
                ordinal in it.firstBlockOrdinal until it.firstBlockOrdinal + it.blockCount
            }) { "تعذّر تحميل جزء من النص" }
            frame.firstBlockOrdinal + frame.blockCount - 1
        }, visit = visit)
    }

    val retainedEntityIds: StateFlow<Set<String>> = content.retainedEntityIds

    /** Starts a whole-book download unless one is already saved or under way. */
    /** The text a reader would open now: the downloaded package, else the catalog's (read-through pages follow it). */
    suspend fun textVersion(entityId: String): String? =
        importDao.state(entityId)?.localPackageHash ?: catalogDao.entity(entityId)?.pkgHash

    suspend fun findPrintedPage(entityId: String, page: Int, volume: Int?): Int? {
        importDao.printedPageOrdinal(entityId, page, volume)?.let { return it }
        var found: Int? = null
        visitTextFrames(entityId) {
            found = importDao.printedPageOrdinal(entityId, page, volume)
            found != null
        }
        return found
    }

    /** (volume, printed page) of a block, for «نسخ مع التذييل». */
    suspend fun printedPlace(entityId: String, ordinal: Int): Pair<Int?, Int?> =
        importDao.blockAt(entityId, ordinal).let { it?.vol to it?.printedPage }

    suspend fun findHeadingAnchor(entityId: String, anchor: String): Int? {
        importDao.headingAnchorOrdinal(entityId, anchor)?.let { return it }
        var found: Int? = null
        visitTextFrames(entityId) {
            found = importDao.headingAnchorOrdinal(entityId, anchor)
            found != null
        }
        return found
    }

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

    suspend fun saveSelection(entityId: String, selection: ReaderSelection, kind: AnnotationKind = AnnotationKind.Highlight) {
            val first = minOf(selection.start.ordinal, selection.end.ordinal)
            val last = maxOf(selection.start.ordinal, selection.end.ordinal)
            val blocks = importDao.blockWindow(entityId, first, last)
            val startBlock = requireNotNull(blocks.find { it.stableId() == selection.start.blockId }) { "موضع بداية التحديد غير متاح" }
            val endBlock = requireNotNull(blocks.find { it.stableId() == selection.end.blockId }) { "موضع نهاية التحديد غير متاح" }
            val capture = AnnotationText.capture(
                blocks = blocks.map(ContentBlockEntity::toAnnotationTextBlock),
                start = AnnotationEndpoint(
                    AnnotationBlockId(startBlock.blockIdHi, startBlock.blockIdLo),
                    startBlock.ordinal,
                    selection.start.offset,
                ),
                end = AnnotationEndpoint(
                    AnnotationBlockId(endBlock.blockIdHi, endBlock.blockIdLo),
                    endBlock.ordinal,
                    selection.end.offset,
                ),
            )
            // The reader joins a sentence running over a page with a space, the capture with a blank
            // line (and a verse's halves with « … »): compare the words, not how parts are joined.
            require(sameWords(capture.quotedText, selection.quotedText)) { "تغيّر النص أو لم يكتمل تحميله؛ أعد تحديد المقطع قبل حفظه" }
            annotationRepository.save(entityId, kind, capture)
    }

    /** A whole block into الكناشة: a poem saves a verse at a time, not a dragged selection. */
    suspend fun saveBlock(entityId: String, ordinal: Int) {
        val block = requireNotNull(importDao.blockAt(entityId, ordinal)) { "النص لم يُحمّل بعد" }
        val id = AnnotationBlockId(block.blockIdHi, block.blockIdLo)
        annotationRepository.save(entityId, AnnotationKind.Highlight, AnnotationText.capture(
            blocks = listOf(block.toAnnotationTextBlock()),
            start = AnnotationEndpoint(id, block.ordinal, 0),
            end = AnnotationEndpoint(id, block.ordinal, block.text.length),
        ))
    }

    /** The highlight a saved verse is kept under, for its note. */
    suspend fun savedBlockId(entityId: String, ordinal: Int): String? {
        val block = importDao.blockAt(entityId, ordinal) ?: return null
        val blockId = AnnotationBlockId(block.blockIdHi, block.blockIdLo)
        return annotationRepository.observe(AnnotationFilter(entityId = entityId)).first()
            .firstOrNull { it.kind != AnnotationKind.Bookmark && it.start.blockId == blockId }?.id
    }

    /** Takes a verse out of الكناشة: every highlight that starts in it. */
    suspend fun removeSavedBlock(entityId: String, ordinal: Int) {
        val block = requireNotNull(importDao.blockAt(entityId, ordinal)) { "النص لم يُحمّل بعد" }
        val blockId = AnnotationBlockId(block.blockIdHi, block.blockIdLo)
        annotationRepository.observe(AnnotationFilter(entityId = entityId)).first()
            .filter { it.kind != AnnotationKind.Bookmark && it.start.blockId == blockId }
            .forEach { annotationRepository.delete(it.id) }
    }

    suspend fun toggleBookmark(entityId: String, ordinal: Int) {
        // The top of the screen can be a page divider or a page's notes: mark the text after it.
        val block = importDao.blockWindow(entityId, ordinal, ordinal + BookmarkLookahead).firstOrNull { it.text.isNotBlank() }
            ?: requireNotNull(importDao.blockAt(entityId, ordinal)) { "النص لم يُحمّل بعد" }
        val blockId = AnnotationBlockId(block.blockIdHi, block.blockIdLo)
        val existing = annotationRepository.observe(
            AnnotationFilter(entityId = entityId, kind = AnnotationKind.Bookmark),
        ).first().find { it.start.blockId == blockId || (it.state != AttachmentState.Exact && it.start.ordinalHint == block.ordinal) }
        if (existing != null) {
            annotationRepository.delete(existing.id)
            return
        }
        val endpoint = AnnotationEndpoint(
            blockId = blockId,
            ordinalHint = block.ordinal,
            offset = 0,
        )
        val capture = AnnotationText.capture(listOf(block.toAnnotationTextBlock()), endpoint, endpoint.copy(offset = block.text.length.coerceAtMost(120)))
        annotationRepository.save(entityId, AnnotationKind.Bookmark, capture)
    }

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

    fun onReaderPosition(entityId: String, ordinal: Int, offset: Int = 0) {
        viewModelScope.launch(errorHandler) {
            val entity = catalogDao.entity(entityId) ?: return@launch
            val block = importDao.blockAt(entityId, ordinal) ?: return@launch
            userDao.saveReadingPosition(
                ReadingPositionEntity(
                    entityId = entityId,
                    chapterAnchor = block.chapterAnchor,
                    blockIdHi = block.blockIdHi,
                    blockIdLo = block.blockIdLo,
                    ordinalHint = ordinal,
                    offsetInBlock = offset.coerceIn(0, block.text.length),
                    progressPct = if (entity.pkgBlocks <= 1) 0.0 else ordinal.toDouble() / (entity.pkgBlocks - 1),
                    updatedAt = System.currentTimeMillis(),
                ),
            )

            val frame = importDao.frames(entityId).firstOrNull {
                ordinal in it.firstBlockOrdinal until (it.firstBlockOrdinal + it.blockCount)
            } ?: return@launch
            // First call comes only after the first page is drawn and the reader is idle, so
            // prefetch never competes with the first open. It then re-runs on each new frame.
            if (prefetchedFrames.put(entityId, frame.frameOrdinal) != frame.frameOrdinal) {
                prefetchSessions.remove(entityId)?.close()
                content.prefetchAdjacent(viewModelScope, entityId, ordinal)?.let {
                    prefetchSessions[entityId] = it
                }
            }
        }
    }

    /**
     * The edition to open for [entityId]. A work opened by its default edition opens the edition
     * last chosen in «الطبعات», so a reader who switched never has to switch again; a link to a
     * specific edition (a search hit, a non-default id) opens as given.
     */
    suspend fun editionToOpen(entityId: String): String {
        val entity = catalogDao.entity(entityId) ?: return entityId
        if (entity.editionOf != null) return entityId
        val preferred = content.preferredEdition(entityId) ?: return entityId
        val edition = catalogDao.entity(preferred)
        return if (edition != null && edition.catalogPresent && edition.editionOf == entityId) preferred else entityId
    }

    /**
     * Remembers [editionId] as the edition the work [primaryId] opens with from now on. Synchronous
     * on purpose: the reader that replaces this one asks [editionToOpen] straight away.
     */
    fun chooseEdition(primaryId: String, editionId: String) = content.setPreferredEdition(primaryId, editionId)

    fun closeReader(entityId: String) {
        prefetchSessions.remove(entityId)?.close()
        prefetchedFrames.remove(entityId)
        readerJobs.remove(entityId)?.cancel()
        readerWindows.remove(entityId)
        readerFlows.remove(entityId)
        poemFlows.remove(entityId)
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

private fun ContentEntity.toReaderUi(chapters: List<ChapterEntity>): ReaderUiState {
    return ReaderUiState(
        bookTitle = title,
        isBook = coll == "book",
        author = personName.orEmpty(),
        blocks = emptyList(),
        hasPages = pageTo != null,
        sourceUrl = PlayStoreUrl,
        card = runCatching { toCatalogEntry().card }.getOrDefault(emptyList()),
        toc = chapters.multiVolume().let { vols ->
            chapters.map { chapter ->
                val page = chapter.page?.let { "ص ${com.atharchive.ui.components.arabicDigits(it)}" }.orEmpty()
                val label = chapter.vol?.takeIf { vols && page.isNotEmpty() }?.let { "${com.atharchive.feature.reader.volumeLabel(it, volumeUnit)} · $page" } ?: page
                TocEntry(chapter.anchor, chapter.title, chapter.level, chapter.firstOrdinal, label)
            }
        },
        bookmarks = emptyList(),
        benefits = emptyList(),
        audio = audioReferences().map { BookAudioUi(it.id, it.title ?: title, "", "", duration(it.seconds), it.seconds) },
        blockCount = pkgBlocks,
        // ponytail: volumes come from the contents' chapters; a book whose chapters carry no vol shows no volume menu.
        volumeStarts = chapters.filter { it.vol != null }.groupBy { it.vol!! }
            .map { (vol, list) -> vol to list.minOf { it.firstOrdinal } }.sortedBy { it.first }.takeIf { it.size > 1 }.orEmpty(),
        volumeUnit = volumeUnit,
        pageRange = if (chapters.multiVolume() || (volumes ?: 1) > 1) null else pageTo?.let { (pageFrom ?: 1)..it },
    )
}

private fun ReaderUiState.withAnnotations(annotations: List<Annotation>, previews: Map<String, String> = emptyMap()): ReaderUiState {
    fun chapterFor(annotation: Annotation): String =
        toc.lastOrNull { it.blockIndex <= annotation.start.ordinalHint }?.title.orEmpty()
    fun pageFor(annotation: Annotation): String? = annotation.printedPage?.let { "ص ${arabicNumber(it)}" }
    val bookmarks = annotations.filter { it.kind == AnnotationKind.Bookmark }.map { annotation ->
        val preview = annotation.quotedText.ifBlank { previews[annotation.id].orEmpty().ifBlank { blocks.firstOrNull { it.ordinal == annotation.start.ordinalHint }.readableText().orEmpty() } }
        ReaderBookmark(
            id = annotation.id,
            blockIndex = annotation.start.ordinalHint,
            chapterTitle = chapterFor(annotation),
            preview = preview.take(120),
            pageLabel = pageFor(annotation),
        )
    }
    val benefits = annotations.filter { it.kind != AnnotationKind.Bookmark }.map { annotation ->
        ReaderBenefit(
            id = annotation.id,
            blockIndex = annotation.start.ordinalHint,
            text = annotation.quotedText,
            chapterTitle = chapterFor(annotation),
            pageLabel = pageFor(annotation),
        )
    }
    val highlights = annotations.filter { it.kind != AnnotationKind.Bookmark && it.state == AttachmentState.Exact }
        .map { ReaderHighlight(it.start.ordinalHint, it.end.ordinalHint, it.start.offset, it.end.offset, it.id) }
    return copy(bookmarks = bookmarks, benefits = benefits, highlights = highlights)
}

/** The parser's «…» / «...» / «---» between a verse's halves (src/lib/app-v2/parser.ts HEMISTICH_RE). */
private val HemistichSeparator = Regex("""\s*(?:---|\.\.\.|…)\s*""")

private fun ContentEntity.toPoemReaderUi(blocks: List<ContentBlockEntity>): PoemReaderUiState {
    val prose = blocks.none { it.type == "verse" }
    // A باب heading rides on the verse after it, so the list stays one row per verse
    // (indices are what the panel and audio jump to).
    var heading: String? = null
    val verses = mutableListOf<VerseUi>()
    blocks.forEach { block ->
        if (block.type.startsWith('h')) { heading = listOfNotNull(heading, block.text).joinToString(" — "); return@forEach }
        if (block.type != "verse" && !(prose && block.type == "p")) return@forEach
        val attrs = block.safeAttributes()
        // A verse carrying a footnote mark or emphasis comes as one line (x), separator and all.
        val halves = if (attrs.s == null && block.type == "verse") block.text.split(HemistichSeparator, limit = 2) else null
        verses += VerseUi(
            id = block.stableId(),
            number = attrs.n ?: verses.size + 1,
            sadr = attrs.s ?: halves?.first() ?: block.text,
            ajz = attrs.j ?: halves?.getOrNull(1).orEmpty(),
            ordinal = block.ordinal,
            commentary = attrs.commentary.map { com.atharchive.feature.poemreader.CommentaryUi(it.sourceLabel, it.text) },
            heading = heading,
        )
        heading = null
    }
    return PoemReaderUiState(
        card = toCatalogEntry().card,
        title = title,
        poet = personName.orEmpty(),
        sourceUrl = PlayStoreUrl,
        bodyLoaded = blocks.size >= pkgBlocks,
        verses = verses,
        recordings = audioReferences().map { audio ->
            PoemAudioUi(
                id = audio.id,
                title = audio.title ?: title,
                collection = "المنظومات",
                reciter = "",
                durationLabel = duration(audio.seconds),
                durationSeconds = audio.seconds,
                cues = audio.cues.map { VerseCue(it.v, it.t) },
            )
        },
    )
}

private fun ContentEntity.audioReferences(): List<AudioReference> =
    runCatching { toCatalogEntry().audio }.getOrDefault(emptyList())

private fun ContentBlockEntity.toReaderBlock(): ReaderBlock {
    val id = stableId()
    val attrs = safeAttributes()
    val spans = runCatching { spans() }.getOrDefault(emptyList())
    return when {
        type.startsWith('h') -> ReaderBlock.Heading(
            id = id,
            level = type.drop(1).toIntOrNull() ?: 2,
            text = text,
            anchor = attrs.ha ?: chapterAnchor,
            ordinal = ordinal,
            spans = spans,
        )
        type == "quote" && attrs.footnotes.isEmpty() -> ReaderBlock.Quote(id, text, ordinal, spans)
        type == "verse" -> ReaderBlock.Verse(id, attrs.s ?: text, attrs.j, ordinal, spans, attrs.footnotes)
        type == "notes" -> ReaderBlock.PageNotes(id, attrs.footnotes, ordinal)
        type == "break" -> ReaderBlock.Divider(id, ordinal)
        type == "page" -> ReaderBlock.PageBreak(id, printedPage ?: 0, vol, ordinal, footnotes = attrs.footnotes)
        else -> ReaderBlock.Paragraph(id, text, attrs.footnotes, ordinal, spans,
            if (type == "li" && attrs.continuation != true) if (attrs.ordered == true) "${attrs.start ?: 1}." else "•" else null,
            quoted = attrs.quoted == true || type == "quote")
    }
}

private fun ContentBlockEntity.toAnnotationTextBlock(): AnnotationTextBlock = AnnotationTextBlock(
    id = AnnotationBlockId(blockIdHi, blockIdLo),
    ordinal = ordinal,
    // The reader leaves page markers, rules and the page-foot notes out of a selection; so does this.
    semanticText = text.takeUnless { type == "page" || type == "break" || type == "notes" },
    fingerprint = fp64,
    chapterAnchor = chapterAnchor,
    printedPage = printedPage,
    copyStyle = if (type == "verse") AnnotationCopyStyle.Verse else AnnotationCopyStyle.Prose,
)

private fun ReaderBlock?.readableText(): String? = when (this) {
    is ReaderBlock.Heading -> text
    is ReaderBlock.Paragraph -> text
    is ReaderBlock.Quote -> text
    is ReaderBlock.Verse -> listOfNotNull(sadr, ajuz).joinToString(" … ")
    is ReaderBlock.FootnoteBody -> text
    is ReaderBlock.PageBreak, is ReaderBlock.PageNotes, is ReaderBlock.Divider, null -> null
}

private fun ContentBlockEntity.safeAttributes(): StoredBlockAttributes =
    runCatching { attributes() }.getOrDefault(StoredBlockAttributes())

private fun ContentBlockEntity.stableId(): String =
    blockIdHi.toULong().toString(16).padStart(16, '0') +
        blockIdLo.toULong().toString(16).padStart(16, '0')

private fun duration(seconds: Int): String =
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

/** Blocks looked past for a bookmark's text: a divider and a notes footer, with room to spare. */
internal const val BookmarkLookahead = 4

private fun List<ChapterEntity>.multiVolume() = mapNotNull { it.vol }.distinct().size > 1

/** Same text once spacing and the « … » between a verse's halves are set aside. */
internal fun sameWords(a: String, b: String): Boolean {
    fun words(s: String) = s.filterNot { it.isWhitespace() || it == '…' }
    return words(a) == words(b)
}
