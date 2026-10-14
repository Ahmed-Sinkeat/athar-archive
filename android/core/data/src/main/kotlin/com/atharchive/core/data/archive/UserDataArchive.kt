package com.atharchive.core.data.archive

import androidx.room3.withWriteTransaction
import com.atharchive.core.data.db.user.LibraryStatus
import com.atharchive.core.data.db.user.AnnotationEntity
import com.atharchive.core.data.db.user.AtharUserDatabase
import com.atharchive.core.data.db.user.CollectionItemEntity
import com.atharchive.core.data.db.user.LibraryEntryEntity
import com.atharchive.core.data.db.user.ReadingPositionEntity
import com.atharchive.core.data.db.user.UserCollectionEntity
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

data class UserDataImportReport(val imported: Int, val skipped: Int)

internal interface UserDataArchiveAccess {
    suspend fun <T> transaction(block: suspend () -> T): T = block()
    suspend fun annotations(): List<AnnotationEntity>
    suspend fun readingPositions(): List<ReadingPositionEntity>
    suspend fun libraryEntries(): List<LibraryEntryEntity>
    suspend fun collections(): List<UserCollectionEntity>
    suspend fun collectionItems(): List<CollectionItemEntity>

    suspend fun merge(annotation: AnnotationEntity): Boolean
    suspend fun merge(position: ReadingPositionEntity): Boolean
    suspend fun merge(entry: LibraryEntryEntity): Boolean
    suspend fun merge(collection: UserCollectionEntity): Boolean
    suspend fun merge(item: CollectionItemEntity): Boolean
}

class UserDataArchive internal constructor(
    private val access: UserDataArchiveAccess,
    private val json: Json = ArchiveJson,
) {
    constructor(database: AtharUserDatabase) : this(RoomUserDataArchiveAccess(database))

    suspend fun exportTo(output: OutputStream) = access.transaction {
        val writer = output.writer(StandardCharsets.UTF_8).buffered()
        val annotations = access.annotations()
        val positions = access.readingPositions()
        val entries = access.libraryEntries()
        val collections = access.collections()
        val items = access.collectionItems()
        val recordCount = annotations.size.toLong() + positions.size + entries.size + collections.size + items.size
        require(recordCount <= MAX_ARCHIVE_RECORDS) { "archive has too many records" }
        writer.appendLine(json.encodeToString(ArchiveHeader(ARCHIVE_FORMAT, ARCHIVE_SCHEMA, recordCount.toInt())))
        annotations.forEach { writer.writeRecord(RECORD_ANNOTATION, json.encodeToJsonElement(it)) }
        positions.forEach { writer.writeRecord(RECORD_READING_POSITION, json.encodeToJsonElement(it)) }
        entries.forEach { writer.writeRecord(RECORD_LIBRARY_ENTRY, json.encodeToJsonElement(it)) }
        collections.forEach { writer.writeRecord(RECORD_COLLECTION, json.encodeToJsonElement(it)) }
        items.forEach { writer.writeRecord(RECORD_COLLECTION_ITEM, json.encodeToJsonElement(it)) }
        writer.flush()
    }

    suspend fun importFrom(input: InputStream): UserDataImportReport = access.transaction {
        val reader = input.bufferedReader(StandardCharsets.UTF_8)
        var characters = 0L
        val lines = sequence {
            while (true) {
                val line = StringBuilder()
                var end = false
                while (true) {
                    val next = reader.read()
                    if (next == -1) { end = true; break }
                    require(++characters <= MAX_ARCHIVE_CHARS) { "archive is too large" }
                    if (next == 10) break
                    require(line.length < MAX_ARCHIVE_LINE_CHARS) { "archive record is too large" }
                    line.append(next.toChar())
                }
                if (end && line.isEmpty()) break
                yield(line.toString())
                if (end) break
            }
        }.iterator()
        require(lines.hasNext()) { "empty Athar user-data archive" }
        val headerLine = lines.next()
        require(headerLine.length <= MAX_ARCHIVE_LINE_CHARS) { "archive header is too large" }
        val header = json.decodeFromString<ArchiveHeader>(headerLine)
        require(header.format == ARCHIVE_FORMAT) { "unsupported archive format ${header.format}" }
        require(header.schema == ARCHIVE_SCHEMA) {
            "unsupported archive schema ${header.schema}; re-export user data with the updated app"
        }
        require(header.recordCount in 0..MAX_ARCHIVE_RECORDS) { "invalid archive record count" }

        var imported = 0
        var skipped = 0
        while (lines.hasNext()) {
            val line = lines.next()
            if (line.isBlank()) continue
            require(line.length <= MAX_ARCHIVE_LINE_CHARS) { "archive record is too large" }
            require(imported + skipped < header.recordCount) { "archive contains more records than declared" }
            val record = json.decodeFromString<ArchiveRecord>(line)
            val merged = when (record.type) {
                RECORD_ANNOTATION -> access.merge(json.decodeFromJsonElement<AnnotationEntity>(record.value).also {
                    require(it.id.isNotBlank() && it.entityId.isNotBlank()) { "annotation identity is missing" }
                    require(it.kind in setOf("bookmark", "highlight", "note")) { "unsupported annotation kind" }
                    require(it.state in setOf("exact", "approximate", "orphaned")) { "unsupported attachment state" }
                    require(it.startOrdinalHint >= 0 && it.endOrdinalHint >= it.startOrdinalHint &&
                        it.startOffset >= 0 && it.endOffset >= 0 &&
                        (it.startBlockHi != it.endBlockHi || it.startBlockLo != it.endBlockLo ||
                            it.endOffset >= it.startOffset)) { "invalid annotation range" }
                    require(it.createdAt >= 0 && it.updatedAt >= 0 && it.colorId in 0..7) { "invalid annotation metadata" }
                })
                RECORD_READING_POSITION -> access.merge(json.decodeFromJsonElement<ReadingPositionEntity>(record.value).also {
                    require(it.entityId.isNotBlank() && it.ordinalHint >= 0 && it.offsetInBlock >= 0 &&
                        it.progressPct.isFinite() && it.progressPct in 0.0..1.0 && it.updatedAt >= 0) { "invalid reading position" }
                })
                RECORD_LIBRARY_ENTRY -> access.merge(json.decodeFromJsonElement<LibraryEntryEntity>(record.value).also {
                    require(it.entityId.isNotBlank() && it.status in LibraryStatus.all &&
                        it.addedAt >= 0 && it.updatedAt >= 0) { "invalid library entry" }
                })
                RECORD_COLLECTION -> access.merge(json.decodeFromJsonElement<UserCollectionEntity>(record.value).also {
                    require(it.id.isNotBlank() && it.title.isNotBlank() &&
                        it.createdAt >= 0 && it.updatedAt >= 0) { "invalid collection" }
                })
                RECORD_COLLECTION_ITEM -> access.merge(json.decodeFromJsonElement<CollectionItemEntity>(record.value).also {
                    require(it.collectionId.isNotBlank() && it.entityId.isNotBlank() && it.addedAt >= 0) { "invalid membership" }
                })
                else -> error("unsupported archive record ${record.type}")
            }
            if (merged) imported++ else skipped++
        }
        require(imported + skipped == header.recordCount) { "incomplete archive: records are missing" }
        UserDataImportReport(imported, skipped)
    }

    private fun java.io.BufferedWriter.writeRecord(type: String, value: JsonElement) {
        appendLine(json.encodeToString(ArchiveRecord(type, value)))
    }
}

