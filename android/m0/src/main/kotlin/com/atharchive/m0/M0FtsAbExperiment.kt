package com.atharchive.m0

import android.content.Context
import android.os.SystemClock
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.atharchive.core.ArNormalize
import java.io.File
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

private data class FtsSample(
    val bookId: String,
    val ordinal: Int,
    val body: String,
    val originalBytes: Int,
)

data class FtsVariantResult(
    val name: String,
    val rows: Int,
    val originalBytes: Long,
    val databaseBytes: Long,
    val mapBytes: Long,
    val importMs: Double,
    val searchMedianMs: Double,
    val prefixMedianMs: Double,
    val deleteMs: Double,
    val hits: Int,
) {
    val indexBytes: Long get() = (databaseBytes - mapBytes).coerceAtLeast(0)
    val ratio: Double get() = indexBytes.toDouble() / originalBytes
    val throughputMbPerSecond: Double get() = originalBytes / 1_048_576.0 / (importMs / 1_000.0)
}

data class R2bResult(
    val regular: FtsVariantResult,
    val contentless: FtsVariantResult,
    val lean: FtsVariantResult,
    val compact: FtsVariantResult,
    val mapBytes: Long,
    val repairMs: Double,
    val repairHits: Int,
) {
    fun report(): String = buildString {
        appendLine("20 real books · ${regular.rows} blocks · map baseline ${mapBytes / 1024} KiB")
        appendLine(regular.line())
        appendLine(contentless.line())
        appendLine(lean.line())
        appendLine(compact.line())
        appendLine("compact offline rebuild: ${repairMs.m0OneDecimal()} ms · hits $repairHits")
        val winner = listOf(regular, contentless, lean, compact).minBy { it.databaseBytes }
        append("size winner: ${winner.name}")
    }

    private fun FtsVariantResult.line(): String =
        "$name · ${(databaseBytes / 1024.0).m0OneDecimal()} KiB total · " +
            "index ${(indexBytes / 1024.0).m0OneDecimal()} KiB/${(ratio * 100).m0OneDecimal()}% raw · " +
            "import ${importMs.m0OneDecimal()} ms " +
            "(${throughputMbPerSecond.m0OneDecimal()} MiB/s) · search " +
            "${searchMedianMs.m0OneDecimal()} ms · prefix ${prefixMedianMs.m0OneDecimal()} ms · " +
            "delete ${deleteMs.m0OneDecimal()} ms · hits $hits"
}

object M0FtsAbExperiment {
    private const val ASSET = "m0/fts-20.ndjson.gzipdata"
    private const val MATCH = "\"الله\""
    private const val PREFIX_MATCH = "\"الل\"*"

    suspend fun run(context: Context): R2bResult = withContext(Dispatchers.IO) {
        val samples = loadSamples(context)
        val mapBytes = benchmarkMapOnly(context, samples)
        val regular = benchmark(context, samples, mapBytes, contentless = false, prefix = "2 3", compact = false, suffix = "regular")
        val contentless = benchmark(context, samples, mapBytes, contentless = true, prefix = "2 3", compact = false, suffix = "contentless")
        val lean = benchmark(context, samples, mapBytes, contentless = true, prefix = null, compact = false, suffix = "lean")
        val compact = benchmark(context, samples, mapBytes, contentless = true, prefix = null, compact = true, suffix = "compact")
        val repairStart = SystemClock.elapsedRealtimeNanos()
        val repair = benchmark(context, samples, mapBytes, contentless = true, prefix = null, compact = true, suffix = "repair")
        R2bResult(
            regular = regular,
            contentless = contentless,
            lean = lean,
            compact = compact,
            mapBytes = mapBytes,
            repairMs = elapsedMs(repairStart),
            repairHits = repair.hits,
        )
    }

    private fun loadSamples(context: Context): List<FtsSample> =
        GZIPInputStream(context.assets.open(ASSET)).bufferedReader().useLines { lines ->
            lines.filter(String::isNotBlank).map { line ->
                val json = JSONObject(line)
                val text = json.getString("text")
                FtsSample(
                    bookId = json.getString("book"),
                    ordinal = json.getInt("ordinal"),
                    body = ArNormalize.normalize(text),
                    originalBytes = text.toByteArray(Charsets.UTF_8).size,
                )
            }.toList()
        }

    private fun benchmark(
        context: Context,
        samples: List<FtsSample>,
        mapBytes: Long,
        contentless: Boolean,
        prefix: String?,
        compact: Boolean,
        suffix: String,
    ): FtsVariantResult {
        val file = File(context.noBackupFilesDir, "m0-fts-$suffix.db")
        deleteDatabaseFiles(file)
        val connection = BundledSQLiteDriver().open(file.absolutePath)
        val originalBytes = samples.sumOf { it.originalBytes.toLong() }
        val importStart = SystemClock.elapsedRealtimeNanos()
        connection.use {
            createSchema(connection, contentless, prefix, compact)
            insertSamples(connection, samples)
            connection.execSQL("PRAGMA optimize")
            val importMs = elapsedMs(importStart)
            val queryTimes = DoubleArray(31) { search(connection, MATCH).second }
            val prefixTimes = DoubleArray(31) { search(connection, PREFIX_MATCH).second }
            val searchHits = search(connection, MATCH).first
            val deleteMs = deleteOneBook(connection, samples.first().bookId)
            connection.execSQL("VACUUM")
            val result = FtsVariantResult(
                name = when {
                    !contentless -> "regular+p23"
                    prefix != null -> "contentless+p23"
                    compact -> "contentless+compact"
                    else -> "contentless+lean"
                },
                rows = samples.size,
                originalBytes = originalBytes,
                databaseBytes = 0,
                mapBytes = mapBytes,
                importMs = importMs,
                searchMedianMs = queryTimes.drop(1).sorted()[15],
                prefixMedianMs = prefixTimes.drop(1).sorted()[15],
                deleteMs = deleteMs,
                hits = searchHits,
            )
            return result.copy(databaseBytes = file.length())
        }
    }

