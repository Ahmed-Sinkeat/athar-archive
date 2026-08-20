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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContentTransferRunnerTest {
    private lateinit var content: AtharContentDatabase
    private lateinit var user: AtharUserDatabase
    private lateinit var server: MockWebServer
    private lateinit var root: File

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        content = Room.inMemoryDatabaseBuilder<AtharContentDatabase>(context)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .setSingleConnectionPool()
            .build()
        user = Room.inMemoryDatabaseBuilder<AtharUserDatabase>(context)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .setSingleConnectionPool()
            .build()
        root = File(context.cacheDir, "m6-transfer-${System.nanoTime()}")
        root.mkdirs()
    }

    @After
    fun tearDown() {
        content.close()
        user.close()
        if (::server.isInitialized) server.shutdown()
        root.deleteRecursively()
    }

    @Test
    fun resumedDownloadImportsRetainsAndUnpinMakesContentEvictable() = runBlocking {
        val packageBytes = gzip(
            """{"t":"header","schema":2,"coll":"book","id":"offline-book","v":1,"blocks":1,"chapters":[{"a":"main","title":"المقدمة","block":0}],"footnotes":0}""",
            """{"t":"p","a":"main","i":0,"id":"00000000000000000000000000000001","fp":"0000000000000001","x":"تنزيل مستأنف يعمل بلا شبكة"}""",
        )
        val packageHash = ContentDigests.sha256Hex(packageBytes)
        val indexBytes = index(packageBytes, packageHash)
        val indexHash = ContentDigests.sha256Hex(indexBytes)
        val entry = entry(packageBytes, packageHash, indexBytes, indexHash)
        ContentImporter(content).applyCatalog(CatalogDocument(2, listOf(entry)))

        val certificate = HeldCertificate.Builder()
            .commonName("localhost")
            .addSubjectAlternativeName("localhost")
            .build()
        val serverCertificates = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientCertificates = HandshakeCertificates.Builder()
            .addTrustedCertificate(certificate.certificate)
            .build()
        server = MockWebServer().also {
            it.useHttps(serverCertificates.sslSocketFactory(), false)
            it.start()
        }
        val httpClient = AppContentHttpClient(
            server.url("/app/v2/"),
            OkHttpClient.Builder()
                .sslSocketFactory(clientCertificates.sslSocketFactory(), clientCertificates.trustManager)
                .followRedirects(false)
                .build(),
        )
        val store = RetainedPackageStore(File(root, "content"))
        val transfers = File(root, "transfers")
        val runner = ContentTransferRunner(
            contentDatabase = content,
            userDatabase = user,
            client = httpClient,
            importer = ContentImporter(content),
            retainedStore = store,
            transferDirectory = transfers,
            storagePolicy = ContentStoragePolicy(root, ContentCacheManager(content, user)) { Long.MAX_VALUE },
        )
        runner.requestPin(entry.id)

        val prefix = packageBytes.size / 3
        transfers.mkdirs()
        File(transfers, "book__${entry.id}.$packageHash.part")
            .writeBytes(packageBytes.copyOfRange(0, prefix))
        server.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(indexBytes)))
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader(
                    "Content-Range",
                    "bytes $prefix-${packageBytes.lastIndex}/${packageBytes.size}",
                )
                .setBody(Buffer().write(packageBytes, prefix, packageBytes.size - prefix)),
        )

        val result = runner.downloadPinned(entry.id)

        assertEquals(prefix.toLong(), result.resumedFrom)
        assertEquals(ContentAvailability.COMPLETE, content.importDao().entity(entry.id)?.availability)
        assertEquals("تنزيل مستأنف يعمل بلا شبكة", content.importDao().blocks(entry.id).single().text)
        assertTrue(store.containsVerified(entry))
        assertTrue(transfers.listFiles().orEmpty().isEmpty())
        server.takeRequest()
        assertEquals(
            "bytes=$prefix-${packageBytes.lastIndex}",
            server.takeRequest().getHeader("Range"),
        )

        runner.unpin(entry.id)

        assertNull(user.userDataDao().pinnedDownload(entry.id))
        assertFalse(store.containsVerified(entry))
        assertEquals(ContentAvailability.COMPLETE, content.importDao().entity(entry.id)?.availability)
        ContentCacheManager(content, user).clearUnpinned()
        assertEquals(ContentAvailability.ABSENT, content.importDao().entity(entry.id)?.availability)
    }

    @Test
    fun completedPackageAndVerifiedIndexResumeImportWithoutNetwork() = runBlocking {
        val firstFrame = gzip(
            """{"t":"header","schema":2,"coll":"book","id":"offline-book","v":1,"blocks":2,"chapters":[{"a":"main","title":"المقدمة","block":0}],"footnotes":0}""",
            """{"t":"p","a":"main","i":0,"id":"00000000000000000000000000000001","fp":"0000000000000001","x":"الإطار الأول"}""",
        )
        val secondFrame = gzip(
            """{"t":"p","a":"main","i":1,"id":"00000000000000000000000000000002","fp":"0000000000000002","x":"الإطار الثاني"}""",
        )
        val packageBytes = firstFrame + secondFrame
        val packageHash = ContentDigests.sha256Hex(packageBytes)
        val frames = listOf(
            FrameIndexEntry(0, firstFrame.size, 0, 1, ContentDigests.sha256Hex(firstFrame)),
            FrameIndexEntry(
                firstFrame.size.toLong(),
                secondFrame.size,
                1,
                1,
                ContentDigests.sha256Hex(secondFrame),
            ),
        )
        val indexBytes = twoFrameIndex(frames)
        val entry = entry(
            packageBytes = packageBytes,
            packageHash = packageHash,
            indexBytes = indexBytes,
            indexHash = ContentDigests.sha256Hex(indexBytes),
            blocks = 2,
        )
        val importer = ContentImporter(content)
        importer.applyCatalog(CatalogDocument(2, listOf(entry)))
        importer.importVerifiedFrame(entry, 0, frames[0], firstFrame)

        val certificate = HeldCertificate.Builder()
            .commonName("localhost")
            .addSubjectAlternativeName("localhost")
            .build()
        val serverCertificates = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientCertificates = HandshakeCertificates.Builder()
            .addTrustedCertificate(certificate.certificate)
            .build()
        server = MockWebServer().also {
            it.useHttps(serverCertificates.sslSocketFactory(), false)
            it.start()
        }
        val transfers = File(root, "transfers").apply { mkdirs() }
        File(transfers, "book__${entry.id}.${entry.pkg.hash}.part").writeBytes(packageBytes)
        File(transfers, "book__${entry.id}.${entry.pkg.idxHash}.idx").writeBytes(indexBytes)
        val runner = ContentTransferRunner(
            contentDatabase = content,
            userDatabase = user,
            client = AppContentHttpClient(
                server.url("/app/v2/"),
                OkHttpClient.Builder()
                    .sslSocketFactory(clientCertificates.sslSocketFactory(), clientCertificates.trustManager)
                    .followRedirects(false)
                    .build(),
            ),
            importer = importer,
            retainedStore = RetainedPackageStore(File(root, "content")),
            transferDirectory = transfers,
            storagePolicy = ContentStoragePolicy(root, ContentCacheManager(content, user)) { Long.MAX_VALUE },
        )
        runner.requestPin(entry.id)

        val result = runner.downloadPinned(entry.id)

        assertEquals(packageBytes.size.toLong(), result.resumedFrom)
        assertEquals(1, result.importedFrames)
        assertEquals(listOf("الإطار الأول", "الإطار الثاني"), content.importDao().blocks(entry.id).map { it.text })
        assertEquals(0, server.requestCount)
        assertTrue(transfers.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun ordinaryOpenCreatesNoPinAndClosingReaderStopsAdjacentPrefetch() = runBlocking {
        val firstFrame = gzip(
            """{"t":"header","schema":2,"coll":"book","id":"offline-book","v":1,"blocks":2,"chapters":[{"a":"main","title":"المقدمة","block":0}],"footnotes":0}""",
            """{"t":"p","a":"main","i":0,"id":"00000000000000000000000000000001","fp":"0000000000000001","x":"قراءة عادية"}""",
        )
        val secondFrame = gzip(
            """{"t":"p","a":"main","i":1,"id":"00000000000000000000000000000002","fp":"0000000000000002","x":"لا يبدأ بعد الإغلاق"}""",
        )
        val packageBytes = firstFrame + secondFrame
        val frames = listOf(
            FrameIndexEntry(0, firstFrame.size, 0, 1, ContentDigests.sha256Hex(firstFrame)),
            FrameIndexEntry(
                firstFrame.size.toLong(),
                secondFrame.size,
                1,
                1,
                ContentDigests.sha256Hex(secondFrame),
            ),
        )
        val indexBytes = twoFrameIndex(frames)
        val entry = entry(
            packageBytes = packageBytes,
            packageHash = ContentDigests.sha256Hex(packageBytes),
            indexBytes = indexBytes,
            indexHash = ContentDigests.sha256Hex(indexBytes),
            blocks = 2,
        )
        val importer = ContentImporter(content)
        importer.applyCatalog(CatalogDocument(2, listOf(entry)))

        val certificate = HeldCertificate.Builder()
            .commonName("localhost")
            .addSubjectAlternativeName("localhost")
            .build()
        val serverCertificates = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientCertificates = HandshakeCertificates.Builder()
            .addTrustedCertificate(certificate.certificate)
            .build()
        server = MockWebServer().also {
            it.useHttps(serverCertificates.sslSocketFactory(), false)
            it.start()
        }
        server.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(indexBytes)))
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader(
                    "Content-Range",
                    "bytes 0-${firstFrame.lastIndex}/${packageBytes.size}",
                )
                .setBody(Buffer().write(firstFrame)),
        )
        val repository = ReadThroughContentRepository(
            database = content,
            userDatabase = user,
            client = AppContentHttpClient(
                server.url("/app/v2/"),
                OkHttpClient.Builder()
                    .sslSocketFactory(clientCertificates.sslSocketFactory(), clientCertificates.trustManager)
                    .followRedirects(false)
                    .build(),
            ),
            importer = importer,
        )

        val opened = repository.openFrame(entry.id, 0)
        val session = repository.prefetchAdjacent(this, entry.id, 0)
        session.close()
        yield()

        assertFalse(opened.fromCache)
        assertEquals(ContentAvailability.PARTIAL, content.importDao().entity(entry.id)?.availability)
        assertNull(user.userDataDao().pinnedDownload(entry.id))
        assertEquals(2, server.requestCount)
        ContentCacheManager(content, user).clearUnpinned()
        assertEquals(ContentAvailability.ABSENT, content.importDao().entity(entry.id)?.availability)
    }

    private fun entry(
        packageBytes: ByteArray,
        packageHash: String,
        indexBytes: ByteArray,
        indexHash: String,
        blocks: Int = 1,
    ) = CatalogEntry(
        id = "offline-book",
        coll = "book",
        v = 1,
        hash = "1".repeat(64),
        title = "كتاب التنزيل",
        pkg = PackageReference(
            path = "content/book/offline-book/$packageHash.athar",
            hash = packageHash,
            size = packageBytes.size.toLong(),
            idxPath = "content/book/offline-book/$indexHash.athar.idx",
            idxHash = indexHash,
            idxSize = indexBytes.size.toLong(),
            uncompressed = 300,
            blocks = blocks,
            chapters = 1,
        ),
    )

    private fun index(packageBytes: ByteArray, packageHash: String) =
        """{"schema":2,"coll":"book","entityId":"offline-book","v":1,"frames":[{"off":0,"len":${packageBytes.size},"ord":0,"n":1,"sha256":"$packageHash"}]}"""
            .encodeToByteArray()

    private fun twoFrameIndex(frames: List<FrameIndexEntry>) =
        """{"schema":2,"coll":"book","entityId":"offline-book","v":1,"frames":[{"off":${frames[0].off},"len":${frames[0].len},"ord":0,"n":1,"sha256":"${frames[0].sha256}"},{"off":${frames[1].off},"len":${frames[1].len},"ord":1,"n":1,"sha256":"${frames[1].sha256}"}]}"""
            .encodeToByteArray()

    private fun gzip(vararg records: String): ByteArray {
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
