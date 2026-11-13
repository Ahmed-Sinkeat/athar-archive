package com.atharchive.data

import androidx.work.NetworkType
import androidx.work.WorkInfo
import com.atharchive.core.data.content.AudioReference
import com.atharchive.core.data.db.content.ContentEntity
import com.atharchive.core.data.db.user.PinnedDownloadEntity
import com.atharchive.core.data.repository.ContentStoragePolicy
import com.atharchive.feature.audio.AudioUiState
import com.atharchive.feature.downloads.BulkCandidate
import com.atharchive.feature.downloads.BulkRowUi
import com.atharchive.feature.downloads.BulkState
import com.atharchive.feature.downloads.DownloadUi
import com.atharchive.feature.downloads.TransferUi
import com.atharchive.feature.downloads.bulkRows
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Downloads (3.1): pins, transfer state, the downloads screen's rows and «تنزيل الكل», and every
 * download action. [AtharContentViewModel] builds it on its own scope and lays [localStates],
 * [retainedPins] and the live transfers ([withTransfers]) over the library lists.
 */
internal class DownloadsModel(
    private val scope: kotlinx.coroutines.CoroutineScope,
    private val content: ContentAccess,
    private val downloadScheduler: ContentDownloadScheduler,
    private val errorHandler: CoroutineExceptionHandler,
    private val errors: MutableSharedFlow<String>,
    // The audio tab's flows, read when collected: they are built after this and need [jobIndex].
    recordingCatalogOf: () -> Flow<AudioUiState>,
    audioSourcesOf: () -> Flow<List<Pair<ContentEntity, List<Pair<AudioReference, List<String>>>>>>,
) {
    private val userDao = content.userDatabase.userDataDao()
    private val catalogDao = content.contentDatabase.catalogDao()
    private val recordingCatalog: Flow<AudioUiState> = kotlinx.coroutines.flow.flow { emitAll(recordingCatalogOf()) }
    private val audioSources = kotlinx.coroutines.flow.flow { emitAll(audioSourcesOf()) }

    private fun <T> Flow<T>.pausedDuringUpdate(): Flow<T> =
        content.catalogUpdate.map { it != null }.distinctUntilChanged().flatMapLatest { updating ->
            if (updating) kotlinx.coroutines.flow.emptyFlow() else this
        }

    private val pins = userDao.observePinnedDownloads()
    // A tap shows at once: saving the pin waits behind any book update being installed (minutes for a large one).
    private val requested = MutableStateFlow<Set<String>>(emptySet())
    val pinIds = combine(pins, requested) { downloads, tapped ->
        downloads.mapTo(hashSetOf(), PinnedDownloadEntity::entityId) + tapped
    }
    val retainedPins = combine(pins, content.retainedEntityIds) { downloads, retained ->
        downloads.asSequence()
            .map(PinnedDownloadEntity::entityId)
            .filterTo(hashSetOf()) { it in retained }
    }.throttled(1_000)

    val pinnedCount: StateFlow<Int> = combine(pins, content.requestedAudio) { text, audio -> text.size + audio.size }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), 0)
    /**
     * Each job's tag → the job that matters for it (unfinished first, else failed), built once per
     * tick. Sampled: a bulk download runs many workers, each reporting progress several times a
     * second, and every list below used to rescan all jobs for every row on each report.
     */
    val jobIndex: StateFlow<Map<String, TransferJob>> = combine(downloadScheduler.workInfos, downloadScheduler.progress, ::Pair).sample(500).map { (jobs, live) ->
        val index = HashMap<String, WorkInfo>()
        for (job in jobs) {
            if (job.state.isFinished && job.state != WorkInfo.State.FAILED) continue
            for (tag in job.tags) {
                if (!tag.startsWith("content:")) continue
                val current = index[tag]
                if (current == null || (current.state.isFinished && !job.state.isFinished)) index[tag] = job
            }
        }
        index.mapValues { (tag, job) ->
            val bytes = live[tag]
            TransferJob(
                job.state, job.outputData.getString("error"), bytes?.downloaded ?: 0, bytes?.total?.takeIf { it > 0 }, bytes?.queued == true,
                wifiOnly = job.constraints.requiredNetworkType == NetworkType.UNMETERED,
            )
        }
    }.distinctUntilChanged().stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /**
     * Which works are fully stored and which failed, from the small `entityState` table: a download
     * step changes only this, so the catalog lists below are no longer reloaded on every step.
     */
    val localStates: StateFlow<LocalStates> = combine(catalogDao.observeCompleteIds(), catalogDao.observeFailedIds()) { complete, failed ->
        LocalStates(complete.toHashSet(), failed.toHashSet())
    }.distinctUntilChanged().throttled(1_000).stateIn(scope, SharingStarted.Eagerly, LocalStates(emptySet(), emptySet()))

    private val textDownloads: StateFlow<List<DownloadUi>> = combine(
        catalogDao.observeAllEntitiesLean().pausedDuringUpdate(), pinIds, retainedPins, jobIndex, combine(content.pausedDownloads, localStates, ::Pair),
    ) { entities, pinnedIds, retainedIds, jobs, (pausedIds, states) ->
        entities.filter { it.id in pinnedIds }.map { entity ->
            val job = jobs["content:${entity.coll}:${entity.id}"]
            val downloaded = job?.downloaded ?: 0
            val total = job?.total ?: entity.pkgSize
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
                    job?.state == WorkInfo.State.FAILED || (job == null && entity.id in states.failed) ->
                        TransferUi.Failed(job?.error ?: "تعذّر التنزيل", progress)
                    else -> TransferUi.Running(
                        progress,
                        when {
                            job?.state == WorkInfo.State.ENQUEUED && job.wifiOnly -> "في انتظار Wi-Fi · $label"
                            job?.state == WorkInfo.State.ENQUEUED || job?.queued == true -> "في الانتظار · $label"
                            else -> label
                        },
                    )
                },
            )
        }
    }.flowOn(Dispatchers.Default).stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Transfer state by entity id for one kind, built once per change instead of searched per row,
     * and quiet while that kind has nothing moving: a poem's progress no longer rebuilds the books.
     */
    private fun transfersOf(kind: String) = textDownloads
        .map { list -> list.filter { it.kindLabel == kind }.associate { it.id to it.transfer } }
        .distinctUntilChanged()

    /**
     * Lays each row's live transfer over a list built without it: a progress tick then copies the
     * rows that are downloading, instead of rebuilding every row of the list twice a second.
     */
    fun <S, R> Flow<S>.withTransfers(
        kind: String, rows: (S) -> List<R>, id: (R) -> String, rebuild: (S, List<R>) -> S, apply: (R, TransferUi) -> R,
    ): Flow<S> = combine(this, transfersOf(kind)) { base, transfers ->
        if (transfers.isEmpty()) base else rebuild(base, rows(base).map { row -> transfers[id(row)]?.let { apply(row, it) } ?: row })
    }

    fun toggleAudioDownload(audioId: String) {
        scope.launch(errorHandler) {
            content.awaitDownloadState()
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

    // The catalog, not `audio`: that one ticks with the playback position.
    private val audioDownloads = combine(
        recordingCatalog, content.requestedAudio, content.downloadedAudio, jobIndex, content.pausedDownloads,
    ) { state, requested, downloaded, jobs, paused ->
        state.recordings.filter { it.id in requested }.map { item ->
            val job = jobs["content:audio:${item.id}"]
            val bytes = job?.downloaded ?: 0
            val total = job?.total ?: 0
            val progress = if (total > 0) (bytes.toFloat() / total).coerceIn(0f, 1f) else 0f
            val label = "${arabicNumber((progress * 100).toInt())}٪"
            DownloadUi("audio:${item.id}", item.title, "تسجيل", item.sizeLabel, when {
                item.id in downloaded -> null
                "audio:${item.id}" in paused -> TransferUi.Paused(progress, label)
                job?.state == WorkInfo.State.FAILED -> TransferUi.Failed(job.error ?: "تعذّر التنزيل", progress)
                else -> TransferUi.Running(progress, label)
            }, bytes = item.bytes)
        }
    }
    val downloads = combine(textDownloads, audioDownloads) { text, audio -> text + audio }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Every work's text and every recording, with how far each got, and the rows the reader started. */
    private val bulkCandidates = combine(
        catalogDao.observeCatalogLean().pausedDuringUpdate(),
        combine(pinIds, retainedPins, content.pausedDownloads) { pinned, retained, paused -> Triple(pinned, retained, paused) },
        audioSources,
        combine(content.requestedAudio, content.downloadedAudio, content.batches) { requested, downloaded, batches -> Triple(requested, downloaded, batches) },
        combine(jobIndex, localStates, ::Pair),
    ) { entities, (pinned, retained, paused), sources, (requestedAudio, downloadedAudio, batches), (jobs, states) ->
        fun state(requested: Boolean, done: Boolean, pauseKey: String, job: TransferJob?, failed: Boolean) = when {
            done -> BulkState.Done
            !requested -> BulkState.Idle
            pauseKey in paused -> BulkState.Paused
            failed || job?.state == WorkInfo.State.FAILED -> BulkState.Failed
            else -> BulkState.Active
        }
        fun TransferJob?.partial() = this?.downloaded ?: 0L
        fun TransferJob?.waitingWifi() = this?.state == WorkInfo.State.ENQUEUED && wifiOnly
        Pair(
            entities.filter { it.editionOf == null }.map { entity ->
                val job = jobs["content:${entity.coll}:${entity.id}"]
                BulkCandidate(
                    entity.coll, audio = false, entity.id, entity.pkgSize,
                    state(entity.id in pinned, entity.id in retained, entity.id, job, job == null && entity.id in states.failed),
                    job.partial(),
                    // measured 2026-11-10: the text table, its indexes and search index come to ≈1.5× the unzipped text
                    diskBytes = entity.pkgSize + entity.pkgUncompressed * 3 / 2,
                    waitingWifi = job.waitingWifi(),
                )
            } + sources.flatMap { (source, refs) ->
                refs.map { (ref, _) ->
                    val job = jobs["content:audio:${ref.id}"]
                    BulkCandidate(
                        source.coll, audio = true, ref.id, ref.size,
                        state(ref.id in requestedAudio, ref.id in downloadedAudio, "audio:${ref.id}", job, failed = false),
                        job.partial(),
                        waitingWifi = job.waitingWifi(),
                    )
                }
            },
            batches.mapTo(hashSetOf()) { it.label },
        )
    }.flowOn(Dispatchers.Default)

    /** «تنزيل الكل»: every type row with what is done, in flight and left, grouped with summed sizes. */
    val bulkDownloads = bulkCandidates.map { (candidates, tracked) ->
        bulkRows(candidates, tracked).also { rows ->
            // A started row that has nothing left moving (finished, or its items cancelled) stops tracking.
            content.clearBatches(rows.filter { it.tracked && !it.busy }.mapTo(hashSetOf()) { it.label })
        }
    }.flowOn(Dispatchers.Default).stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** An author's «تنزيل الكل»: the same row as the downloads screen's, over [ids]' texts only. */
    fun worksRow(label: String, ids: Set<String>): Flow<BulkRowUi> = bulkCandidates.map { (candidates, tracked) ->
        BulkRowUi(label, candidates.filter { !it.audio && it.id in ids }, label in tracked).also {
            if (it.tracked && !it.busy) content.clearBatches(setOf(label))
        }
    }.flowOn(Dispatchers.Default)

    // One unique work per item; at most three transfer at once (transferSlots in ContentDownloadWorker.kt).
    /**
     * «تنزيل المتبقي»: starts what never started and resumes what stopped, tracked under the row.
     * Waits for Wi-Fi unless [mobileData]. Refused up front when the row cannot fit, instead of
     * filling the phone and failing item by item.
     */
    fun downloadRemaining(row: BulkRowUi, mobileData: Boolean) {
        val remaining = row.idle + row.stopped
        val needed = ContentStoragePolicy.LOW_STORAGE_BYTES + remaining.sumOf { it.diskBytes - it.partialBytes }
        val free = content.freeBytes()
        if (free < needed) {
            errors.tryEmit("لا تكفي المساحة: يلزم ${byteSize(needed)} والمتاح ${byteSize(free)}")
            return
        }
        content.startBatch(row.label, remaining.map(BulkCandidate::pauseKey))
        downloadAll(row.idle, wifiOnly = !mobileData)
        // On mobile data, what was queued for Wi-Fi starts now too.
        pauseResumeMany(row.stopped + if (mobileData) row.items.filter { it.waitingWifi } else emptyList(), pause = false, wifiOnly = !mobileData)
    }

    fun downloadAll(items: List<BulkCandidate>, wifiOnly: Boolean = false) {
        if (items.isEmpty()) return
        val (audio, text) = items.partition { it.audio }
        val textIds = text.map(BulkCandidate::id)
        requested.update { it + textIds }
        scope.launch(errorHandler) {
            // One write per kind, not one per item: each write re-ran every download list.
            if (audio.isNotEmpty()) content.requestAudioDownloads(audio.map(BulkCandidate::id))
            audio.forEach { downloadScheduler.enqueue(it.id, "audio", wifiOnly = wifiOnly) }
            if (text.isNotEmpty()) {
                try { content.requestDownloads(textIds) } catch (error: Throwable) { requested.update { it - textIds.toSet() }; throw error }
            }
            if (text.isNotEmpty()) downloadScheduler.enqueueBulk(wifiOnly)
        }
    }

    /** «إيقاف الكل» / «استئناف الكل» over a whole row, written once. */
    fun pauseResumeMany(items: List<BulkCandidate>, pause: Boolean, wifiOnly: Boolean = false) {
        if (items.isEmpty()) return
        content.setDownloadsPaused(items.map(BulkCandidate::pauseKey), pause)
        items.forEach {
            val coll = if (it.audio) "audio" else it.coll
            if (pause) downloadScheduler.cancelQuietly(it.id, coll)
            else if (it.audio) downloadScheduler.enqueue(it.id, coll, replace = true, wifiOnly = wifiOnly)
        }
        if (!pause && items.any { !it.audio }) downloadScheduler.enqueueBulk(wifiOnly)
    }

    /** One work's download in flight, changing only when its own state does (not on every other download's tick). */
    fun transferOf(entityId: String): Flow<TransferUi?> =
        downloads.map { list -> list.firstOrNull { it.id == entityId }?.transfer }.distinctUntilChanged()

    fun downloadForSearch(entityId: String) {
        scope.launch(errorHandler) {
            if (content.isPinned(entityId)) return@launch
            toggleDownload(entityId)
        }
    }

    fun toggleDownload(entityId: String) {
        scope.launch(errorHandler) {
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
        scope.launch(errorHandler) {
            content.awaitDownloadState()
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
            if (entityId in content.pausedDownloads.value || entityId in localStates.value.failed ||
                (jobs.none { !it.state.isFinished } && jobs.any { it.state == WorkInfo.State.FAILED })) {
                content.setDownloadPaused(entityId, false)
                downloadScheduler.enqueue(entityId, entity.coll, replace = true)
            } else {
                content.setDownloadPaused(entityId, true)
                downloadScheduler.cancel(entityId, entity.coll)
            }
        }
    }

    /** «حذف (n)»: one write per kind, so the lists update once instead of once per item. */
    fun removeDownloads(ids: List<String>) {
        val (audio, text) = ids.partition { it.startsWith("audio:") }
        requested.update { it - text.toSet() }
        scope.launch(errorHandler) {
            val audioIds = audio.map { it.removePrefix("audio:") }
            audioIds.forEach { downloadScheduler.cancelQuietly(it, "audio") }
            content.removeAudioDownloads(audioIds)
            val entities = text.mapNotNull { catalogDao.entity(it) }
            entities.forEach { downloadScheduler.cancelQuietly(it.id, it.coll) }
            content.unpinAll(entities.map { it.id })
        }
    }

    fun removeDownload(entityId: String) {
        requested.update { it - entityId }
        if (entityId.startsWith("audio:")) {
            val id = entityId.removePrefix("audio:")
            scope.launch(errorHandler) { downloadScheduler.cancel(id, "audio"); content.removeAudioDownload(id) }
            return
        }
        scope.launch(errorHandler) {
            val entity = catalogDao.entity(entityId) ?: return@launch
            downloadScheduler.cancel(entityId, entity.coll)
            content.unpin(entityId)
        }
    }

    suspend fun recoverPendingDownloads() {
        content.reconcileAudioDownloads()
        (content.requestedAudio.value - content.downloadedAudio.value).forEach { id ->
            if ("audio:$id" !in content.pausedDownloads.value) downloadScheduler.enqueue(id, "audio")
        }
        val pending = content.reconcilePinnedDownloads().filter { it.entityId !in content.pausedDownloads.value }
        // many at once is a «تنزيل الكل» cut short: one queue worker, not a job each
        if (pending.size > 8) downloadScheduler.enqueueBulk(wifiOnly = false)
        else pending.forEach { downloadScheduler.enqueue(it.entityId, it.collection) }
    }

}

/**
 * First value at once, then at most one per [ms]: a bulk download finishes several items a second,
 * and every value rebuilt the whole-catalog lists (≈1 core-second per item, measured 2026-11-10).
 */
internal fun <T> Flow<T>.throttled(ms: Long): Flow<T> = conflate().transform { emit(it); delay(ms) }
