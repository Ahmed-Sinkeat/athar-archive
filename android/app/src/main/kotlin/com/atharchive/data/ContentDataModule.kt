package com.atharchive.data

import android.content.Context
import androidx.core.content.edit
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.atharchive.BuildConfig
import com.atharchive.core.data.content.SignedRootVerifier
import com.atharchive.core.data.db.AtharDatabases
import com.atharchive.core.data.db.content.AtharContentDatabase
import com.atharchive.core.data.db.content.ContentBlockEntity
import com.atharchive.core.data.db.content.ContentAvailability
import com.atharchive.core.data.db.content.ContentTransferState
import com.atharchive.core.data.db.user.PinnedDownloadEntity
import com.atharchive.core.data.db.user.AtharUserDatabase
import com.atharchive.core.data.network.AppContentHttpClient
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

@Singleton
class ContentAccess @Inject constructor(
    @ApplicationContext private val context: Context,
    val contentDatabase: AtharContentDatabase,
    val userDatabase: AtharUserDatabase,
    private val importer: ContentImporter,
    private val okHttpClient: OkHttpClient,
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
    private val storagePolicy by lazy {
        ContentStoragePolicy(
            storageRoot = context.filesDir,
            cacheManager = cacheManager,
            configuredBudgetBytes = { _cacheBudgetBytes.value },
        )
    }
    private val _storageStatus = MutableStateFlow<ContentStorageStatus?>(null)
    val storageStatus: StateFlow<ContentStorageStatus?> = _storageStatus
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
            AppContentHttpClient(config.baseUrl.toHttpUrl(), okHttpClient)
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
        return repository!!.syncCatalog(mapOf(config.keyId to config.publicKey))
    }

    suspend fun open(entityId: String, ordinal: Int): ReadThroughResult? {
        val source = repository ?: return null
        val cachedFrame = contentDatabase.importDao().frames(entityId).firstOrNull {
            ordinal in it.firstBlockOrdinal until (it.firstBlockOrdinal + it.blockCount)
        }
        val status = mutationMutex.withLock {
            storagePolicy.prepareForReadThrough(
                activeEntityId = entityId,
                activeFrameOrdinal = cachedFrame?.frameOrdinal,
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
        val result = source.openFrame(entityId, ordinal)
        mutationMutex.withLock {
            cacheManager.evictToBudget(
                budgetBytes = _cacheBudgetBytes.value,
                activeEntityId = entityId,
                activeFrameOrdinal = result.frameNumber,
            )
        }
        return result
    }

    suspend fun requestDownload(entityId: String) {
        requireNotNull(transferRunner) { "app-content origin is not configured" }.requestPin(entityId)
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

    suspend fun unpin(entityId: String) {
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
                retainedStore.remove(entry)
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
                if (!retained) retainedStore.remove(entry)
                pending += ContentDownloadTarget(entityId, entity.coll)
            }
        }
        _retainedEntityIds.value = verified
        pending
    }

    fun pagedBlocks(entityId: String): Flow<PagingData<ContentBlockEntity>> =
        Pager(
            config = PagingConfig(
                pageSize = 60,
                prefetchDistance = 20,
                enablePlaceholders = true,
                initialLoadSize = 120,
                maxSize = 300,
            ),
            pagingSourceFactory = { contentDatabase.importDao().pagingSource(entityId) },
        ).flow

    fun prefetchAdjacent(
        scope: CoroutineScope,
        entityId: String,
        currentOrdinal: Int,
    ): ReaderPrefetchSession? {
        val source = repository ?: return null
        var visibleFrameOrdinal: Int? = null
        return source.prefetchAdjacent(
            scope = scope,
            entityId = entityId,
            currentOrdinal = currentOrdinal,
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
                        activeFrameOrdinals = setOfNotNull(visibleFrameOrdinal, result.frameNumber),
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
            if (contentDatabase.catalogDao().allEntities().isEmpty()) {
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
