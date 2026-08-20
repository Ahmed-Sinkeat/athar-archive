package com.atharchive.core.data.network

import com.atharchive.core.data.content.CatalogEntry
import com.atharchive.core.data.content.ContentDigests
import com.atharchive.core.data.content.ContentIntegrityException
import com.atharchive.core.data.content.FrameIndexEntry
import com.atharchive.core.data.content.PackageReference
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class AppContentHttpClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: AppContentHttpClient

    @Before
    fun setUp() {
        val certificate = HeldCertificate.Builder()
            .commonName("localhost")
            .addSubjectAlternativeName("localhost")
            .build()
        val serverCertificates = HandshakeCertificates.Builder()
            .heldCertificate(certificate)
            .build()
        val clientCertificates = HandshakeCertificates.Builder()
            .addTrustedCertificate(certificate.certificate)
            .build()
        server = MockWebServer().also {
            it.useHttps(serverCertificates.sslSocketFactory(), false)
            it.start()
        }
        val okhttp = OkHttpClient.Builder()
            .sslSocketFactory(clientCertificates.sslSocketFactory(), clientCertificates.trustManager)
            .followRedirects(false)
            .build()
        client = AppContentHttpClient(server.url("/app/v2/"), okhttp)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun requestsAndVerifiesTheExactCompressedFrameRange() = runBlocking {
        val bytes = "compressed-frame".encodeToByteArray()
        val frame = FrameIndexEntry(10, bytes.size, 2_000, 25, ContentDigests.sha256Hex(bytes))
        val entry = entry(packageSize = 10L + bytes.size)
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Range", "bytes 10-${9 + bytes.size}/${entry.pkg.size}")
                .setBody(Buffer().write(bytes)),
        )

        assertArrayEquals(bytes, client.fetchFrame(entry, frame))
        val request = server.takeRequest()
        assertEquals("bytes=10-${9 + bytes.size}", request.getHeader("Range"))
        assertEquals("identity", request.getHeader("Accept-Encoding"))
    }

    @Test
    fun rejectsASilentPartialRangeResponse() {
        val expected = "complete".encodeToByteArray()
        val frame = FrameIndexEntry(0, expected.size, 0, 1, ContentDigests.sha256Hex(expected))
        val entry = entry(packageSize = expected.size.toLong())
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Range", "bytes 0-${expected.lastIndex}/${expected.size}")
                .setBody("short"),
        )

        assertThrows(ContentIntegrityException::class.java) {
            runBlocking { client.fetchFrame(entry, frame) }
        }
    }

    private fun entry(packageSize: Long) = CatalogEntry(
        id = "test-book",
        coll = "book",
        v = 1,
        hash = "1".repeat(64),
        title = "كتاب اختبار",
        pkg = PackageReference(
            path = "content/book/test-book/${"2".repeat(64)}.athar",
            hash = "2".repeat(64),
            size = packageSize,
            idxPath = "content/book/test-book/${"3".repeat(64)}.athar.idx",
            idxHash = "3".repeat(64),
            idxSize = 100,
            uncompressed = 200,
            blocks = 25,
            chapters = 1,
        ),
    )
}
