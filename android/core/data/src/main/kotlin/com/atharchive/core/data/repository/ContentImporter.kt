package com.atharchive.core.data.repository

import androidx.room3.withWriteTransaction
import com.atharchive.core.ArNormalize
import com.atharchive.core.data.content.AppContentJson
import com.atharchive.core.data.content.CatalogDocument
import com.atharchive.core.data.content.CatalogEntry
import com.atharchive.core.data.content.DecodedFrame
import com.atharchive.core.data.content.FrameIndexEntry
import com.atharchive.core.data.content.FramedPackageDecoder
import com.atharchive.core.data.content.InlineSpan
import com.atharchive.core.data.content.PackageBlock
import com.atharchive.core.data.db.content.AtharContentDatabase
import com.atharchive.core.data.db.content.ChapterEntity
import com.atharchive.core.data.db.content.ContentBlockEntity
import com.atharchive.core.data.db.content.ContentEntity
import com.atharchive.core.data.db.content.ContentGenerationEntity
import com.atharchive.core.data.db.content.ContentTransferState
import com.atharchive.core.data.db.content.EntityFrameEntity
import com.atharchive.core.data.db.content.FootnoteEntity
import com.atharchive.core.data.db.content.FrameImportResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

class ContentImporter(
    private val database: AtharContentDatabase,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    suspend fun applyCatalog(catalog: CatalogDocument) {
        val synthetic = ContentGenerationEntity(
            generationId = "legacy-${catalog.entries.hashCode()}",
            catalogHash = "0".repeat(64),
            tombstonesHash = "0".repeat(64),
            appliedAt = nowMillis(),
        )
        ContentSearchSchema.ensure(database)
        database.withWriteTransaction {
            database.catalogDao().applyGeneration(
                incoming = catalog.entries.map(CatalogEntry::toEntity),
                tombstonedIds = emptySet(),
                protectedIds = emptySet(),
                generation = synthetic,
            )
            ContentSearchSchema.replaceCatalog(this, database.catalogDao().allEntities())
        }
    }

    suspend fun applyGeneration(
        generationId: String,
        catalogHash: String,
        tombstonesHash: String,
        catalog: CatalogDocument,
        tombstonedIds: Set<String>,
        protectedIds: Set<String>,
    ) {
        ContentSearchSchema.ensure(database)
        database.withWriteTransaction {
            database.catalogDao().applyGeneration(
                incoming = catalog.entries.map(CatalogEntry::toEntity),
                tombstonedIds = tombstonedIds,
                protectedIds = protectedIds,
                generation = ContentGenerationEntity(
                    generationId = generationId,
                    catalogHash = catalogHash,
                    tombstonesHash = tombstonesHash,
                    appliedAt = nowMillis(),
                ),
            )
            ContentSearchSchema.replaceCatalog(this, database.catalogDao().allEntities())
        }
    }

    suspend fun importVerifiedFrame(
        entry: CatalogEntry,
        frameNumber: Int,
        frame: FrameIndexEntry,
        compressedBytes: ByteArray,
    ): FrameImportResult {
        val dao = database.importDao()
        ContentSearchSchema.ensure(database)
        dao.markTransfer(entry.id, ContentTransferState.VERIFYING)
        return try {
            val decoded = FramedPackageDecoder.decodeFrame(compressedBytes, frame, entry)
            dao.markTransfer(entry.id, ContentTransferState.IMPORTING)
            val now = nowMillis()
            database.withWriteTransaction {
                val result = dao.importFrame(
                    entityId = entry.id,
                    frame = EntityFrameEntity(
                        entityId = entry.id,
                        frameOrdinal = frameNumber,
                        firstBlockOrdinal = frame.ord,
                        blockCount = frame.n,
                        compressedBytes = compressedBytes.size.toLong(),
                        lastOpenedAt = now,
                    ),
                    blocks = decoded.blocks.map { it.toEntity(entry.id) },
                    chapters = decoded.chapterEntities(entry.id),
                    footnotes = decoded.footnotes.map {
                        FootnoteEntity(
                            entityId = entry.id,
                            fnId = it.id,
                            text = it.x,
                            inlineSpans = encodeSpans(it.sp),
                        )
                    },
                    now = now,
                )
                val indexed = dao.blockWindow(
                    entry.id,
                    frame.ord,
                    frame.ord + frame.n - 1,
                )
                ContentSearchSchema.indexBlocks(this, indexed)
                result
            }
        } catch (error: Throwable) {
            runCatching { dao.markTransfer(entry.id, ContentTransferState.FAILED) }
            throw error
        }
    }
}

