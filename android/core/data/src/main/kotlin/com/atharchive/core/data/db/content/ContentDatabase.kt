package com.atharchive.core.data.db.content

import androidx.paging.PagingSource
import androidx.room3.Dao
import androidx.room3.DaoReturnTypeConverters
import androidx.room3.Database
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.RoomDatabase
import androidx.room3.Transaction
import androidx.room3.paging.PagingSourceDaoReturnTypeConverter
import kotlinx.coroutines.flow.Flow

object ContentAvailability {
    const val ABSENT = "absent"
    const val PARTIAL = "partial"
    const val COMPLETE = "complete"
}

object ContentTransferState {
    const val IDLE = "idle"
    const val FETCHING = "fetching"
    const val VERIFYING = "verifying"
    const val IMPORTING = "importing"
    const val FAILED = "failed"
}

@Entity(
    tableName = "entity",
    indices = [
        Index(value = ["coll"]),
        Index(value = ["person"]),
        Index(value = ["availability"]),
        Index(value = ["transferState"]),
        Index(value = ["lastOpenedAt"]),
        Index(value = ["catalogPresent"]),
    ],
)
data class ContentEntity(
    @PrimaryKey val id: String,
    val coll: String,
    val v: Int,
    val hash: String,
    val title: String,
    val titleNorm: String,
    val person: String?,
    val personName: String?,
    val died: Int?,
    val kind: String?,
    val authoredYear: Int?,
    val publishedAt: String?,
    val description: String?,
    val excerpt: String?,
    val openingVersesJson: String,
    val topicsCsv: String,
    val pkgPath: String,
    val pkgHash: String,
    val pkgSize: Long,
    val pkgUncompressed: Long,
    val idxPath: String,
    val idxHash: String,
    val idxSize: Long,
    val pkgBlocks: Int,
    val pkgChapters: Int,
    /** Serialized [com.atharchive.core.data.content.AudioReference] list. */
    val audioJson: String,
    /** False for a protected local work removed by a signed tombstone. */
    val catalogPresent: Boolean = true,
    val availability: String = ContentAvailability.ABSENT,
    val transferState: String = ContentTransferState.IDLE,
    val updateAvailable: Boolean = false,
    val localVersion: Int? = null,
    val localPackageHash: String? = null,
    val lastOpenedAt: Long? = null,
    val bytesOnDisk: Long = 0,
    val importProgress: Int = 0,
)

@Entity(tableName = "contentGeneration")
data class ContentGenerationEntity(
    @PrimaryKey val singleton: Int = 0,
    val generationId: String,
    val catalogHash: String,
    val tombstonesHash: String,
    val appliedAt: Long,
)

@Entity(
    tableName = "entityFrame",
    primaryKeys = ["entityId", "frameOrdinal"],
    indices = [Index(value = ["lastOpenedAt"])],
)
data class EntityFrameEntity(
    val entityId: String,
    val frameOrdinal: Int,
    val firstBlockOrdinal: Int,
    val blockCount: Int,
    val compressedBytes: Long,
    val lastOpenedAt: Long,
)

@Entity(
    tableName = "block",
    indices = [
        Index(value = ["entityId", "ordinal"], unique = true),
        Index(value = ["blockIdHi", "blockIdLo"]),
        Index(value = ["entityId", "chapterAnchor"]),
        Index(value = ["fp64"]),
    ],
)
data class ContentBlockEntity(
    @PrimaryKey(autoGenerate = true) val rowid: Long = 0,
    val entityId: String,
    val ordinal: Int,
    val blockIdHi: Long,
    val blockIdLo: Long,
    val fp64: Long,
    val chapterAnchor: String,
    val type: String,
    val printedPage: Int?,
    val vol: Int?,
    val text: String,
    val attrs: ByteArray,
    val inlineSpans: ByteArray,
)

@Entity(
    tableName = "chapter",
    primaryKeys = ["entityId", "anchor"],
)
data class ChapterEntity(
    val entityId: String,
    val anchor: String,
    val title: String,
    val firstOrdinal: Int,
)

@Entity(
    tableName = "footnote",
    primaryKeys = ["entityId", "fnId"],
)
data class FootnoteEntity(
    val entityId: String,
    val fnId: String,
    val text: String,
    val inlineSpans: ByteArray,
)

data class FrameImportResult(
    val availability: String,
    val importedBlocks: Int,
    val bytesOnDisk: Long,
)

