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
import com.atharchive.core.data.db.user.PinnedDownloadEntity
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.sync.Mutex
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
    }

    private val mutationMutex = Mutex()
    private val retainedStore by lazy { RetainedPackageStore(File(context.filesDir, "content")) }
    private val cacheManager by lazy { ContentCacheManager(contentDatabase, userDatabase) }
    private val storagePreferences by lazy {
        context.getSharedPreferences(StoragePreferences, Context.MODE_PRIVATE)
    }
    private val _cacheBudgetBytes = MutableStateFlow(readCacheBudget())
    val cacheBudgetBytes: StateFlow<Long> = _cacheBudgetBytes
    private val _pausedDownloads = MutableStateFlow(storagePreferences.getStringSet("paused_downloads", emptySet()).orEmpty().toSet())
    val pausedDownloads: StateFlow<Set<String>> = _pausedDownloads
    private val _requestedAudio = MutableStateFlow(storagePreferences.getStringSet("requested_audio", emptySet()).orEmpty().toSet())
    val requestedAudio: StateFlow<Set<String>> = _requestedAudio
    private val _downloadedAudio = MutableStateFlow(storagePreferences.getStringSet("downloaded_audio", emptySet()).orEmpty().toSet())
    val downloadedAudio: StateFlow<Set<String>> = _downloadedAudio
    private val verifiedAudio = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    suspend fun reconcileAudioDownloads() = withContext(Dispatchers.IO) {
        if (_downloadedAudio.value.isEmpty()) return@withContext
        val references = contentDatabase.catalogDao().entitiesWithAudio().flatMap { it.toCatalogEntry().audio }.associateBy { it.id }
        val downloaded = _downloadedAudio.value
        val valid = downloaded.filterTo(hashSetOf()) { id ->
            references[id]?.let { audio -> audioFile(audio).let { it.isFile && it.length() == audio.size } } == true
        }
        val invalid = downloaded - valid
        synchronized(storagePreferences) {
            _downloadedAudio.value -= invalid
            storagePreferences.edit { putStringSet("downloaded_audio", _downloadedAudio.value) }
        }
    }

    private fun markAudioDownloaded(audioId: String, retained: Boolean) = synchronized(storagePreferences) {
        _downloadedAudio.value = if (retained) _downloadedAudio.value + audioId else _downloadedAudio.value - audioId
        storagePreferences.edit { putStringSet("downloaded_audio", _downloadedAudio.value) }
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

    fun requestAudioDownloads(audioIds: Collection<String>) = synchronized(storagePreferences) {
        _requestedAudio.value += audioIds
        storagePreferences.edit { putStringSet("requested_audio", _requestedAudio.value) }
    }

    fun requestAudioDownload(audioId: String) = synchronized(storagePreferences) {
        _requestedAudio.value += audioId
        storagePreferences.edit { putStringSet("requested_audio", _requestedAudio.value) }
    }

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
        synchronized(storagePreferences) {
            _downloadedAudio.value -= ids
            _requestedAudio.value -= ids
            storagePreferences.edit {
                putStringSet("downloaded_audio", _downloadedAudio.value)
                putStringSet("requested_audio", _requestedAudio.value)
            }
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

    private val _batches = MutableStateFlow(readBatches())
    val batches: StateFlow<List<DownloadBatch>> = _batches

    private fun readBatches() = storagePreferences.getStringSet("batches", emptySet()).orEmpty().map { label ->
        DownloadBatch(label, storagePreferences.getInt("batch_total:$label", 0), storagePreferences.getStringSet("batch_left:$label", emptySet()).orEmpty().toSet())
    }

    private fun writeBatches(batches: List<DownloadBatch>) {
        val gone = _batches.value.map { it.label } - batches.map { it.label }.toSet()
        _batches.value = batches
        storagePreferences.edit {
            putStringSet("batches", batches.mapTo(hashSetOf()) { it.label })
            gone.forEach { remove("batch_total:$it"); remove("batch_left:$it") }
            batches.forEach { putInt("batch_total:${it.label}", it.total); putStringSet("batch_left:${it.label}", it.left) }
        }
    }

    fun startBatch(label: String, keys: Collection<String>) = synchronized(storagePreferences) {
        if (keys.isEmpty()) return@synchronized
        val current = _batches.value.firstOrNull { it.label == label }
        val left = current?.left.orEmpty() + keys
        writeBatches(_batches.value.filter { it.label != label } + DownloadBatch(label, (current?.done ?: 0) + left.size, left))
    }

    /** One item finished: returns the batches it was part of, after the update (empty if none). */
    fun finishBatchItem(key: String): List<DownloadBatch> = synchronized(storagePreferences) {
        val touched = _batches.value.filter { key in it.left }.map { it.copy(left = it.left - key) }
        if (touched.isEmpty()) return@synchronized emptyList()
        val labels = touched.mapTo(hashSetOf()) { it.label }
        writeBatches(_batches.value.filter { it.label !in labels } + touched.filter { it.left.isNotEmpty() })
        touched
    }

    fun clearBatches(labels: Set<String>) = synchronized(storagePreferences) {
        if (_batches.value.any { it.label in labels }) writeBatches(_batches.value.filter { it.label !in labels })
    }

    suspend fun removeAudioDownload(audioId: String) = withContext(Dispatchers.IO) {
        val audio = findAudio(audioId)
        val file = audioFile(audio)
        check(!file.exists() || file.delete()) { "تعذّر حذف التسجيل" }
        verifiedAudio -= audio.hash
        val part = File(file.parentFile, "${audio.hash}.part")
        check(!part.exists() || part.delete()) { "تعذّر حذف التنزيل الجزئي" }
        markAudioDownloaded(audioId, false)
        synchronized(storagePreferences) {
            _requestedAudio.value -= audioId
            storagePreferences.edit { putStringSet("requested_audio", _requestedAudio.value) }
        }
        setDownloadPaused("audio:$audioId", false)
    }

    fun setDownloadsPaused(ids: Collection<String>, paused: Boolean) = synchronized(storagePreferences) {
        _pausedDownloads.value = if (paused) _pausedDownloads.value + ids else _pausedDownloads.value - ids.toSet()
        storagePreferences.edit { putStringSet("paused_downloads", _pausedDownloads.value) }
    }

    fun setDownloadPaused(entityId: String, paused: Boolean) = synchronized(storagePreferences) {
        _pausedDownloads.value = if (paused) _pausedDownloads.value + entityId else _pausedDownloads.value - entityId
        storagePreferences.edit { putStringSet("paused_downloads", _pausedDownloads.value) }
    }

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
    private val _retainedEntityIds = MutableStateFlow<Set<String>>(emptySet())
    val retainedEntityIds: StateFlow<Set<String>> = _retainedEntityIds

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

    /**
     * Seeds an empty catalog from the snapshot in the app's assets (`app-content/`, put there
     * by `npm run app:bundle-catalog`). Quietly does nothing without a snapshot, with a
     * catalog already applied, or when the snapshot does not verify: sync still follows.
     */
    suspend fun seedBundledCatalog(): Boolean {
        val config = configuration ?: return false
        val source = repository ?: return false
        return try {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                source.seedCatalog(mapOf(config.keyId to config.publicKey)) { path ->
                    try { context.assets.open("app-content/$path").use { it.readBytes() } }
                    catch (_: java.io.FileNotFoundException) { null }
                }
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            android.util.Log.w("AtharContent", "bundled catalog not applied: ${error.message}")
            false
        }
    }

    suspend fun sync(): CatalogSyncResult? {
        val config = configuration ?: return null
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
        val status = mutationMutex.withLock {
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
        mutationMutex.withLock {
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
        _retainedEntityIds.value += entityId
        return result
    }

    suspend fun unpinAll(entityIds: List<String>) {
        if (entityIds.isEmpty()) return
        setDownloadsPaused(entityIds, false)
        val runner = requireNotNull(transferRunner) { "app-content origin is not configured" }
        runner.removePins(entityIds)
        entityIds.forEach { runner.unpin(it) }
        _retainedEntityIds.value -= entityIds.toSet()
    }

    suspend fun unpin(entityId: String) {
        setDownloadPaused(entityId, false)
        requireNotNull(transferRunner) { "app-content origin is not configured" }.unpin(entityId)
        _retainedEntityIds.value -= entityId
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
            if (retained && entity.availability == ContentAvailability.COMPLETE) {
                importDao.markTransfer(entityId, ContentTransferState.IDLE)
                verified += entityId
            } else {
                pending += ContentDownloadTarget(entityId, entity.coll)
            }
        }
        _retainedEntityIds.value = verified
        pending
    }

    /** The installed package of a work; it changes when a download finishes or replaces it. */
    fun observePackage(entityId: String): Flow<String?> =
        contentDatabase.catalogDao().observeEntity(entityId).map { it?.localPackageHash }.distinctUntilChanged()

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
