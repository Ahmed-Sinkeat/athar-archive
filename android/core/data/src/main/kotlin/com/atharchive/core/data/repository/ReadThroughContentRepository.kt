package com.atharchive.core.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.atharchive.core.data.content.CatalogEntry
import com.atharchive.core.data.content.FramedPackageDecoder
import com.atharchive.core.data.db.content.AtharContentDatabase
import com.atharchive.core.data.db.content.ContentAvailability
import com.atharchive.core.data.db.content.ContentBlockEntity
import com.atharchive.core.data.db.content.ContentEntity
import com.atharchive.core.data.db.content.ContentTransferState
import com.atharchive.core.data.db.content.FrameImportResult
import com.atharchive.core.data.db.user.AtharUserDatabase
import com.atharchive.core.data.network.AppContentHttpClient
import com.atharchive.core.data.network.VerifiedGeneration
import java.security.PublicKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ReadThroughResult(
    val fromCache: Boolean,
    val frameNumber: Int,
    val availability: String,
    val importedBlocks: Int,
)

sealed interface CatalogSyncResult {
    data class Applied(val generation: VerifiedGeneration) : CatalogSyncResult
    data class UpToDate(val generationId: String) : CatalogSyncResult
}

/**
 * The M5 repository is Room-first: callers observe database Flows and invoke network work
 * separately. Constructing or failing the network client can never prevent local startup.
 */
class ReadThroughContentRepository(
    private val database: AtharContentDatabase,
    private val userDatabase: AtharUserDatabase,
    private val client: AppContentHttpClient,
    private val importer: ContentImporter,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val mutationMutex = Mutex()

    fun observeCatalog(): Flow<List<ContentEntity>> = database.catalogDao().observeCatalog()

    fun observeCollection(collection: String): Flow<List<ContentEntity>> =
        database.catalogDao().observeCollection(collection)

    fun observeEntity(entityId: String): Flow<ContentEntity?> =
        database.catalogDao().observeEntity(entityId)

    fun observeBlocks(entityId: String): Flow<List<ContentBlockEntity>> =
        database.importDao().observeBlocks(entityId)

    fun pagedBlocks(entityId: String): Flow<PagingData<ContentBlockEntity>> = Pager(
        config = PagingConfig(
            pageSize = 60,
            prefetchDistance = 20,
            enablePlaceholders = true,
            initialLoadSize = 120,
            maxSize = 300,
        ),
        pagingSourceFactory = { database.importDao().pagingSource(entityId) },
    ).flow

    suspend fun syncCatalog(trustedKeys: Map<String, PublicKey>): CatalogSyncResult = mutationMutex.withLock {
        val root = client.fetchRoot(trustedKeys).root
        val current = database.catalogDao().generation()
        if (current?.generationId == root.generationId) {
            return@withLock CatalogSyncResult.UpToDate(root.generationId)
        }

        val generation = client.fetchGeneration(root)
        val protectedBefore = userDatabase.userDataDao().protectedEntityIds().toSet()
        importer.applyGeneration(
            generationId = root.generationId,
            catalogHash = root.catalog.hash,
            tombstonesHash = root.tombstones.hash,
            catalog = generation.catalog,
            tombstonedIds = generation.tombstones.deleted.mapTo(mutableSetOf()) { it.id },
            protectedIds = protectedBefore,
        )
        // Re-snapshot after the content transaction. Tombstoned rows were only marked in
        // that transaction, so a concurrent user action cannot lose its content metadata.
        val protectedAfter = userDatabase.userDataDao().protectedEntityIds().toSet()
        database.catalogDao().purgeUnprotectedTombstones(protectedAfter)
        CatalogSyncResult.Applied(generation)
    }

    suspend fun openFrame(entityId: String, targetOrdinal: Int): ReadThroughResult {
        val entity = requireNotNull(database.catalogDao().entity(entityId)) {
            "catalog entity $entityId is missing"
        }
        return openFrame(entity.toCatalogEntry(), targetOrdinal)
    }

    suspend fun openFrame(entry: CatalogEntry, targetOrdinal: Int): ReadThroughResult = mutationMutex.withLock {
        require(targetOrdinal in 0 until entry.pkg.blocks) { "target ordinal is outside the package" }
        val dao = database.importDao()
        val local = dao.entity(entry.id)
        if (local?.updateAvailable == true || (local?.localPackageHash != null && local.localPackageHash != entry.pkg.hash)) {
            dao.clearImportedContent(entry.id)
        }
        val cached = dao.frames(entry.id).firstOrNull {
            targetOrdinal >= it.firstBlockOrdinal && targetOrdinal < it.firstBlockOrdinal + it.blockCount
        }
        if (cached != null) {
            val now = nowMillis()
            dao.touchFrame(entry.id, cached.frameOrdinal, now)
            dao.touchEntity(entry.id, now)
            val entity = requireNotNull(dao.entity(entry.id))
            return@withLock ReadThroughResult(
                fromCache = true,
                frameNumber = cached.frameOrdinal,
                availability = entity.availability,
                importedBlocks = dao.importedBlockCount(entry.id),
            )
        }

        dao.markTransfer(entry.id, ContentTransferState.FETCHING)
        try {
            val indexBytes = client.fetchPackageIndex(entry)
            val index = FramedPackageDecoder.decodeIndex(indexBytes, entry)
            val frameNumber = index.frames.indexOfFirst {
                targetOrdinal >= it.ord && targetOrdinal < it.ord + it.n
            }
            require(frameNumber >= 0) { "package index does not cover target ordinal" }
            val frame = index.frames[frameNumber]
            val bytes = client.fetchFrame(entry, frame)
            importer.importVerifiedFrame(entry, frameNumber, frame, bytes).toReadThrough(frameNumber)
        } catch (error: Throwable) {
            runCatching { dao.markTransfer(entry.id, ContentTransferState.FAILED) }
            throw error
        }
    }

    /**
     * Starts the sole allowed adjacent prefetch. Closing the returned session cancels work
     * that has not entered the repository's serialized fetch/import section.
     */
    fun prefetchAdjacent(
        scope: CoroutineScope,
        entityId: String,
        currentOrdinal: Int,
        direction: Int = 1,
    ): ReaderPrefetchSession {
        require(direction == -1 || direction == 1)
        val job = scope.launch {
            val entity = database.catalogDao().entity(entityId) ?: return@launch
            val indexBytes = client.fetchPackageIndex(entity.toCatalogEntry())
            val index = FramedPackageDecoder.decodeIndex(indexBytes, entity.toCatalogEntry())
            val currentFrame = index.frames.indexOfFirst { currentOrdinal in it.ord until (it.ord + it.n) }
            val target = index.frames.getOrNull(currentFrame + direction) ?: return@launch
            openFrame(entityId, target.ord)
        }
        return ReaderPrefetchSession(job)
    }
}

