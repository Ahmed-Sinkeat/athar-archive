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

    /**
     * Resumes the immutable package into [partFile]. A truncated response remains available
     * for the next attempt; a size/hash-invalid complete file is deleted so corruption can
     * never become a permanent retry loop.
     */
    suspend fun downloadPackage(
        entry: CatalogEntry,
        partFile: File,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ): PackageDownloadResult = withContext(Dispatchers.IO) {
        require(entry.pkg.size > 0) { "package must not be empty" }
        partFile.parentFile?.mkdirs()
        check(partFile.parentFile?.isDirectory == true) { "cannot create transfer directory" }

        if (partFile.exists() && partFile.length() > entry.pkg.size) partFile.delete()
        if (partFile.isFile && partFile.length() == entry.pkg.size) {
            try {
                ContentDigests.verify(partFile, entry.pkg.hash, entry.pkg.size, "downloaded package")
                onProgress(entry.pkg.size, entry.pkg.size)
                return@withContext PackageDownloadResult(entry.pkg.size, 0)
            } catch (error: ContentIntegrityException) {
                partFile.delete()
            }
        }

        val resumedFrom = partFile.takeIf(File::isFile)?.length() ?: 0L
        val last = entry.pkg.size - 1
        val request = Request.Builder()
            .url(resolve(entry.pkg.path))
            .header("Accept-Encoding", "identity")
            .header("Range", "bytes=$resumedFrom-$last")
            .build()
        execute(request).use { response ->
            if (response.code != 206) {
                throw ContentTransportException("package request returned HTTP ${response.code}, expected 206")
            }
            val expectedRange = "bytes $resumedFrom-$last/${entry.pkg.size}"
            if (response.header("Content-Range") != expectedRange) {
                throw ContentTransportException("package response has an unexpected Content-Range")
            }
            requireIdentity(response)
            val body = response.body ?: throw ContentTransportException("package response has no body")
            val remaining = entry.pkg.size - resumedFrom
            if (body.contentLength() > remaining) {
                throw ContentTransportException("package response exceeds its declared range")
            }
            FileOutputStream(partFile, resumedFrom > 0).use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = resumedFrom
                    onProgress(total, entry.pkg.size)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > entry.pkg.size) {
                            throw ContentTransportException("package response exceeds its declared size")
                        }
                        output.write(buffer, 0, read)
                        onProgress(total, entry.pkg.size)
                    }
                    output.fd.sync()
                    if (total != entry.pkg.size) {
                        throw ContentTransportException("package response ended at $total of ${entry.pkg.size} bytes")
                    }
                }
            }
        }
        try {
            ContentDigests.verify(partFile, entry.pkg.hash, entry.pkg.size, "downloaded package")
        } catch (error: ContentIntegrityException) {
            partFile.delete()
            throw error
        }
        PackageDownloadResult(resumedFrom, entry.pkg.size - resumedFrom)
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
