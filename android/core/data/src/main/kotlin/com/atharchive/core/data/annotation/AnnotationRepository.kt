package com.atharchive.core.data.annotation

import androidx.room3.withWriteTransaction
import com.atharchive.core.data.db.content.AtharContentDatabase
import com.atharchive.core.data.db.user.AnnotationEntity
import com.atharchive.core.data.db.user.AtharUserDatabase
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class AnnotationKind {
    Bookmark,
    Highlight,
    Note,
}

enum class AttachmentState {
    Exact,
    Approximate,
    Orphaned,
}

data class Annotation(
    val id: String,
    val entityId: String,
    val kind: AnnotationKind,
    val start: AnnotationEndpoint,
    val end: AnnotationEndpoint,
    val startFingerprint: Long,
    val endFingerprint: Long,
    val quotedText: String,
    val prefixContext: String,
    val suffixContext: String,
    val chapterAnchor: String,
    val printedPage: Int?,
    val colorId: Int,
    val noteText: String?,
    val state: AttachmentState,
    val createdAt: Long,
    val updatedAt: Long,
)

data class AnnotationFilter(
    val entityId: String? = null,
    val kind: AnnotationKind? = null,
    val state: AttachmentState? = null,
)

internal interface AnnotationStore {
    fun observe(): Flow<List<Annotation>>
    suspend fun find(id: String): Annotation?
    suspend fun upsert(annotation: Annotation)
    suspend fun delete(id: String)
    suspend fun updateIfUnchanged(expected: Annotation, recovered: Annotation): Annotation?
}

internal fun interface AnnotationContentSource {
    suspend fun blocks(entityId: String): List<AnnotationTextBlock>
    suspend fun candidates(entityId: String, saved: List<Annotation>): List<AnnotationTextBlock> = blocks(entityId)
    suspend fun isComplete(entityId: String): Boolean = true
}

