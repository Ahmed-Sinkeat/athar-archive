package com.atharchive.m0

import android.content.Context
import android.os.SystemClock
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

private data class PackageFrame(val offset: Long, val length: Long, val ordinal: Int, val count: Int)

private data class PackageIndex(
    val rawMarkdownBytes: Long,
    val recordBytes: Long,
    val packageBytes: Long,
    val blocks: Int,
    val frameSize: Int,
    val frames: List<PackageFrame>,
)

data class R3Result(
    val mode: String,
    val importedBlocks: Int,
    val importedFrames: Int,
    val elapsedMs: Double,
    val firstNewBlockMs: Double?,
    val importedTextBytes: Long,
    val peakRssKb: Long,
    val index: String,
) {
    val throughputMbPerSecond: Double
        get() = importedTextBytes / 1_048_576.0 / (elapsedMs / 1_000.0)

    fun report(): String = buildString {
        appendLine("$mode: PASS")
        appendLine("$importedBlocks blocks · $importedFrames frames · ${elapsedMs.r3OneDecimal()} ms")
        appendLine("throughput: ${throughputMbPerSecond.r3OneDecimal()} MiB/s")
        appendLine("peak RSS sample: ${(peakRssKb / 1024.0).r3OneDecimal()} MiB")
        firstNewBlockMs?.let { appendLine("resume to first new block: ${it.r3OneDecimal()} ms") }
        append(index)
    }
}

object M0FramedImportExperiment {
    private const val PACKAGE_ASSET = "m0/r3-big.athar"
    private const val INDEX_ASSET = "m0/r3-big.athar.idx"
    private const val DATABASE = "m0-r3.db"
    private const val PREFS = "m0-r3"
    private const val NEXT_FRAME = "next-frame"

    suspend fun runFull(context: Context): R3Result = withContext(Dispatchers.IO) {
        preparePackage(context)
        reset(context)
        import(context, maxFrames = Int.MAX_VALUE, mode = "full import")
    }

    suspend fun runFirstTenFrames(context: Context): R3Result = withContext(Dispatchers.IO) {
        preparePackage(context)
        reset(context)
        import(context, maxFrames = 10, mode = "checkpoint setup")
    }

    suspend fun resume(context: Context): R3Result = withContext(Dispatchers.IO) {
        preparePackage(context)
        import(context, maxFrames = Int.MAX_VALUE, mode = "resume after process kill")
    }

    private fun preparePackage(context: Context) {
        val index = readIndex(context)
        val packageFile = packageFile(context)
        if (packageFile.length() == index.packageBytes) return
        packageFile.parentFile?.mkdirs()
        context.assets.open(PACKAGE_ASSET).use { input ->
            packageFile.outputStream().buffered().use(input::copyTo)
        }
        check(packageFile.length() == index.packageBytes) { "package copy length mismatch" }
    }

    private fun reset(context: Context) {
        val database = databaseFile(context)
        listOf(database, File(database.path + "-wal"), File(database.path + "-shm")).forEach { if (it.exists()) it.delete() }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        BundledSQLiteDriver().open(database.absolutePath).use { connection ->
            connection.execSQL("PRAGMA journal_mode=DELETE")
            connection.execSQL("PRAGMA synchronous=NORMAL")
            connection.execSQL(
                "CREATE TABLE blocks(rowid INTEGER PRIMARY KEY, block_id TEXT NOT NULL UNIQUE, body TEXT NOT NULL)",
            )
        }
    }

