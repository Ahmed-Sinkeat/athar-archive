package com.atharchive.core.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.room3.PooledConnection
import androidx.room3.RoomRawQuery
import androidx.room3.paging.LimitOffsetPagingSource
import androidx.room3.useReaderConnection
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.execSQL
import com.atharchive.core.ArNormalize
import com.atharchive.core.data.content.AppContentJson
import com.atharchive.core.data.content.DiwanPoem
import com.atharchive.core.data.db.content.AtharContentDatabase
import com.atharchive.core.data.db.content.ContentEntity
import kotlinx.coroutines.flow.Flow

/** One line of الشعر: a poem of a ديوان ([ordinal] its heading's block), or a poem that is a work of its own. */
data class PoemListRow(
    val id: String,
    val entityId: String,
    val ordinal: Int?,
    val title: String,
    val openingVerses: List<String>,
    val verseCount: Int?,
)

enum class PoemRowOrder { Died, Title, Poet, Catalog, Given }

/**
 * [entityIds]: only the poems of these works (null for all); with [PoemRowOrder.Given] also their order.
 * [words]: normalized; each must be in the title, the poet or the opening verses.
 */
data class PoemPageQuery(
    val entityIds: List<String>?,
    val words: List<String>,
    val order: PoemRowOrder,
    val descending: Boolean,
)

/**
 * الشعر lists ~53k poems, most of them inside 772 دواوين: one row each here, so the screen reads
 * the rows it shows and never holds the list. Kept with the search tables (same open, apply and
 * reset), and like them outside Room's schema: the bundled catalog fills it on first open.
 */
internal object PoemRows {
    val CreateTables = listOf(
        // Lean on purpose (~15 MB for ~53k poems; a text id and a search column each doubled it):
        // a line's id is entityId#ordinal, and a ديوان's poem searches its title and poet as stored.
        "CREATE TABLE IF NOT EXISTS poem_row(entityId TEXT NOT NULL, ordinal INTEGER, title TEXT NOT NULL, " +
            "titleNorm TEXT NOT NULL, poetNorm TEXT NOT NULL, diedKey INTEGER NOT NULL, opening TEXT, verseCount INTEGER, openingNorm TEXT)",
        "CREATE INDEX IF NOT EXISTS poem_row_entity ON poem_row(entityId, ordinal)",
        // The usual orders read a page off an index; the rest sort (~50 ms on a PC).
        "CREATE INDEX IF NOT EXISTS poem_row_died ON poem_row(diedKey, titleNorm)",
        "CREATE INDEX IF NOT EXISTS poem_row_title ON poem_row(titleNorm, ordinal)",
        "CREATE INDEX IF NOT EXISTS poem_row_poet ON poem_row(poetNorm, entityId, ordinal)",
    )
    private const val Insert = "INSERT INTO poem_row(entityId, ordinal, title, titleNorm, poetNorm, diedKey, opening, verseCount, openingNorm) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
    private const val SelectWorks = "SELECT id, title, personName, died, openingVersesJson, poemsJson, verseCount FROM entity " +
        "WHERE coll = 'poem' AND catalogPresent = 1 AND editionOf IS NULL"
    private const val NoDeath = 100_000

    private class Work(val id: String, val title: String, val poet: String, val died: Int?, val openingJson: String, val poemsJson: String?, val verseCount: Int?)

    private fun ContentEntity.work() = Work(id, title, personName.orEmpty(), died, openingVersesJson, poemsJson, verseCount)

    private fun SQLiteStatement.bindRows(work: Work, step: () -> Unit) {
        val poetNorm = ArNormalize.normalize(work.poet)
        val poems = work.poemsJson?.let { AppContentJson.decodeFromString<List<DiwanPoem>>(it) }.orEmpty()
        fun row(ordinal: Int?, title: String, opening: String?, verses: List<String>, verseCount: Int?) {
            bindText(1, work.id)
            if (ordinal == null) bindNull(2) else bindLong(2, ordinal.toLong())
            bindText(3, title); bindText(4, ArNormalize.normalize(title)); bindText(5, poetNorm)
            bindLong(6, (work.died ?: NoDeath).toLong())
            if (opening == null) bindNull(7) else bindText(7, opening)
            if (verseCount == null) bindNull(8) else bindLong(8, verseCount.toLong())
            if (verses.isEmpty()) bindNull(9) else bindText(9, verses.joinToString(" ") { ArNormalize.normalize(it) })
            step()
            reset()
            clearBindings()
        }
        if (poems.isEmpty()) {
            row(null, work.title, work.openingJson, AppContentJson.decodeFromString(work.openingJson), work.verseCount?.takeIf { it > 0 })
        } else {
            poems.forEach { row(it.o, it.t, null, emptyList(), null) }
        }
    }