class AnnotationRepository internal constructor(
    private val store: AnnotationStore,
    private val content: AnnotationContentSource = AnnotationContentSource { emptyList() },
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    constructor(
        userDatabase: AtharUserDatabase,
        contentDatabase: AtharContentDatabase,
        nowMillis: () -> Long = System::currentTimeMillis,
        newId: () -> String = { UUID.randomUUID().toString() },
    ) : this(
        store = RoomAnnotationStore(userDatabase),
        content = RoomAnnotationContentSource(contentDatabase),
        nowMillis = nowMillis,
        newId = newId,
    )

    fun observe(filter: AnnotationFilter = AnnotationFilter()): Flow<List<Annotation>> =
        store.observe().map { annotations ->
            annotations
                .asSequence()
                .filter { filter.entityId == null || it.entityId == filter.entityId }
                .filter { filter.kind == null || it.kind == filter.kind }
                .filter { filter.state == null || it.state == filter.state }
                .sortedByDescending(Annotation::updatedAt)
                .toList()
        }

    suspend fun save(
        entityId: String,
        kind: AnnotationKind,
        capture: AnnotationCapture,
        colorId: Int = 0,
        noteText: String? = null,
    ): Annotation {
        require(entityId.isNotBlank())
        val now = nowMillis()
        return Annotation(
            id = newId(),
            entityId = entityId,
            kind = kind,
            start = capture.start,
            end = capture.end,
            startFingerprint = capture.startFingerprint,
            endFingerprint = capture.endFingerprint,
            quotedText = capture.quotedText,
            prefixContext = capture.prefixContext,
            suffixContext = capture.suffixContext,
            chapterAnchor = capture.chapterAnchor,
            printedPage = capture.printedPage,
            colorId = colorId,
            noteText = noteText?.trim()?.ifEmpty { null },
            state = AttachmentState.Exact,
            createdAt = now,
            updatedAt = now,
        ).also { store.upsert(it) }
    }

    suspend fun edit(
        id: String,
        noteText: String?,
        colorId: Int,
        kind: AnnotationKind? = null,
    ): Annotation {
        val existing = requireNotNull(store.find(id)) { "annotation $id does not exist" }
        return existing.copy(
            noteText = noteText?.trim()?.ifEmpty { null },
            colorId = colorId,
            kind = kind ?: existing.kind,
            updatedAt = nowMillis(),
        ).also { store.upsert(it) }
    }

    suspend fun delete(id: String) {
        store.delete(id)
    }

    suspend fun reattach(id: String): Annotation {
        val existing = requireNotNull(store.find(id)) { "annotation $id does not exist" }
        val blocks = content.candidates(existing.entityId, listOf(existing))
        return requireNotNull(store.updateIfUnchanged(
            existing, reattach(existing, blocks, content.isComplete(existing.entityId)),
        )) { "annotation $id was deleted during recovery" }
    }

    suspend fun reattachEntity(entityId: String): List<Annotation> {
        val saved = store.observe().first().filter { it.entityId == entityId }
        if (saved.isEmpty()) return emptyList()
        val blocks = content.candidates(entityId, saved)
        if (blocks.isEmpty()) return saved
        val complete = content.isComplete(entityId)
        return saved.mapNotNull { existing ->
            store.updateIfUnchanged(existing, reattach(existing, blocks, complete))
        }
    }

    private fun reattach(existing: Annotation, blocks: List<AnnotationTextBlock>, complete: Boolean): Annotation {
        val startBlock = blocks.find { it.id == existing.start.blockId }
        val endBlock = blocks.find { it.id == existing.end.blockId }
        val exactText = if (startBlock != null && endBlock != null) runCatching {
            AnnotationText.capture(blocks, existing.start, existing.end).quotedText
        }.getOrNull() else null
        return if (startBlock != null && endBlock != null && startBlock.ordinal <= endBlock.ordinal &&
            (existing.kind == AnnotationKind.Bookmark || exactText == existing.quotedText)) {
            existing.copy(
                start = existing.start.copy(ordinalHint = startBlock.ordinal),
                end = existing.end.copy(ordinalHint = endBlock.ordinal),
                state = AttachmentState.Exact,
            )
        } else {
            val recoveredStart = recoverEndpoint(
                blocks = blocks,
                fingerprint = existing.startFingerprint,
                ordinalHint = existing.start.ordinalHint,
                context = existing.prefixContext,
                selectedText = existing.quotedText.substringBefore("\n\n"),
                contextBeforeSelection = true,
            )
            val recoveredEnd = recoverEndpoint(
                blocks = blocks,
                fingerprint = existing.endFingerprint,
                ordinalHint = existing.end.ordinalHint,
                context = existing.suffixContext,
                selectedText = existing.quotedText.substringAfterLast("\n\n"),
                contextBeforeSelection = false,
            )
            if (recoveredStart != null && recoveredEnd != null && recoveredStart.first.ordinal <= recoveredEnd.first.ordinal &&
                (existing.kind == AnnotationKind.Bookmark || runCatching {
                    AnnotationText.capture(blocks,
                        AnnotationEndpoint(recoveredStart.first.id, recoveredStart.first.ordinal, recoveredStart.second),
                        AnnotationEndpoint(recoveredEnd.first.id, recoveredEnd.first.ordinal, recoveredEnd.second)).quotedText
                }.getOrNull() == existing.quotedText)) {
                existing.copy(
                    start = AnnotationEndpoint(recoveredStart.first.id, recoveredStart.first.ordinal, recoveredStart.second),
                    end = AnnotationEndpoint(recoveredEnd.first.id, recoveredEnd.first.ordinal, recoveredEnd.second),
                    state = AttachmentState.Exact,
                )
            } else {
                if (!complete) return existing.copy(state = AttachmentState.Approximate)
                val pageBlock = existing.printedPage?.let { page ->
                    blocks.find { it.chapterAnchor == existing.chapterAnchor && it.printedPage == page }
                }
                if (pageBlock != null) {
                    existing.copy(
                        // Keep the original IDs and offsets so a later update can recover the quote.
                        start = existing.start.copy(ordinalHint = pageBlock.ordinal),
                        end = existing.end.copy(ordinalHint = pageBlock.ordinal),
                        state = AttachmentState.Approximate,
                    )
                } else {
                    existing.copy(state = AttachmentState.Orphaned)
                }
            }
        }
    }
}

private fun recoverEndpoint(
    blocks: List<AnnotationTextBlock>,
    fingerprint: Long,
    ordinalHint: Int,
    context: String,
    selectedText: String,
    contextBeforeSelection: Boolean,
): Pair<AnnotationTextBlock, Int>? =
    blocks.asSequence()
        .filter { it.fingerprint == fingerprint && kotlin.math.abs(it.ordinal - ordinalHint) <= REATTACH_RADIUS }
        .sortedBy { kotlin.math.abs(it.ordinal - ordinalHint) }
        .mapNotNull { block ->
            val text = block.semanticText ?: return@mapNotNull null
            val storedSelection = when (block.copyStyle) {
                AnnotationCopyStyle.Prose -> selectedText
                AnnotationCopyStyle.Verse -> selectedText.replace(" … ", "\n")
            }
            val needle = if (contextBeforeSelection) context + storedSelection else storedSelection + context
            val fullMatch = text.indexOf(needle).takeIf { it >= 0 }
            val contextMatch = text.indexOf(context).takeIf { context.isNotEmpty() && it >= 0 }
            val offset = when {
                fullMatch != null && contextBeforeSelection -> fullMatch + context.length
                fullMatch != null -> fullMatch + storedSelection.length
                contextMatch != null && contextBeforeSelection -> contextMatch + context.length
                contextMatch != null -> contextMatch
                else -> return@mapNotNull null
            }
            block to offset
        }
        .firstOrNull()

private const val REATTACH_RADIUS = 50

private class RoomAnnotationStore(private val database: AtharUserDatabase) : AnnotationStore {
    private val dao = database.userDataDao()

