package com.atharchive.core.data.repository

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.atharchive.core.data.content.CatalogDocument
import com.atharchive.core.data.content.CatalogEntry
import com.atharchive.core.data.content.ContentDigests
import com.atharchive.core.data.content.FrameIndexEntry
import com.atharchive.core.data.content.PackageReference
import com.atharchive.core.data.db.content.AtharContentDatabase
import com.atharchive.core.data.db.content.ContentAvailability
import com.atharchive.core.data.db.user.AtharUserDatabase
import com.atharchive.core.data.network.AppContentHttpClient
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * «تنزيل الكل» froze the app: page reads queued behind every download's writes. Three large
 * imports run at once here (the most `transferSlots` allows) while the reader keeps opening a
 * page the way the open path does; every one of those must stay fast.
 */
@RunWith(AndroidJUnit4::class)
class ContentWriteStressTest {
    private lateinit var content: AtharContentDatabase
    private lateinit var user: AtharUserDatabase
    private lateinit var root: File

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        root = File(context.cacheDir, "stress-${System.nanoTime()}").apply { mkdirs() }
        // A file database with Room's default pool, as in the app: WAL, readers beside the writer.
        content = Room.databaseBuilder<AtharContentDatabase>(context, File(root, "content.db").absolutePath)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        user = Room.inMemoryDatabaseBuilder<AtharUserDatabase>(context)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .setSingleConnectionPool()
            .build()
    }

    @After
    fun tearDown() {
        content.close()
        user.close()
        root.deleteRecursively()
    }

    @Test
    fun pageReadsStayFastWhileThreeBooksImport() = runBlocking {
        val books = (1..3).map { book("bulk-$it", frames = 120, blocksPerFrame = 40) }
        val open = book("open-book", frames = 1, blocksPerFrame = 40)
        val importer = ContentImporter(content)
        importer.applyCatalog(CatalogDocument(2, (books + open).map { it.entry }))
        importer.importVerifiedFrame(open.entry, 0, open.frames[0], open.frameBytes[0])

        val lock = ContentWriteLock()
        val transfers = File(root, "transfers").apply { mkdirs() }
        val runner = ContentTransferRunner(
            contentDatabase = content,
            userDatabase = user,
            // Never contacted: every package and index is already on disk.
            client = AppContentHttpClient("https://127.0.0.1:9/app/v2/".toHttpUrl(), OkHttpClient()),
            importer = importer,
            retainedStore = RetainedPackageStore(File(root, "content")),
            transferDirectory = transfers,
            storagePolicy = ContentStoragePolicy(root, ContentCacheManager(content, user)) { Long.MAX_VALUE },
            mutationMutex = lock,
        )
        for (book in books) {
            File(transfers, "book__${book.entry.id}.${book.entry.pkg.hash}.part").writeBytes(book.packageBytes)
            File(transfers, "book__${book.entry.id}.${book.entry.pkg.idxHash}.idx").writeBytes(book.indexBytes)
            runner.requestPin(book.entry.id)
        }

        val waits = mutableListOf<Long>()
        val lockWaits = mutableListOf<Long>()
        val importStart = System.nanoTime()
        val imports = books.map { book -> async(Dispatchers.IO) { runner.downloadPinned(book.entry.id) } }
        val reader = launch(Dispatchers.IO) {
            while (true) {
                val started = System.nanoTime()
                lock.forReader { }
                val locked = System.nanoTime()
                content.importDao().blockWindow(open.entry.id, 0, 39)
                synchronized(waits) {
                    waits += (System.nanoTime() - started) / 1_000_000
                    lockWaits += (locked - started) / 1_000_000
                }
                delay(15)
            }
        }
        imports.awaitAll()
        reader.cancel()

        android.util.Log.i("AtharStress", "imports took ${(System.nanoTime() - importStart) / 1_000_000} ms")
        books.forEach { assertEquals(ContentAvailability.COMPLETE, content.importDao().state(it.entry.id)?.availability) }
        val sorted = synchronized(waits) { waits.sorted() }
        assertTrue("too few reads during the imports: ${sorted.size}", sorted.size >= 20)
        val median = sorted[sorted.size / 2]
        val worst = sorted.last()
        android.util.Log.i("AtharStress", "reads ${sorted.size}, median $median ms, worst $worst ms; lock wait median ${lockWaits.sorted()[lockWaits.size / 2]} ms, worst ${lockWaits.maxOrNull()} ms")
        // One commit (~50 ms of writing, plus its sync) is the most a read may wait for. On the
        // Redmi 12 it was 750 ms median and 917 ms worst with the decode inside the lock and
        // fixed eight-frame commits; now about 55 ms median, 200 ms worst.
        assertTrue("median page read ${median} ms", median < 100)
        assertTrue("slowest page read ${worst} ms", worst < 400)
    }

    private class Book(
        val entry: CatalogEntry,
        val packageBytes: ByteArray,
        val indexBytes: ByteArray,
        val frames: List<FrameIndexEntry>,
        val frameBytes: List<ByteArray>,
    )

    private fun book(id: String, frames: Int, blocksPerFrame: Int): Book {
        val blocks = frames * blocksPerFrame
        val frameBytes = (0 until frames).map { f ->
            val records = buildList {
                if (f == 0) {
                    add("""{"t":"header","schema":2,"coll":"book","id":"$id","v":1,"blocks":$blocks,"chapters":[{"a":"main","title":"المقدمة","block":0}],"footnotes":0}""")
                }
                for (b in 0 until blocksPerFrame) {
                    val i = f * blocksPerFrame + b
                    add("""{"t":"p","a":"main","i":$i,"id":"${"%032x".format(i + 1)}","fp":"${"%016x".format(i + 1)}","x":"${"فقرة من نص الكتاب للاختبار ".repeat(12)}$i"}""")
                }
            }
            gzip(records)
        }
        var offset = 0L
        val index = frameBytes.mapIndexed { f, bytes ->
            FrameIndexEntry(offset, bytes.size, f * blocksPerFrame, blocksPerFrame, ContentDigests.sha256Hex(bytes)).also { offset += bytes.size }
        }
        val packageBytes = frameBytes.reduce(ByteArray::plus)
        val packageHash = ContentDigests.sha256Hex(packageBytes)
        val indexBytes = (
            """{"schema":2,"coll":"book","entityId":"$id","v":1,"frames":[""" +
                index.joinToString(",") { """{"off":${it.off},"len":${it.len},"ord":${it.ord},"n":${it.n},"sha256":"${it.sha256}"}""" } +
                "]}"
            ).encodeToByteArray()
        val indexHash = ContentDigests.sha256Hex(indexBytes)
        val entry = CatalogEntry(
            id = id,
            coll = "book",
            v = 1,
            hash = "1".repeat(64),
            title = "كتاب $id",
            pkg = PackageReference(
                path = "content/book/$id/$packageHash.athar",
                hash = packageHash,
                size = packageBytes.size.toLong(),
                idxPath = "content/book/$id/$indexHash.athar.idx",
                idxHash = indexHash,
                idxSize = indexBytes.size.toLong(),
                uncompressed = blocks * 400L,
                blocks = blocks,
                chapters = 1,
            ),
        )
        return Book(entry, packageBytes, indexBytes, index, frameBytes)
    }

    private fun gzip(records: List<String>): ByteArray {
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).bufferedWriter(Charsets.UTF_8).use { writer ->
            records.forEach {
                writer.write(it)
                writer.newLine()
            }
        }
        return output.toByteArray()
    }
}
