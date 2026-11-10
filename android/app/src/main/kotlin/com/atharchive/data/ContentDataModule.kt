package com.atharchive.data

import android.content.Context
import androidx.core.content.edit
import com.atharchive.BuildConfig
import com.atharchive.core.data.content.AudioReference
import com.atharchive.core.data.content.ContentDigests
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.atharchive.core.data.content.SignedRootVerifier
import com.atharchive.core.data.db.AtharDatabases
import com.atharchive.core.data.db.content.AtharContentDatabase
import com.atharchive.core.data.db.content.ContentBlockEntity
import com.atharchive.core.data.db.content.ContentAvailability
import com.atharchive.core.data.db.content.ContentTransferState
import com.atharchive.core.data.db.user.AudioDownloadEntity
import com.atharchive.core.data.db.user.DownloadBatchEntity
import com.atharchive.core.data.db.user.DownloadBatchItemEntity
import com.atharchive.core.data.db.user.PausedDownloadEntity
import com.atharchive.core.data.db.user.PinnedDownloadEntity
import com.atharchive.core.data.db.user.UserDataDao
import com.atharchive.core.data.db.user.AtharUserDatabase
import com.atharchive.core.data.network.AppContentHttpClient
import com.atharchive.core.data.repository.OrdinalBlockReader
import com.atharchive.core.data.repository.CatalogSyncResult
import com.atharchive.core.data.repository.ContentCacheManager
import com.atharchive.core.data.repository.ContentImporter
import com.atharchive.core.data.repository.ContentStoragePolicy
import com.atharchive.core.data.repository.ContentStorageStatus
import com.atharchive.core.data.repository.ContentTransferResult
import com.atharchive.core.data.repository.ContentTransferRunner
import com.atharchive.core.data.repository.InsufficientContentStorageException
import com.atharchive.core.data.repository.OfflineContentRebuilder
import com.atharchive.core.data.repository.OfflineRebuildResult
import com.atharchive.core.data.repository.ReadThroughContentRepository
import com.atharchive.core.data.repository.ReadThroughResult
import com.atharchive.core.data.repository.ReaderPrefetchSession
import com.atharchive.core.data.repository.RetainedPackageStore
import com.atharchive.core.data.repository.toCatalogEntry
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrl

@Module
@InstallIn(SingletonComponent::class)
object ContentDataModule {
    @Provides
    @Singleton
    fun contentDatabase(@ApplicationContext context: Context): AtharContentDatabase =
        AtharDatabases.openContent(context)

    @Provides
    @Singleton
    fun userDatabase(@ApplicationContext context: Context): AtharUserDatabase =
        AtharDatabases.openUser(context)

    @Provides
    @Singleton
    fun contentImporter(database: AtharContentDatabase): ContentImporter = ContentImporter(database)

    @Provides
    @Singleton
    fun contentHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(true)
        // Sudanese carriers blackhole some Cloudflare IPs: fall back to ours, and give up on a dead one fast.
        .dns(com.atharchive.core.data.network.FallbackDns())
        .connectTimeout(6, java.util.concurrent.TimeUnit.SECONDS)
        // Default 10 s read timeout drops a slow mobile link mid-catalog; a stall this long is a real outage.
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun workManager(@ApplicationContext context: Context): androidx.work.WorkManager =
        androidx.work.WorkManager.getInstance(context)
}

/** Frames kept ready ahead of the reader (250 blocks each); ponytail: tune on a slow link. */
private const val PrefetchAheadFrames = 3

