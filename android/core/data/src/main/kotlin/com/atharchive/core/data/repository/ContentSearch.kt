package com.atharchive.core.data.repository

import androidx.room3.PooledConnection
import androidx.room3.RoomDatabase
import androidx.room3.useReaderConnection
import androidx.room3.useWriterConnection
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.execSQL
import com.atharchive.core.ArNormalize
import com.atharchive.core.CompactFtsPlan
import com.atharchive.core.FtsQueryBuilder
import com.atharchive.core.data.db.content.AtharContentDatabase
import com.atharchive.core.data.db.content.ContentAvailability
import com.atharchive.core.data.db.content.ContentBlockEntity
import com.atharchive.core.data.db.content.ContentEntity
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

private const val SearchSchemaVersion = 1L
private const val CandidateBatchSize = 80

/** Raw DDL selected by M0/R2b. Room deliberately does not model these virtual tables. */
object ContentSearchSchema {
    val callback = object : RoomDatabase.Callback() {
        override suspend fun onCreate(connection: SQLiteConnection) {
            create(connection)
            writeVersion(connection)
        }

        override suspend fun onDestructiveMigration(connection: SQLiteConnection) {
            reset(connection)
            create(connection)
            writeVersion(connection)
        }

        override suspend fun onOpen(connection: SQLiteConnection) {
            createMetadata(connection)
            val version = connection.prepare(
                "SELECT value FROM athar_search_meta WHERE key = 'schema'",
            ).use { query -> if (query.step()) query.getLong(0) else null }
            if (version != SearchSchemaVersion) {
                reset(connection)
                create(connection)
                rebuild(connection)
                writeVersion(connection)
            } else {
                create(connection)
            }
        }
    }

    suspend fun ensure(database: AtharContentDatabase) {
        database.useWriterConnection { create(it) }
    }

    suspend fun replaceCatalog(
        connection: PooledConnection,
        entities: List<ContentEntity>,
    ) {
        connection.execute("DELETE FROM catalog_fts")
        connection.usePrepared(
            "INSERT INTO catalog_fts(titleNorm, personNorm, topicsNorm, id) VALUES (?, ?, ?, ?)",
        ) { insert ->
            entities.asSequence().filter(ContentEntity::catalogPresent).forEach { entity ->
                insert.bindText(1, entity.titleNorm)
                insert.bindText(2, ArNormalize.normalize(entity.personName.orEmpty()))
                insert.bindText(3, ArNormalize.normalize(entity.topicsCsv.replace(',', ' ')))
                insert.bindText(4, entity.id)
                insert.step()
                insert.reset()
                insert.clearBindings()
            }
        }
    }

    suspend fun indexBlocks(
        connection: PooledConnection,
        blocks: List<ContentBlockEntity>,
    ) {
        connection.usePrepared("INSERT INTO block_fts(rowid, norm) VALUES (?, ?)") { insert ->
            blocks.forEach { block ->
                insert.bindLong(1, block.rowid)
                insert.bindText(2, ArNormalize.normalize(block.text))
                insert.step()
                insert.reset()
                insert.clearBindings()
            }
        }
    }

    private suspend fun create(connection: PooledConnection) {
        connection.execute(
            "CREATE VIRTUAL TABLE IF NOT EXISTS block_fts USING fts5(" +
                "norm, tokenize='unicode61', content='', contentless_delete=1, detail=none)",
        )
        connection.execute(
            "CREATE VIRTUAL TABLE IF NOT EXISTS catalog_fts USING fts5(" +
                "titleNorm, personNorm, topicsNorm, id UNINDEXED, tokenize='unicode61')",
        )
        connection.execute(
            "CREATE TRIGGER IF NOT EXISTS block_fts_before_delete BEFORE DELETE ON block BEGIN " +
                "DELETE FROM block_fts WHERE rowid = OLD.rowid; END",
        )
    }

