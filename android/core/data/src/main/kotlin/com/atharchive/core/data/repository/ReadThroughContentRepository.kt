package com.atharchive.core.data.repository

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
import kotlinx.coroutines.CancellationException
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
    private val mutationMutex: Mutex = Mutex(),
) {
    fun observeCatalog(): Flow<List<ContentEntity>> = database.catalogDao().observeCatalog()

    fun observeCollection(collection: String): Flow<List<ContentEntity>> =
        database.catalogDao().observeCollection(collection)

    fun observeEntity(entityId: String): Flow<ContentEntity?> =
        database.catalogDao().observeEntity(entityId)

    fun observeBlocks(entityId: String): Flow<List<ContentBlockEntity>> =
        database.importDao().observeBlocks(entityId)

    suspend fun syncCatalog(trustedKeys: Map<String, PublicKey>, requestedAudioIds: () -> Set<String> = { emptySet() }): CatalogSyncResult {
        val root = client.fetchRoot(trustedKeys).root
        val current = database.catalogDao().generation()
        if (current?.generationId == root.generationId) {
            return CatalogSyncResult.UpToDate(root.generationId)
        }

        val generation = client.fetchGeneration(root)
        return mutationMutex.withLock {
        if (database.catalogDao().generation()?.generationId != current?.generationId) return@withLock CatalogSyncResult.UpToDate(database.catalogDao().generation()?.generationId ?: root.generationId)
        val protectedBefore = protectedIds(requestedAudioIds())
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
        val protectedAfter = protectedIds(requestedAudioIds())
        database.catalogDao().purgeUnprotectedTombstones(protectedAfter)
        CatalogSyncResult.Applied(generation)
        }
    }

    /**
     * First launch only: applies the catalog snapshot shipped inside the app so the library and
     * title/author/topic search work before (or without) any network. It goes through the same
     * signature and hash checks as a downloaded generation, and it never runs when a
     * generation is already applied, so a stale snapshot can not replace a newer catalog.
     * [read] returns the snapshot file at a path relative to the content root, or null.
     */
    suspend fun seedCatalog(trustedKeys: Map<String, PublicKey>, read: (String) -> ByteArray?): Boolean {
        if (database.catalogDao().generation() != null) return false
        val envelope = read("index.json") ?: return false
        val root = com.atharchive.core.data.content.SignedRootVerifier.verify(envelope, trustedKeys)
        val catalog = com.atharchive.core.data.content.AppContentDocuments.decodeCatalog(
            read(root.catalog.path) ?: return false, root.catalog,
        )
        val tombstones = com.atharchive.core.data.content.AppContentDocuments.decodeTombstones(
            read(root.tombstones.path) ?: return false, root.tombstones,
        )
        return mutationMutex.withLock {
            if (database.catalogDao().generation() != null) return@withLock false
            importer.applyGeneration(
                generationId = root.generationId,
                catalogHash = root.catalog.hash,
                tombstonesHash = root.tombstones.hash,
                catalog = catalog,
                tombstonedIds = tombstones.deleted.mapTo(mutableSetOf()) { it.id },
                protectedIds = emptySet(),
            )
            true
        }
    }

    private suspend fun protectedIds(audioIds: Set<String>): Set<String> = retainedMetadataIds(
        userDatabase.userDataDao().protectedEntityIds().toSet(), audioIds,
        database.catalogDao().allEntities().map { it.toCatalogEntry() },
    )

    suspend fun openFrame(entityId: String, targetOrdinal: Int): ReadThroughResult {
        val entity = requireNotNull(database.catalogDao().entity(entityId)) {
            "catalog entity $entityId is missing"
        }
        return openFrame(entity.toCatalogEntry(), targetOrdinal)
    }

    suspend fun openFrame(entry: CatalogEntry, targetOrdinal: Int): ReadThroughResult {
        require(targetOrdinal in 0 until entry.pkg.blocks) { "target ordinal is outside the package" }
        val dao = database.importDao()
        val cached = dao.frames(entry.id).firstOrNull {
            targetOrdinal >= it.firstBlockOrdinal && targetOrdinal < it.firstBlockOrdinal + it.blockCount
        }
        if (cached != null) {
            val now = nowMillis()
            dao.touchFrame(entry.id, cached.frameOrdinal, now)
            dao.touchEntity(entry.id, now)
            val entity = requireNotNull(dao.entity(entry.id))
            return ReadThroughResult(
                fromCache = true,
                frameNumber = cached.frameOrdinal,
                availability = entity.availability,
                importedBlocks = dao.importedBlockCount(entry.id),
            )
        }

        // No transfer-state writes on the read-through path: every UPDATE on `entity`
        // re-runs each catalog list query and costs a commit (≈0.3 s each on a Redmi).
        try {
            val indexBytes = client.fetchPackageIndex(entry)
            val index = FramedPackageDecoder.decodeIndex(indexBytes, entry)
            val frameNumber = index.frames.indexOfFirst {
                targetOrdinal >= it.ord && targetOrdinal < it.ord + it.n
            }
            require(frameNumber >= 0) { "package index does not cover target ordinal" }
            val frame = index.frames[frameNumber]
            val bytes = client.fetchFrame(entry, frame)
            return mutationMutex.withLock {
                check(dao.entity(entry.id)?.pkgHash == entry.pkg.hash) { "catalog changed during open; retry" }
                importer.importVerifiedFrame(entry, frameNumber, frame, bytes).toReadThrough(frameNumber)
            }
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
        beforeFetch: suspend () -> Boolean = { true },
        afterFetch: suspend (ReadThroughResult) -> Unit = {},
        /** How many frames to keep ready in [direction]; fetched one at a time, nearest first. */
        count: Int = 1,
    ): ReaderPrefetchSession {
        require(direction == -1 || direction == 1)
        require(count >= 1)
        val job = scope.launch {
            try {
                val localFrames = database.importDao().frames(entityId)
                val currentLocal = localFrames.firstOrNull {
                    currentOrdinal in it.firstBlockOrdinal until (it.firstBlockOrdinal + it.blockCount)
                }
                var index: com.atharchive.core.data.content.PackageIndex? = null
                for (step in 1..count) {
                    // Asked again before every frame: storage can run low mid-prefetch.
                    if (!beforeFetch()) return@launch
                    val adjacentLocal = currentLocal?.let { current ->
                        localFrames.firstOrNull { it.frameOrdinal == current.frameOrdinal + direction * step }
                    }
                    if (adjacentLocal != null) {
                        afterFetch(openFrame(entityId, adjacentLocal.firstBlockOrdinal))
                        continue
                    }

                    val entity = database.catalogDao().entity(entityId) ?: return@launch
                    val entry = entity.toCatalogEntry()
                    val loaded = index ?: FramedPackageDecoder.decodeIndex(client.fetchPackageIndex(entry), entry)
                        .also { index = it }
                    val currentFrame = loaded.frames.indexOfFirst {
                        currentOrdinal in it.ord until (it.ord + it.n)
                    }
                    val target = loaded.frames.getOrNull(currentFrame + direction * step) ?: return@launch
                    afterFetch(openFrame(entityId, target.ord))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                // Adjacent prefetch is best-effort. A reader backed by local frames must
                // remain usable when the network is unavailable or a prefetch fails.
            }
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
        const val NO_LIMIT_BYTES = Long.MAX_VALUE

        fun requireConfiguredBudget(bytes: Long) {
            require(bytes == NO_LIMIT_BYTES || bytes in MIN_BUDGET_BYTES..MAX_BUDGET_BYTES) {
                "cache budget must be 500 MiB to 20 GiB, or no limit"
            }
        }
    }

    suspend fun usedBytes(): Long = contentDatabase.importDao().totalImportedBytes()

    suspend fun evictToBudget(
        budgetBytes: Long = DEFAULT_BUDGET_BYTES,
        activeEntityId: String? = null,
        activeFrameOrdinal: Int? = null,
        activeFrameOrdinals: Set<Int> = activeFrameOrdinal?.let { setOf(it) }.orEmpty(),
    ): Long {
        require(budgetBytes == NO_LIMIT_BYTES || budgetBytes in 0..MAX_BUDGET_BYTES)
        val dao = contentDatabase.importDao()
        var total = dao.totalImportedBytes()
        if (total <= budgetBytes) return 0
        val protected = userDatabase.userDataDao().pinnedEntityIds().toSet()
        var removed = 0L
        for (candidate in dao.evictionCandidates()) {
            if (total <= budgetBytes) break
            if (candidate.entityId in protected) continue
            if (
                candidate.entityId == activeEntityId &&
                candidate.frameOrdinal in activeFrameOrdinals
            ) continue
            val result = dao.evictFrame(candidate)
            total -= result.bytesRemoved
            removed += result.bytesRemoved
        }
        return removed
    }

    suspend fun clearUnpinned(
        activeEntityId: String? = null,
        activeFrameOrdinal: Int? = null,
        activeFrameOrdinals: Set<Int> = activeFrameOrdinal?.let { setOf(it) }.orEmpty(),
    ): Long {
        val dao = contentDatabase.importDao()
        val protected = userDatabase.userDataDao().pinnedEntityIds().toSet()
        var removed = 0L
        for (candidate in dao.evictionCandidates()) {
            if (candidate.entityId in protected) continue
            if (
                candidate.entityId == activeEntityId &&
                candidate.frameOrdinal in activeFrameOrdinals
            ) continue
            removed += dao.evictFrame(candidate).bytesRemoved
        }
        return removed
    }
}

internal fun retainedMetadataIds(userIds: Set<String>, requestedAudioIds: Set<String>, catalog: List<CatalogEntry>): Set<String> =
    userIds + catalog.filter { source ->
        source.audio.any { it.id in requestedAudioIds || "audio:${it.id}" in userIds }
    }.map { it.id }

private fun FrameImportResult.toReadThrough(frameNumber: Int): ReadThroughResult =
    ReadThroughResult(
        fromCache = false,
        frameNumber = frameNumber,
        availability = availability,
        importedBlocks = importedBlocks,
    )