@Singleton
class ContentAccess @Inject constructor(
    @ApplicationContext private val context: Context,
    val contentDatabase: AtharContentDatabase,
    val userDatabase: AtharUserDatabase,
    private val importer: ContentImporter,
    /** Also used by online search: one connection pool. */
    val httpClient: OkHttpClient,
) {
    companion object {
        private const val StoragePreferences = "content_storage"
        private const val CacheBudgetKey = "cache_budget_bytes"
        private const val RetainedIdsKey = "retained_entity_ids"
    }

    private val mutationMutex = com.atharchive.core.data.repository.ContentWriteLock()
    private val retainedStore by lazy { RetainedPackageStore(File(context.filesDir, "content")) }
    private val cacheManager by lazy { ContentCacheManager(contentDatabase, userDatabase) }
    private val storagePreferences by lazy {
        context.getSharedPreferences(StoragePreferences, Context.MODE_PRIVATE)
    }
    private val _cacheBudgetBytes = MutableStateFlow(readCacheBudget())
    val cacheBudgetBytes: StateFlow<Long> = _cacheBudgetBytes
    /*
     * Download bookkeeping: these sets are the live state, and every change is written to its own
     * rows in the user DB, in order, by [writeBehind]. They were SharedPreferences sets, rewritten
     * whole (with the rest of the file) for every item a bulk download finished, and Android waits
     * for those writes on the main thread whenever a service starts or stops. Read the sets after
     * [awaitDownloadState] where a wrong answer would act on an item (skip it, enqueue it).
     */
    private val _pausedDownloads = MutableStateFlow(emptySet<String>())
    val pausedDownloads: StateFlow<Set<String>> = _pausedDownloads
    private val _requestedAudio = MutableStateFlow(emptySet<String>())
    val requestedAudio: StateFlow<Set<String>> = _requestedAudio
    private val _downloadedAudio = MutableStateFlow(emptySet<String>())
    val downloadedAudio: StateFlow<Set<String>> = _downloadedAudio
    private val bookkeeping = Any()
    /** Guarded by [bookkeeping]: keys changed before the stored rows loaded keep their in-memory state. */
    private var downloadRowsLoaded = false
    private val touchedBeforeLoad = hashSetOf<String>()

    private fun touch(kind: String, keys: Collection<String>) {
        if (!downloadRowsLoaded) keys.mapTo(touchedBeforeLoad) { "$kind:$it" }
    }
    private val downloadStateLoaded = kotlinx.coroutines.CompletableDeferred<Unit>()
    private val writes = kotlinx.coroutines.channels.Channel<suspend UserDataDao.() -> Unit>(kotlinx.coroutines.channels.Channel.UNLIMITED)

    suspend fun awaitDownloadState() = downloadStateLoaded.await()

    /** Free space where downloads are stored. */
    fun freeBytes(): Long = android.os.StatFs(context.filesDir.absolutePath).availableBytes

    /** Queues a row write; called inside `synchronized(bookkeeping)` so rows change in the same order as the sets. */
    private fun writeBehind(write: suspend UserDataDao.() -> Unit) {
        writes.trySend(write)
    }

    private suspend fun loadDownloadState() {
        val dao = userDatabase.userDataDao()
        importLegacyDownloadState(dao)
        val audio = dao.audioDownloads()
        val paused = dao.pausedDownloadKeys()
        val batchItems = dao.downloadBatchItems().groupBy({ it.label }, { it.pauseKey })
        val batches = dao.downloadBatches().map { DownloadBatch(it.label, it.total, batchItems[it.label].orEmpty().toSet()) }
        synchronized(bookkeeping) {
            fun stored(kind: String, key: String) = "$kind:$key" !in touchedBeforeLoad
            _requestedAudio.value += audio.filter { it.requested && stored("audio", it.audioId) }.map { it.audioId }
            _downloadedAudio.value += audio.filter { it.downloaded && stored("audio", it.audioId) }.map { it.audioId }
            _pausedDownloads.value += paused.filter { stored("paused", it) }
            _batches.value = batches.filter { stored("batch", it.label) }
                .map { batch -> batch.copy(left = batch.left.filterTo(hashSetOf()) { stored("item", it) }) }
                .filter { it.left.isNotEmpty() } + _batches.value
            downloadRowsLoaded = true
            touchedBeforeLoad.clear()
        }
    }

    /** One-time move of the sets an older version kept in SharedPreferences. */
    private suspend fun importLegacyDownloadState(dao: UserDataDao) {
        val prefs = storagePreferences
        val keys = listOf("paused_downloads", "requested_audio", "downloaded_audio", "batches")
        if (keys.none(prefs::contains)) return
        val requested = prefs.getStringSet("requested_audio", emptySet()).orEmpty()
        val downloaded = prefs.getStringSet("downloaded_audio", emptySet()).orEmpty()
        dao.saveAudioDownloads((requested + downloaded).map { AudioDownloadEntity(it, it in requested, it in downloaded) })
        dao.addPausedDownloads(prefs.getStringSet("paused_downloads", emptySet()).orEmpty().map(::PausedDownloadEntity))
        val labels = prefs.getStringSet("batches", emptySet()).orEmpty()
        for (label in labels) {
            dao.saveDownloadBatch(DownloadBatchEntity(label, prefs.getInt("batch_total:$label", 0)))
            dao.addDownloadBatchItems(prefs.getStringSet("batch_left:$label", emptySet()).orEmpty().map { DownloadBatchItemEntity(label, it) })
        }
        prefs.edit(commit = true) {
            keys.forEach(::remove)
            labels.forEach { remove("batch_total:$it"); remove("batch_left:$it") }
        }
    }

    /** Persists the audio rows of [ids] as the sets now hold them. */
    private fun writeAudio(ids: Collection<String>) {
        val requested = _requestedAudio.value
        val downloaded = _downloadedAudio.value
        touch("audio", ids)
        val (kept, gone) = ids.toSet().partition { it in requested || it in downloaded }
        writeBehind {
            if (kept.isNotEmpty()) saveAudioDownloads(kept.map { AudioDownloadEntity(it, it in requested, it in downloaded) })
            if (gone.isNotEmpty()) deleteAudioDownloads(gone)
        }
    }
    /** 0..1 while a new catalog is downloaded and applied, null otherwise. */
    val catalogUpdate: StateFlow<Float?> = importer.applyProgress
    private val verifiedAudio = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    suspend fun reconcileAudioDownloads() = withContext(Dispatchers.IO) {
        awaitDownloadState()
        if (_downloadedAudio.value.isEmpty()) return@withContext
        val references = contentDatabase.catalogDao().entitiesWithAudio().flatMap { it.toCatalogEntry().audio }.associateBy { it.id }
        val downloaded = _downloadedAudio.value
        val valid = downloaded.filterTo(hashSetOf()) { id ->
            references[id]?.let { audio -> audioFile(audio).let { it.isFile && it.length() == audio.size } } == true
        }
        val invalid = downloaded - valid
        if (invalid.isEmpty()) return@withContext
        synchronized(bookkeeping) {
            _downloadedAudio.value -= invalid
            writeAudio(invalid)
        }
    }

    private fun markAudioDownloaded(audioId: String, retained: Boolean) = synchronized(bookkeeping) {
        _downloadedAudio.value = if (retained) _downloadedAudio.value + audioId else _downloadedAudio.value - audioId
        writeAudio(listOf(audioId))
    }

    private suspend fun findAudio(audioId: String): AudioReference =
        contentDatabase.catalogDao().entitiesMentioningAudio(audioId).asSequence().flatMap { it.toCatalogEntry().audio.asSequence() }
            .firstOrNull { it.id == audioId } ?: error("التسجيل غير موجود في الفهرس")

    private fun audioFile(audio: AudioReference) = File(context.filesDir, "audio/${audio.hash}.${audio.format}")

    suspend fun audioUri(audioId: String): String = audioUri(findAudio(audioId))

    suspend fun audioUri(audio: AudioReference): String = withContext(Dispatchers.IO) {
        val file = audioFile(audio)
        if (file.isFile && audio.hash !in verifiedAudio) {
            try {
                ContentDigests.verify(file, audio.hash, audio.size, "audio")
                verifiedAudio += audio.hash
            } catch (error: com.atharchive.core.data.content.ContentIntegrityException) {
                check(file.delete()) { "تعذّر إزالة التسجيل التالف" }
                markAudioDownloaded(audio.id, false)
            }
        }
        if (file.isFile) {
            android.net.Uri.fromFile(file).toString()
        } else requireNotNull(appContentClient).artifactUrl(audio.path)
    }

    fun requestAudioDownloads(audioIds: Collection<String>) = synchronized(bookkeeping) {
        _requestedAudio.value += audioIds
        writeAudio(audioIds)
    }

    fun requestAudioDownload(audioId: String) = requestAudioDownloads(listOf(audioId))

    suspend fun runAudioDownload(audioId: String, onProgress: (Long, Long) -> Unit) = withContext(Dispatchers.IO) {
        val audio = findAudio(audioId)
        val file = audioFile(audio)
        val part = File(file.parentFile, "${audio.hash}.part")
        if (file.isFile) {
            try { ContentDigests.verify(file, audio.hash, audio.size, "audio") }
            catch (error: com.atharchive.core.data.content.ContentIntegrityException) {
                check(file.delete()) { "تعذّر إزالة التسجيل التالف" }
            }
        }
        if (!file.isFile) {
            file.parentFile?.mkdirs()
            check((file.parentFile?.usableSpace ?: 0L) > (audio.size - part.length()).coerceAtLeast(0) + 20L * 1024 * 1024) { "مساحة التخزين غير كافية" }
            requireNotNull(appContentClient).downloadArtifact(audio.path, audio.hash, audio.size, part, onProgress)
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            check(part.renameTo(file)) { "تعذّر حفظ التسجيل" }
        }
        ContentDigests.verify(file, audio.hash, audio.size, "audio")
        verifiedAudio += audio.hash
        markAudioDownloaded(audioId, true)
    }

    /** Many at once: files one by one, the stored sets once. */
    suspend fun removeAudioDownloads(audioIds: List<String>) = withContext(Dispatchers.IO) {
        if (audioIds.isEmpty()) return@withContext
        val references = contentDatabase.catalogDao().entitiesWithAudio().flatMap { it.toCatalogEntry().audio }.associateBy { it.id }
        audioIds.forEach { id ->
            val audio = references[id] ?: return@forEach
            val file = audioFile(audio)
            check(!file.exists() || file.delete()) { "تعذّر حذف التسجيل" }
            verifiedAudio -= audio.hash
            File(file.parentFile, "${audio.hash}.part").delete()
        }
        val ids = audioIds.toSet()
        synchronized(bookkeeping) {
            _downloadedAudio.value -= ids
            _requestedAudio.value -= ids
            writeAudio(ids)
        }
        setDownloadsPaused(ids.map { "audio:$it" }, false)
    }

    /**
     * Bulk downloads in flight, by type-row label: the pause keys still to finish and how many the
     * batch holds. It gives the notification one line per type and tells the screen which row to
     * show progress under — only the one the reader started.
     */
    data class DownloadBatch(val label: String, val total: Int, val left: Set<String>) {
        val done: Int get() = total - left.size
    }

    private val _batches = MutableStateFlow(emptyList<DownloadBatch>())
    val batches: StateFlow<List<DownloadBatch>> = _batches

    init {
        CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO).launch {
            try {
                loadDownloadState()
            } finally {
                downloadStateLoaded.complete(Unit)
            }
            for (write in writes) {
                try {
                    write(userDatabase.userDataDao())
                } catch (error: Exception) {
                    android.util.Log.e("ContentAccess", "download bookkeeping write failed", error)
                }
            }
        }
    }

    fun startBatch(label: String, keys: Collection<String>) = synchronized(bookkeeping) {
        if (keys.isEmpty()) return@synchronized
        val current = _batches.value.firstOrNull { it.label == label }
        val left = current?.left.orEmpty() + keys
        val batch = DownloadBatch(label, (current?.done ?: 0) + left.size, left)
        touch("batch", listOf(label))
        _batches.value = _batches.value.filter { it.label != label } + batch
        writeBehind {
            saveDownloadBatch(DownloadBatchEntity(label, batch.total))
            addDownloadBatchItems(keys.map { DownloadBatchItemEntity(label, it) })
        }
    }

    /** One item finished: returns the batches it was part of, after the update (empty if none). */
    fun finishBatchItem(key: String): List<DownloadBatch> = synchronized(bookkeeping) {
        touch("item", listOf(key))
        val touched = _batches.value.filter { key in it.left }.map { it.copy(left = it.left - key) }
        if (touched.isEmpty()) return@synchronized emptyList()
        val labels = touched.mapTo(hashSetOf()) { it.label }
        _batches.value = _batches.value.filter { it.label !in labels } + touched.filter { it.left.isNotEmpty() }
        val finished = touched.filter { it.left.isEmpty() }.map { it.label }
        writeBehind {
            removeDownloadBatchItem(key)
            if (finished.isNotEmpty()) deleteDownloadBatches(finished)
        }
        touched
    }

    fun clearBatches(labels: Set<String>) = synchronized(bookkeeping) {
        touch("batch", labels)
        if (_batches.value.none { it.label in labels }) return@synchronized
        _batches.value = _batches.value.filter { it.label !in labels }
        val gone = labels.toList()
        writeBehind {
            deleteDownloadBatchItems(gone)
            deleteDownloadBatches(gone)
        }
    }

    suspend fun removeAudioDownload(audioId: String) = withContext(Dispatchers.IO) {
        val audio = findAudio(audioId)
        val file = audioFile(audio)
        check(!file.exists() || file.delete()) { "تعذّر حذف التسجيل" }
        verifiedAudio -= audio.hash
        val part = File(file.parentFile, "${audio.hash}.part")
        check(!part.exists() || part.delete()) { "تعذّر حذف التنزيل الجزئي" }
        synchronized(bookkeeping) {
            _downloadedAudio.value -= audioId
            _requestedAudio.value -= audioId
            writeAudio(listOf(audioId))
        }
        setDownloadPaused("audio:$audioId", false)
    }

    fun setDownloadsPaused(ids: Collection<String>, paused: Boolean) = synchronized(bookkeeping) {
        val keys = ids.toList()
        if (keys.isEmpty()) return@synchronized
        touch("paused", keys)
        _pausedDownloads.value = if (paused) _pausedDownloads.value + keys else _pausedDownloads.value - keys.toSet()
        writeBehind { if (paused) addPausedDownloads(keys.map(::PausedDownloadEntity)) else removePausedDownloads(keys) }
    }

    fun setDownloadPaused(entityId: String, paused: Boolean) = setDownloadsPaused(listOf(entityId), paused)

    /** The edition last chosen from «الطبعات» for a work (keyed by its default edition), or null for the default. */
    fun preferredEdition(primaryId: String): String? = storagePreferences.getString("edition:$primaryId", null)

    fun setPreferredEdition(primaryId: String, editionId: String) = storagePreferences.edit {
        if (editionId == primaryId) remove("edition:$primaryId") else putString("edition:$primaryId", editionId)
    }
    private val storagePolicy by lazy {
        ContentStoragePolicy(
            storageRoot = context.filesDir,
            cacheManager = cacheManager,
            configuredBudgetBytes = { _cacheBudgetBytes.value },
        )
    }
    private val _storageStatus = MutableStateFlow<ContentStorageStatus?>(null)
    val storageStatus: StateFlow<ContentStorageStatus?> = _storageStatus
    private val _openProgress = MutableStateFlow<Pair<String, Float>?>(null)
    /** The entity whose text is being fetched and how far along (0..1); null when nothing is. */
    val openProgress: StateFlow<Pair<String, Float>?> = _openProgress
    // Seeded from the last launch's verified set: starting empty showed every saved book as
    // downloading again for the seconds reconcilePinnedDownloads takes. Reconcile still corrects it.
    private val _retainedEntityIds = MutableStateFlow(storagePreferences.getStringSet(RetainedIdsKey, null)?.toSet().orEmpty())
    val retainedEntityIds: StateFlow<Set<String>> = _retainedEntityIds

    private fun setRetained(ids: Set<String>) {
        _retainedEntityIds.value = ids
        storagePreferences.edit { putStringSet(RetainedIdsKey, ids) }
    }

    private val configuration: ContentOriginConfiguration? by lazy {
        val base = BuildConfig.ATHAR_CONTENT_BASE_URL.trim()
        val keyId = BuildConfig.ATHAR_CONTENT_SIGNING_KEY_ID.trim()
        val publicKey = BuildConfig.ATHAR_CONTENT_PUBLIC_KEY_DER_BASE64.trim()
        if (base.isEmpty() || keyId.isEmpty() || publicKey.isEmpty()) {
            null
        } else {
            val decoded = try {
                Base64.getDecoder().decode(publicKey)
            } catch (error: IllegalArgumentException) {
                throw IllegalStateException("malformed configured app-content public key", error)
            }
            ContentOriginConfiguration(
                baseUrl = base,
                keyId = keyId,
                publicKey = SignedRootVerifier.rsaPublicKeyFromX509(decoded),
            )
        }
    }

    private val appContentClient: AppContentHttpClient? by lazy {
        configuration?.let { config ->
            AppContentHttpClient(config.baseUrl.toHttpUrl(), httpClient)
        }
    }

    private val repository: ReadThroughContentRepository? by lazy {
        appContentClient?.let { client ->
            ReadThroughContentRepository(
                database = contentDatabase,
                userDatabase = userDatabase,
                client = client,
                importer = importer,
                mutationMutex = mutationMutex,
            )
        }
    }

    private val transferRunner: ContentTransferRunner? by lazy {
        appContentClient?.let { client ->
            ContentTransferRunner(
                contentDatabase = contentDatabase,
                userDatabase = userDatabase,
                client = client,
                importer = importer,
                retainedStore = retainedStore,
                transferDirectory = File(context.filesDir, "transfers"),
                storagePolicy = storagePolicy,
                mutationMutex = mutationMutex,
            )
        }
    }

    val configured: Boolean get() = configuration != null

    suspend fun sync(): CatalogSyncResult? {
        val config = configuration ?: return null
        awaitDownloadState() // requested recordings keep their catalog rows
        return repository!!.syncCatalog(mapOf(config.keyId to config.publicKey)) { _requestedAudio.value }
    }

    // One open per work at a time: opening the reader asks for the same first frame twice
    // (route + reader window). The second caller waits, then finds the frame already cached.
    private val openLocks = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.sync.Mutex>()

    suspend fun open(entityId: String, ordinal: Int): ReadThroughResult? =
        openLocks.getOrPut(entityId) { kotlinx.coroutines.sync.Mutex() }.withLock { openLocked(entityId, ordinal) }

    private suspend fun openLocked(entityId: String, ordinal: Int): ReadThroughResult? {
        val source = repository ?: return null
        val frames = contentDatabase.importDao().frames(entityId)
        val cachedFrame = frames.firstOrNull {
            ordinal in it.firstBlockOrdinal until (it.firstBlockOrdinal + it.blockCount)
        }
        val readingOrdinal = userDatabase.userDataDao().readingPosition(entityId)?.ordinalHint
        val readingFrame = frames.firstOrNull { readingOrdinal != null && readingOrdinal in it.firstBlockOrdinal until (it.firstBlockOrdinal + it.blockCount) }?.frameOrdinal
        // The page on screen: reader priority over download batches (ContentWriteLock).
        val status = mutationMutex.forReader {
            storagePolicy.prepareForReadThrough(
                activeEntityId = entityId,
                activeFrameOrdinal = cachedFrame?.frameOrdinal,
                activeFrameOrdinals = setOfNotNull(cachedFrame?.frameOrdinal, readingFrame),
            )
        }
        _storageStatus.value = status
        if (cachedFrame == null && status.lowStorage) {
            throw InsufficientContentStorageException(
                availableBytes = status.availableBytes,
                requiredBytes = ContentStoragePolicy.LOW_STORAGE_BYTES,
                cacheBytesRemoved = status.cacheBytesRemoved,
            )
        }
        val result = try {
            source.openFrame(entityId, ordinal) { _openProgress.value = entityId to it }
        } finally {
            _openProgress.value = null
        }
        mutationMutex.forReader {
            cacheManager.evictToBudget(
                budgetBytes = _cacheBudgetBytes.value,
                activeEntityId = entityId,
                activeFrameOrdinal = result.frameNumber,
                activeFrameOrdinals = setOfNotNull(result.frameNumber, readingFrame),
            )
        }
        return result
    }

    suspend fun requestDownload(entityId: String) {
        requireNotNull(transferRunner) { "app-content origin is not configured" }.requestPin(entityId)
    }

    suspend fun exportShared(entityIds: Collection<String>, output: java.io.OutputStream): Int =
        requireNotNull(transferRunner) { "app-content origin is not configured" }.exportShared(entityIds, output)

    suspend fun importShared(input: java.io.InputStream): List<ContentDownloadTarget> =
        requireNotNull(transferRunner) { "app-content origin is not configured" }.importShared(input).map { ContentDownloadTarget(it.id, it.coll) }

    suspend fun requestDownloads(entityIds: Collection<String>) {
        requireNotNull(transferRunner) { "app-content origin is not configured" }.requestPins(entityIds)
    }

    suspend fun runDownload(
        entityId: String,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ): ContentTransferResult {
        val result = try {
            requireNotNull(transferRunner) {
                "app-content origin is not configured"
            }.downloadPinned(entityId, onProgress)
        } catch (error: InsufficientContentStorageException) {
            _storageStatus.value = ContentStorageStatus(
                lowStorage = true,
                availableBytes = error.availableBytes,
                cacheBytesRemoved = error.cacheBytesRemoved,
            )
            throw error
        }
        setRetained(_retainedEntityIds.value + entityId)
        return result
    }

    suspend fun unpinAll(entityIds: List<String>) {
        if (entityIds.isEmpty()) return
        setDownloadsPaused(entityIds, false)
        val runner = requireNotNull(transferRunner) { "app-content origin is not configured" }
        runner.removePins(entityIds)
        entityIds.forEach { runner.unpin(it) }
        setRetained(_retainedEntityIds.value - entityIds.toSet())
    }

    suspend fun unpin(entityId: String) {
        setDownloadPaused(entityId, false)
        requireNotNull(transferRunner) { "app-content origin is not configured" }.unpin(entityId)
        setRetained(_retainedEntityIds.value - entityId)
    }

    /** Pinned, not yet on the phone and not paused: what the bulk worker still has to fetch. */
    suspend fun pendingTextDownloads(): List<ContentDownloadTarget> {
        val retained = _retainedEntityIds.value
        val paused = _pausedDownloads.value
        val catalog = contentDatabase.catalogDao()
        return userDatabase.userDataDao().pinnedEntityIds()
            .filter { it !in retained && it !in paused }
            .mapNotNull { id -> catalog.entity(id)?.let { ContentDownloadTarget(id, it.coll) } }
    }

    suspend fun isPinned(entityId: String): Boolean =
        userDatabase.userDataDao().pinnedDownload(entityId) != null

    suspend fun entityTitle(entityId: String): String =
        contentDatabase.catalogDao().entity(entityId)?.title ?: entityId

    suspend fun reconcilePinnedDownloads(): List<ContentDownloadTarget> = mutationMutex.withLock {
        val userDao = userDatabase.userDataDao()
        val importDao = contentDatabase.importDao()
        val verified = linkedSetOf<String>()
        val pending = mutableListOf<ContentDownloadTarget>()
        for (entityId in userDao.pinnedEntityIds()) {
            val entity = importDao.entity(entityId) ?: continue
            val entry = entity.toCatalogEntry()
            val pin = userDao.pinnedDownload(entityId) ?: continue
            if (pin.pkgHash != entry.pkg.hash) {
                userDao.savePinnedDownload(
                    PinnedDownloadEntity(
                        entityId = entityId,
                        pinnedAt = pin.pinnedAt,
                        pkgHash = entry.pkg.hash,
                    ),
                )
            }
            val retained = retainedStore.containsVerified(entry)
            if (retained && importDao.state(entityId)?.availability == ContentAvailability.COMPLETE) {
                importDao.markTransfer(entityId, ContentTransferState.IDLE)
                verified += entityId
            } else {
                pending += ContentDownloadTarget(entityId, entity.coll)
            }
        }
        setRetained(verified)
        pending
    }

    /** The installed package of a work; it changes when a download finishes or replaces it. */
    fun observePackage(entityId: String): Flow<String?> =
        contentDatabase.catalogDao().observeLocalPackage(entityId).distinctUntilChanged()

    private fun blockReader(entityId: String) = OrdinalBlockReader(
        count = { contentDatabase.catalogDao().entity(entityId)?.pkgBlocks ?: 0 },
        read = { first, last -> contentDatabase.importDao().blockWindow(entityId, first, last) },
        fetch = { ordinal -> open(entityId, ordinal) },
    )

    suspend fun blockCount(entityId: String): Int = blockReader(entityId).count()

    /** Exactly [range], fetching any frame it needs; reads in pieces so one call never holds a huge window. */
    suspend fun blockRange(entityId: String, range: IntRange): List<ContentBlockEntity> {
        require(!range.isEmpty() && range.first >= 0 && range.last.toLong() - range.first < 5000) { "نطاق التحديد كبير جدًا" }
        val reader = blockReader(entityId)
        return (range step 100).flatMap { reader.range(it, minOf(it + 99, range.last)) }
    }

    fun prefetchAdjacent(
        scope: CoroutineScope,
        entityId: String,
        currentOrdinal: Int,
    ): ReaderPrefetchSession? {
        val source = repository ?: return null
        var visibleFrameOrdinal: Int? = null
        // Frames this session already fetched stay protected from eviction while it runs.
        val fetched = mutableSetOf<Int>()
        return source.prefetchAdjacent(
            scope = scope,
            entityId = entityId,
            currentOrdinal = currentOrdinal,
            count = PrefetchAheadFrames,
            beforeFetch = {
                visibleFrameOrdinal = contentDatabase.importDao().frames(entityId).firstOrNull {
                    currentOrdinal in it.firstBlockOrdinal until (it.firstBlockOrdinal + it.blockCount)
                }?.frameOrdinal
                val status = mutationMutex.withLock {
                    storagePolicy.prepareForReadThrough(
                        activeEntityId = entityId,
                        activeFrameOrdinal = visibleFrameOrdinal,
                    )
                }
                _storageStatus.value = status
                !status.lowStorage
            },
            afterFetch = { result ->
                mutationMutex.withLock {
                    cacheManager.evictToBudget(
                        budgetBytes = _cacheBudgetBytes.value,
                        activeEntityId = entityId,
                        activeFrameOrdinals = setOfNotNull(visibleFrameOrdinal) + fetched.also { it += result.frameNumber },
                    )
                }
            },
        )
    }

    suspend fun trimCache(
        activeEntityId: String? = null,
        activeFrameOrdinal: Int? = null,
    ): Long = mutationMutex.withLock {
        cacheManager.evictToBudget(
            budgetBytes = _cacheBudgetBytes.value,
            activeEntityId = activeEntityId,
            activeFrameOrdinal = activeFrameOrdinal,
        )
    }

    suspend fun clearCache(): Long = mutationMutex.withLock { cacheManager.clearUnpinned() }

    suspend fun cacheBytes(): Long = cacheManager.usedBytes()

    suspend fun setCacheBudget(bytes: Long): Long {
        ContentCacheManager.requireConfiguredBudget(bytes)
        storagePreferences.edit { putLong(CacheBudgetKey, bytes) }
        _cacheBudgetBytes.value = bytes
        return trimCache()
    }

    suspend fun rebuildRetainedContent(): OfflineRebuildResult {
        val result = OfflineContentRebuilder(
            contentDatabase = contentDatabase,
            userDatabase = userDatabase,
            store = retainedStore,
            importer = importer,
        ).let { rebuilder ->
            if (!contentDatabase.catalogDao().hasEntities()) {
                rebuilder.rebuild()
            } else {
                OfflineRebuildResult(0, 0, 0, emptyMap())
            }
        }
        return result
    }

    private fun readCacheBudget(): Long {
        val stored = storagePreferences.getLong(
            CacheBudgetKey,
            ContentCacheManager.DEFAULT_BUDGET_BYTES,
        )
        return runCatching {
            ContentCacheManager.requireConfiguredBudget(stored)
            stored
        }.getOrDefault(ContentCacheManager.DEFAULT_BUDGET_BYTES)
    }
}

data class ContentDownloadTarget(val entityId: String, val collection: String)

private data class ContentOriginConfiguration(
    val baseUrl: String,
    val keyId: String,
    val publicKey: java.security.PublicKey,
)