class ReaderPrefetchSession internal constructor(private val job: Job) : AutoCloseable {
    override fun close() {
        job.cancel()
    }
}

class ContentCacheManager(
    private val contentDatabase: AtharContentDatabase,
    private val userDatabase: AtharUserDatabase,
) {
    companion object {
        const val DEFAULT_BUDGET_BYTES = 2L * 1024 * 1024 * 1024
        const val MIN_BUDGET_BYTES = 500L * 1024 * 1024
        const val MAX_BUDGET_BYTES = 20L * 1024 * 1024 * 1024
    }

    suspend fun evictToBudget(
        budgetBytes: Long = DEFAULT_BUDGET_BYTES,
        activeEntityId: String? = null,
        activeFrameOrdinal: Int? = null,
    ): Long {
        require(budgetBytes == Long.MAX_VALUE || budgetBytes in MIN_BUDGET_BYTES..MAX_BUDGET_BYTES)
        val dao = contentDatabase.importDao()
        var total = dao.totalImportedBytes()
        if (total <= budgetBytes) return 0
        val protected = userDatabase.userDataDao().pinnedEntityIds().toSet()
        var removed = 0L
        for (candidate in dao.evictionCandidates()) {
            if (total <= budgetBytes) break
            if (candidate.entityId in protected) continue
            if (candidate.entityId == activeEntityId && candidate.frameOrdinal == activeFrameOrdinal) continue
            val result = dao.evictFrame(candidate)
            total -= result.bytesRemoved
            removed += result.bytesRemoved
        }
        return removed
    }
}

private fun FrameImportResult.toReadThrough(frameNumber: Int): ReadThroughResult =
    ReadThroughResult(
        fromCache = false,
        frameNumber = frameNumber,
        availability = availability,
        importedBlocks = importedBlocks,
    )
