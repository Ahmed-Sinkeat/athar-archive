package com.atharchive.data

import android.content.Context
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.atharchive.BuildConfig
import com.atharchive.core.data.content.SignedRootVerifier
import com.atharchive.core.data.db.AtharDatabases
import com.atharchive.core.data.db.content.AtharContentDatabase
import com.atharchive.core.data.db.content.ContentBlockEntity
import com.atharchive.core.data.db.user.AtharUserDatabase
import com.atharchive.core.data.network.AppContentHttpClient
import com.atharchive.core.data.repository.CatalogSyncResult
import com.atharchive.core.data.repository.ContentCacheManager
import com.atharchive.core.data.repository.ContentImporter
import com.atharchive.core.data.repository.OfflineContentRebuilder
import com.atharchive.core.data.repository.OfflineRebuildResult
import com.atharchive.core.data.repository.ReadThroughContentRepository
import com.atharchive.core.data.repository.ReadThroughResult
import com.atharchive.core.data.repository.ReaderPrefetchSession
import com.atharchive.core.data.repository.RetainedPackageStore
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
}

@Singleton
class ContentAccess @Inject constructor(
    @ApplicationContext private val context: Context,
    val contentDatabase: AtharContentDatabase,
    val userDatabase: AtharUserDatabase,
    private val importer: ContentImporter,
    private val okHttpClient: OkHttpClient,
) {
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

    private val repository: ReadThroughContentRepository? by lazy {
        configuration?.let { config ->
            ReadThroughContentRepository(
                database = contentDatabase,
                userDatabase = userDatabase,
                client = AppContentHttpClient(config.baseUrl.toHttpUrl(), okHttpClient),
                importer = importer,
            )
        }
    }

    val configured: Boolean get() = configuration != null

    suspend fun sync(): CatalogSyncResult? {
        val config = configuration ?: return null
        return repository!!.syncCatalog(mapOf(config.keyId to config.publicKey))
    }

    suspend fun open(entityId: String, ordinal: Int): ReadThroughResult? =
        repository?.openFrame(entityId, ordinal)

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
    ): ReaderPrefetchSession? = repository?.prefetchAdjacent(scope, entityId, currentOrdinal)

    suspend fun trimCache(
        activeEntityId: String? = null,
        activeFrameOrdinal: Int? = null,
    ): Long = ContentCacheManager(contentDatabase, userDatabase).evictToBudget(
        activeEntityId = activeEntityId,
        activeFrameOrdinal = activeFrameOrdinal,
    )

    suspend fun rebuildRetainedContent(): OfflineRebuildResult = OfflineContentRebuilder(
        contentDatabase = contentDatabase,
        userDatabase = userDatabase,
        store = RetainedPackageStore(File(context.filesDir, "content")),
        importer = importer,
    ).let { rebuilder ->
        if (contentDatabase.catalogDao().allEntities().isEmpty()) {
            rebuilder.rebuild()
        } else {
            OfflineRebuildResult(0, 0, 0, emptyMap())
        }
    }
}

private data class ContentOriginConfiguration(
    val baseUrl: String,
    val keyId: String,
    val publicKey: java.security.PublicKey,
)
