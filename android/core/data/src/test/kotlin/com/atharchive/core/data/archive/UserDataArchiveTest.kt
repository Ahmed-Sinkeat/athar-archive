package com.atharchive.core.data.archive

import com.atharchive.core.data.db.user.AnnotationEntity
import com.atharchive.core.data.db.user.CollectionItemEntity
import com.atharchive.core.data.db.user.LibraryEntryEntity
import com.atharchive.core.data.db.user.ReadingPositionEntity
import com.atharchive.core.data.db.user.UserCollectionEntity
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class UserDataArchiveTest {
    @Test
    fun approximateMultiBlockRangeWithCollapsedOrdinalHintsRoundTrips() = runBlocking {
        val row = annotation("two blocks").copy(
            startOrdinalHint = 80, endOrdinalHint = 80,
            startOffset = 10, endOffset = 3, state = "approximate",
        )
        val bytes = ByteArrayOutputStream()
        UserDataArchive(MemoryUserDataArchiveAccess(annotationRows = mutableListOf(row))).exportTo(bytes)
        val target = MemoryUserDataArchiveAccess()
        assertEquals(UserDataImportReport(1, 0), UserDataArchive(target).importFrom(ByteArrayInputStream(bytes.toByteArray())))
        assertEquals(row, target.annotationRows.single())
    }

    @Test
    fun reversedOffsetsInTheSameBlockAreRejected() = runBlocking {
        val row = annotation("invalid").copy(
            endBlockHi = 1, endBlockLo = 2, startOffset = 10, endOffset = 3,
        )
        val bytes = ByteArrayOutputStream()
        UserDataArchive(MemoryUserDataArchiveAccess(annotationRows = mutableListOf(row))).exportTo(bytes)
        assertRejected(bytes.toString("UTF-8"))
    }

    @Test
    fun incompleteAndUnidentifiedArchivesAreRejectedWithoutPartialRestore() = runBlocking {
        val bytes = ByteArrayOutputStream()
        UserDataArchive(MemoryUserDataArchiveAccess(
            annotationRows = mutableListOf(annotation("first"), annotation("second").copy(id = "note-2")),
        )).exportTo(bytes)
        val lines = bytes.toString("UTF-8").trimEnd().lines()
        for (bad in listOf(
            "{}", "{\"format\":\"athar-user-data\"}",
            "{\"format\":\"athar-user-data\",\"schema\":1}",
            lines.take(2).joinToString("\n", postfix = "\n"),
            lines.take(1).joinToString("\n"),
            bytes.toString("UTF-8") + lines.last() + "\n",
        )) assertRejected(bad)
    }

    @Test
    fun emptyExportIsAValidCompleteArchive() = runBlocking {
        val bytes = ByteArrayOutputStream()
        UserDataArchive(MemoryUserDataArchiveAccess()).exportTo(bytes)
        assertEquals(UserDataImportReport(0, 0), UserDataArchive(MemoryUserDataArchiveAccess())
            .importFrom(ByteArrayInputStream(bytes.toByteArray())))
    }

    private suspend fun assertRejected(archive: String) {
        val target = MemoryUserDataArchiveAccess()
        var rejected = false
        try { UserDataArchive(target).importFrom(ByteArrayInputStream(archive.toByteArray())) }
        catch (_: IllegalArgumentException) { rejected = true }
        assertEquals("invalid archive must fail", true, rejected)
        assertEquals(emptyList<AnnotationEntity>(), target.annotationRows)
    }

    @Test
    fun invalidRangesAndUnknownKindsAreRejectedBeforeTheyReachUserData() = runBlocking {
        val bytes = ByteArrayOutputStream()
        UserDataArchive(MemoryUserDataArchiveAccess(annotationRows = mutableListOf(annotation("نص")))).exportTo(bytes)
        for (bad in listOf(
            bytes.toString("UTF-8").replace("\"startOffset\":3", "\"startOffset\":-1"),
            bytes.toString("UTF-8").replace("\"kind\":\"note\"", "\"kind\":\"unknown\""),
        )) {
            val target = MemoryUserDataArchiveAccess()
            var rejected = false
            try { UserDataArchive(target).importFrom(ByteArrayInputStream(bad.toByteArray())) }
            catch (_: IllegalArgumentException) { rejected = true }
            assertEquals(true, rejected)
            assertEquals(emptyList<AnnotationEntity>(), target.annotationRows)
        }
    }

    @Test
    fun importKeepsNewerLocalEdits() = runBlocking {
        val older = annotation("نسخة قديمة")
        val source = MemoryUserDataArchiveAccess(annotationRows = mutableListOf(older))
        val bytes = ByteArrayOutputStream()
        UserDataArchive(source).exportTo(bytes)
        val newer = older.copy(quotedText = "نسخة محلية أحدث", updatedAt = 300)
        val target = MemoryUserDataArchiveAccess(annotationRows = mutableListOf(newer))

        val report = UserDataArchive(target).importFrom(ByteArrayInputStream(bytes.toByteArray()))

        assertEquals(UserDataImportReport(imported = 0, skipped = 1), report)
        assertEquals(newer, target.annotationRows.single())
    }

    @Test
    fun roundTripStreamsRangesReadingStateShelvesAndCollectionsWithoutNormalizingText() = runBlocking {
        val exactText = "ا\u0654ثرٌ محفوظ 😀"
        val source = MemoryUserDataArchiveAccess(
            annotationRows = mutableListOf(annotation(exactText)),
            positionRows = mutableListOf(position()),
            libraryRows = mutableListOf(libraryEntry()),
            collectionRows = mutableListOf(collection()),
            itemRows = mutableListOf(collectionItem()),
        )
        val bytes = ByteArrayOutputStream()

        UserDataArchive(source).exportTo(bytes)

        val target = MemoryUserDataArchiveAccess()
        val report = UserDataArchive(target).importFrom(ByteArrayInputStream(bytes.toByteArray()))
        assertEquals(UserDataImportReport(imported = 5, skipped = 0), report)
        assertEquals(source.annotationRows, target.annotationRows)
        assertEquals(exactText, target.annotationRows.single().quotedText)
        assertEquals(source.positionRows, target.positionRows)
        assertEquals(source.libraryRows, target.libraryRows)
        assertEquals(source.collectionRows, target.collectionRows)
        assertEquals(source.itemRows, target.itemRows)
        assertEquals(UserDataImportReport(0, 5), UserDataArchive(target).importFrom(ByteArrayInputStream(bytes.toByteArray())))
    }

    private fun annotation(text: String) = AnnotationEntity(
        id = "note-1",
        entityId = "book-1",
        kind = "note",
        startBlockHi = 1,
        startBlockLo = 2,
        startOffset = 3,
        endBlockHi = 4,
        endBlockLo = 5,
        endOffset = 6,
        startOrdinalHint = 7,
        endOrdinalHint = 8,
        startFp64 = 9,
        endFp64 = 10,
        quotedText = text,
        prefixContext = "قبل",
        suffixContext = "بعد",
        chapterAnchor = "opening",
        printedPage = 11,
        colorId = 2,
        noteText = "تعليق",
        state = "exact",
        createdAt = 100,
        updatedAt = 200,
    )

    private fun position() = ReadingPositionEntity(
        entityId = "book-1",
        chapterAnchor = "opening",
        blockIdHi = 1,
        blockIdLo = 2,
        ordinalHint = 7,
        offsetInBlock = 3,
        progressPct = 0.25,
        updatedAt = 200,
    )

    private fun libraryEntry() = LibraryEntryEntity("book-1", "reading", addedAt = 100, updatedAt = 200)
    private fun collection() = UserCollectionEntity("collection-1", "فوائد", createdAt = 100, updatedAt = 200)
    private fun collectionItem() = CollectionItemEntity("collection-1", "book-1", addedAt = 200)
}