    private fun import(context: Context, maxFrames: Int, mode: String): R3Result {
        val index = readIndex(context)
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val startFrame = preferences.getInt(NEXT_FRAME, 0)
        check(startFrame < index.frames.size) { "nothing left to resume" }
        check(databaseFile(context).exists()) { "run checkpoint setup first" }
        val remainingFrames = index.frames.size - startFrame
        val endFrame = if (maxFrames >= remainingFrames) index.frames.size else startFrame + maxFrames
        var importedBlocks = 0
        var importedTextBytes = 0L
        var firstNewBlockMs: Double? = null
        var peakRssKb = currentRssKb()
        val start = SystemClock.elapsedRealtimeNanos()

        BundledSQLiteDriver().open(databaseFile(context).absolutePath).use { connection ->
            for (frameIndex in startFrame until endFrame) {
                val frame = index.frames[frameIndex]
                connection.execSQL("BEGIN IMMEDIATE")
                try {
                    connection.prepare("INSERT INTO blocks(rowid, block_id, body) VALUES (?, ?, ?)").use { insert ->
                        openFrame(packageFile(context), frame).bufferedReader().useLines { lines ->
                            for (line in lines) {
                                if (line.isBlank()) continue
                                val record = JSONObject(line)
                                if (record.getString("t") == "header") continue
                                val body = record.getString("x")
                                insert.bindLong(1, record.getInt("i").toLong() + 1)
                                insert.bindText(2, record.getString("id"))
                                insert.bindText(3, body)
                                insert.step()
                                insert.reset()
                                insert.clearBindings()
                                importedBlocks++
                                importedTextBytes += body.toByteArray(Charsets.UTF_8).size
                                if (firstNewBlockMs == null) firstNewBlockMs = elapsedMs(start)
                            }
                        }
                    }
                    connection.execSQL("COMMIT")
                } catch (error: Throwable) {
                    connection.execSQL("ROLLBACK")
                    throw error
                }
                check(preferences.edit().putInt(NEXT_FRAME, frameIndex + 1).commit())
                peakRssKb = maxOf(peakRssKb, currentRssKb())
            }
        }

        val elapsed = elapsedMs(start)
        val overhead = index.recordBytes.toDouble() / index.rawMarkdownBytes
        val packageRatio = index.packageBytes.toDouble() / index.rawMarkdownBytes
        return R3Result(
            mode = mode,
            importedBlocks = importedBlocks,
            importedFrames = endFrame - startFrame,
            elapsedMs = elapsed,
            firstNewBlockMs = if (startFrame > 0) firstNewBlockMs else null,
            importedTextBytes = importedTextBytes,
            peakRssKb = peakRssKb,
            index = "frame size ${index.frameSize} · ${index.frames.size} frames · ${index.blocks} total blocks\n" +
                "record overhead ${overhead.r3ThreeDecimals()}× · package/raw ${packageRatio.r3ThreeDecimals()}×",
        )
    }

    private fun readIndex(context: Context): PackageIndex {
        val json = context.assets.open(INDEX_ASSET).bufferedReader().use { JSONObject(it.readText()) }
        val framesJson = json.getJSONArray("frames")
        val frames = List(framesJson.length()) { index ->
            val frame = framesJson.getJSONObject(index)
            PackageFrame(
                offset = frame.getLong("off"),
                length = frame.getLong("len"),
                ordinal = frame.getInt("ord"),
                count = frame.getInt("n"),
            )
        }
        return PackageIndex(
            rawMarkdownBytes = json.getLong("rawMarkdown"),
            recordBytes = json.getLong("uncompressedRecords"),
            packageBytes = json.getLong("packageBytes"),
            blocks = json.getInt("blocks"),
            frameSize = json.getInt("frameSize"),
            frames = frames,
        )
    }

    private fun openFrame(file: File, frame: PackageFrame): GZIPInputStream {
        val random = RandomAccessFile(file, "r")
        random.seek(frame.offset)
        return GZIPInputStream(BoundedRandomAccessInputStream(random, frame.length), 64 * 1024)
    }

    private fun currentRssKb(): Long = runCatching {
        File("/proc/self/status").useLines { lines ->
            lines.first { it.startsWith("VmRSS:") }.filter(Char::isDigit).toLong()
        }
    }.getOrDefault(0)

    private fun packageFile(context: Context) = File(context.noBackupFilesDir, "r3-big.athar")
    private fun databaseFile(context: Context) = File(context.noBackupFilesDir, DATABASE)
    private fun elapsedMs(startNanos: Long): Double =
        (SystemClock.elapsedRealtimeNanos() - startNanos) / 1_000_000.0
}

private class BoundedRandomAccessInputStream(
    private val file: RandomAccessFile,
    length: Long,
) : InputStream() {
    private var remaining = length

    override fun read(): Int {
        if (remaining == 0L) return -1
        val value = file.read()
        if (value >= 0) remaining--
        return value
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (remaining == 0L) return -1
        val requested = minOf(length.toLong(), remaining).toInt()
        val read = file.read(buffer, offset, requested)
        if (read > 0) remaining -= read
        return read
    }

    override fun close() = file.close()
}

private fun Double.r3OneDecimal(): String = "%.1f".format(this)
private fun Double.r3ThreeDecimals(): String = "%.3f".format(this)