    override fun observe(): Flow<List<Annotation>> =
        dao.observeAnnotations().map { annotations -> annotations.map(AnnotationEntity::toDomain) }

    override suspend fun find(id: String): Annotation? = dao.annotation(id)?.toDomain()

    override suspend fun upsert(annotation: Annotation) {
        dao.saveAnnotation(annotation.toEntity())
    }

    override suspend fun delete(id: String) {
        dao.deleteAnnotation(id)
    }

    override suspend fun updateIfUnchanged(expected: Annotation, recovered: Annotation): Annotation? =
        database.withWriteTransaction {
            val current = find(expected.id)
            // Compare the full snapshot: edits can share a millisecond timestamp.
            if (current == expected) recovered.also { upsert(it) } else current
        }
}

private class RoomAnnotationContentSource(database: AtharContentDatabase) : AnnotationContentSource {
    private val dao = database.importDao()

    override suspend fun isComplete(entityId: String): Boolean =
        dao.entity(entityId)?.availability == com.atharchive.core.data.db.content.ContentAvailability.COMPLETE

    override suspend fun candidates(entityId: String, saved: List<Annotation>): List<AnnotationTextBlock> {
        val candidates = mutableListOf<com.atharchive.core.data.db.content.ContentBlockEntity>()
        for (annotation in saved) {
            for (endpoint in listOf(annotation.start, annotation.end)) {
                dao.blockByStableId(entityId, endpoint.blockId.hi, endpoint.blockId.lo)?.let(candidates::add)
                candidates += dao.blockWindow(entityId, (endpoint.ordinalHint - 50).coerceAtLeast(0),
                    (endpoint.ordinalHint.toLong() + 50).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            }
            annotation.printedPage?.let { page ->
                dao.printedPageOrdinal(entityId, page, null)?.let { ordinal -> dao.blockAt(entityId, ordinal)?.let(candidates::add) }
            }
        }
        return candidates.distinctBy { it.ordinal }.sortedBy { it.ordinal }.map { it.annotationBlock() }
    }

    override suspend fun blocks(entityId: String): List<AnnotationTextBlock> =
        dao.blocks(entityId).map { it.annotationBlock() }

    private fun com.atharchive.core.data.db.content.ContentBlockEntity.annotationBlock() = AnnotationTextBlock(
        id = AnnotationBlockId(blockIdHi, blockIdLo), ordinal = ordinal,
        semanticText = text.takeUnless { type == "page" || type == "break" },
        fingerprint = fp64, chapterAnchor = chapterAnchor, printedPage = printedPage,
        copyStyle = if (type == "verse") AnnotationCopyStyle.Verse else AnnotationCopyStyle.Prose,
    )
}

private fun AnnotationEntity.toDomain(): Annotation = Annotation(
    id = id,
    entityId = entityId,
    kind = kind.toAnnotationKind(),
    start = AnnotationEndpoint(AnnotationBlockId(startBlockHi, startBlockLo), startOrdinalHint, startOffset),
    end = AnnotationEndpoint(AnnotationBlockId(endBlockHi, endBlockLo), endOrdinalHint, endOffset),
    startFingerprint = startFp64,
    endFingerprint = endFp64,
    quotedText = quotedText,
    prefixContext = prefixContext,
    suffixContext = suffixContext,
    chapterAnchor = chapterAnchor,
    printedPage = printedPage,
    colorId = colorId,
    noteText = noteText,
    state = state.toAttachmentState(),
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun Annotation.toEntity(): AnnotationEntity = AnnotationEntity(
    id = id,
    entityId = entityId,
    kind = kind.storageValue,
    startBlockHi = start.blockId.hi,
    startBlockLo = start.blockId.lo,
    startOffset = start.offset,
    endBlockHi = end.blockId.hi,
    endBlockLo = end.blockId.lo,
    endOffset = end.offset,
    startOrdinalHint = start.ordinalHint,
    endOrdinalHint = end.ordinalHint,
    startFp64 = startFingerprint,
    endFp64 = endFingerprint,
    quotedText = quotedText,
    prefixContext = prefixContext,
    suffixContext = suffixContext,
    chapterAnchor = chapterAnchor,
    printedPage = printedPage,
    colorId = colorId,
    noteText = noteText,
    state = state.storageValue,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private val AnnotationKind.storageValue: String
    get() = name.lowercase()

private val AttachmentState.storageValue: String
    get() = name.lowercase()

private fun String.toAnnotationKind(): AnnotationKind = when (this) {
    "bookmark" -> AnnotationKind.Bookmark
    "highlight" -> AnnotationKind.Highlight
    "note" -> AnnotationKind.Note
    else -> error("unsupported annotation kind $this")
}

private fun String.toAttachmentState(): AttachmentState = when (this) {
    "exact" -> AttachmentState.Exact
    "approximate" -> AttachmentState.Approximate
    "orphaned" -> AttachmentState.Orphaned
    else -> error("unsupported attachment state $this")
}