    private fun create(connection: SQLiteConnection) {
        createMetadata(connection)
        connection.execSQL(
            "CREATE VIRTUAL TABLE IF NOT EXISTS block_fts USING fts5(" +
                "norm, tokenize='unicode61', content='', contentless_delete=1, detail=none)",
        )
        connection.execSQL(
            "CREATE VIRTUAL TABLE IF NOT EXISTS catalog_fts USING fts5(" +
                "titleNorm, personNorm, topicsNorm, id UNINDEXED, tokenize='unicode61')",
        )
        connection.execSQL(
            "CREATE TRIGGER IF NOT EXISTS block_fts_before_delete BEFORE DELETE ON block BEGIN " +
                "DELETE FROM block_fts WHERE rowid = OLD.rowid; END",
        )
    }

    private fun createMetadata(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS athar_search_meta(" +
                "key TEXT PRIMARY KEY NOT NULL, value INTEGER NOT NULL)",
        )
    }

    private fun reset(connection: SQLiteConnection) {
        connection.execSQL("DROP TRIGGER IF EXISTS block_fts_before_delete")
        connection.execSQL("DROP TABLE IF EXISTS block_fts")
        connection.execSQL("DROP TABLE IF EXISTS catalog_fts")
        connection.execSQL("DROP TABLE IF EXISTS athar_search_meta")
    }

    private fun rebuild(connection: SQLiteConnection) {
        connection.prepare("SELECT rowid, text FROM block ORDER BY rowid").use { blocks ->
            connection.prepare("INSERT INTO block_fts(rowid, norm) VALUES (?, ?)").use { insert ->
                while (blocks.step()) {
                    insert.bindLong(1, blocks.getLong(0))
                    insert.bindText(2, ArNormalize.normalize(blocks.getText(1)))
                    insert.step()
                    insert.reset()
                    insert.clearBindings()
                }
            }
        }
        connection.prepare(
            "SELECT id, titleNorm, personName, topicsCsv FROM entity WHERE catalogPresent = 1 ORDER BY id",
        ).use { entities ->
            connection.prepare(
                "INSERT INTO catalog_fts(titleNorm, personNorm, topicsNorm, id) VALUES (?, ?, ?, ?)",
            ).use { insert ->
                while (entities.step()) {
                    insert.bindText(1, entities.getText(1))
                    insert.bindText(2, if (entities.isNull(2)) "" else ArNormalize.normalize(entities.getText(2)))
                    insert.bindText(3, ArNormalize.normalize(entities.getText(3).replace(',', ' ')))
                    insert.bindText(4, entities.getText(0))
                    insert.step()
                    insert.reset()
                    insert.clearBindings()
                }
            }
        }
    }

    private fun writeVersion(connection: SQLiteConnection) {
        connection.prepare(
            "INSERT OR REPLACE INTO athar_search_meta(key, value) VALUES ('schema', ?)",
        ).use { statement ->
            statement.bindLong(1, SearchSchemaVersion)
            statement.step()
        }
    }
}

enum class ContentSearchField { FullText, Title, Author, Topic }
enum class ContentSearchSort { Relevance, Newest }

data class ContentSearchFilter(
    val collections: Set<String> = emptySet(),
    /** null means unrestricted; empty means a resolved personal collection with no items. */
    val entityIds: Set<String>? = null,
    val authors: Set<String> = emptySet(),
    val topics: Set<String> = emptySet(),
    val kinds: Set<String> = emptySet(),
    val fullyCachedOnly: Boolean = false,
    val diedFrom: Int? = null,
    val diedTo: Int? = null,
)

data class ContentSearchRequest(
    val query: String,
    val field: ContentSearchField = ContentSearchField.FullText,
    val sort: ContentSearchSort = ContentSearchSort.Relevance,
    val filter: ContentSearchFilter = ContentSearchFilter(),
)

data class CatalogSearchHit(
    val entityId: String,
    val title: String,
    val personName: String,
    val collection: String,
    val kind: String,
)

data class BlockSearchHit(
    val rowid: Long,
    val entityId: String,
    val ordinal: Int,
    val chapterAnchor: String,
    val chapterTitle: String,
    val printedPage: Int?,
    val blockType: String,
    val excerpt: String,
    val matchStart: Int,
    val matchEnd: Int,
    val sourceMatchStart: Int,
    val sourceMatchEnd: Int,
    val sourceTitle: String,
    val sourceAuthor: String,
    val collection: String,
)

data class ContentSearchResult(
    val catalog: List<CatalogSearchHit>,
    val blocks: List<BlockSearchHit>,
)

