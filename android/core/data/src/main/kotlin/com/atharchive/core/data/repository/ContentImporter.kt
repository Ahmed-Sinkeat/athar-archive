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
            personsJson = AppContentJson.encodeToString(catalog.persons),
        )
        apply(catalog, emptySet(), synthetic)
    }

    suspend fun applyGeneration(
        generationId: String,
        catalogHash: String,
        tombstonesHash: String,
        catalog: CatalogDocument,
        tombstonedIds: Set<String>,
        protectedIds: Set<String>,
    ) {
        apply(
            catalog,
            tombstonedIds,
            ContentGenerationEntity(
                generationId = generationId,
                catalogHash = catalogHash,
                tombstonesHash = tombstonesHash,
                appliedAt = nowMillis(),
                personsJson = AppContentJson.encodeToString(catalog.persons),
            ),
        )
    }

    // Catalog rows commit slice by slice; only the search index and the generation row (written
    // last, so an interrupted apply runs again) share one small transaction.
    private suspend fun apply(catalog: CatalogDocument, tombstonedIds: Set<String>, generation: ContentGenerationEntity) {
        ContentSearchSchema.ensure(database)
        val dao = database.catalogDao()
        dao.applyCatalogSlices(catalog.entries.map(CatalogEntry::toEntity))
        database.withWriteTransaction {
            dao.finishGeneration(tombstonedIds, generation)
            ContentSearchSchema.replaceCatalog(this, dao.allEntities())
        }
    }

    /** Imports a verified frame; with [into] set to a staging id the frame is written beside the readable copy. */
    suspend fun importVerifiedFrame(
        entry: CatalogEntry,
        frameNumber: Int,
        frame: FrameIndexEntry,
        compressedBytes: ByteArray,
        into: String = entry.id,
    ): FrameImportResult {
        val dao = database.importDao()
        ContentSearchSchema.ensure(database)
        return try {
            val decoded = FramedPackageDecoder.decodeFrame(compressedBytes, frame, entry)
            val now = nowMillis()
            val entities = decoded.blocks.map { it.toEntity(into) }
            val frameEntity = EntityFrameEntity(
                entityId = into,
                frameOrdinal = frameNumber,
                firstBlockOrdinal = frame.ord,
                blockCount = frame.n,
                compressedBytes = compressedBytes.size.toLong(),
                lastOpenedAt = now,
            )
            val chapters = decoded.chapterEntities(into)
            val footnotes = decoded.footnotes.map {
                FootnoteEntity(entityId = into, fnId = it.id, text = it.x, inlineSpans = encodeSpans(it.sp), marker = it.n)
            }
            database.withWriteTransaction {
                val result = if (into != entry.id) {
                    dao.stageFrame(frameEntity, entities, chapters, footnotes)
                } else {
                    val local = dao.entity(entry.id)
                    if (local?.localPackageHash != null && local.localPackageHash != entry.pkg.hash) {
                        dao.clearImportedContent(entry.id)
                    }
                    dao.importFrame(entry.id, frameEntity, entities, chapters, footnotes, now)
                }
                ContentSearchSchema.indexBlocks(this, dao.blockWindow(into, frame.ord, frame.ord + frame.n - 1))
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
    editionOf = editionOf,
    edition = edition,
    editionsJson = AppContentJson.encodeToString(editions),
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
    verseCount = verseCount,
    pageFrom = pkg.pages?.from,
    pageTo = pkg.pages?.to,
    volumes = pkg.pages?.vols,
    f0Json = pkg.f0?.let { AppContentJson.encodeToString(it) },
    cardJson = card.takeIf { it.isNotEmpty() }?.let { AppContentJson.encodeToString(it) },
)

fun ContentEntity.toCatalogEntry(): CatalogEntry = CatalogEntry(
    id = id,
    coll = coll,
    v = v,
    hash = hash,
    title = title,
    editionOf = editionOf,
    edition = edition,
    editions = editionsJson?.let { AppContentJson.decodeFromString<List<String>>(it) }.orEmpty(),
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
    verseCount = verseCount,
    card = cardJson?.let { AppContentJson.decodeFromString(it) } ?: emptyList(),
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
        pages = if (pageFrom != null && pageTo != null && volumes != null) com.atharchive.core.data.content.PageRange(pageFrom, pageTo, volumes) else null,
        f0 = f0Json?.let { AppContentJson.decodeFromString(it) },
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
            level = it.lv,
            page = it.p,
            vol = it.vol,
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
                commentary = commentary,
                ordered = ordered,
                start = start,
                depth = depth,
                continuation = continuation,
                quoted = quoted,
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
    val commentary: List<com.atharchive.core.data.content.VerseCommentary> = emptyList(),
    val ordered: Boolean? = null,
    val start: Int? = null,
    val depth: Int? = null,
    val continuation: Boolean? = null,
    val quoted: Boolean? = null,
    val ha: String? = null,
    val footnotes: List<String> = emptyList(),
)

fun ContentBlockEntity.attributes(): StoredBlockAttributes =
    AppContentJson.decodeFromString(attrs.toString(Charsets.UTF_8))

fun ContentBlockEntity.spans(): List<InlineSpan> =
    AppContentJson.decodeFromString(inlineSpans.toString(Charsets.UTF_8))

fun FootnoteEntity.spans(): List<InlineSpan> =
    AppContentJson.decodeFromString(inlineSpans.toString(Charsets.UTF_8))

fun ContentGenerationEntity.persons(): List<com.atharchive.core.data.content.CatalogPerson> =
    personsJson?.let { AppContentJson.decodeFromString<List<com.atharchive.core.data.content.CatalogPerson>>(it) }.orEmpty()
