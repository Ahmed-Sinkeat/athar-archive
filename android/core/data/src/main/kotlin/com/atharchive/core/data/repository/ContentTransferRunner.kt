package com.atharchive.core.data.repository

import com.atharchive.core.data.content.CatalogEntry
import com.atharchive.core.data.content.ContentDigests
import com.atharchive.core.data.content.FramedPackageDecoder
import com.atharchive.core.data.db.content.AtharContentDatabase
import com.atharchive.core.data.db.content.ContentAvailability
import com.atharchive.core.data.db.content.ContentTransferState
import com.atharchive.core.data.db.user.AtharUserDatabase
import com.atharchive.core.data.db.user.PinnedDownloadEntity
import com.atharchive.core.data.network.AppContentHttpClient
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ContentTransferResult(
    val entityId: String,
    val packageBytes: Long,
    val resumedFrom: Long,
    val importedFrames: Int,
    val alreadyRetained: Boolean,
)

class ContentTransferRunner(
    private val contentDatabase: AtharContentDatabase,
    private val userDatabase: AtharUserDatabase,
    private val client: AppContentHttpClient,
    private val importer: ContentImporter,
    private val retainedStore: RetainedPackageStore,
    private val transferDirectory: File,
    private val storagePolicy: ContentStoragePolicy,
    private val mutationMutex: Mutex = Mutex(),
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    suspend fun requestPin(entityId: String) = mutationMutex.withLock {
        val entry = requireNotNull(contentDatabase.catalogDao().entity(entityId)) {
            "catalog entity $entityId is missing"
        }.toCatalogEntry()
        userDatabase.userDataDao().savePinnedDownload(
            PinnedDownloadEntity(entityId = entityId, pinnedAt = nowMillis(), pkgHash = entry.pkg.hash),
        )
    }

    suspend fun unpin(entityId: String) = mutationMutex.withLock {
        val entity = contentDatabase.catalogDao().entity(entityId)
        userDatabase.userDataDao().removePinnedDownload(entityId)
        if (entity == null) {
            removeTransferFiles(entityId)
        } else {
            entity.toCatalogEntry().let {
                retainedStore.remove(it)
                removeTransferFiles(it)
            }
        }
        contentDatabase.importDao().markTransfer(entityId, ContentTransferState.IDLE)
    }

    suspend fun downloadPinned(
        entityId: String,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ): ContentTransferResult = mutationMutex.withLock {
        val dao = contentDatabase.importDao()
        val entity = requireNotNull(dao.entity(entityId)) { "catalog entity $entityId is missing" }
        val entry = entity.toCatalogEntry()
        val pin = requireNotNull(userDatabase.userDataDao().pinnedDownload(entityId)) {
            "download intent for $entityId is missing"
        }
        require(pin.pkgHash == entry.pkg.hash) { "download intent targets an older package" }

        if (entity.availability == ContentAvailability.COMPLETE && retainedStore.containsVerified(entry)) {
            dao.markTransfer(entityId, ContentTransferState.IDLE)
            removeTransferFiles(entry)
            return@withLock ContentTransferResult(
                entityId = entityId,
                packageBytes = entry.pkg.size,
                resumedFrom = entry.pkg.size,
                importedFrames = 0,
                alreadyRetained = true,
            )
        }

        dao.markTransfer(entityId, ContentTransferState.FETCHING)
        try {
            storagePolicy.prepareForDownload(entry.pkg.uncompressed)
            val verifiedIndexBytes = loadOrFetchIndex(entry)
            val index = FramedPackageDecoder.decodeIndex(verifiedIndexBytes, entry)
            val partFile = partFile(entry)
            val download = client.downloadPackage(entry, partFile, onProgress)
            dao.markTransfer(entityId, ContentTransferState.VERIFYING)
            ensureStillPinned(entry)

            val local = requireNotNull(dao.entity(entityId))
            if (local.updateAvailable || (local.localPackageHash != null && local.localPackageHash != entry.pkg.hash)) {
                dao.clearImportedContent(entityId)
            }
            val importedFrameNumbers = dao.frames(entityId).mapTo(hashSetOf()) { it.frameOrdinal }
            var importedFrames = 0
            dao.markTransfer(entityId, ContentTransferState.IMPORTING)
            RandomAccessFile(partFile, "r").use { packageInput ->
                index.frames.forEachIndexed { frameNumber, frame ->
                    ensureStillPinned(entry)
                    if (frameNumber in importedFrameNumbers) return@forEachIndexed
                    val bytes = ByteArray(frame.len)
                    packageInput.seek(frame.off)
                    packageInput.readFully(bytes)
                    importer.importVerifiedFrame(entry, frameNumber, frame, bytes)
                    dao.markTransfer(entityId, ContentTransferState.IMPORTING)
                    importedFrames++
                }
            }
            val completed = requireNotNull(dao.entity(entityId))
            check(completed.availability == ContentAvailability.COMPLETE) {
                "package import did not produce complete availability"
            }
            ensureStillPinned(entry)
            retainedStore.retainVerified(entry, partFile, verifiedIndexBytes)
            removeTransferFiles(entry)
            dao.markTransfer(entityId, ContentTransferState.IDLE)
            ContentTransferResult(
                entityId = entityId,
                packageBytes = entry.pkg.size,
                resumedFrom = download.resumedFrom,
                importedFrames = importedFrames,
                alreadyRetained = false,
            )
        } catch (error: CancellationException) {
            runCatching { dao.markTransfer(entityId, ContentTransferState.IDLE) }
            throw error
        } catch (error: Throwable) {
            runCatching { dao.markTransfer(entityId, ContentTransferState.FAILED) }
            throw error
        }
    }

    private suspend fun ensureStillPinned(entry: CatalogEntry) {
        val pin = userDatabase.userDataDao().pinnedDownload(entry.id)
        check(pin?.pkgHash == entry.pkg.hash) { "download intent was removed or changed" }
    }

    private fun partFile(entry: CatalogEntry): File {
        prepareTransferDirectory()
        return File(transferDirectory, "${transferStem(entry)}.${entry.pkg.hash}.part")
    }

    private suspend fun loadOrFetchIndex(entry: CatalogEntry): ByteArray {
        prepareTransferDirectory()
        val target = File(transferDirectory, "${transferStem(entry)}.${entry.pkg.idxHash}.idx")
        if (target.isFile) {
            runCatching {
                val bytes = target.readBytes()
                ContentDigests.verify(bytes, entry.pkg.idxHash, entry.pkg.idxSize, "saved package index")
                FramedPackageDecoder.decodeIndex(bytes, entry)
                return bytes
            }
            target.delete()
        }
        val bytes = client.fetchPackageIndex(entry)
        writeAtomically(target, bytes)
        return bytes
    }

    private fun prepareTransferDirectory() {
        transferDirectory.mkdirs()
        check(transferDirectory.isDirectory) { "cannot create transfer directory" }
    }

    private fun writeAtomically(target: File, bytes: ByteArray) {
        val temporary = File(target.parentFile, ".${target.name}.part")
        temporary.outputStream().use { output ->
            output.write(bytes)
            output.fd.sync()
        }
        check(temporary.renameTo(target)) { "cannot persist verified package index" }
    }

    private fun transferStem(entry: CatalogEntry): String {
        require(entry.coll.all { it.isLetterOrDigit() || it == '-' || it == '_' })
        require(entry.id.all { it.isLetterOrDigit() || it == '-' || it == '_' })
        return "${entry.coll}__${entry.id}"
    }

    private fun removeTransferFiles(entry: CatalogEntry) {
        val prefix = "${transferStem(entry)}."
        transferDirectory.listFiles().orEmpty()
            .filter { it.name.startsWith(prefix) || it.name.startsWith(".$prefix") }
            .forEach(File::delete)
    }

    private fun removeTransferFiles(entityId: String) {
        require(entityId.all { it.isLetterOrDigit() || it == '-' || it == '_' })
        val marker = "__$entityId."
        transferDirectory.listFiles().orEmpty()
            .filter { marker in it.name }
            .forEach(File::delete)
    }
}