class ContentSearchRepository(private val database: AtharContentDatabase) {
    suspend fun search(request: ContentSearchRequest, limit: Int = 50): ContentSearchResult {
        require(limit in 1..200)
        val plan = FtsQueryBuilder.compactPlan(request.query) ?: return ContentSearchResult(emptyList(), emptyList())
        ContentSearchSchema.ensure(database)
        return database.useReaderConnection { connection ->
            val catalog = searchCatalog(connection, request, plan, limit.coerceAtMost(12))
            val blocks = if (request.field == ContentSearchField.FullText) {
                searchBlocks(connection, request, plan, limit)
            } else {
                emptyList()
            }
            ContentSearchResult(catalog, blocks)
        }
    }

    private suspend fun searchCatalog(
        connection: PooledConnection,
        request: ContentSearchRequest,
        plan: CompactFtsPlan,
        limit: Int,
    ): List<CatalogSearchHit> {
        val filter = structuredFilter(request.filter, "e")
        val scopedMatch = when (request.field) {
            ContentSearchField.FullText -> plan.matchExpression
            ContentSearchField.Title -> "titleNorm : (${plan.matchExpression})"
            ContentSearchField.Author -> "personNorm : (${plan.matchExpression})"
            ContentSearchField.Topic -> "topicsNorm : (${plan.matchExpression})"
        }
        val order = if (request.sort == ContentSearchSort.Newest) {
            "e.publishedAt DESC, e.authoredYear DESC, e.id"
        } else {
            "bm25(catalog_fts, 0.25, 0.6, 1.0), e.id"
        }
        val sql = "SELECT e.id, e.title, e.personName, e.coll, e.kind " +
            "FROM catalog_fts JOIN entity e ON e.id = catalog_fts.id " +
            "WHERE catalog_fts MATCH ? AND e.catalogPresent = 1${filter.sql} " +
            "ORDER BY $order LIMIT ?"
        return connection.usePrepared(sql) { query ->
            var bind = 1
            query.bindText(bind++, scopedMatch)
            bind = query.bindAll(bind, filter.values)
            query.bindLong(bind, limit.toLong())
            buildList {
                while (query.step()) {
                    add(
                        CatalogSearchHit(
                            entityId = query.getText(0),
                            title = query.getText(1),
                            personName = query.nullableText(2),
                            collection = query.getText(3),
                            kind = query.nullableText(4),
                        ),
                    )
                }
            }
        }
    }

    private suspend fun searchBlocks(
        connection: PooledConnection,
        request: ContentSearchRequest,
        plan: CompactFtsPlan,
        limit: Int,
    ): List<BlockSearchHit> {
        val filter = structuredFilter(request.filter, "e")
        val order = if (request.sort == ContentSearchSort.Newest) {
            "e.publishedAt DESC, e.authoredYear DESC, b.rowid"
        } else {
            "bm25(block_fts), b.rowid"
        }
        val sql = "SELECT b.rowid, b.entityId, b.ordinal, b.chapterAnchor, c.title, b.printedPage, " +
            "b.type, b.text, e.title, e.personName, e.coll " +
            "FROM block_fts JOIN block b ON b.rowid = block_fts.rowid " +
            "JOIN entity e ON e.id = b.entityId " +
            "LEFT JOIN chapter c ON c.entityId = b.entityId AND c.anchor = b.chapterAnchor " +
            "WHERE block_fts MATCH ? AND e.catalogPresent = 1${filter.sql} " +
            "ORDER BY $order LIMIT ? OFFSET ?"
        val result = mutableListOf<BlockSearchHit>()
        var offset = 0
        while (result.size < limit) {
            currentCoroutineContext().ensureActive()
            var candidates = 0
            connection.usePrepared(sql) { query ->
                var bind = 1
                query.bindText(bind++, plan.matchExpression)
                bind = query.bindAll(bind, filter.values)
                query.bindLong(bind++, CandidateBatchSize.toLong())
                query.bindLong(bind, offset.toLong())
                while (query.step()) {
                    candidates++
                    query.toBlockHit(plan)?.let(result::add)
                    if (result.size == limit) break
                }
            }
            if (candidates < CandidateBatchSize || result.size == limit) break
            offset += CandidateBatchSize
        }
        return result
    }
}

