package com.atharchive.m0

import android.content.Context
import android.os.SystemClock
import androidx.paging.PagingSource
import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.DaoReturnTypeConverters
import androidx.room3.Database
import androidx.room3.Entity
import androidx.room3.Fts5
import androidx.room3.Insert
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.room3.executeSQL
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.room3.paging.PagingSourceDaoReturnTypeConverter
import com.atharchive.core.ArNormalize
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Entity(tableName = "r7_blocks")
internal data class R7Block(
    @PrimaryKey
    @ColumnInfo(name = "rowid")
    val rowId: Long,
    @ColumnInfo(name = "block_id")
    val blockId: String,
    val ordinal: Int,
    val body: String,
    val norm: String,
)

@Entity(tableName = "r7_blocks_fts")
@Fts5(contentEntity = R7Block::class)
internal data class R7BlockFts(val norm: String)

@Dao
@DaoReturnTypeConverters(PagingSourceDaoReturnTypeConverter::class)
internal interface R7Dao {
    @Insert
    suspend fun insert(rows: List<R7Block>)

    @Query("SELECT * FROM r7_blocks ORDER BY ordinal")
    fun pagingSource(): PagingSource<Int, R7Block>

    @Query("SELECT * FROM r7_blocks WHERE ordinal = :ordinal LIMIT 1")
    suspend fun blockAt(ordinal: Int): R7Block?

    @Query("SELECT * FROM r7_blocks WHERE block_id = :blockId LIMIT 1")
    suspend fun blockById(blockId: String): R7Block?

    @Query("SELECT * FROM r7_blocks ORDER BY ordinal DESC LIMIT :limit")
    suspend fun tail(limit: Int): List<R7Block>

    @Query(
        "SELECT r7_blocks.* FROM r7_blocks " +
            "JOIN r7_blocks_fts ON r7_blocks.rowid = r7_blocks_fts.rowid " +
            "WHERE r7_blocks_fts MATCH :match " +
            "ORDER BY bm25(r7_blocks_fts) LIMIT 1",
    )
    suspend fun search(match: String): R7Block?

    @Query(
        "SELECT r7_blocks.* FROM r7_blocks " +
            "JOIN r7_blocks_fts ON r7_blocks.rowid = r7_blocks_fts.rowid " +
            "WHERE r7_blocks_fts MATCH :match " +
            "ORDER BY r7_blocks.ordinal DESC LIMIT 1",
    )
    suspend fun searchNearEnd(match: String): R7Block?

    @Query(
        "SELECT * FROM r7_blocks WHERE ordinal BETWEEN :first AND :last ORDER BY ordinal",
    )
    suspend fun window(first: Int, last: Int): List<R7Block>
}

@Database(entities = [R7Block::class, R7BlockFts::class], version = 1, exportSchema = false)
internal abstract class R7Database : RoomDatabase() {
    abstract fun dao(): R7Dao
}

internal data class R7Target(
    val match: String,
    val normalizedPhrase: String,
    val blockId: String,
    val ordinal: Int,
)

internal data class R7SetupResult(
    val rows: Int,
    val importMs: Double,
    val openMiddleMs: Double,
    val databaseBytes: Long,
    val peakImportRssKb: Long,
    val target: R7Target,
) {
    fun report(): String = buildString {
        appendLine("real 82 MiB book · $rows blocks")
        appendLine("import + FTS: ${importMs.r7OneDecimal()} ms")
        appendLine("cold logical open at middle: ${openMiddleMs.r7OneDecimal()} ms")
        appendLine("database: ${(databaseBytes / 1_048_576.0).r7OneDecimal()} MiB")
        appendLine("peak import RSS sample: ${(peakImportRssKb / 1024.0).r7OneDecimal()} MiB")
        append("near-end phrase target: block ${target.ordinal} · ${target.normalizedPhrase}")
    }
}

internal data class R7Session(val database: R7Database, val setup: R7SetupResult)

internal data class R7SearchResult(
    val block: R7Block,
    val searchMs: Double,
    val sourceRange: IntRange,
    val exactVocalizedHighlight: Boolean,
)