data class CacheFrameCandidate(
    val entityId: String,
    val frameOrdinal: Int,
    val firstBlockOrdinal: Int,
    val blockCount: Int,
    val compressedBytes: Long,
    val lastOpenedAt: Long,
)

data class CacheEvictionResult(
    val entityId: String,
    val frameOrdinal: Int,
    val bytesRemoved: Long,
)

@Dao
abstract class ContentCatalogDao {
    @Query("SELECT * FROM entity")
    abstract suspend fun allEntities(): List<ContentEntity>

    @Query("SELECT * FROM entity WHERE catalogPresent = 1 ORDER BY titleNorm, id")
    abstract fun observeCatalog(): Flow<List<ContentEntity>>

    @Query("SELECT * FROM entity WHERE coll = :coll AND catalogPresent = 1 ORDER BY titleNorm, id")
    abstract fun observeCollection(coll: String): Flow<List<ContentEntity>>

    @Query("SELECT * FROM entity WHERE id = :entityId")
    abstract suspend fun entity(entityId: String): ContentEntity?

    @Query("SELECT * FROM entity WHERE id = :entityId")
    abstract fun observeEntity(entityId: String): Flow<ContentEntity?>

    @Query("SELECT * FROM contentGeneration WHERE singleton = 0")
    abstract suspend fun generation(): ContentGenerationEntity?

    @Query("SELECT * FROM contentGeneration WHERE singleton = 0")
    abstract fun observeGeneration(): Flow<ContentGenerationEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun replaceEntities(entities: List<ContentEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun replaceGeneration(generation: ContentGenerationEntity)

    @Query("DELETE FROM block WHERE entityId = :entityId")
    protected abstract suspend fun deleteBlocks(entityId: String)

    @Query("DELETE FROM entityFrame WHERE entityId = :entityId")
    protected abstract suspend fun deleteFrames(entityId: String)

    @Query("DELETE FROM chapter WHERE entityId = :entityId")
    protected abstract suspend fun deleteChapters(entityId: String)

    @Query("DELETE FROM footnote WHERE entityId = :entityId")
    protected abstract suspend fun deleteFootnotes(entityId: String)

    @Query("DELETE FROM entity WHERE id = :entityId")
    protected abstract suspend fun deleteEntity(entityId: String)

    @Query("UPDATE entity SET catalogPresent = 0, transferState = 'idle' WHERE id = :entityId")
    protected abstract suspend fun retainTombstonedEntity(entityId: String)

    /**
     * Applies one authenticated generation atomically inside the disposable content DB.
     * Protected IDs are snapshotted from the user DB before this transaction; a caller
     * performs one reconciliation pass afterwards to close cross-database races.
     */
    @Transaction
    open suspend fun applyGeneration(
        incoming: List<ContentEntity>,
        tombstonedIds: Set<String>,
        protectedIds: Set<String>,
        generation: ContentGenerationEntity,
    ) {
        val current = allEntities().associateBy(ContentEntity::id)
        val merged = incoming.map { fresh ->
            current[fresh.id]?.let { local ->
                val contentChanged = local.localVersion != null && local.localPackageHash != fresh.pkgHash
                fresh.copy(
                    catalogPresent = true,
                    availability = local.availability,
                    transferState = ContentTransferState.IDLE,
                    updateAvailable = contentChanged,
                    localVersion = local.localVersion,
                    localPackageHash = local.localPackageHash,
                    lastOpenedAt = local.lastOpenedAt,
                    bytesOnDisk = local.bytesOnDisk,
                    importProgress = local.importProgress,
                )
            } ?: fresh
        }
        if (merged.isNotEmpty()) replaceEntities(merged)

        for (entityId in tombstonedIds) {
            // Mark first, then let the repository re-snapshot the user DB before purge.
            // This closes the cross-database race without pretending both files share a
            // transaction. The protected snapshot argument documents the ordering and is
            // also useful to callers auditing the apply operation.
            if (current.containsKey(entityId)) retainTombstonedEntity(entityId)
        }
        replaceGeneration(generation)
    }

    @Transaction
    open suspend fun purgeUnprotectedTombstones(protectedIds: Set<String>) {
        allEntities()
            .asSequence()
            .filter { !it.catalogPresent && it.id !in protectedIds }
            .forEach { removeEntity(it.id) }
    }

    @Transaction
    open suspend fun removeEntity(entityId: String) {
        deleteBlocks(entityId)
        deleteFrames(entityId)
        deleteChapters(entityId)
        deleteFootnotes(entityId)
        deleteEntity(entityId)
    }
}

@Dao
@DaoReturnTypeConverters(PagingSourceDaoReturnTypeConverter::class)
abstract class ContentImportDao {
    @Query("SELECT * FROM entity WHERE id = :entityId")
    abstract suspend fun entity(entityId: String): ContentEntity?

