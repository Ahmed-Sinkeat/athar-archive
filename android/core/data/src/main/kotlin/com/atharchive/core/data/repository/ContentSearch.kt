package com.atharchive.core.data.repository

import androidx.room3.PooledConnection
import androidx.room3.RoomDatabase
import androidx.room3.useReaderConnection
import androidx.room3.useWriterConnection
import androidx.room3.withWriteTransaction
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.execSQL
import com.atharchive.core.ArNormalize
import com.atharchive.core.CompactFtsPlan
import com.atharchive.core.FtsQueryBuilder
import com.atharchive.core.data.db.content.AtharContentDatabase
import com.atharchive.core.data.db.content.BlockText
import com.atharchive.core.data.db.content.ContentAvailability
import com.atharchive.core.data.db.content.ContentBlockEntity
import com.atharchive.core.data.db.content.ContentEntity
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

private const val SearchSchemaVersion = 1L
private const val CandidateBatchSize = 80
private const val RankCap = 20_000
private const val PositionsChunk = 2_000
private const val InsertCatalog = "INSERT INTO catalog_fts(titleNorm, personNorm, topicsNorm, id) VALUES (?, ?, ?, ?)"
private const val BlockFtsDdl = "norm, tokenize='unicode61', content='', contentless_delete=1, detail=full"

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
                indexMissingCatalog(connection)
                startPositions(connection)
            }
        }
    }

    /**
     * Whether `block_fts` keeps word positions (detail=full): phrase and NEAR then run in SQL and
     * search ranks with the site's tiers. Installs from before 8.11 have a detail=none index and
     * gain one through [addPositions], in the background; until it swaps in they search as before.
     */
    @Volatile var positions = false
        private set
    @Volatile private var copying = false

    /** An old index: make the new one beside it; [addPositions] fills it up to today's last block. */
    private fun startPositions(connection: SQLiteConnection) {
        val meta = meta(connection)
        if (meta["positions"] == 1L) { positions = true; return }
        connection.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS block_fts_full USING fts5($BlockFtsDdl)")
        connection.execSQL(deleteTrigger("block_fts_full"))
        // Rowids only grow (autoGenerate), so blocks imported from now on are above this mark and
        // [indexBlocks] writes them to both indexes; the copy walks the ones below it.
        if (meta["positionsTo"] == null) {
            val last = connection.prepare("SELECT coalesce(max(rowid), 0) FROM block").use { it.step(); it.getLong(0) }
            setMeta(connection, "positionsTo", last)
            setMeta(connection, "positionsAt", 0)
        }
        copying = true
    }

    /**
     * Copies the old index's blocks into the one with positions, [PositionsChunk] per write
     * transaction so downloads and catalog updates interleave, then swaps it in. Resumes where a
     * killed process stopped. ~2 s of a writer per 10k blocks on a desktop; a whole library is minutes.
     */
    suspend fun addPositions(database: AtharContentDatabase) {
        ensure(database) // opens the database, which decides whether there is anything to copy
        if (!copying) return
        while (true) {
            currentCoroutineContext().ensureActive()
            if (database.withWriteTransaction { copyPositions(this) }) break
        }
        copying = false
        positions = true
        android.util.Log.i("AtharContent", "search index now keeps word positions")
    }

    /** One chunk; true once the copy is done and swapped in. */
    private suspend fun copyPositions(connection: PooledConnection): Boolean {
        val meta = connection.usePrepared("SELECT key, value FROM athar_search_meta") { q ->
            buildMap { while (q.step()) put(q.getText(0), q.getLong(1)) }
        }
        val at = meta["positionsAt"] ?: 0
        val to = meta["positionsTo"] ?: 0
        // Read first, then write: no insert into an FTS table while a select over block is open.
        val rows = connection.usePrepared("SELECT rowid, text, packed FROM block WHERE rowid > ? AND rowid <= ? ORDER BY rowid LIMIT ?") { q ->
            q.bindLong(1, at); q.bindLong(2, to); q.bindLong(3, PositionsChunk.toLong())
            buildList { while (q.step()) add(q.getLong(0) to ArNormalize.normalize(q.blockText(1))) }
        }
        if (rows.isNotEmpty()) {
            connection.usePrepared("INSERT INTO block_fts_full(rowid, norm) VALUES (?, ?)") { insert ->
                rows.forEach { (rowid, norm) ->
                    insert.bindLong(1, rowid)
                    insert.bindText(2, norm)
                    insert.step()
                    insert.reset()
                    insert.clearBindings()
                }
            }
            connection.usePrepared("INSERT OR REPLACE INTO athar_search_meta(key, value) VALUES ('positionsAt', ?)") {
                it.bindLong(1, rows.last().first); it.step()
            }
            return false
        }
        listOf(
            "DROP TRIGGER IF EXISTS block_fts_before_delete",
            "DROP TRIGGER IF EXISTS block_fts_full_before_delete",
            "DROP TABLE IF EXISTS block_fts",
            "ALTER TABLE block_fts_full RENAME TO block_fts",
            deleteTrigger("block_fts"),
            "DELETE FROM athar_search_meta WHERE key IN ('positionsAt', 'positionsTo')",
            "INSERT OR REPLACE INTO athar_search_meta(key, value) VALUES ('positions', 1)",
        ).forEach { connection.execute(it) }
        return true
    }

    private fun meta(connection: SQLiteConnection): Map<String, Long> =
        connection.prepare("SELECT key, value FROM athar_search_meta").use { q ->
            buildMap { while (q.step()) put(q.getText(0), q.getLong(1)) }
        }

    private fun setMeta(connection: SQLiteConnection, key: String, value: Long) {
        connection.prepare("INSERT OR REPLACE INTO athar_search_meta(key, value) VALUES (?, ?)").use {
            it.bindText(1, key); it.bindLong(2, value); it.step()
        }
    }

    // Once per process: every search called this, and taking the writer queued a search behind
    // a download's whole write batch.
    @Volatile private var ensured = false

    suspend fun ensure(database: AtharContentDatabase) {
        if (ensured) return
        database.useWriterConnection { create(it) }
        ensured = true
    }

    /** Search rows of the entries a generation changed or removed; the rest stay as they are. */
    suspend fun updateCatalog(
        connection: PooledConnection,
        changed: List<ContentEntity>,
        removedIds: Collection<String>,
    ) {
        (changed.map(ContentEntity::id) + removedIds).chunked(500).forEach { ids ->
            connection.usePrepared("DELETE FROM catalog_fts WHERE id IN (${ids.joinToString(",") { "?" }})") { delete ->
                ids.forEachIndexed { i, id -> delete.bindText(i + 1, id) }
                delete.step()
            }
        }
        connection.usePrepared(
            InsertCatalog,
        ) { insert ->
            changed.asSequence().filter { it.catalogPresent && it.editionOf == null }.forEach { entity ->
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

    /** [searchText]: each block's normalized text by ordinal, computed before the write transaction. */
    suspend fun indexBlocks(
        connection: PooledConnection,
        blocks: List<ContentBlockEntity>,
        searchText: Map<Int, String>,
    ) {
        for (table in if (copying) listOf("block_fts", "block_fts_full") else listOf("block_fts")) {
            connection.usePrepared("INSERT INTO $table(rowid, norm) VALUES (?, ?)") { insert ->
                blocks.forEach { block ->
                    insert.bindLong(1, block.rowid)
                    insert.bindText(2, searchText[block.ordinal] ?: ArNormalize.normalize(block.text))
                    insert.step()
                    insert.reset()
                    insert.clearBindings()
                }
            }
        }
    }

    // The search tables, written once here (scripts/bundle-app-catalog.mjs builds the same for the
    // bundled catalog and must follow any change).
    private val CreateTables = listOf(
        "CREATE VIRTUAL TABLE IF NOT EXISTS block_fts USING fts5($BlockFtsDdl)",
        "CREATE VIRTUAL TABLE IF NOT EXISTS catalog_fts USING fts5(" +
            "titleNorm, personNorm, topicsNorm, id UNINDEXED, tokenize='unicode61')",
        deleteTrigger("block_fts"),
    )

    private fun deleteTrigger(table: String) =
        "CREATE TRIGGER IF NOT EXISTS ${table}_before_delete BEFORE DELETE ON block BEGIN " +
            "DELETE FROM $table WHERE rowid = OLD.rowid; END"

    private suspend fun create(connection: PooledConnection) = CreateTables.forEach { connection.execute(it) }

    private fun create(connection: SQLiteConnection) {
        createMetadata(connection)
        CreateTables.forEach(connection::execSQL)
    }

    private fun createMetadata(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS athar_search_meta(" +
                "key TEXT PRIMARY KEY NOT NULL, value INTEGER NOT NULL)",
        )
    }

    private fun reset(connection: SQLiteConnection) {
        connection.execSQL("DROP TRIGGER IF EXISTS block_fts_before_delete")
        connection.execSQL("DROP TRIGGER IF EXISTS block_fts_full_before_delete")
        connection.execSQL("DROP TABLE IF EXISTS block_fts")
        connection.execSQL("DROP TABLE IF EXISTS block_fts_full")
        connection.execSQL("DROP TABLE IF EXISTS catalog_fts")
        connection.execSQL("DROP TABLE IF EXISTS athar_search_meta")
    }

    private fun rebuild(connection: SQLiteConnection) {
        connection.prepare("SELECT rowid, text, packed FROM block ORDER BY rowid").use { blocks ->
            connection.prepare("INSERT INTO block_fts(rowid, norm) VALUES (?, ?)").use { insert ->
                while (blocks.step()) {
                    insert.bindLong(1, blocks.getLong(0))
                    insert.bindText(2, ArNormalize.normalize(blocks.blockText(1)))
                    insert.step()
                    insert.reset()
                    insert.clearBindings()
                }
            }
        }
        connection.prepare(
            "SELECT id, titleNorm, personName, topicsCsv FROM entity WHERE catalogPresent = 1 AND editionOf IS NULL ORDER BY id",
        ).use { entities ->
            connection.prepare(
                InsertCatalog,
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

    /**
     * An apply cut short commits its catalog slices but not their search rows, and the next apply
     * sees those rows as unchanged: the books show in the library but its search never finds them.
     * Each open indexes every listed entry that has no search row, which also repairs such installs.
     */
    private fun indexMissingCatalog(connection: SQLiteConnection) {
        // Read first: the select reads catalog_fts, so no insert into it while that is open.
        val missing = connection.prepare(
            "SELECT id, titleNorm, personName, topicsCsv FROM entity " +
                "WHERE catalogPresent = 1 AND editionOf IS NULL AND id NOT IN (SELECT id FROM catalog_fts)",
        ).use { entities ->
            buildList {
                while (entities.step()) {
                    add(listOf(entities.getText(0), entities.getText(1), if (entities.isNull(2)) "" else entities.getText(2), entities.getText(3)))
                }
            }
        }
        if (missing.isEmpty()) return
        connection.prepare(
            InsertCatalog,
        ).use { insert ->
            missing.forEach { (id, titleNorm, personName, topicsCsv) ->
                insert.bindText(1, titleNorm)
                insert.bindText(2, ArNormalize.normalize(personName))
                insert.bindText(3, ArNormalize.normalize(topicsCsv.replace(',', ' ')))
                insert.bindText(4, id)
                insert.step()
                insert.reset()
                insert.clearBindings()
            }
        }
        android.util.Log.w("AtharContent", "indexed ${missing.size} catalog entries the search had lost")
    }

    /** A fresh or rebuilt index: created with positions. */
    private fun writeVersion(connection: SQLiteConnection) {
        setMeta(connection, "schema", SearchSchemaVersion)
        setMeta(connection, "positions", 1)
        positions = true
    }
}

/** [Catalog]: what [FullText] matches in the catalog, without the passages (the section lists). */
enum class ContentSearchField { FullText, Catalog, Title, Author, Topic }
enum class ContentSearchSort { Relevance, Newest, BookOrder }

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
    val ordinals: IntRange? = null,
    /** Match any of the words rather than all of them (the search screen's «بعض الكلمات»). */
    val anyWord: Boolean = false,
    /** «مطابق تمامًا»: the letters as typed (hamza, ة, ى count); only tashkeel is ignored. */
    val exact: Boolean = false,
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
        require(limit >= 1)
        val plan = FtsQueryBuilder.compactPlan(request.query, request.anyWord) ?: return ContentSearchResult(emptyList(), emptyList())
        ContentSearchSchema.ensure(database)
        return database.useReaderConnection { connection ->
            val catalog = searchCatalog(connection, request, plan, limit)
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
        val catalogMatch = requireNotNull(FtsQueryBuilder.build(request.query, request.anyWord))
        val scopedMatch = when (request.field) {
            ContentSearchField.FullText, ContentSearchField.Catalog -> catalogMatch
            ContentSearchField.Title -> "titleNorm : (${catalogMatch})"
            ContentSearchField.Author -> "personNorm : (${catalogMatch})"
            ContentSearchField.Topic -> "topicsNorm : (${catalogMatch})"
        }
        val order = if (request.sort == ContentSearchSort.Newest) {
            "e.publishedAt DESC, e.authoredYear DESC, e.id"
        } else {
            "bm25(catalog_fts, 0.25, 0.6, 1.0), e.id"
        }
        val sql = "SELECT e.id, e.title, e.personName, e.coll, e.kind, e.titleNorm " +
            "FROM catalog_fts JOIN entity e ON e.id = catalog_fts.id " +
            "WHERE catalog_fts MATCH ? AND e.catalogPresent = 1${filter.sql} " +
            "ORDER BY $order LIMIT ?"
        return connection.usePrepared(sql) { query ->
            var bind = 1
            query.bindText(bind++, scopedMatch)
            bind = query.bindAll(bind, filter.values)
            // A few more than shown, so the work named exactly comes first: «زاد المستقنع» is the
            // متن before الروض المربع شرح زاد المستقنع, however often a شرح repeats the name.
            query.bindLong(bind, if (request.sort == ContentSearchSort.Relevance) maxOf(limit * 4, 40).toLong() else limit.toLong())
            val typed = ArNormalize.normalize(request.query.replace("\"", " ")).trim()
            val titles = HashMap<String, String>()
            buildList {
                while (query.step()) {
                    titles[query.getText(0)] = query.getText(5)
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
            }.let { hits ->
                if (request.sort != ContentSearchSort.Relevance) hits
                else hits.sortedBy { titles[it.entityId].orEmpty().let { t -> if (t == typed) 0 else if (t.startsWith(typed)) 1 else 2 } }.take(limit)
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
        val range = request.ordinals
        require(range == null || (range.first >= 0 && !range.isEmpty())) { "invalid search block range" }
        // One book (in-book search, locating a hit): FTS5 skips straight to that book's rowids
        // instead of matching the whole library and filtering — «قال» in a book went 384→79 ms.
        val bookRowids = request.filter.entityIds?.singleOrNull()?.let { id ->
            connection.usePrepared("SELECT min(rowid), max(rowid) FROM block WHERE entityId = ?") { query ->
                query.bindText(1, id)
                if (query.step() && !query.isNull(0)) query.getLong(0)..query.getLong(1) else null
            } ?: return emptyList()
        }
        val ranked = request.sort == ContentSearchSort.Relevance
        val order = when (request.sort) {
            ContentSearchSort.Newest -> "e.publishedAt DESC, e.authoredYear DESC, b.rowid"
            ContentSearchSort.BookOrder -> "b.entityId, b.ordinal"
            ContentSearchSort.Relevance -> "f.score, b.rowid"
        }
        val where = "WHERE block_fts MATCH ?${if (bookRowids == null) "" else " AND block_fts.rowid BETWEEN ? AND ?"} " +
            "AND e.catalogPresent = 1${filter.sql}${if (range == null) "" else " AND b.ordinal BETWEEN ? AND ?"}" +
            // A poem is found by its verses: its «المقدمة» card and prose came up as hits that read like a book's.
            // Searching inside one poem still finds every block.
            (if (bookRowids == null) " AND (e.coll <> 'poem' OR b.type = 'verse')" else "")
        val columns = "b.rowid, b.entityId, b.ordinal, b.chapterAnchor, c.title, b.printedPage, " +
            "b.type, b.text, e.title, e.personName, e.coll, b.packed "
        val joins = "JOIN entity e ON e.id = b.entityId " +
            "LEFT JOIN chapter c ON c.entityId = b.entityId AND c.anchor = b.chapterAnchor "
        val sql = if (ranked) {
            // bm25 ranks the first RankCap matches in index order only, as the site does
            // (src/lib/search-handler.ts RANK_CAP). Ranking and sorting all 260k «الله» blocks took
            // 3.5 s on a desktop CPU (≈10+ s on a phone); capped it is 0.3 s. Raise the cap if a
            // common word misses passages that matter.
            "SELECT $columns FROM (SELECT b.rowid AS rowid, bm25(block_fts) AS score " +
                "FROM block_fts JOIN block b ON b.rowid = block_fts.rowid $joins$where LIMIT $RankCap) f " +
                "JOIN block b ON b.rowid = f.rowid $joins" +
                "ORDER BY $order LIMIT ? OFFSET ?"
        } else {
            "SELECT $columns FROM block_fts JOIN block b ON b.rowid = block_fts.rowid $joins$where " +
                "ORDER BY $order LIMIT ? OFFSET ?"
        }
        // Relevance reads a few pages of candidates and reorders them as the site does: the exact
        // phrase, then every word whole, then words that only start the same; the typed spelling
        // (إنما over انما) first within each.
        val exact = if (request.exact) request.query.replace("\"", "").trim().ifBlank { null } else null
        val tiers = if (request.sort == ContentSearchSort.Relevance) MatchTiers(request.query) else null
        val want = if (tiers != null) maxOf(limit * 4, CandidateBatchSize) else limit
        // Ranked candidates come in one read: each OFFSET page would rank the capped set again.
        val batch = if (ranked) want else CandidateBatchSize
        // With word positions the site's tiers run in SQL, each over the whole library: every
        // passage with the phrase comes before one with the words apart, not only those in the
        // first RankCap matches. An index without positions (before its copy finishes) does one pass.
        val matches = if (ranked && ContentSearchSchema.positions && !request.anyWord && exact == null) {
            FtsQueryBuilder.tiers(request.query).ifEmpty { listOf(plan.matchExpression) }
        } else listOf(plan.matchExpression)
        val result = mutableListOf<BlockSearchHit>()
        val scores = HashMap<Long, Int>()
        for ((tierIndex, match) in matches.withIndex()) {
            if (result.size >= want) break
            var offset = 0
            while (result.size < want) {
                currentCoroutineContext().ensureActive()
                var candidates = 0
                connection.usePrepared(sql) { query ->
                    var bind = 1
                    query.bindText(bind++, match)
                    if (bookRowids != null) { query.bindLong(bind++, bookRowids.first); query.bindLong(bind++, bookRowids.last) }
                    bind = query.bindAll(bind, filter.values)
                    if (range != null) { query.bindLong(bind++, range.first.toLong()); query.bindLong(bind++, range.last.toLong()) }
                    query.bindLong(bind++, batch.toLong())
                    query.bindLong(bind, offset.toLong())
                    while (query.step()) {
                        candidates++
                        val source = query.blockText(7)
                        query.toBlockHit(source, plan, exact)?.let { hit ->
                            result += hit
                            if (tiers != null) scores[hit.rowid] = tierIndex * 10 + tiers.score(source)
                        }
                        if (result.size == want) break
                    }
                }
                if (candidates < batch || result.size == want) break
                offset += batch
            }
        }
        return if (tiers == null) result else result.sortedBy { scores[it.rowid] ?: 0 }.take(limit)
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
        clauses += "$alias.id IN (SELECT id FROM entityState WHERE availability = ?)"
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

/** Text without tashkeel, letters unfolded. Folding maps one letter to one, so an index here is an index in the normalized text. */
fun exactLetters(text: String): String =
    java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFC).filterNot(::isTashkeel)

/**
 * Where [query] sits in [text] letter for letter, as a range in [exactLetters] of [text] (so it maps like
 * any normalized offset). Tashkeel the reader typed must be on that letter too; a bare letter takes any:
 * «عِلم» finds «عِلْم» and not «عَلَم».
 */
fun exactMatch(text: String, query: String): IntRange? {
    val t = lettersWithMarks(text)
    val q = lettersWithMarks(query)
    if (q.isEmpty()) return null
    val letters = String(CharArray(t.size) { t[it].first })
    val word = String(CharArray(q.size) { q[it].first })
    var at = letters.indexOf(word)
    while (at >= 0) {
        if (q.indices.all { t[at + it].second.containsAll(q[it].second) }) return at until at + q.size
        at = letters.indexOf(word, at + 1)
    }
    return null
}

private fun lettersWithMarks(text: String): List<Pair<Char, Set<Char>>> {
    val out = ArrayList<Pair<Char, MutableSet<Char>>>()
    for (c in java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFC)) {
        if (!isTashkeel(c)) out += c to HashSet() else if (c.code != 0x0640) out.lastOrNull()?.second?.add(c)
    }
    return out
}

/** Harakat, tanween, shadda, sukun, dagger alif, Quranic marks and tatweel. */
fun isTashkeel(c: Char): Boolean =
    c.code in 0x0610..0x061A || c.code == 0x0640 || c.code in 0x064B..0x065F || c.code == 0x0670 || c.code in 0x06D6..0x06ED

/** The block text at [column], or its packed form in the column after the last selected one. */
private fun SQLiteStatement.blockText(column: Int): String {
    val packed = getColumnCount() - 1
    return if (isNull(packed)) getText(column) else BlockText.unpack(getBlob(packed))
}

private fun SQLiteStatement.toBlockHit(source: String, plan: CompactFtsPlan, exact: String? = null): BlockSearchHit? {
    val mapped = ArNormalize.normalizeWithMap(source)
    if (plan.exactPhrases.any { !mapped.text.contains(it) }) return null
    val terms = if (plan.exactPhrases.isNotEmpty()) plan.exactPhrases else plan.highlightTerms
    val match = if (exact != null) {
        exactMatch(source, exact) ?: return null
    } else terms.firstNotNullOfOrNull { term ->
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

/** The site's tiers (src/lib/search-rank.ts) for a passage on the phone: lower is better. */
internal class MatchTiers(query: String) {
    private val words = ArNormalize.normalize(query.replace("\"", " ")).split(' ').filter { it.isNotBlank() }
    private val phrase = words.joinToString(" ")
    private val typed = query.replace("\"", " ").trim().split(Regex("\\s+")).filter { it.isNotBlank() }.map(::stripMarks)
    // the hamza/ة/ى as typed, when they say more than the folded words
    private val spelling = typed.takeIf { it.joinToString(" ") != phrase }

    fun score(source: String): Int {
        val text = ArNormalize.normalize(source)
        val tier = when {
            words.isEmpty() -> 0
            hasWord(text, phrase) -> 0
            words.all { hasWord(text, it) } -> 1
            else -> 2
        }
        val written = spelling?.let { s -> stripMarks(source).let { plain -> s.all { plain.contains(it) } } } ?: true
        return tier * 2 + if (written) 0 else 1
    }

    private fun hasWord(text: String, word: String): Boolean {
        var at = text.indexOf(word)
        while (at >= 0) {
            val before = at == 0 || !text[at - 1].isLetter()
            val after = at + word.length >= text.length || !text[at + word.length].isLetter()
            if (before && after) return true
            at = text.indexOf(word, at + 1)
        }
        return false
    }

    // tashkeel and tatweel only (the letters stay as written); the same ranges ArNormalize drops
    private fun stripMarks(s: String) = s.filterNot { c ->
        val code = c.code
        code in 0x610..0x61a || code in 0x64b..0x65f || code == 0x670 || code in 0x6d6..0x6ed || code == 0x640
    }
}