private data class SqlFilter(val sql: String, val values: List<Any>)

private fun structuredFilter(filter: ContentSearchFilter, alias: String): SqlFilter {
    val clauses = mutableListOf<String>()
    val values = mutableListOf<Any>()
    fun exactSet(column: String, selected: Set<String>) {
        if (selected.isEmpty()) return
        clauses += "$alias.$column IN (${selected.joinToString { "?" }})"
        values.addAll(selected.sorted())
    }
    exactSet("coll", filter.collections)
    exactSet("personName", filter.authors)
    exactSet("kind", filter.kinds)
    filter.entityIds?.let { ids ->
        if (ids.isEmpty()) {
            clauses += "0"
        } else {
            clauses += "$alias.id IN (${ids.joinToString { "?" }})"
            values.addAll(ids.sorted())
        }
    }
    if (filter.topics.isNotEmpty()) {
        clauses += filter.topics.joinToString(prefix = "(", postfix = ")", separator = " OR ") {
            "(',' || $alias.topicsCsv || ',') LIKE ('%,' || ? || ',%')"
        }
        values.addAll(filter.topics.sorted())
    }
    if (filter.fullyCachedOnly) {
        clauses += "$alias.availability = ?"
        values += ContentAvailability.COMPLETE
    }
    filter.diedFrom?.let {
        clauses += "$alias.died >= ?"
        values += it
    }
    filter.diedTo?.let {
        clauses += "$alias.died <= ?"
        values += it
    }
    return SqlFilter(
        sql = clauses.joinToString(prefix = if (clauses.isEmpty()) "" else " AND ", separator = " AND "),
        values = values,
    )
}

private fun SQLiteStatement.toBlockHit(plan: CompactFtsPlan): BlockSearchHit? {
    val source = getText(7)
    val mapped = ArNormalize.normalizeWithMap(source)
    if (plan.exactPhrases.any { !mapped.text.contains(it) }) return null
    val terms = if (plan.exactPhrases.isNotEmpty()) plan.exactPhrases else plan.highlightTerms
    val match = terms.firstNotNullOfOrNull { term ->
        mapped.text.indexOf(term).takeIf { it >= 0 }?.let { at -> at until at + term.length }
    } ?: return null
    val sourceRange = mapped.sourceRange(match.first, match.last + 1)
    val rawStart = (sourceRange.first - 72).coerceAtLeast(0)
    val rawEnd = (sourceRange.last + 1 + 96).coerceAtMost(source.length)
    val excerptStart = source.safeUtf16Start(rawStart)
    val excerptEnd = source.safeUtf16End(rawEnd)
    return BlockSearchHit(
        rowid = getLong(0),
        entityId = getText(1),
        ordinal = getInt(2),
        chapterAnchor = getText(3),
        chapterTitle = nullableText(4),
        printedPage = if (isNull(5)) null else getInt(5),
        blockType = getText(6),
        excerpt = source.substring(excerptStart, excerptEnd),
        matchStart = sourceRange.first - excerptStart,
        matchEnd = sourceRange.last + 1 - excerptStart,
        sourceMatchStart = sourceRange.first,
        sourceMatchEnd = sourceRange.last + 1,
        sourceTitle = getText(8),
        sourceAuthor = nullableText(9),
        collection = getText(10),
    )
}

private suspend fun PooledConnection.execute(sql: String) {
    usePrepared(sql) { it.step() }
}

private fun SQLiteStatement.bindAll(first: Int, values: List<Any>): Int {
    var index = first
    values.forEach { value ->
        when (value) {
            is String -> bindText(index, value)
            is Int -> bindLong(index, value.toLong())
            is Long -> bindLong(index, value)
            else -> error("unsupported search bind ${value::class}")
        }
        index++
    }
    return index
}

private fun SQLiteStatement.nullableText(index: Int): String = if (isNull(index)) "" else getText(index)

private fun String.safeUtf16Start(index: Int): Int =
    if (index in 1 until length && this[index].isLowSurrogate()) index - 1 else index

private fun String.safeUtf16End(index: Int): Int =
    if (index in 1 until length && this[index - 1].isHighSurrogate()) index + 1 else index
