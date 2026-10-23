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
import com.atharchive.feature.downloads.bulkRows
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
import com.atharchive.core.data.repository.BlockSearchHit
import com.atharchive.core.data.repository.spans
import com.atharchive.feature.reader.ReaderHighlight
import com.atharchive.core.data.content.AudioReference
import com.atharchive.core.data.db.content.ChapterEntity
import com.atharchive.core.data.db.content.ContentAvailability
import com.atharchive.core.data.db.content.ContentBlockEntity
import com.atharchive.core.data.db.content.ContentEntity
import com.atharchive.core.data.db.content.ContentTransferState
import com.atharchive.core.data.db.content.FootnoteEntity
import com.atharchive.core.data.db.user.LibraryEntryEntity
import com.atharchive.core.data.db.user.LibraryStatus
import com.atharchive.core.data.db.user.PinnedDownloadEntity
import com.atharchive.core.data.db.user.ReadingHistoryEntity
import com.atharchive.core.data.db.user.ReadingPositionEntity
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
import com.atharchive.feature.reader.BookAudioUi
import com.atharchive.feature.reader.ReaderBlock
import com.atharchive.feature.reader.ReaderBenefit
import com.atharchive.feature.reader.ReaderBookmark
import com.atharchive.feature.reader.ReaderSelection
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
import java.util.concurrent.atomic.AtomicInteger
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.drop

private const val SearchDebounceMs = 60L
private const val SearchLatencyTraceName = "AtharSearchKeystrokeToResults"

sealed interface ContentSyncUiState {
    data object LocalReady : ContentSyncUiState
    data object Checking : ContentSyncUiState
    data object UpToDate : ContentSyncUiState
    data object Disabled : ContentSyncUiState
    data class Failed(val message: String) : ContentSyncUiState
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@HiltViewModel
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class AtharContentViewModel @Inject constructor(
    private val savedState: androidx.lifecycle.SavedStateHandle,
    private val content: ContentAccess,
    val playback: AudioPlayback,
    private val downloadScheduler: ContentDownloadScheduler,
) : ViewModel() {
    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val errors = _errors.asSharedFlow()
    private val errorHandler = CoroutineExceptionHandler { _, error ->
        _errors.tryEmit(error.message ?: "تعذّرت العملية؛ أعد المحاولة")
    }
    private val userDao = content.userDatabase.userDataDao()
    private val catalogDao = content.contentDatabase.catalogDao()
    private val importDao = content.contentDatabase.importDao()
    private val pins = userDao.observePinnedDownloads()
    // A tap shows at once: saving the pin waits behind any book update being installed (minutes for a large one).
    private val requested = MutableStateFlow<Set<String>>(emptySet())
    private val pinIds = combine(pins, requested) { downloads, tapped ->
        downloads.mapTo(hashSetOf(), PinnedDownloadEntity::entityId) + tapped
    }
    private val retainedPins = combine(pins, content.retainedEntityIds) { downloads, retained ->
        downloads.asSequence()
            .map(PinnedDownloadEntity::entityId)
            .filterTo(hashSetOf()) { it in retained }
    }
    private val library = userDao.observeLibraryEntries()
    private val positions = userDao.observeReadingPositions()
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
    private val recentSearches = personalLibrary.observeRecentSearches()
    private val _searchQuery = MutableStateFlow(savedState.get<String>("searchQuery").orEmpty())
    private val _searchFilters = MutableStateFlow(SearchFilters(
        types = savedState.get<ArrayList<String>>("searchTypes").orEmpty().mapNotNull { key -> SearchResultType.entries.find { it.name == key } }.toSet(),
        field = SearchField.entries.find { it.name == savedState.get<String>("searchField") } ?: SearchField.FullText,
        sources = savedState.get<ArrayList<String>>("searchSources").orEmpty().toSet(),
        authors = savedState.get<ArrayList<String>>("searchAuthors").orEmpty().toSet(),
        sort = SearchSort.entries.find { it.name == savedState.get<String>("searchSort") } ?: SearchSort.Relevance,
    ))
    val searchQuery: StateFlow<String> = _searchQuery
    private val _searchLimit = MutableStateFlow(50)
    private val readerFlows = ConcurrentHashMap<String, Flow<ReaderUiState?>>()
    private val readerJobs = ConcurrentHashMap<String, Job>()
    private val readerWindows = ConcurrentHashMap<String, com.atharchive.feature.reader.ReaderWindow>()
    private val poemFlows = ConcurrentHashMap<String, Flow<PoemReaderUiState?>>()
    private val prefetchSessions = ConcurrentHashMap<String, ReaderPrefetchSession>()
    private val prefetchedFrames = ConcurrentHashMap<String, Int>()
    private val searchTraceSequence = AtomicInteger()
    private var activeSearchTrace: SearchTrace? = null

    private val _syncState = MutableStateFlow<ContentSyncUiState>(ContentSyncUiState.LocalReady)
    private val _cacheBytes = MutableStateFlow(0L)
    val syncState: StateFlow<ContentSyncUiState> = _syncState
    val storageStatus = content.storageStatus
    val cacheBytes: StateFlow<Long> = _cacheBytes
    val cacheBudgetBytes: StateFlow<Long> = content.cacheBudgetBytes
    val pinnedCount: StateFlow<Int> = combine(pins, content.requestedAudio) { text, audio -> text.size + audio.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val favouriteIds: StateFlow<Set<String>> = personalLibrary.observeFavouriteIds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val kannashahState: StateFlow<KannashahUiState> = combine(
        annotations,
        catalogDao.observeAllEntities(),
    ) { saved, catalog ->
        val sourceById = catalog.associateBy(ContentEntity::id)
        val excerpts = saved.asSequence()
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
                    sourceUrl = source?.let { "https://athar.arthurarchive.com/${if (it.coll == "question") "questions" else it.coll}/${it.id}" }.orEmpty(),
                    locationLabel = when (annotation.state) {
                        AttachmentState.Orphaned -> "فقد موضعه"
                        AttachmentState.Approximate -> annotation.printedPage?.let {
                            "قرب ص ${arabicNumber(it)}"
                        } ?: "موضع تقريبي"
                        AttachmentState.Exact -> annotation.printedPage?.let { "ص ${arabicNumber(it)}" }
                    },
                    topics = source?.topicsCsv?.split(',')?.filter(String::isNotBlank)?.map(::topicName).orEmpty(),
                    comment = annotation.noteText,
                    sourceOrdinal = annotation.start.ordinalHint,
                    addedOrder = annotation.updatedAt,
                    kind = annotation.kind.storageValue,
                    attachmentState = annotation.state.storageValue,
                )
            }
            .toList()
        KannashahUiState(
            headline = if (excerpts.isEmpty()) "" else
                "${arabicCount(excerpts.size, "", "مقتطف واحد", "مقتطفان", "مقتطفات", "مقتطفًا")} من " +
                "${arabicCount(excerpts.map(ExcerptUi::entityId).distinct().size, "", "مصدر واحد", "مصدرين", "مصادر", "مصدرًا")}",
            excerpts = excerpts,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), KannashahUiState())