    /** On open: every listed poem work with no rows (a bundled catalog, an install from before this table). */
    fun fillMissing(connection: SQLiteConnection) {
        // Read first: the select reads poem_row, so no insert into it while that is open.
        val missing = connection.prepare("$SelectWorks AND id NOT IN (SELECT entityId FROM poem_row)").use { q ->
            buildList {
                while (q.step()) add(
                    Work(
                        q.getText(0), q.getText(1), if (q.isNull(2)) "" else q.getText(2), if (q.isNull(3)) null else q.getInt(3),
                        q.getText(4), if (q.isNull(5)) null else q.getText(5), if (q.isNull(6)) null else q.getInt(6),
                    ),
                )
            }
        }
        if (missing.isEmpty()) return
        // One transaction: ~53k rows one commit each took seconds.
        connection.execSQL("SAVEPOINT poem_rows")
        connection.prepare(Insert).use { insert -> missing.forEach { insert.bindRows(it) { insert.step() } } }
        connection.execSQL("RELEASE poem_rows")
        android.util.Log.i("AtharContent", "listed the poems of ${missing.size} poem works")
    }

    /** The rows of the works a generation changed or removed, inside its write transaction. */
    suspend fun update(connection: PooledConnection, changed: List<ContentEntity>, removedIds: Collection<String>) {
        (changed.map(ContentEntity::id) + removedIds).chunked(500).forEach { ids ->
            connection.usePrepared("DELETE FROM poem_row WHERE entityId IN (${ids.joinToString(",") { "?" }})") { delete ->
                ids.forEachIndexed { i, id -> delete.bindText(i + 1, id) }
                delete.step()
            }
        }
        val works = changed.filter { it.coll == "poem" && it.catalogPresent && it.editionOf == null }.map { it.work() }
        if (works.isEmpty()) return
        connection.usePrepared(Insert) { insert -> works.forEach { insert.bindRows(it) { insert.step() } } }
    }

    fun pages(database: AtharContentDatabase, query: PoemPageQuery): Flow<PagingData<PoemListRow>> =
        Pager(PagingConfig(pageSize = 60, enablePlaceholders = true)) { Source(database, query) }.flow

    private class Source(private val database: AtharContentDatabase, query: PoemPageQuery) :
        LimitOffsetPagingSource<PoemListRow>(query.sql(), database, "contentGeneration") {
        // The generation row is written last, in the same transaction as these rows: a list
        // reloads once an apply is done, not on each catalog slice before it.
        override suspend fun convertRows(limitOffsetQuery: RoomRawQuery, itemCount: Int): List<PoemListRow> =
            database.useReaderConnection { connection ->
                connection.usePrepared(limitOffsetQuery.sql) { q ->
                    limitOffsetQuery.getBindingFunction()(q)
                    buildList {
                        while (q.step()) add(
                            q.getText(0).let { entityId ->
                                val ordinal = if (q.isNull(1)) null else q.getInt(1)
                                PoemListRow(
                                    id = if (ordinal == null) entityId else "$entityId#$ordinal", entityId = entityId, ordinal = ordinal,
                                    title = q.getText(2),
                                    openingVerses = if (q.isNull(3)) emptyList() else AppContentJson.decodeFromString(q.getText(3)),
                                    verseCount = if (q.isNull(4)) null else q.getInt(4),
                                )
                            },
                        )
                    }
                }
            }
    }

    private fun PoemPageQuery.sql(): RoomRawQuery {
        // IN over json_each is one lookup per row; instr over a list of thousands was a scan each.
        val idSet = entityIds?.let { AppContentJson.encodeToString(it) }
        val ids = entityIds?.joinToString(",", ",", ",")
        val where = listOfNotNull(ids?.let { "entityId IN (SELECT value FROM json_each(?))" }) + words.map { "(titleNorm || ' ' || poetNorm || ' ' || ifnull(openingNorm, '')) LIKE ? ESCAPE '\\'" }
        val dir = if (descending) "DESC" else "ASC"
        val orderBy = when (order) {
            PoemRowOrder.Died -> if (descending) "diedKey = $NoDeath, diedKey DESC, titleNorm" else "diedKey, titleNorm"
            PoemRowOrder.Title -> "titleNorm $dir, ordinal $dir"
            PoemRowOrder.Poet -> "poetNorm $dir, entityId $dir, ordinal $dir"
            PoemRowOrder.Catalog -> "title $dir, ordinal $dir"
            // Only «الأخيرة»: a few dozen works.
            PoemRowOrder.Given -> if (ids == null) "entityId, ordinal" else "instr(?, ',' || entityId || ','), ordinal"
        }
        val sql = "SELECT entityId, ordinal, title, opening, verseCount FROM poem_row" +
            (if (where.isEmpty()) "" else " WHERE " + where.joinToString(" AND ")) + " ORDER BY $orderBy"
        return RoomRawQuery(sql) { s ->
            var i = 1
            if (idSet != null) s.bindText(i++, idSet)
            words.forEach { s.bindText(i++, "%" + it.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%") }
            if (ids != null && order == PoemRowOrder.Given) s.bindText(i, ids)
        }
    }
}

fun poemPages(database: AtharContentDatabase, query: PoemPageQuery): Flow<PagingData<PoemListRow>> = PoemRows.pages(database, query)