internal object M0EndToEndExperiment {
    private const val PACKAGE_ASSET = "m0/r3-big.athar"
    private const val INDEX_ASSET = "m0/r3-big.athar.idx"
    private const val DATABASE_NAME = "m0-r7.db"
    private const val PACKAGE_FILE = "m0-r7.athar"
    private const val PREFS = "m0-r7"
    private const val READY = "ready"
    private const val ROWS = "rows"
    private const val IMPORT_MS = "import-ms"
    private const val OPEN_MS = "open-ms"
    private const val DATABASE_BYTES = "database-bytes"
    private const val PEAK_RSS = "peak-rss"
    private const val TARGET_MATCH = "target-match"
    private const val TARGET_PHRASE = "target-phrase"
    private const val TARGET_BLOCK = "target-block"
    private const val TARGET_ORDINAL = "target-ordinal"
    private const val READING_BLOCK = "reading-block"
    private const val READING_ORDINAL = "reading-ordinal"

    @Volatile
    private var active: R7Session? = null

    suspend fun build(context: Context): R7Session = withContext(Dispatchers.IO) {
        active?.database?.close()
        active = null
        context.deleteDatabase(DATABASE_NAME)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        val database = openDatabase(context)
        val index = readIndex(context)
        val packageFile = preparePackage(context, index.packageBytes)
        suspendFtsSync(database)

        var peakRssKb = currentRssKb()
        var rows = 0
        val importStart = SystemClock.elapsedRealtimeNanos()
        for (frame in index.frames) {
            val batch = ArrayList<R7Block>(frame.count)
            openFrame(packageFile, frame).bufferedReader().useLines { lines ->
                for (line in lines) {
                    if (line.isBlank()) continue
                    val record = JSONObject(line)
                    if (record.getString("t") == "header") continue
                    val ordinal = record.getInt("i")
                    val body = record.getString("x")
                    batch += R7Block(
                        rowId = ordinal.toLong() + 1,
                        blockId = record.getString("id"),
                        ordinal = ordinal,
                        body = body,
                        norm = ArNormalize.normalize(body),
                    )
                }
            }
            database.dao().insert(batch)
            rows += batch.size
            peakRssKb = maxOf(peakRssKb, currentRssKb())
        }
        rebuildAndRestoreFtsSync(database)
        val importMs = elapsedMs(importStart)
        check(rows == index.blocks) { "expected ${index.blocks} blocks, imported $rows" }

        val target = selectNearEndTarget(database.dao())
        val middleStart = SystemClock.elapsedRealtimeNanos()
        checkNotNull(database.dao().blockAt(rows / 2))
        val openMiddleMs = elapsedMs(middleStart)
        val databaseBytes = databaseBytes(context.getDatabasePath(DATABASE_NAME))
        val setup = R7SetupResult(rows, importMs, openMiddleMs, databaseBytes, peakRssKb, target)
        persistSetup(context, setup)
        R7Session(database, setup).also { active = it }
    }

