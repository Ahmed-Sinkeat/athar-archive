package com.atharchive.core.data.network

import com.atharchive.core.data.content.AppContentDocuments
import com.atharchive.core.data.content.ArtifactReference
import com.atharchive.core.data.content.CatalogDocument
import com.atharchive.core.data.content.CatalogEntry
import com.atharchive.core.data.content.ContentDigests
import com.atharchive.core.data.content.ContentIntegrityException
import com.atharchive.core.data.content.FrameIndexEntry
import com.atharchive.core.data.content.MAX_SIGNED_DOCUMENT_BYTES
import com.atharchive.core.data.content.RootPayload
import com.atharchive.core.data.content.SignedRootVerifier
import com.atharchive.core.data.content.TombstoneDocument
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.PublicKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class ContentTransportException(message: String, cause: Throwable? = null) : Exception(message, cause)

data class VerifiedGeneration(
    val root: RootPayload,
    val catalog: CatalogDocument,
    val tombstones: TombstoneDocument,
)

data class VerifiedRoot(val root: RootPayload)

class AppContentHttpClient(
    private val baseUrl: HttpUrl,
    private val client: OkHttpClient,
) {
    init {
        require(baseUrl.isHttps) { "app-content origin must use HTTPS" }
        require(baseUrl.query == null && baseUrl.fragment == null) { "app-content base URL cannot contain a query or fragment" }
        require(baseUrl.encodedPath.endsWith("/app/v2/")) { "app-content base URL must end with /app/v2/" }
    }

    suspend fun fetchRoot(trustedKeys: Map<String, PublicKey>): VerifiedRoot = withContext(Dispatchers.IO) {
        val envelope = get("index.json", MAX_SIGNED_DOCUMENT_BYTES)
        VerifiedRoot(SignedRootVerifier.verify(envelope, trustedKeys))
    }

    suspend fun fetchGeneration(root: RootPayload): VerifiedGeneration = withContext(Dispatchers.IO) {
        val catalogBytes = get(root.catalog.path, root.catalog.size.checkedSize("catalog"))
        val tombstoneBytes = get(root.tombstones.path, root.tombstones.size.checkedSize("tombstones"))
        VerifiedGeneration(
            root = root,
            catalog = AppContentDocuments.decodeCatalog(catalogBytes, root.catalog),
            tombstones = AppContentDocuments.decodeTombstones(tombstoneBytes, root.tombstones),
        )
    }

    suspend fun fetchGeneration(trustedKeys: Map<String, PublicKey>): VerifiedGeneration {
        return fetchGeneration(fetchRoot(trustedKeys).root)
    }

    suspend fun fetchPackageIndex(entry: CatalogEntry): ByteArray = withContext(Dispatchers.IO) {
        val bytes = get(entry.pkg.idxPath, entry.pkg.idxSize.checkedSize("package index"))
        ContentDigests.verify(bytes, entry.pkg.idxHash, entry.pkg.idxSize, "package index")
        bytes
    }

    suspend fun fetchFrame(entry: CatalogEntry, frame: FrameIndexEntry): ByteArray = withContext(Dispatchers.IO) {
        val first = frame.off
        val last = first + frame.len - 1
        val request = Request.Builder()
            .url(resolve(entry.pkg.path))
            .header("Accept-Encoding", "identity")
            .header("Range", "bytes=$first-$last")
            .build()
        execute(request).use { response ->
            if (response.code != 206) throw ContentTransportException("frame request returned HTTP ${response.code}, expected 206")
            val expectedRange = "bytes $first-$last/${entry.pkg.size}"
            if (response.header("Content-Range") != expectedRange) {
                throw ContentTransportException("frame response has an unexpected Content-Range")
            }
            requireIdentity(response)
            val bytes = readBounded(response, frame.len)
            ContentDigests.verify(bytes, frame.sha256, frame.len.toLong(), "package frame ${frame.ord}")
            bytes
        }
    }

    private fun get(relativePath: String, maximumBytes: Int): ByteArray {
        val request = Request.Builder()
            .url(resolve(relativePath))
            .header("Accept-Encoding", "identity")
            .build()
        execute(request).use { response ->
            if (!response.isSuccessful) throw ContentTransportException("$relativePath returned HTTP ${response.code}")
            requireIdentity(response)
            return readBounded(response, maximumBytes)
        }
    }

    private fun execute(request: Request): Response = try {
        client.newCall(request).execute()
    } catch (error: Exception) {
        throw ContentTransportException("app-content request failed", error)
    }

    private fun resolve(relativePath: String): HttpUrl {
        if (
            relativePath.isBlank() || relativePath.startsWith('/') || '\\' in relativePath ||
            relativePath.split('/').any { it.isBlank() || it == "." || it == ".." }
        ) throw ContentIntegrityException("unsafe app-content path")
        return baseUrl.resolve(relativePath) ?: throw ContentIntegrityException("unsafe app-content path")
    }

    private fun requireIdentity(response: Response) {
        val encoding = response.header("Content-Encoding")
        if (encoding != null && !encoding.equals("identity", ignoreCase = true)) {
            throw ContentTransportException("app-content response was transformed with $encoding")
        }
        if (response.request.url.host != baseUrl.host) {
            throw ContentTransportException("app-content request left the configured origin")
        }
    }

    private fun readBounded(response: Response, maximumBytes: Int): ByteArray {
        val body = response.body ?: throw ContentTransportException("app-content response has no body")
        if (body.contentLength() > maximumBytes) throw ContentTransportException("app-content response exceeds its declared limit")
        return body.byteStream().readAtMost(maximumBytes)
    }
}

private fun InputStream.readAtMost(maximumBytes: Int): ByteArray {
    val output = ByteArrayOutputStream(minOf(maximumBytes, 64 * 1024))
    val buffer = ByteArray(16 * 1024)
    var total = 0
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        total += read
        if (total > maximumBytes) throw ContentTransportException("app-content response exceeds its declared limit")
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}

private fun Long.checkedSize(label: String): Int {
    if (this < 0 || this > Int.MAX_VALUE) throw ContentIntegrityException("$label size is unsupported")
    return toInt()
}
