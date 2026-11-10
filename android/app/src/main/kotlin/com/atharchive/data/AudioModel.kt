package com.atharchive.data

import com.atharchive.core.data.repository.persons
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.atharchive.feature.audio.AudioUi
import com.atharchive.feature.audio.AudioUiState
import com.atharchive.feature.audio.AudioSourceKind
import com.atharchive.feature.audio.NowPlayingUi
import androidx.work.WorkInfo
import com.atharchive.feature.downloads.TransferUi
import com.atharchive.core.data.db.user.LibraryEntryEntity
import com.atharchive.core.data.repository.PersonalLibraryRepository
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Audio (3.1): the recordings list with each one's download state, what is playing now, and
 * starting playback. Built by [AtharContentViewModel] on its scope; the screens reach it through
 * there, and [DownloadsModel] reads [recordingCatalog] and [audioSources] for its rows.
 */
internal class AudioModel(
    private val scope: CoroutineScope,
    private val content: ContentAccess,
    private val playback: AudioPlayback,
    private val personalLibrary: PersonalLibraryRepository,
    private val errorHandler: CoroutineExceptionHandler,
    persons: Flow<List<com.atharchive.core.data.content.CatalogPerson>>,
    library: Flow<List<LibraryEntryEntity>>,
    recentRanks: Flow<Map<String, Int>>,
    jobIndex: Flow<Map<String, TransferJob>>,
) {
    private val catalogDao = content.contentDatabase.catalogDao()

    private fun <T> Flow<T>.pausedDuringUpdate(): Flow<T> =
        content.catalogUpdate.map { it != null }.distinctUntilChanged().flatMapLatest { updating ->
            if (updating) kotlinx.coroutines.flow.emptyFlow() else this
        }

    // Only works with recordings are decoded, and only when the catalog changes, not on every download tick.
    val audioSources = combine(catalogDao.observeEntitiesWithAudio().pausedDuringUpdate(), persons) { list, people ->
        val names = people.associate { it.id to it.name }
        list.map { entity -> entity to entity.audioReferences().map { it to it.reciters.mapNotNull(names::get) } }
    }
    val recordingCatalog = combine(
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
    }.flowOn(Dispatchers.Default).stateIn(scope, SharingStarted.WhileSubscribed(5_000), AudioUiState("٠ تسجيل", null, emptyList()))

    val audio = combine(recordingCatalog, playback.state) { catalog, player ->
        val current = catalog.recordings.firstOrNull { it.id == player.id }
        catalog.copy(nowPlaying = current?.let {
            NowPlayingUi(it, duration((player.positionMs / 1000).toInt()), duration((player.durationMs.takeIf { n -> n > 0 } ?: it.durationSeconds * 1000L).div(1000).toInt()),
                (player.positionMs.toFloat() / (player.durationMs.takeIf { n -> n > 0 } ?: it.durationSeconds * 1000L).coerceAtLeast(1)).coerceIn(0f, 1f), player.playWhenReady)
        })
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), AudioUiState("٠ تسجيل", null, emptyList()))

    fun playWorkAudio(entityId: String, audioId: String) {
        playAudio(audioId, audio.value.recordings.filter { it.sourceEntityId == entityId })
    }

    fun playAudio(audioId: String, queue: List<AudioUi> = audio.value.recordings) {
        scope.launch(errorHandler) {
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
}