    val search: StateFlow<SearchUiState> = combine(
        // Leave half of the §17 120 ms keystroke-to-results target for Room and drawing.
        _searchQuery.debounce(SearchDebounceMs),
        _searchFilters,
        recentSearches,
        // The filter lists are built once per catalog change, not on every keystroke.
        catalogDao.observeCatalog().map { SearchCatalog(it, it.map(ContentEntity::title), it.mapNotNull(ContentEntity::personName).distinct()) },
    ) { query, filters, recent, catalog ->
        SearchInput(query, filters, recent.map { it.q }, catalog)
    }.combine(_searchLimit) { input, limit -> input to limit }.mapLatest { (input, limit) ->
        val clean = input.query.trim()
        val base = SearchUiState(
            query = input.query,
            filters = input.filters,
            recentQueries = input.recent,
            directMatches = emptyList(),
            hits = emptyList(),
            availableSources = input.catalog.sources,
            availableAuthors = input.catalog.authors,
        )
        if (clean.length < 2) return@mapLatest base
        val sourceIds = input.filters.sources.takeIf { it.isNotEmpty() }?.let { sources ->
            input.catalog.entities.asSequence().filter { it.title in sources }.mapTo(linkedSetOf()) { it.id }
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
            limit = limit + 1,
        )
        base.copy(
            hasMore = result.catalog.size > limit || result.blocks.size > limit,
            directMatches = result.catalog.take(limit).map { hit ->
                DirectMatchUi(
                    id = hit.entityId,
                    title = hit.title,
                    kindLabel = hit.kind.ifBlank { hit.collection.toResultType().label },
                    contextLabel = hit.personName,
                    type = hit.collection.toResultType(),
                )
            },
            hits = result.blocks.take(limit).map { hit ->
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

    private val textDownloads: StateFlow<List<DownloadUi>> = combine(
        catalogDao.observeAllEntities(), pinIds, retainedPins, downloadScheduler.workInfos, content.pausedDownloads,
    ) { entities, pinnedIds, retainedIds, jobs, pausedIds ->
        entities.filter { it.id in pinnedIds }.map { entity ->
            val identity = "content:${entity.coll}:${entity.id}"
            val related = jobs.filter { identity in it.tags }
            val job = related.firstOrNull { !it.state.isFinished } ?: related.firstOrNull { it.state == WorkInfo.State.FAILED }
            val downloaded = job?.progress?.getLong("downloaded_bytes", 0) ?: 0
            val total = job?.progress?.getLong("total_bytes", entity.pkgSize) ?: entity.pkgSize
            val progress = if (total > 0) (downloaded.toFloat() / total).coerceIn(0f, 1f) else 0f
            val label = "${arabicNumber((progress * 100).toInt())}٪"
            DownloadUi(
                id = entity.id, title = entity.title,
                kindLabel = when (entity.coll) { "question" -> "مسألة"; "poem" -> "قصيدة"; "article" -> "مقال"; else -> "كتاب" },
                sizeLabel = byteSize(entity.pkgSize),
                bytes = entity.pkgSize,
                transfer = when {
                    entity.id in retainedIds -> null
                    entity.id in pausedIds -> TransferUi.Paused(progress, label)
                    job?.state == WorkInfo.State.FAILED || (job == null && entity.transferState == ContentTransferState.FAILED) ->
                        TransferUi.Failed(job?.outputData?.getString("error") ?: "تعذّر التنزيل", progress)
                    else -> TransferUi.Running(progress, if (job?.state == WorkInfo.State.ENQUEUED) "في الانتظار · $label" else label)
                },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Transfer state by entity id, built once per change instead of searched per row. */
    private val textTransfers = textDownloads.map { list -> list.associate { it.id to it.transfer } }

    val persons = catalogDao.observeGeneration().map { generation ->
        generation?.persons().orEmpty()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val personWorks = catalogDao.observeCatalog().map { entities -> entities.associateBy { it.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val books: StateFlow<BooksUiState> = combine(
        catalogDao.observeCollection("book"), pinIds, retainedPins, library, combine(positions, textTransfers) { positions, transfers -> positions to transfers },
    ) { entities, pinnedIds, retainedIds, shelves, reading ->
        val (savedPositions, transfers) = reading
        val shelfIds = shelves.mapTo(hashSetOf(), LibraryEntryEntity::entityId)
        val positionById = savedPositions.associateBy(ReadingPositionEntity::entityId)
        val recentRanks = savedPositions.sortedByDescending { it.updatedAt }.mapIndexed { index, position -> position.entityId to index }.toMap()
        BooksUiState(
            archiveCountLabel = arabicCount(entities.size, "الكتب", "كتاب واحد", "كتابين", "كتب", "كتابًا"),
            books = entities.map {
                it.toBookUi(
                    pinned = it.id in pinnedIds,
                    retained = it.id in retainedIds,
                    saved = it.id in shelfIds,
                    position = positionById[it.id],
                    recentRank = recentRanks[it.id],
                    transfer = transfers[it.id],
                )
            },
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        BooksUiState("٠ كتاب", emptyList(), loaded = false),
    )

    val articles: StateFlow<ArticlesUiState> = combine(
        catalogDao.observeCollection("article"), pinIds, retainedPins, library, combine(textTransfers, recentRanks) { t, r -> t to r },
    ) { entities, pinnedIds, retainedIds, shelves, transferAndRank ->
        val (transfers, ranks) = transferAndRank
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
                    downloaded = entity.id in retainedIds && entity.availability == ContentAvailability.COMPLETE,
                    downloading = entity.id in pinnedIds &&
                        entity.id !in retainedIds &&
                        entity.transferState != ContentTransferState.FAILED,
                    saved = entity.id in shelfIds,
                    recentRank = ranks[entity.id],
                    transfer = transfers[entity.id],
                    sizeLabel = byteSize(entity.pkgSize),
                )
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ArticlesUiState("٠ مقال", emptyList()))

    // Only works with recordings are decoded, and only when the catalog changes, not on every download tick.
    private val audioSources = catalogDao.observeEntitiesWithAudio().map { list -> list.map { it to it.audioReferences() } }
    private val recordingCatalog = combine(
        audioSources, library, content.downloadedAudio, recentRanks,
        combine(content.requestedAudio, content.pausedDownloads, downloadScheduler.workInfos) { requested, paused, jobs -> Triple(requested, paused, jobs) },
    ) { catalog, shelves, downloaded, ranks, transfers ->
        val shelfIds = shelves.mapTo(hashSetOf(), LibraryEntryEntity::entityId)
        val recordings = catalog.flatMap { (source, refs) ->
            refs.map { ref ->
                AudioUi(
                    id = ref.id, title = ref.title ?: source.title, sourceTitle = source.title,
                    sourceKind = when (source.coll) { "poem" -> AudioSourceKind.Poem; "question" -> AudioSourceKind.Question; else -> AudioSourceKind.Book },
                    durationLabel = duration(ref.seconds), durationSeconds = ref.seconds,
                    sizeLabel = byteSize(ref.size), bytes = ref.size,
                    downloaded = ref.id in downloaded, saved = source.id in shelfIds,
                    recentRank = ranks["audio:${ref.id}"],
                    sourceEntityId = source.id,
                    transfer = if (ref.id !in transfers.first || ref.id in downloaded) null else {
                        val job = transfers.third.firstOrNull { "content:audio:${ref.id}" in it.tags && !it.state.isFinished }
                            ?: transfers.third.firstOrNull { "content:audio:${ref.id}" in it.tags && it.state == WorkInfo.State.FAILED }
                        val bytes = job?.progress?.getLong("downloaded_bytes", 0) ?: 0L
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
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AudioUiState("٠ تسجيل", null, emptyList()))

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
            val references = catalogDao.entitiesWithAudio().flatMap { it.audioReferences() }.associateBy { it.id }
            val items = queue.map { recording ->
                MediaItem.Builder().setMediaId(recording.id).setCustomCacheKey(recording.id).setUri(content.audioUri(requireNotNull(references[recording.id])))
                    .setMediaMetadata(MediaMetadata.Builder().setTitle(recording.title).setArtist(recording.speaker).build()).build()
            }
            playback.play(items, queue.indexOfFirst { it.id == audioId })
        }
    }

    fun toggleAudioDownload(audioId: String) {
        viewModelScope.launch(errorHandler) {
            if (audioId in content.requestedAudio.value && audioId !in content.downloadedAudio.value) {
                pauseResumeDownload("audio:$audioId")
            } else if (audioId in content.requestedAudio.value) {
                downloadScheduler.cancel(audioId, "audio")
                content.removeAudioDownload(audioId)
            } else {
                content.requestAudioDownload(audioId)
                downloadScheduler.enqueue(audioId, "audio")
            }
        }
    }

    private val audioDownloads = combine(
        audio, content.requestedAudio, content.downloadedAudio, downloadScheduler.workInfos, content.pausedDownloads,
    ) { state, requested, downloaded, jobs, paused ->
        state.recordings.filter { it.id in requested }.map { item ->
            val identity = "content:audio:${item.id}"
            val related = jobs.filter { identity in it.tags }
            val job = related.firstOrNull { !it.state.isFinished } ?: related.firstOrNull { it.state == WorkInfo.State.FAILED }
            val bytes = job?.progress?.getLong("downloaded_bytes", 0) ?: 0
            val total = job?.progress?.getLong("total_bytes", 0) ?: 0
            val progress = if (total > 0) (bytes.toFloat() / total).coerceIn(0f, 1f) else 0f
            val label = "${arabicNumber((progress * 100).toInt())}٪"
            DownloadUi("audio:${item.id}", item.title, "تسجيل", item.sizeLabel, when {
                item.id in downloaded -> null
                "audio:${item.id}" in paused -> TransferUi.Paused(progress, label)
                job?.state == WorkInfo.State.FAILED -> TransferUi.Failed(job.outputData.getString("error") ?: "تعذّر التنزيل", progress)
                else -> TransferUi.Running(progress, label)
            }, bytes = item.bytes)
        }
    }
    val downloads = combine(textDownloads, audioDownloads) { text, audio -> text + audio }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** «تنزيل الكل»: everything in the catalog not already pinned or requested, grouped with summed sizes. */
    val bulkDownloads = combine(catalogDao.observeCatalog(), pinIds, audioSources, content.requestedAudio) { entities, pinned, sources, requestedAudio ->
        bulkRows(
            entities.filter { it.editionOf == null && it.id !in pinned }.map { BulkCandidate(it.coll, audio = false, it.id, it.pkgSize) } +
                sources.flatMap { (source, refs) ->
                    refs.filter { it.id !in requestedAudio }.map { BulkCandidate(source.coll, audio = true, it.id, it.size) }
                },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ponytail: one unique work per item; WorkManager's scheduler limit (20 by default) caps how many run at once.
    fun downloadAll(items: List<BulkCandidate>) {
        val (audio, text) = items.partition { it.audio }
        requested.update { it + text.map(BulkCandidate::id) }
        viewModelScope.launch(errorHandler) {
            audio.forEach { content.requestAudioDownload(it.id); downloadScheduler.enqueue(it.id, "audio") }
            text.forEach { item ->
                try { content.requestDownload(item.id) } catch (error: Throwable) { requested.update { it - item.id }; throw error }
                downloadScheduler.enqueue(item.id, item.coll)
            }
        }
    }

    val issues: StateFlow<IssuesUiState> = combine(
        catalogDao.observeCollection("question"), retainedPins, library, combine(textTransfers, recentRanks) { t, r -> t to r },
    ) { entities, retainedIds, shelves, transferAndRank ->
        val (transfers, ranks) = transferAndRank
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
                    downloaded = entity.id in retainedIds && entity.availability == ContentAvailability.COMPLETE,
                    saved = entity.id in shelfIds,
                    recentRank = ranks[entity.id],
                    transfer = transfers[entity.id],
                )
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IssuesUiState("٠ مسألة", emptyList()))

    val poetry: StateFlow<PoetryUiState> = combine(
        catalogDao.observeCollection("poem"), pinIds, retainedPins, library, combine(textTransfers, recentRanks) { t, r -> t to r },
    ) { entities, pinnedIds, retainedIds, shelves, transferAndRank ->
        val (transfers, ranks) = transferAndRank
        val shelfIds = shelves.mapTo(hashSetOf(), LibraryEntryEntity::entityId)
        PoetryUiState(
            countLabel = arabicCount(entities.size, "القصائد", "قصيدة واحدة", "قصيدتين", "قصائد", "قصيدة"),
            poems = entities.map { entity ->
                val entry = entity.toCatalogEntry()
                PoemUi(
                    id = entity.id,
                    title = entity.title,
                    poet = entity.personName.orEmpty(),
                    openingVerses = entry.openingVerses,
                    topic = topicName(entity.topicsCsv.substringBefore(',')).ifBlank { "" },
                    downloaded = entity.id in retainedIds && entity.availability == ContentAvailability.COMPLETE,
                    downloading = entity.id in pinnedIds &&
                        entity.id !in retainedIds &&
                        entity.transferState != ContentTransferState.FAILED,
                    saved = entity.id in shelfIds,
                    recentRank = ranks[entity.id],
                    transfer = transfers[entity.id],
                    verseCountLabel = entry.verseCount?.takeIf { it > 0 }?.let { arabicCount(it, "", "بيت واحد", "بيتان", "أبيات", "بيتًا") },
                    sizeLabel = byteSize(entity.pkgSize),
                )
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PoetryUiState("٠ قصيدة", emptyList()))

    init {
        viewModelScope.launch(errorHandler) {
            // This is local disk work only and returns immediately when the content DB is
            // already healthy. UI Flows are active before either rebuild or sync begins.
            runCatching { content.rebuildRetainedContent() }
            // Fresh install: the library is usable before the first sync returns.
            content.seedBundledCatalog()
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
        viewModelScope.launch(errorHandler) {
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
            combine(
                catalogDao.observeEntity(entityId),
                userDao.observeReadingPosition(entityId).flatMapLatest { position ->
                    val center = position?.ordinalHint ?: 0
                    importDao.observeBlockWindow(entityId, (center - 24).coerceAtLeast(0), center + 24)
                },
                importDao.observeChapters(entityId),
                importDao.observeFootnotes(entityId),
                userDao.observeReadingPosition(entityId),
            ) { entity, blocks, chapters, footnotes, position ->
                entity?.toReaderUi(blocks, chapters, footnotes, position)?.copy(
                    editions = catalogDao.editionsFor(entity.editionOf ?: entity.id).takeIf { it.size > 1 }.orEmpty().map {
                        com.atharchive.feature.reader.ReaderEdition(it.id, it.edition?.takeIf(String::isNotBlank) ?: it.title)
                    },
                )
            },
            annotationRepository.observe(AnnotationFilter(entityId = entityId)),
        ) { state, saved ->
            state?.withAnnotations(saved, saved.filter { it.kind == AnnotationKind.Bookmark && it.quotedText.isBlank() }
                .associate { it.id to importDao.blockAt(entityId, it.start.ordinalHint)?.text.orEmpty() })
        }
    }

    fun readerWindow(entityId: String): com.atharchive.feature.reader.ReaderWindow =
        readerWindows.getOrPut(entityId) {
            val job = SupervisorJob(viewModelScope.coroutineContext[Job])
            readerJobs[entityId] = job
            val scope = CoroutineScope(viewModelScope.coroutineContext + job)
            com.atharchive.feature.reader.ReaderWindow(
                scope = scope,
                total = { content.blockCount(entityId) },
                load = { range -> content.blockRange(entityId, range).map(ContentBlockEntity::toReaderBlock) },
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
        viewModelScope.launch(errorHandler) {
            personalLibrary.recordOpened(entityId)
            val saved = userDao.readingPosition(entityId)
            val stable = saved?.let { importDao.blockByStableId(entityId, it.blockIdHi, it.blockIdLo) }
            val entity = catalogDao.entity(entityId) ?: return@launch
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

    suspend fun searchInBook(entityId: String, query: String): List<InBookHit> {
        if (query.trim().length < 2) return emptyList()
        // ponytail: first 200 source-order hits; paginate if more results are needed.
        val hits = mutableListOf<BlockSearchHit>()
        visitTextFrames(entityId) { range ->
            hits += contentSearch.search(
                ContentSearchRequest(query = query, sort = ContentSearchSort.BookOrder,
                    filter = ContentSearchFilter(entityIds = setOf(entityId)), ordinals = range),
                limit = 200 - hits.size,
            ).blocks
            hits.size >= 200
        }
        return hits.sortedBy { it.ordinal }.map { hit ->
            InBookHit(
                id = hit.rowid.toString(), blockIndex = hit.ordinal,
                chapterTitle = hit.chapterTitle, excerpt = hit.excerpt,
                matchStart = hit.matchStart, matchEnd = hit.matchEnd,
                pageLabel = hit.printedPage?.let { "ص ${arabicNumber(it)}" },
                sourceMatchStart = hit.sourceMatchStart, sourceMatchEnd = hit.sourceMatchEnd,
            )
        }
    }

    suspend fun findPrintedPage(entityId: String, page: Int, volume: Int?): Int? {
        importDao.printedPageOrdinal(entityId, page, volume)?.let { return it }
        var found: Int? = null
        visitTextFrames(entityId) {
            found = importDao.printedPageOrdinal(entityId, page, volume)
            found != null
        }
        return found
    }

    suspend fun findHeadingAnchor(entityId: String, anchor: String): Int? {
        importDao.headingAnchorOrdinal(entityId, anchor)?.let { return it }
        var found: Int? = null
        visitTextFrames(entityId) {
            found = importDao.headingAnchorOrdinal(entityId, anchor)
            found != null
        }
        return found
    }

    fun loadMoreSearch() { _searchLimit.value += 50 }

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
        _searchLimit.value = 50
        _searchQuery.value = clipped
    }

    /** Called by Compose after the frame containing results for [query] has been produced. */
    fun reportSearchResultsDrawn(query: String) {
        if (activeSearchTrace?.query == query) finishActiveSearchTrace()
    }

    fun setSearchFilters(filters: SearchFilters) {
        savedState["searchTypes"] = ArrayList(filters.types.map { it.name })
        savedState["searchField"] = filters.field.name
        savedState["searchSources"] = ArrayList(filters.sources)
        savedState["searchAuthors"] = ArrayList(filters.authors)
        savedState["searchSort"] = filters.sort.name
        _searchLimit.value = 50
        _searchFilters.value = filters
    }

    fun rememberCurrentSearch() {
        viewModelScope.launch(errorHandler) { personalLibrary.rememberSearch(_searchQuery.value) }
    }

    fun clearRecentSearches() {
        viewModelScope.launch(errorHandler) { personalLibrary.clearRecentSearches() }
    }

    override fun onCleared() {
        finishActiveSearchTrace()
        super.onCleared()
    }

    private fun finishActiveSearchTrace() {
        val trace = activeSearchTrace ?: return
        Trace.endAsyncSection(SearchLatencyTraceName, trace.cookie)
        activeSearchTrace = null
    }

    /** The work read most recently as (id, collection), or null on a fresh install. */
    suspend fun lastReadWork(): Pair<String, String>? {
        val latest = userDao.readingPositions().maxByOrNull { it.updatedAt } ?: return null
        val entity = catalogDao.entity(latest.entityId) ?: return null
        return entity.id to entity.coll
    }

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
            require(capture.quotedText == selection.quotedText) { "تغيّر النص أو لم يكتمل تحميله؛ أعد تحديد المقطع قبل حفظه" }
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

    suspend fun exportUserData(output: OutputStream) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { userDataArchive.exportTo(output) }
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

    fun closeReader(entityId: String) {
        prefetchSessions.remove(entityId)?.close()
        prefetchedFrames.remove(entityId)
        readerJobs.remove(entityId)?.cancel()
        readerWindows.remove(entityId)
        readerFlows.remove(entityId)
        poemFlows.remove(entityId)
    }

    fun toggleDownload(entityId: String) {
        viewModelScope.launch(errorHandler) {
            val entity = catalogDao.entity(entityId) ?: return@launch
            if (content.isPinned(entityId) && entityId !in content.retainedEntityIds.value) {
                pauseResumeDownload(entityId)
            } else if (content.isPinned(entityId)) {
                requested.update { it - entityId }
                downloadScheduler.cancel(entityId, entity.coll)
                content.unpin(entityId)
            } else {
                requested.update { it + entityId }
                try { content.requestDownload(entityId) } catch (error: Throwable) { requested.update { it - entityId }; throw error }
                downloadScheduler.enqueue(entityId, entity.coll)
            }
        }
    }

    fun pauseResumeDownload(entityId: String) {
        viewModelScope.launch(errorHandler) {
            if (entityId.startsWith("audio:")) {
                val audioId = entityId.removePrefix("audio:")
                val jobs = downloadScheduler.workInfos.first().filter { "content:audio:$audioId" in it.tags }
                if (entityId in content.pausedDownloads.value || (jobs.none { !it.state.isFinished } && jobs.any {
                    "content:audio:$audioId" in it.tags && it.state == WorkInfo.State.FAILED
                })) {
                    content.setDownloadPaused(entityId, false)
                    downloadScheduler.enqueue(audioId, "audio", replace = true)
                } else {
                    content.setDownloadPaused(entityId, true)
                    downloadScheduler.cancel(audioId, "audio")
                }
                return@launch
            }
            val entity = catalogDao.entity(entityId) ?: return@launch
            val jobs = downloadScheduler.workInfos.first().filter { "content:${entity.coll}:$entityId" in it.tags }
            if (entityId in content.pausedDownloads.value || entity.transferState == ContentTransferState.FAILED ||
                (jobs.none { !it.state.isFinished } && jobs.any { it.state == WorkInfo.State.FAILED })) {
                content.setDownloadPaused(entityId, false)
                downloadScheduler.enqueue(entityId, entity.coll, replace = true)
            } else {
                content.setDownloadPaused(entityId, true)
                downloadScheduler.cancel(entityId, entity.coll)
            }
        }
    }

    fun removeDownload(entityId: String) {
        requested.update { it - entityId }
        if (entityId.startsWith("audio:")) {
            val id = entityId.removePrefix("audio:")
            viewModelScope.launch(errorHandler) { downloadScheduler.cancel(id, "audio"); content.removeAudioDownload(id) }
            return
        }
        viewModelScope.launch(errorHandler) {
            val entity = catalogDao.entity(entityId) ?: return@launch
            downloadScheduler.cancel(entityId, entity.coll)
            content.unpin(entityId)
        }
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

    private suspend fun recoverPendingDownloads() {
        content.reconcileAudioDownloads()
        (content.requestedAudio.value - content.downloadedAudio.value).forEach { id ->
            if ("audio:$id" !in content.pausedDownloads.value) downloadScheduler.enqueue(id, "audio")
        }
        content.reconcilePinnedDownloads().forEach { target ->
            if (target.entityId !in content.pausedDownloads.value) downloadScheduler.enqueue(target.entityId, target.collection)
        }
    }

    private fun ContentEntity.toBookUi(
        pinned: Boolean,
        retained: Boolean,
        saved: Boolean,
        position: ReadingPositionEntity?,
        recentRank: Int?,
        transfer: TransferUi?,
    ): BookUi {
        val progress = position?.progressPct?.toFloat()?.coerceIn(0f, 1f)
        val download = when {
            pinned && !retained ->
                BookDownloadUi.Downloading(
                    sizeLabel = byteSize(pkgSize),
                    transfer = transfer ?: TransferUi.Running(0f, ""),
                )
            retained && availability == ContentAvailability.COMPLETE -> BookDownloadUi.Downloaded(byteSize(pkgSize))
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
            publishedAt = publishedAt.orEmpty(),
            recentRank = recentRank,
            // ponytail: chapterAnchor is a slug, not a title; the title lives in the content DB per book. Show the place only.
            readingPosition = position?.let { "الموضع ${arabicNumber(it.ordinalHint + 1)}" },
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
    val footnoteBlocks = footnotes.mapIndexed { index, note ->
        ReaderBlock.FootnoteBody("fn-${note.fnId}", note.marker ?: arabicNumber(index + 1), note.text, spans = note.spans())
    }
    val byId = footnotes.indices.associate { footnotes[it].fnId to footnoteBlocks[it] }
    val contentBlocks = blocks.map { entity ->
        val block = entity.toReaderBlock()
        if (block is ReaderBlock.PageNotes) block.copy(notes = block.footnotes.mapNotNull(byId::get)) else block
    }
    return ReaderUiState(
        bookTitle = title,
        author = personName.orEmpty(),
        blocks = contentBlocks,
        footnotes = byId,
        sourceUrl = "https://athar.arthurarchive.com/${if (coll == "question") "questions" else coll}/$id",
        card = runCatching { toCatalogEntry().card }.getOrDefault(emptyList()),
        toc = chapters.multiVolume().let { vols ->
            chapters.map { chapter ->
                val page = chapter.page?.let { "ص ${com.atharchive.ui.components.arabicDigits(it)}" }.orEmpty()
                val label = chapter.vol?.takeIf { vols && page.isNotEmpty() }?.let { "ج${com.atharchive.ui.components.arabicDigits(it)} · $page" } ?: page
                TocEntry(chapter.anchor, chapter.title, chapter.level, chapter.firstOrdinal, label)
            }
        },
        bookmarks = emptyList(),
        benefits = emptyList(),
        readingPositionIndex = position?.ordinalHint ?: 0,
        readingOffset = position?.offsetInBlock ?: 0,
        audio = audioReferences().map { BookAudioUi(it.id, it.title ?: title, duration(it.seconds)) },
        blockCount = pkgBlocks,
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
        .map { ReaderHighlight(it.start.ordinalHint, it.end.ordinalHint, it.start.offset, it.end.offset) }
    return copy(bookmarks = bookmarks, benefits = benefits, highlights = highlights)
}

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
        verses += VerseUi(
            id = block.stableId(),
            number = attrs.n ?: verses.size + 1,
            sadr = attrs.s ?: block.text,
            ajz = attrs.j.orEmpty(),
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
    semanticText = text.takeUnless { type == "page" || type == "break" },
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
internal fun byteSize(bytes: Long): String {
    val (value, unit) = when {
        bytes >= 1L shl 30 -> bytes / (1L shl 30).toDouble() to "غ.ب"
        bytes >= 1L shl 20 -> bytes / (1L shl 20).toDouble() to "م.ب"
        bytes >= 1L shl 10 -> bytes / (1L shl 10).toDouble() to "ك.ب"
        else -> return "${arabicNumerals(bytes.toString())} بايت"
    }
    return "${arabicNumerals("%.1f".format(java.util.Locale.ROOT, value).replace('.', '\u066B'))} $unit"
}

private fun arabicNumerals(s: String) = s.map { if (it in '0'..'9') '\u0660' + (it - '0') else it }.joinToString("")

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

private fun arabicNumber(value: Any): String = arabicDigits(value.toString())

private data class SearchInput(
    val query: String,
    val filters: SearchFilters,
    val recent: List<String>,
    val catalog: SearchCatalog,
)

private data class SearchCatalog(val entities: List<ContentEntity>, val sources: List<String>, val authors: List<String>)

private data class SearchTrace(val query: String, val cookie: Int)

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

private val AnnotationKind.storageValue: String
    get() = name.lowercase()

private val AttachmentState.storageValue: String
    get() = name.lowercase()

/** Blocks looked past for a bookmark's text: a divider and a notes footer, with room to spare. */
internal const val BookmarkLookahead = 4

private fun List<ChapterEntity>.multiVolume() = mapNotNull { it.vol }.distinct().size > 1