    @Query("UPDATE entity SET transferState = :state WHERE id = :entityId")
    abstract suspend fun markTransfer(entityId: String, state: String)

    @Query("DELETE FROM block WHERE entityId = :entityId AND ordinal BETWEEN :first AND :last")
    protected abstract suspend fun deleteBlockRange(entityId: String, first: Int, last: Int)

    @Insert
    protected abstract suspend fun insertBlocks(blocks: List<ContentBlockEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun replaceFrame(frame: EntityFrameEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun replaceChapters(chapters: List<ChapterEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun replaceFootnotes(footnotes: List<FootnoteEntity>)

    @Query("SELECT COUNT(*) FROM block WHERE entityId = :entityId")
    abstract suspend fun importedBlockCount(entityId: String): Int

    @Query("SELECT COALESCE(SUM(compressedBytes), 0) FROM entityFrame WHERE entityId = :entityId")
    abstract suspend fun importedBytes(entityId: String): Long

    @Query("SELECT COALESCE(SUM(compressedBytes), 0) FROM entityFrame")
    abstract suspend fun totalImportedBytes(): Long

    @Query(
        "UPDATE entity SET availability = :availability, transferState = 'idle', " +
            "localVersion = v, localPackageHash = pkgHash, updateAvailable = 0, lastOpenedAt = :now, " +
            "bytesOnDisk = :bytesOnDisk, importProgress = :importProgress WHERE id = :entityId",
    )
    protected abstract suspend fun finishImport(
        entityId: String,
        availability: String,
        bytesOnDisk: Long,
        importProgress: Int,
        now: Long,
    )

    @Query("SELECT * FROM block WHERE entityId = :entityId ORDER BY ordinal")
    abstract suspend fun blocks(entityId: String): List<ContentBlockEntity>

    @Query("SELECT * FROM block WHERE entityId = :entityId ORDER BY ordinal")
    abstract fun observeBlocks(entityId: String): Flow<List<ContentBlockEntity>>

    @Query("SELECT * FROM block WHERE entityId = :entityId ORDER BY ordinal")
    abstract fun pagingSource(entityId: String): PagingSource<Int, ContentBlockEntity>

    @Query("SELECT * FROM block WHERE entityId = :entityId AND ordinal BETWEEN :first AND :last ORDER BY ordinal")
    abstract suspend fun blockWindow(entityId: String, first: Int, last: Int): List<ContentBlockEntity>

    @Query("SELECT * FROM block WHERE entityId = :entityId AND ordinal = :ordinal LIMIT 1")
    abstract suspend fun blockAt(entityId: String, ordinal: Int): ContentBlockEntity?

    @Query("SELECT * FROM chapter WHERE entityId = :entityId ORDER BY firstOrdinal")
    abstract fun observeChapters(entityId: String): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM footnote WHERE entityId = :entityId ORDER BY fnId")
    abstract fun observeFootnotes(entityId: String): Flow<List<FootnoteEntity>>

    @Query("SELECT * FROM entityFrame WHERE entityId = :entityId ORDER BY frameOrdinal")
    abstract suspend fun frames(entityId: String): List<EntityFrameEntity>

    @Query("UPDATE entityFrame SET lastOpenedAt = :now WHERE entityId = :entityId AND frameOrdinal = :frameOrdinal")
    abstract suspend fun touchFrame(entityId: String, frameOrdinal: Int, now: Long)

    @Query("UPDATE entity SET lastOpenedAt = :now WHERE id = :entityId")
    abstract suspend fun touchEntity(entityId: String, now: Long)

    @Query(
        "SELECT entityId, frameOrdinal, firstBlockOrdinal, blockCount, compressedBytes, lastOpenedAt " +
            "FROM entityFrame ORDER BY lastOpenedAt ASC, entityId, frameOrdinal",
    )
    abstract suspend fun evictionCandidates(): List<CacheFrameCandidate>

    @Query("DELETE FROM entityFrame WHERE entityId = :entityId AND frameOrdinal = :frameOrdinal")
    protected abstract suspend fun deleteFrame(entityId: String, frameOrdinal: Int)

    @Query("DELETE FROM chapter WHERE entityId = :entityId")
    protected abstract suspend fun deleteEntityChapters(entityId: String)

    @Query("DELETE FROM footnote WHERE entityId = :entityId")
    protected abstract suspend fun deleteEntityFootnotes(entityId: String)

    @Query(
        "UPDATE entity SET availability = :availability, bytesOnDisk = :bytesOnDisk, " +
            "importProgress = :importProgress WHERE id = :entityId",
    )
    protected abstract suspend fun updateAfterEviction(
        entityId: String,
        availability: String,
        bytesOnDisk: Long,
        importProgress: Int,
    )

    @Transaction
    open suspend fun importFrame(
        entityId: String,
        frame: EntityFrameEntity,
        blocks: List<ContentBlockEntity>,
        chapters: List<ChapterEntity>,
        footnotes: List<FootnoteEntity>,
        now: Long,
    ): FrameImportResult {
        val target = requireNotNull(entity(entityId)) { "catalog entity $entityId is missing" }
        require(blocks.size == frame.blockCount) { "decoded block count differs from frame" }
        require(blocks.zipWithNext().all { (a, b) -> a.ordinal < b.ordinal }) {
            "decoded block ordinals must be strictly increasing"
        }
        deleteBlockRange(entityId, frame.firstBlockOrdinal, frame.firstBlockOrdinal + frame.blockCount - 1)
        insertBlocks(blocks)
        replaceFrame(frame)
        if (chapters.isNotEmpty()) replaceChapters(chapters)
        if (footnotes.isNotEmpty()) replaceFootnotes(footnotes)

        val importedBlocks = importedBlockCount(entityId)
        val bytesOnDisk = importedBytes(entityId)
        val availability = if (importedBlocks == target.pkgBlocks) {
            ContentAvailability.COMPLETE
        } else {
            ContentAvailability.PARTIAL
        }
        require(importedBlocks <= target.pkgBlocks) { "imported frames exceed catalog block count" }
        val progress = contiguousProgress(frames(entityId))
        finishImport(entityId, availability, bytesOnDisk, progress, now)
        return FrameImportResult(availability, importedBlocks, bytesOnDisk)
    }

    @Transaction
    open suspend fun evictFrame(candidate: CacheFrameCandidate): CacheEvictionResult {
        deleteBlockRange(
            candidate.entityId,
            candidate.firstBlockOrdinal,
            candidate.firstBlockOrdinal + candidate.blockCount - 1,
        )
        deleteFrame(candidate.entityId, candidate.frameOrdinal)
        val remaining = frames(candidate.entityId)
        val bytes = remaining.sumOf(EntityFrameEntity::compressedBytes)
        if (remaining.isEmpty()) {
            deleteEntityChapters(candidate.entityId)
            deleteEntityFootnotes(candidate.entityId)
        }
        updateAfterEviction(
            entityId = candidate.entityId,
            availability = if (remaining.isEmpty()) ContentAvailability.ABSENT else ContentAvailability.PARTIAL,
            bytesOnDisk = bytes,
            importProgress = contiguousProgress(remaining),
        )
        return CacheEvictionResult(candidate.entityId, candidate.frameOrdinal, candidate.compressedBytes)
    }

    @Transaction
    open suspend fun clearImportedContent(entityId: String) {
        val candidates = frames(entityId).map {
            CacheFrameCandidate(
                entityId = it.entityId,
                frameOrdinal = it.frameOrdinal,
                firstBlockOrdinal = it.firstBlockOrdinal,
                blockCount = it.blockCount,
                compressedBytes = it.compressedBytes,
                lastOpenedAt = it.lastOpenedAt,
            )
        }
        for (candidate in candidates) evictFrame(candidate)
    }
}

private fun contiguousProgress(frames: List<EntityFrameEntity>): Int {
    var progress = 0
    for (frame in frames.sortedBy(EntityFrameEntity::firstBlockOrdinal)) {
        if (frame.firstBlockOrdinal != progress) break
        progress += frame.blockCount
    }
    return progress
}

@Database(
    entities = [
        ContentEntity::class,
        ContentGenerationEntity::class,
        EntityFrameEntity::class,
        ContentBlockEntity::class,
        ChapterEntity::class,
        FootnoteEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AtharContentDatabase : RoomDatabase() {
    abstract fun catalogDao(): ContentCatalogDao
    abstract fun importDao(): ContentImportDao
}
