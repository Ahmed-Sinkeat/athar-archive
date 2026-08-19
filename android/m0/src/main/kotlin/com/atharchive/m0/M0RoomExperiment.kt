package com.atharchive.m0

import android.content.Context
import android.os.SystemClock
import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.Database
import androidx.room3.Entity
import androidx.room3.Fts5
import androidx.room3.Insert
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.atharchive.core.ArNormalize
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Entity(tableName = "m0_blocks_fts")
@Fts5(prefix = [2, 3], notIndexed = ["bookId"])
data class M0SearchBlock(
    @PrimaryKey
    @ColumnInfo(name = "rowid")
    val rowId: Long,
    val bookId: String,
    val body: String,
)

data class M0SearchHit(
    val rowId: Long,
    val body: String,
    val score: Double,
)

@Dao
interface M0SearchDao {
    @Insert
    fun insertAll(rows: List<M0SearchBlock>)

    @Query(
        "SELECT rowid AS rowId, body, bm25(m0_blocks_fts) AS score " +
            "FROM m0_blocks_fts WHERE m0_blocks_fts MATCH :match " +
            "ORDER BY bm25(m0_blocks_fts) LIMIT 20",
    )
    fun search(match: String): List<M0SearchHit>

    @Query("SELECT sql FROM sqlite_master WHERE name = 'm0_blocks_fts'")
    fun tableSql(): String
}

@Database(entities = [M0SearchBlock::class], version = 1, exportSchema = false)
abstract class M0RoomDatabase : RoomDatabase() {
    abstract fun searchDao(): M0SearchDao
}

data class R2Result(
    val rows: Int,
    val openMs: Double,
    val importMs: Double,
    val phraseMs: Double,
    val prefixMs: Double,
    val phraseHits: Int,
    val prefixHits: Int,
    val databaseBytes: Long,
    val ddl: String,
) {
    fun report(): String = buildString {
        appendLine("Room 3 + BundledSQLiteDriver: PASS")
        appendLine("rows: $rows")
        appendLine("cold create/open: ${openMs.oneDecimal()} ms")
        appendLine("import: ${importMs.oneDecimal()} ms")
        appendLine("phrase bm25: ${phraseMs.oneDecimal()} ms · hits $phraseHits")
        appendLine("prefix: ${prefixMs.oneDecimal()} ms · hits $prefixHits")
        appendLine("database: ${databaseBytes / 1024} KiB")
        append("DDL: $ddl")
    }
}

private fun Double.oneDecimal(): String = "%.1f".format(this)

object M0RoomExperiment {
    private const val DATABASE_NAME = "m0_room.db"
    private const val ROWS = 20_000

    suspend fun run(context: Context): R2Result = withContext(Dispatchers.IO) {
        context.deleteDatabase(DATABASE_NAME)
        val openStart = SystemClock.elapsedRealtimeNanos()
        val database = Room.databaseBuilder(context, M0RoomDatabase::class.java, DATABASE_NAME)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .setSingleConnectionPool()
            .build()
        val dao = database.searchDao()
        val ddl = dao.tableSql()
        val openMs = elapsedMs(openStart)

        val importStart = SystemClock.elapsedRealtimeNanos()
        for (batchStart in 0 until ROWS step 500) {
            val rows = (batchStart until minOf(batchStart + 500, ROWS)).map { index ->
                val text = if (index % 137 == 0) {
                    "باب طلب العلم والإيمان وفضل عبد الله رقم $index"
                } else {
                    "مسألة علمية في الكتاب رقم $index من أبواب الآداب والفقه"
                }
                M0SearchBlock(
                    rowId = index.toLong() + 1,
                    bookId = "book-${index / 1_000}",
                    body = ArNormalize.normalize(text),
                )
            }
            dao.insertAll(rows)
        }
        val importMs = elapsedMs(importStart)

        val phraseStart = SystemClock.elapsedRealtimeNanos()
        val phrase = dao.search("\"طلب\" AND \"العلم\"")
        val phraseMs = elapsedMs(phraseStart)
        val prefixStart = SystemClock.elapsedRealtimeNanos()
        val prefix = dao.search("\"والايم\"*")
        val prefixMs = elapsedMs(prefixStart)
        database.close()

        R2Result(
            rows = ROWS,
            openMs = openMs,
            importMs = importMs,
            phraseMs = phraseMs,
            prefixMs = prefixMs,
            phraseHits = phrase.size,
            prefixHits = prefix.size,
            databaseBytes = databaseBytes(context.getDatabasePath(DATABASE_NAME)),
            ddl = ddl,
        )
    }

    private fun databaseBytes(file: File): Long =
        listOf(file, File(file.path + "-wal"), File(file.path + "-shm")).sumOf { it.takeIf(File::exists)?.length() ?: 0 }

    private fun elapsedMs(startNanos: Long): Double =
        (SystemClock.elapsedRealtimeNanos() - startNanos) / 1_000_000.0

}