@Serializable
private data class ArchiveHeader(
    val format: String,
    val schema: Int,
    val recordCount: Int,
)

@Serializable
private data class ArchiveRecord(val type: String, val value: JsonElement)

private class RoomUserDataArchiveAccess(private val database: AtharUserDatabase) : UserDataArchiveAccess {
    private val dao = database.userDataDao()

    override suspend fun <T> transaction(block: suspend () -> T): T =
        database.withWriteTransaction { block() }

    override suspend fun annotations(): List<AnnotationEntity> = dao.annotations()
    override suspend fun readingPositions(): List<ReadingPositionEntity> = dao.readingPositions()
    override suspend fun libraryEntries(): List<LibraryEntryEntity> = dao.libraryEntries()
    override suspend fun collections(): List<UserCollectionEntity> = dao.userCollections()
    override suspend fun collectionItems(): List<CollectionItemEntity> = dao.collectionItems()

    override suspend fun merge(annotation: AnnotationEntity): Boolean {
        val local = dao.annotation(annotation.id)
        if (local != null && local.updatedAt >= annotation.updatedAt) return false
        dao.saveAnnotation(annotation)
        return true
    }

    override suspend fun merge(position: ReadingPositionEntity): Boolean {
        val local = dao.readingPosition(position.entityId)
        if (local != null && local.updatedAt >= position.updatedAt) return false
        dao.saveReadingPosition(position)
        return true
    }

    override suspend fun merge(entry: LibraryEntryEntity): Boolean {
        val local = dao.libraryEntry(entry.entityId)
        if (local != null && local.updatedAt >= entry.updatedAt) return false
        dao.saveLibraryEntry(entry)
        return true
    }

    override suspend fun merge(collection: UserCollectionEntity): Boolean {
        val local = dao.userCollection(collection.id)
        if (local != null && local.updatedAt >= collection.updatedAt) return false
        dao.saveUserCollection(collection)
        return true
    }

    override suspend fun merge(item: CollectionItemEntity): Boolean = dao.addCollectionItem(item) != -1L
}

private val ArchiveJson = Json {
    encodeDefaults = true
    explicitNulls = true
}

private const val ARCHIVE_FORMAT = "athar-user-data"
private const val ARCHIVE_SCHEMA = 2
private const val RECORD_ANNOTATION = "annotation"
private const val RECORD_READING_POSITION = "readingPosition"
private const val RECORD_LIBRARY_ENTRY = "libraryEntry"
private const val RECORD_COLLECTION = "collection"
private const val RECORD_COLLECTION_ITEM = "collectionItem"
private const val MAX_ARCHIVE_LINE_CHARS = 2 * 1024 * 1024
private const val MAX_ARCHIVE_RECORDS = 1_000_000

private const val MAX_ARCHIVE_CHARS = 256L * 1024 * 1024