private class MemoryUserDataArchiveAccess(
    val annotationRows: MutableList<AnnotationEntity> = mutableListOf(),
    val positionRows: MutableList<ReadingPositionEntity> = mutableListOf(),
    val libraryRows: MutableList<LibraryEntryEntity> = mutableListOf(),
    val collectionRows: MutableList<UserCollectionEntity> = mutableListOf(),
    val itemRows: MutableList<CollectionItemEntity> = mutableListOf(),
) : UserDataArchiveAccess {
    override suspend fun <T> transaction(block: suspend () -> T): T {
        val annotations = annotationRows.toList()
        val positions = positionRows.toList()
        val entries = libraryRows.toList()
        val collections = collectionRows.toList()
        val items = itemRows.toList()
        try { return block() } catch (failure: Throwable) {
            annotationRows.clear(); annotationRows.addAll(annotations)
            positionRows.clear(); positionRows.addAll(positions)
            libraryRows.clear(); libraryRows.addAll(entries)
            collectionRows.clear(); collectionRows.addAll(collections)
            itemRows.clear(); itemRows.addAll(items)
            throw failure
        }
    }

    override suspend fun annotations(): List<AnnotationEntity> = annotationRows
    override suspend fun readingPositions(): List<ReadingPositionEntity> = positionRows
    override suspend fun libraryEntries(): List<LibraryEntryEntity> = libraryRows
    override suspend fun collections(): List<UserCollectionEntity> = collectionRows
    override suspend fun collectionItems(): List<CollectionItemEntity> = itemRows

    override suspend fun merge(annotation: AnnotationEntity): Boolean = mergeBy(
        annotationRows,
        annotation,
        key = AnnotationEntity::id,
        updatedAt = AnnotationEntity::updatedAt,
    )

    override suspend fun merge(position: ReadingPositionEntity): Boolean = mergeBy(
        positionRows,
        position,
        key = ReadingPositionEntity::entityId,
        updatedAt = ReadingPositionEntity::updatedAt,
    )

    override suspend fun merge(entry: LibraryEntryEntity): Boolean = mergeBy(
        libraryRows,
        entry,
        key = LibraryEntryEntity::entityId,
        updatedAt = LibraryEntryEntity::updatedAt,
    )

    override suspend fun merge(collection: UserCollectionEntity): Boolean = mergeBy(
        collectionRows,
        collection,
        key = UserCollectionEntity::id,
        updatedAt = UserCollectionEntity::updatedAt,
    )

    override suspend fun merge(item: CollectionItemEntity): Boolean {
        if (itemRows.any { it.collectionId == item.collectionId && it.entityId == item.entityId }) return false
        itemRows += item
        return true
    }

    private fun <T, K> mergeBy(
        rows: MutableList<T>,
        incoming: T,
        key: (T) -> K,
        updatedAt: (T) -> Long,
    ): Boolean {
        val index = rows.indexOfFirst { key(it) == key(incoming) }
        if (index >= 0 && updatedAt(rows[index]) >= updatedAt(incoming)) return false
        if (index >= 0) rows[index] = incoming else rows += incoming
        return true
    }
}
