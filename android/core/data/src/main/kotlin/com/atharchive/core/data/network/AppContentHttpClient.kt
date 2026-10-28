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
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.PublicKey
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
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

data class PackageDownloadResult(
    val resumedFrom: Long,
    val downloadedBytes: Long,
)

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

    /** [onBytes] gets the bytes received so far, as they arrive. */
    suspend fun fetchPackageIndex(entry: CatalogEntry, onBytes: (Int) -> Unit = {}): ByteArray = withContext(Dispatchers.IO) {
        val bytes = get(entry.pkg.idxPath, entry.pkg.idxSize.checkedSize("package index"), onBytes)
        ContentDigests.verify(bytes, entry.pkg.idxHash, entry.pkg.idxSize, "package index")
        bytes
    }

    suspend fun fetchFrame(entry: CatalogEntry, frame: FrameIndexEntry, onBytes: (Int) -> Unit = {}): ByteArray = withContext(Dispatchers.IO) {
        val first = frame.off
        val last = first + frame.len - 1
        val request = Request.Builder()
            .url(resolve(entry.pkg.path))
            .header("Accept-Encoding", "identity")
            .header("Range", "bytes=$first-$last")
            .build()
        withResponse(request) { response ->
            if (response.code != 206) throw ContentTransportException("frame request returned HTTP ${response.code}, expected 206")
            val expectedRange = "bytes $first-$last/${entry.pkg.size}"
            if (response.header("Content-Range") != expectedRange) {
                throw ContentTransportException("frame response has an unexpected Content-Range")
            }
            requireIdentity(response)
            val bytes = readBounded(response, frame.len, onBytes)
            ContentDigests.verify(bytes, frame.sha256, frame.len.toLong(), "package frame ${frame.ord}")
            bytes
        }
    }

    /**
     * Resumes the immutable package into [partFile]. A truncated response remains available
     * for the next attempt; a size/hash-invalid complete file is deleted so corruption can
     * never become a permanent retry loop.
     */
    suspend fun downloadPackage(
        entry: CatalogEntry,
        partFile: File,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ): PackageDownloadResult = downloadArtifact(entry.pkg.path, entry.pkg.hash, entry.pkg.size, partFile, onProgress)

    suspend fun downloadArtifact(
        path: String,
        hash: String,
        size: Long,
        partFile: File,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ): PackageDownloadResult = withContext(Dispatchers.IO) {
        require(size > 0) { "package must not be empty" }
        partFile.parentFile?.mkdirs()
        check(partFile.parentFile?.isDirectory == true) { "cannot create transfer directory" }

        if (partFile.exists() && partFile.length() > size) partFile.delete()
        if (partFile.isFile && partFile.length() == size) {
            try {
                ContentDigests.verify(partFile, hash, size, "downloaded package")
                onProgress(size, size)
                return@withContext PackageDownloadResult(size, 0)
            } catch (error: ContentIntegrityException) {
                partFile.delete()
            }
        }

        val resumedFrom = partFile.takeIf(File::isFile)?.length() ?: 0L
        val last = size - 1
        val request = Request.Builder()
            .url(resolve(path))
            .header("Accept-Encoding", "identity")
            .header("Range", "bytes=$resumedFrom-$last")
            .build()
        withResponse(request) { response ->
            if (response.code != 206) {
                throw ContentTransportException("package request returned HTTP ${response.code}, expected 206")
            }
            val expectedRange = "bytes $resumedFrom-$last/${size}"
            if (response.header("Content-Range") != expectedRange) {
                throw ContentTransportException("package response has an unexpected Content-Range")
            }
            requireIdentity(response)
            val body = response.body ?: throw ContentTransportException("package response has no body")
            val remaining = size - resumedFrom
            if (body.contentLength() > remaining) {
                throw ContentTransportException("package response exceeds its declared range")
            }
            FileOutputStream(partFile, resumedFrom > 0).use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = resumedFrom
                    onProgress(total, size)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > size) {
                            throw ContentTransportException("package response exceeds its declared size")
                        }
                        output.write(buffer, 0, read)
                        onProgress(total, size)
                    }
                    output.fd.sync()
                    if (total != size) {
                        throw ContentTransportException("package response ended at $total of ${size} bytes")
                    }
                }
            }
        }
        try {
            ContentDigests.verify(partFile, hash, size, "downloaded package")
        } catch (error: ContentIntegrityException) {
            partFile.delete()
            throw error
        }
        PackageDownloadResult(resumedFrom, size - resumedFrom)
    }

    private suspend fun get(relativePath: String, maximumBytes: Int, onBytes: (Int) -> Unit = {}): ByteArray {
        val request = Request.Builder()
            .url(resolve(relativePath))
            .header("Accept-Encoding", "identity")
            .build()
        return withResponse(request) { response ->
            if (!response.isSuccessful) throw ContentTransportException("$relativePath returned HTTP ${response.code}")
            requireIdentity(response)
            readBounded(response, maximumBytes, onBytes)
        }
    }

    private suspend fun <T> withResponse(request: Request, consume: (Response) -> T): T =
        kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                    if (continuation.isActive) continuation.resumeWith(Result.failure(ContentTransportException("app-content request failed", e)))
                }
                override fun onResponse(call: okhttp3.Call, response: Response) {
                    val result = runCatching { response.use(consume) }
                    if (continuation.isActive) continuation.resumeWith(result)
                }
            })
        }

    fun artifactUrl(relativePath: String): String = resolve(relativePath).toString()

    private fun resolve(relativePath: String): HttpUrl {
        if (
            relativePath.isBlank() || relativePath.startsWith('/') || '\\' in relativePath ||
            relativePath.split('/').any { it.isBlank() || it == "." || it == ".." }
        ) throw ContentIntegrityException("unsafe app-content path")
        val resolved = baseUrl.resolve(relativePath) ?: throw ContentIntegrityException("unsafe app-content path")
        if (resolved.host != baseUrl.host || resolved.port != baseUrl.port || resolved.scheme != baseUrl.scheme ||
            !resolved.encodedPath.startsWith(baseUrl.encodedPath) || resolved.query != null || resolved.fragment != null) {
            throw ContentIntegrityException("unsafe app-content path")
        }
        return resolved
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

    private fun readBounded(response: Response, maximumBytes: Int, onBytes: (Int) -> Unit = {}): ByteArray {
        val body = response.body ?: throw ContentTransportException("app-content response has no body")
        if (body.contentLength() > maximumBytes) throw ContentTransportException("app-content response exceeds its declared limit")
        return body.byteStream().readAtMost(maximumBytes, onBytes)
    }
}

private fun InputStream.readAtMost(maximumBytes: Int, onBytes: (Int) -> Unit = {}): ByteArray {
    val output = ByteArrayOutputStream(minOf(maximumBytes, 64 * 1024))
    val buffer = ByteArray(16 * 1024)
    var total = 0
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        total += read
        if (total > maximumBytes) throw ContentTransportException("app-content response exceeds its declared limit")
        output.write(buffer, 0, read)
        onBytes(total)
    }
    return output.toByteArray()
}

private fun Long.checkedSize(label: String): Int {
    if (this < 0 || this > Int.MAX_VALUE) throw ContentIntegrityException("$label size is unsupported")
    return toInt()
}
