package com.atharchive.core.data.repository

import com.atharchive.core.data.db.content.stagingId

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
    private val transferLocks = java.util.concurrent.ConcurrentHashMap<String, Mutex>()

    suspend fun requestPin(entityId: String) = mutationMutex.withLock {
        val entry = requireNotNull(contentDatabase.catalogDao().entity(entityId)) {
            "catalog entity $entityId is missing"
        }.toCatalogEntry()
        userDatabase.userDataDao().savePinnedDownload(
            PinnedDownloadEntity(entityId = entityId, pinnedAt = nowMillis(), pkgHash = entry.pkg.hash),
        )
    }

    suspend fun requestPins(entityIds: Collection<String>) = mutationMutex.withLock {
        val dao = contentDatabase.catalogDao()
        val now = nowMillis()
        userDatabase.userDataDao().savePinnedDownloads(
            entityIds.mapNotNull { id -> dao.entity(id)?.toCatalogEntry()?.let { PinnedDownloadEntity(entityId = id, pinnedAt = now, pkgHash = it.pkg.hash) } },
        )
    }

    /** Drops every pin in one write, so lists update once; [unpin] then clears each one's files. */
    suspend fun removePins(entityIds: List<String>) = mutationMutex.withLock {
        entityIds.chunked(500).forEach { userDatabase.userDataDao().removePinnedDownloads(it) }
    }

    suspend fun unpin(entityId: String) = transferLocks.getOrPut(entityId) { Mutex() }.withLock { mutationMutex.withLock {
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
    }.also {
        contentDatabase.importDao().purgeCopies(entityId) // a half-staged update is useless once unpinned
    } }

    suspend fun downloadPinned(
        entityId: String,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ): ContentTransferResult = transferLocks.getOrPut(entityId) { Mutex() }.withLock {
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
            mutationMutex.withLock { storagePolicy.prepareForDownload(entry.pkg.uncompressed) }
            val verifiedIndexBytes = loadOrFetchIndex(entry)
            val index = FramedPackageDecoder.decodeIndex(verifiedIndexBytes, entry)
            val partFile = partFile(entry)
            // One honest bar for the whole wait: bytes fill the first half, frames written into the
            // database the second (over a fast link the import is most of the wait).
            // ponytail: fixed 50/50 split; weight by measured phase times if one side always dominates.
            val size = entry.pkg.size
            val download = client.downloadPackage(entry, partFile) { done, of -> onProgress(done, 2 * of) }
            val onFrame = { done: Int, of: Int -> onProgress(size + size * done / of.coerceAtLeast(1), 2 * size) }
            dao.markTransfer(entityId, ContentTransferState.VERIFYING)
            ensureStillPinned(entry)

            var importedFrames = 0
            val replacing = dao.entity(entityId)?.let {
                it.updateAvailable || (it.localPackageHash != null && it.localPackageHash != entry.pkg.hash)
            } == true
            if (replacing) {
                // The new copy is written beside the old one, a frame per commit, then renamed in
                // one short transaction: the reader keeps the old text until the swap, and nothing
                // waits on (or holds memory for) the whole package. One transaction per package
                // held ~2 GB of SQLite temp memory on a big book and killed the app on every launch.
                val staging = stagingId(entityId, entry.pkg.hash)
                dao.purgeCopies(entityId, keep = staging)
                mutationMutex.withLock {
                    ensureStillPinned(entry)
                    check(dao.entity(entityId)?.pkgHash == entry.pkg.hash) { "catalog changed during download; retry" }
                    dao.markTransfer(entityId, ContentTransferState.IMPORTING)
                }
                importedFrames = importFrames(entry, index, partFile, frameLock = mutationMutex, onFrame = onFrame, into = staging)
                mutationMutex.withLock {
                    ensureStillPinned(entry)
                    check(dao.entity(entityId)?.pkgHash == entry.pkg.hash) { "catalog changed during download; retry" }
                    dao.promoteStaged(entityId, staging, nowMillis())
                    finishImport(entry, partFile, verifiedIndexBytes)
                }
                dao.purgeCopies(entityId)
            } else {
                // First download or resume: every frame commits on its own and a resumed
                // import skips frames already stored, so nothing is lost on a crash. The
                // mutex and connection are taken per frame, so reading (or opening this very
                // book) is never frozen for the whole import.
                mutationMutex.withLock {
                    ensureStillPinned(entry)
                    check(dao.entity(entityId)?.pkgHash == entry.pkg.hash) { "catalog changed during download; retry" }
                    dao.markTransfer(entityId, ContentTransferState.IMPORTING)
                }
                importedFrames = importFrames(entry, index, partFile, frameLock = mutationMutex, onFrame = onFrame)
                mutationMutex.withLock { finishImport(entry, partFile, verifiedIndexBytes) }
            }
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

    /** Imports the frames not stored yet; [frameLock] is taken per frame, or null if the caller already holds it. */
    private suspend fun importFrames(
        entry: CatalogEntry,
        index: com.atharchive.core.data.content.PackageIndex,
        partFile: File,
        frameLock: Mutex?,
        onFrame: (done: Int, of: Int) -> Unit = { _, _ -> },
        into: String = entry.id,
    ): Int {
        val dao = contentDatabase.importDao()
        val stored = dao.frames(into).mapTo(hashSetOf()) { it.frameOrdinal }
        var imported = 0
        RandomAccessFile(partFile, "r").use { packageInput ->
            for ((frameNumber, frame) in index.frames.withIndex()) {
                if (frameNumber in stored) continue
                val bytes = ByteArray(frame.len)
                packageInput.seek(frame.off)
                packageInput.readFully(bytes)
                suspend fun import() {
                    ensureStillPinned(entry)
                    importer.importVerifiedFrame(entry, frameNumber, frame, bytes, into)
                    dao.markTransfer(entry.id, ContentTransferState.IMPORTING)
                }
                if (frameLock == null) import() else frameLock.withLock { import() }
                imported++
                onFrame(frameNumber + 1, index.frames.size)
            }
        }
        return imported
    }

    private suspend fun finishImport(entry: CatalogEntry, partFile: File, indexBytes: ByteArray) {
        val dao = contentDatabase.importDao()
        ensureStillPinned(entry)
        val completed = requireNotNull(dao.entity(entry.id))
        check(completed.availability == ContentAvailability.COMPLETE) {
            "package import did not produce complete availability"
        }
        retainedStore.retainVerified(entry, partFile, indexBytes)
        removeTransferFiles(entry)
        dao.markTransfer(entry.id, ContentTransferState.IDLE)
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