    private fun createSchema(
        connection: SQLiteConnection,
        contentless: Boolean,
        prefix: String?,
        compact: Boolean,
    ) {
        connection.execSQL("PRAGMA journal_mode=DELETE")
        connection.execSQL("PRAGMA synchronous=NORMAL")
        connection.execSQL("CREATE TABLE block_map(rowid INTEGER PRIMARY KEY, book_id TEXT NOT NULL, ordinal INTEGER NOT NULL)")
        connection.execSQL("CREATE INDEX block_map_book ON block_map(book_id)")
        val options = buildList {
            prefix?.let { add("prefix='$it'") }
            if (contentless) {
                add("content=''")
                add("contentless_delete=1")
            }
            if (compact) {
                add("detail=none")
            }
        }.joinToString(prefix = if (prefix != null || contentless) ", " else "", separator = ", ")
        connection.execSQL("CREATE VIRTUAL TABLE blocks_fts USING fts5(body$options)")
    }

    private fun benchmarkMapOnly(context: Context, samples: List<FtsSample>): Long {
        val file = File(context.noBackupFilesDir, "m0-fts-map.db")
        deleteDatabaseFiles(file)
        BundledSQLiteDriver().open(file.absolutePath).use { connection ->
            connection.execSQL("PRAGMA journal_mode=DELETE")
            connection.execSQL("CREATE TABLE block_map(rowid INTEGER PRIMARY KEY, book_id TEXT NOT NULL, ordinal INTEGER NOT NULL)")
            connection.execSQL("CREATE INDEX block_map_book ON block_map(book_id)")
            connection.execSQL("BEGIN IMMEDIATE")
            try {
                connection.prepare("INSERT INTO block_map(rowid, book_id, ordinal) VALUES (?, ?, ?)").use { insert ->
                    samples.forEachIndexed { index, sample ->
                        insert.bindLong(1, index.toLong() + 1)
                        insert.bindText(2, sample.bookId)
                        insert.bindLong(3, sample.ordinal.toLong())
                        insert.step()
                        insert.reset()
                        insert.clearBindings()
                    }
                }
                connection.execSQL("COMMIT")
            } catch (error: Throwable) {
                connection.execSQL("ROLLBACK")
                throw error
            }
            connection.execSQL("VACUUM")
        }
        return file.length()
    }

    private fun insertSamples(connection: SQLiteConnection, samples: List<FtsSample>) {
        connection.execSQL("BEGIN IMMEDIATE")
        try {
            connection.prepare("INSERT INTO block_map(rowid, book_id, ordinal) VALUES (?, ?, ?)").use { map ->
                connection.prepare("INSERT INTO blocks_fts(rowid, body) VALUES (?, ?)").use { fts ->
                    samples.forEachIndexed { index, sample ->
                        val rowId = index.toLong() + 1
                        map.bindLong(1, rowId)
                        map.bindText(2, sample.bookId)
                        map.bindLong(3, sample.ordinal.toLong())
                        map.step()
                        map.reset()
                        map.clearBindings()

                        fts.bindLong(1, rowId)
                        fts.bindText(2, sample.body)
                        fts.step()
                        fts.reset()
                        fts.clearBindings()
                    }
                }
            }
            connection.execSQL("COMMIT")
        } catch (error: Throwable) {
            connection.execSQL("ROLLBACK")
            throw error
        }
    }

    private fun search(connection: SQLiteConnection, match: String): Pair<Int, Double> {
        val start = SystemClock.elapsedRealtimeNanos()
        var hits = 0
        connection.prepare(
            "SELECT rowid, bm25(blocks_fts) FROM blocks_fts " +
                "WHERE blocks_fts MATCH ? ORDER BY bm25(blocks_fts) LIMIT 20",
        ).use { query ->
            query.bindText(1, match)
            while (query.step()) hits++
        }
        return hits to elapsedMs(start)
    }

    private fun deleteOneBook(connection: SQLiteConnection, bookId: String): Double {
        val rowIds = mutableListOf<Long>()
        connection.prepare("SELECT rowid FROM block_map WHERE book_id = ?").use { query ->
            query.bindText(1, bookId)
            while (query.step()) rowIds += query.getLong(0)
        }
        val start = SystemClock.elapsedRealtimeNanos()
        connection.execSQL("BEGIN IMMEDIATE")
        try {
            connection.prepare("DELETE FROM blocks_fts WHERE rowid = ?").use { delete ->
                for (rowId in rowIds) {
                    delete.bindLong(1, rowId)
                    delete.step()
                    delete.reset()
                    delete.clearBindings()
                }
            }
            connection.prepare("DELETE FROM block_map WHERE book_id = ?").use { delete ->
                delete.bindText(1, bookId)
                delete.step()
            }
            connection.execSQL("COMMIT")
        } catch (error: Throwable) {
            connection.execSQL("ROLLBACK")
            throw error
        }
        return elapsedMs(start)
    }

    private fun deleteDatabaseFiles(file: File) {
        listOf(file, File(file.path + "-wal"), File(file.path + "-shm")).forEach { if (it.exists()) it.delete() }
    }

    private fun elapsedMs(startNanos: Long): Double =
        (SystemClock.elapsedRealtimeNanos() - startNanos) / 1_000_000.0
}

private fun Double.m0OneDecimal(): String = "%.1f".format(this)