fun CatalogEntry.toEntity(): ContentEntity = ContentEntity(
    id = id,
    coll = coll,
    v = v,
    hash = hash,
    title = title,
    titleNorm = ArNormalize.normalize(title),
    person = person,
    personName = personName,
    died = died,
    kind = kind,
    authoredYear = authoredYear,
    publishedAt = publishedAt,
    description = description,
    excerpt = excerpt,
    openingVersesJson = AppContentJson.encodeToString(openingVerses),
    topicsCsv = topics.joinToString(","),
    pkgPath = pkg.path,
    pkgHash = pkg.hash,
    pkgSize = pkg.size,
    pkgUncompressed = pkg.uncompressed,
    idxPath = pkg.idxPath,
    idxHash = pkg.idxHash,
    idxSize = pkg.idxSize,
    pkgBlocks = pkg.blocks,
    pkgChapters = pkg.chapters,
    audioJson = AppContentJson.encodeToString(audio),
)

fun ContentEntity.toCatalogEntry(): CatalogEntry = CatalogEntry(
    id = id,
    coll = coll,
    v = v,
    hash = hash,
    title = title,
    person = person,
    personName = personName,
    died = died,
    topics = topicsCsv.split(',').filter(String::isNotBlank),
    kind = kind,
    authoredYear = authoredYear,
    publishedAt = publishedAt,
    description = description,
    excerpt = excerpt,
    openingVerses = AppContentJson.decodeFromString(openingVersesJson),
    pkg = com.atharchive.core.data.content.PackageReference(
        path = pkgPath,
        hash = pkgHash,
        size = pkgSize,
        idxPath = idxPath,
        idxHash = idxHash,
        idxSize = idxSize,
        uncompressed = pkgUncompressed,
        blocks = pkgBlocks,
        chapters = pkgChapters,
    ),
    audio = AppContentJson.decodeFromString(audioJson),
)

private fun DecodedFrame.chapterEntities(entityId: String): List<ChapterEntity> =
    header?.chapters?.map {
        ChapterEntity(
            entityId = entityId,
            anchor = it.a,
            title = it.title,
            firstOrdinal = it.block,
        )
    }.orEmpty()

private fun PackageBlock.toEntity(entityId: String): ContentBlockEntity {
    val (blockIdHi, blockIdLo) = id.parseBlockId()
    val visibleText = x ?: listOfNotNull(s, j).joinToString("\n")
    return ContentBlockEntity(
        entityId = entityId,
        ordinal = i,
        blockIdHi = blockIdHi,
        blockIdLo = blockIdLo,
        fp64 = java.lang.Long.parseUnsignedLong(fp, 16),
        chapterAnchor = a,
        type = t,
        printedPage = p,
        vol = vol,
        text = visibleText,
        attrs = AppContentJson.encodeToString(
            StoredBlockAttributes(
                s = s,
                j = j,
                n = n,
                ordered = ordered,
                start = start,
                depth = depth,
                continuation = continuation,
                ha = ha,
                footnotes = f,
            ),
        ).encodeToByteArray(),
        inlineSpans = encodeSpans(sp),
    )
}

private fun String.parseBlockId(): Pair<Long, Long> =
    java.lang.Long.parseUnsignedLong(substring(0, 16), 16) to
        java.lang.Long.parseUnsignedLong(substring(16), 16)

private fun encodeSpans(spans: List<InlineSpan>): ByteArray =
    AppContentJson.encodeToString(spans).encodeToByteArray()

@Serializable
data class StoredBlockAttributes(
    val s: String? = null,
    val j: String? = null,
    val n: Int? = null,
    val ordered: Boolean? = null,
    val start: Int? = null,
    val depth: Int? = null,
    val continuation: Boolean? = null,
    val ha: String? = null,
    val footnotes: List<String> = emptyList(),
)

fun ContentBlockEntity.attributes(): StoredBlockAttributes =
    AppContentJson.decodeFromString(attrs.toString(Charsets.UTF_8))

fun ContentBlockEntity.spans(): List<InlineSpan> =
    AppContentJson.decodeFromString(inlineSpans.toString(Charsets.UTF_8))
