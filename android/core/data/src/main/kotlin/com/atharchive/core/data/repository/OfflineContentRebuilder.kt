package com.atharchive.core.data.repository

import com.atharchive.core.data.content.AppContentJson
import com.atharchive.core.data.content.CatalogDocument
import com.atharchive.core.data.content.CatalogEntry
import com.atharchive.core.data.content.ContentDigests
import com.atharchive.core.data.content.FramedPackageDecoder
import com.atharchive.core.data.content.PackageIndex
import com.atharchive.core.data.db.content.AtharContentDatabase
import com.atharchive.core.data.db.user.AtharUserDatabase
import java.io.File
import java.io.RandomAccessFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

@Serializable
private data class RetainedPackageManifest(
    val schema: Int = 1,
    val entry: CatalogEntry,
    val packageFile: String,
    val indexFile: String,
)

data class OfflineRebuildResult(
    val retainedPackages: Int,
    val rebuiltPackages: Int,
    val rebuiltFrames: Int,
    val failures: Map<String, String>,
)

/**
 * Owns only verified, user-pinned transport packages. Disposable Range members never enter
 * this directory. M6's transfer runner will call [retainVerified]; M5 provides the offline
 * rebuild invariant now so a destructive content-DB migration cannot endanger user intent.
 */
class RetainedPackageStore(private val directory: File) {
    init {
        require(directory.path.isNotBlank())
    }

    suspend fun retainVerified(
        entry: CatalogEntry,
        packageBytes: ByteArray,
        indexBytes: ByteArray,
    ) = withContext(Dispatchers.IO) {
        ContentDigests.verify(packageBytes, entry.pkg.hash, entry.pkg.size, "retained package")
        FramedPackageDecoder.decodeIndex(indexBytes, entry)
        directory.mkdirs()
        check(directory.isDirectory) { "cannot create retained content directory" }

        val stem = safeStem(entry)
        val packageName = "$stem.${entry.pkg.hash}.athar"
        val indexName = "$stem.${entry.pkg.idxHash}.idx"
        writeAtomically(File(directory, packageName), packageBytes)
        writeAtomically(File(directory, indexName), indexBytes)
        val manifest = RetainedPackageManifest(
            entry = entry,
            packageFile = packageName,
            indexFile = indexName,
        )
        writeAtomically(
            File(directory, "$stem.json"),
            AppContentJson.encodeToString(manifest).encodeToByteArray(),
        )
    }

    internal fun manifests(): List<Pair<File, CatalogEntry>> {
        if (!directory.isDirectory) return emptyList()
        return directory.listFiles { file -> file.isFile && file.extension == "json" }
            .orEmpty()
            .sortedBy(File::getName)
            .mapNotNull { manifestFile ->
                runCatching {
                    val manifest = AppContentJson.decodeFromString<RetainedPackageManifest>(manifestFile.readText())
                    require(manifest.schema == 1)
                    val packageFile = File(directory, manifest.packageFile).canonicalFile
                    val indexFile = File(directory, manifest.indexFile).canonicalFile
                    require(packageFile.parentFile == directory.canonicalFile)
                    require(indexFile.parentFile == directory.canonicalFile)
                    require(packageFile.isFile && indexFile.isFile)
                    manifestFile to manifest.entry
                }.getOrNull()
            }
    }

    internal fun filesFor(manifestFile: File): Pair<File, File> {
        val manifest = AppContentJson.decodeFromString<RetainedPackageManifest>(manifestFile.readText())
        return File(directory, manifest.packageFile) to File(directory, manifest.indexFile)
    }

    private fun safeStem(entry: CatalogEntry): String {
        require(entry.coll.all { it.isLetterOrDigit() || it == '-' || it == '_' })
        require(entry.id.all { it.isLetterOrDigit() || it == '-' || it == '_' })
        return "${entry.coll}__${entry.id}"
    }

    private fun writeAtomically(target: File, bytes: ByteArray) {
        val temporary = File(target.parentFile, ".${target.name}.part")
        temporary.outputStream().use { output ->
            output.write(bytes)
            output.fd.sync()
        }
        check(temporary.renameTo(target)) { "cannot atomically retain ${target.name}" }
    }
}

class OfflineContentRebuilder(
    private val contentDatabase: AtharContentDatabase,
    private val userDatabase: AtharUserDatabase,
    private val store: RetainedPackageStore,
    private val importer: ContentImporter,
) {
    suspend fun rebuild(): OfflineRebuildResult = withContext(Dispatchers.IO) {
        val retained = store.manifests()
        val pinned = userDatabase.userDataDao().pinnedEntityIds().toSet()
        val selected = retained.filter { (_, entry) -> entry.id in pinned }
        if (selected.isNotEmpty()) {
            importer.applyCatalog(CatalogDocument(schema = 2, entries = selected.map { it.second }))
        }

        var packages = 0
        var frames = 0
        val failures = linkedMapOf<String, String>()
        for ((manifest, entry) in selected) {
            try {
                val (packageFile, indexFile) = store.filesFor(manifest)
                val indexBytes = indexFile.readBytes()
                val index: PackageIndex = FramedPackageDecoder.decodeIndex(indexBytes, entry)
                require(packageFile.length() == entry.pkg.size) { "retained package size differs from catalog" }
                RandomAccessFile(packageFile, "r").use { packageInput ->
                    index.frames.forEachIndexed { frameNumber, frame ->
                        val bytes = ByteArray(frame.len)
                        packageInput.seek(frame.off)
                        packageInput.readFully(bytes)
                        importer.importVerifiedFrame(entry, frameNumber, frame, bytes)
                        frames++
                    }
                }
                // The complete-object digest catches reordered or substituted frames even
                // though each member was independently authenticated during import.
                ContentDigests.verify(packageFile.readBytes(), entry.pkg.hash, entry.pkg.size, "retained package")
                packages++
            } catch (error: Throwable) {
                runCatching { contentDatabase.importDao().clearImportedContent(entry.id) }
                failures[entry.id] = error.message ?: error::class.java.simpleName
            }
        }
        OfflineRebuildResult(
            retainedPackages = selected.size,
            rebuiltPackages = packages,
            rebuiltFrames = frames,
            failures = failures,
        )
    }
}