    suspend fun openExisting(context: Context): R7Session? = withContext(Dispatchers.IO) {
        active?.let { return@withContext it }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(READY, false) || !context.getDatabasePath(DATABASE_NAME).exists()) {
            return@withContext null
        }
        val database = openDatabase(context)
        val persistedTarget = R7Target(
            match = prefs.getString(TARGET_MATCH, null) ?: return@withContext null,
            normalizedPhrase = prefs.getString(TARGET_PHRASE, null) ?: return@withContext null,
            blockId = prefs.getString(TARGET_BLOCK, null) ?: return@withContext null,
            ordinal = prefs.getInt(TARGET_ORDINAL, -1).also { check(it >= 0) },
        )
        val target = persistedTarget.takeIf {
            database.dao().searchNearEnd(it.match)?.body
                ?.let(ArNormalize::normalize)
                ?.contains(it.normalizedPhrase) == true
        } ?: selectNearEndTarget(database.dao())
        val setup = R7SetupResult(
            rows = prefs.getInt(ROWS, 0),
            importMs = prefs.getLong(IMPORT_MS, 0).toDouble() / 1_000.0,
            openMiddleMs = prefs.getLong(OPEN_MS, 0).toDouble() / 1_000.0,
            databaseBytes = prefs.getLong(DATABASE_BYTES, 0),
            peakImportRssKb = prefs.getLong(PEAK_RSS, 0),
            target = target,
        )
        if (target != persistedTarget) persistSetup(context, setup)
        R7Session(database, setup).also { active = it }
    }

    suspend fun search(session: R7Session): R7SearchResult = withContext(Dispatchers.IO) {
        val start = SystemClock.elapsedRealtimeNanos()
        val block = checkNotNull(session.database.dao().searchNearEnd(session.setup.target.match))
        val searchMs = elapsedMs(start)
        val mapped = ArNormalize.normalizeWithMap(block.body)
        val normStart = mapped.text.indexOf(session.setup.target.normalizedPhrase)
        check(normStart >= 0) { "normalized phrase missing from returned block" }
        val range = mapped.sourceRange(normStart, normStart + session.setup.target.normalizedPhrase.length)
        val sourceSlice = block.body.substring(range)
        R7SearchResult(
            block = block,
            searchMs = searchMs,
            sourceRange = range,
            exactVocalizedHighlight = ArNormalize.normalize(sourceSlice) == session.setup.target.normalizedPhrase,
        )
    }

    suspend fun selectionWindow(session: R7Session, center: Int): List<R7Block> = withContext(Dispatchers.IO) {
        session.database.dao().window(maxOf(0, center - 60), minOf(session.setup.rows - 1, center + 60))
    }

    fun savePosition(context: Context, block: R7Block) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(READING_BLOCK, block.blockId)
            .putInt(READING_ORDINAL, block.ordinal)
            .apply()
    }

    suspend fun restoredPosition(context: Context, session: R7Session): Pair<Int, Boolean> = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val ordinalHint = prefs.getInt(READING_ORDINAL, session.setup.rows / 2)
        val blockId = prefs.getString(READING_BLOCK, null)
        val restored = blockId?.let { session.database.dao().blockById(it) }
            ?: session.database.dao().blockAt(ordinalHint)
        (restored?.ordinal ?: ordinalHint) to (restored?.blockId == blockId && blockId != null)
    }

    fun currentRssKb(): Long = runCatching {
        File("/proc/self/status").useLines { lines ->
            lines.first { it.startsWith("VmRSS:") }.filter(Char::isDigit).toLong()
        }
    }.getOrDefault(0)

    private fun openDatabase(context: Context): R7Database =
        Room.databaseBuilder(context, R7Database::class.java, DATABASE_NAME)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .setSingleConnectionPool()
            .build()

    private suspend fun suspendFtsSync(database: R7Database) {
        database.useConnection(isReadOnly = false) { connection ->
            connection.executeSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_r7_blocks_fts_BEFORE_UPDATE")
            connection.executeSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_r7_blocks_fts_BEFORE_DELETE")
            connection.executeSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_r7_blocks_fts_AFTER_UPDATE")
            connection.executeSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_r7_blocks_fts_AFTER_INSERT")
        }
    }

    private suspend fun rebuildAndRestoreFtsSync(database: R7Database) {
        database.useConnection(isReadOnly = false) { connection ->
            connection.executeSQL("INSERT INTO r7_blocks_fts(r7_blocks_fts) VALUES('rebuild')")
            connection.executeSQL(
                "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_r7_blocks_fts_BEFORE_UPDATE " +
                    "BEFORE UPDATE ON r7_blocks BEGIN " +
                    "DELETE FROM r7_blocks_fts WHERE rowid=OLD.rowid; END",
            )
            connection.executeSQL(
                "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_r7_blocks_fts_BEFORE_DELETE " +
                    "BEFORE DELETE ON r7_blocks BEGIN " +
                    "DELETE FROM r7_blocks_fts WHERE rowid=OLD.rowid; END",
            )
            connection.executeSQL(
                "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_r7_blocks_fts_AFTER_UPDATE " +
                    "AFTER UPDATE ON r7_blocks BEGIN " +
                    "INSERT INTO r7_blocks_fts(rowid, norm) VALUES (NEW.rowid, NEW.norm); END",
            )
            connection.executeSQL(
                "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_r7_blocks_fts_AFTER_INSERT " +
                    "AFTER INSERT ON r7_blocks BEGIN " +
                    "INSERT INTO r7_blocks_fts(rowid, norm) VALUES (NEW.rowid, NEW.norm); END",
            )
        }
    }

    private suspend fun selectNearEndTarget(dao: R7Dao): R7Target {
        val adjacentArabicWords = Regex("[\u0621-\u064A]{3,} [\u0621-\u064A]{3,}")
        for (candidate in dao.tail(300)) {
            val phrase = adjacentArabicWords.find(ArNormalize.normalize(candidate.body))?.value ?: continue
            val match = "\"$phrase\""
            val found = dao.searchNearEnd(match) ?: continue
            if (!ArNormalize.normalize(found.body).contains(phrase)) continue
            return R7Target(match, phrase, found.blockId, found.ordinal)
        }
        error("could not find a deterministic near-end phrase target")
    }

    private fun persistSetup(context: Context, setup: R7SetupResult) {
        check(
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(READY, true)
                .putInt(ROWS, setup.rows)
                .putLong(IMPORT_MS, (setup.importMs * 1_000).toLong())
                .putLong(OPEN_MS, (setup.openMiddleMs * 1_000).toLong())
                .putLong(DATABASE_BYTES, setup.databaseBytes)
                .putLong(PEAK_RSS, setup.peakImportRssKb)
                .putString(TARGET_MATCH, setup.target.match)
                .putString(TARGET_PHRASE, setup.target.normalizedPhrase)
                .putString(TARGET_BLOCK, setup.target.blockId)
                .putInt(TARGET_ORDINAL, setup.target.ordinal)
                .commit(),
        )
    }

    private data class Frame(val offset: Long, val length: Long, val count: Int)
    private data class Index(val packageBytes: Long, val blocks: Int, val frames: List<Frame>)

    private fun readIndex(context: Context): Index {
        val json = context.assets.open(INDEX_ASSET).bufferedReader().use { JSONObject(it.readText()) }
        val framesJson = json.getJSONArray("frames")
        return Index(
            packageBytes = json.getLong("packageBytes"),
            blocks = json.getInt("blocks"),
            frames = List(framesJson.length()) { index ->
                val frame = framesJson.getJSONObject(index)
                Frame(frame.getLong("off"), frame.getLong("len"), frame.getInt("n"))
            },
        )
    }

    private fun preparePackage(context: Context, expectedBytes: Long): File {
        val file = File(context.noBackupFilesDir, PACKAGE_FILE)
        if (file.length() != expectedBytes) {
            context.assets.open(PACKAGE_ASSET).use { input ->
                file.outputStream().buffered().use(input::copyTo)
            }
        }
        check(file.length() == expectedBytes)
        return file
    }

    private fun openFrame(file: File, frame: Frame): GZIPInputStream {
        val random = RandomAccessFile(file, "r")
        random.seek(frame.offset)
        return GZIPInputStream(R7BoundedInputStream(random, frame.length), 64 * 1024)
    }

    private fun databaseBytes(file: File): Long =
        listOf(file, File(file.path + "-wal"), File(file.path + "-shm"))
            .sumOf { it.takeIf(File::exists)?.length() ?: 0 }

    private fun elapsedMs(startNanos: Long): Double =
        (SystemClock.elapsedRealtimeNanos() - startNanos) / 1_000_000.0
}

private class R7BoundedInputStream(private val file: RandomAccessFile, length: Long) : InputStream() {
    private var remaining = length

    override fun read(): Int {
        if (remaining == 0L) return -1
        return file.read().also { if (it >= 0) remaining-- }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (remaining == 0L) return -1
        return file.read(buffer, offset, minOf(length.toLong(), remaining).toInt())
            .also { if (it > 0) remaining -= it }
    }

    override fun close() = file.close()
}

private fun Double.r7OneDecimal(): String = "%.1f".format(this)
