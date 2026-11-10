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
import androidx.room3.withWriteTransaction
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

data class ContentTransferResult(
    val entityId: String,
    val packageBytes: Long,
    val resumedFrom: Long,
    val importedFrames: Int,
    val alreadyRetained: Boolean,
)

/** Frames read and decoded ahead, outside the write lock. */
private const val FRAMES_PREPARED_AHEAD = 8
/** Longest a download writes before committing and letting a page read in. */
private const val WRITE_BUDGET_NANOS = 50_000_000L
/** Packages up to this size go through [ContentTransferRunner.downloadSmallTogether]. */
private const val SMALL_PACKAGE_BYTES = 256 * 1024L

private val SharedName = Regex("^([A-Za-z0-9_-]+)\\.(athar|idx)$")

// The bundle rule, kept identical to the site's src/lib/app-v2/bundles.ts: a kind's packages up to
// SMALL_PACKAGE_BYTES, by id, cut before a part passes BUNDLE_PART_BYTES, named by their hashes.
private val BUNDLED_KINDS = listOf("article", "question", "poem", "book")
private const val BUNDLE_PART_BYTES = 8 * 1024 * 1024L
/** Local header + central directory record of a stored zip entry, without its name. */
private const val ZIP_ENTRY_OVERHEAD = 30 + 46

internal fun bundleParts(rows: List<com.atharchive.core.data.db.content.BundleRow>): List<List<com.atharchive.core.data.db.content.BundleRow>> {
    val parts = mutableListOf<MutableList<com.atharchive.core.data.db.content.BundleRow>>()
    var bytes = 0L
    for (row in rows) {
        val size = row.pkgSize + row.idxSize
        if (parts.isEmpty() || bytes + size > BUNDLE_PART_BYTES) {
            parts += mutableListOf<com.atharchive.core.data.db.content.BundleRow>()
            bytes = 0
        }
        parts.last() += row
        bytes += size
    }
    return parts
}

internal fun bundleName(part: List<com.atharchive.core.data.db.content.BundleRow>): String =
    java.security.MessageDigest.getInstance("SHA-256")
        .digest(part.joinToString("\n") { it.pkgHash }.toByteArray())
        .joinToString("") { "%02x".format(it) }

class ContentTransferRunner(
    private val contentDatabase: AtharContentDatabase,
    private val userDatabase: AtharUserDatabase,
    private val client: AppContentHttpClient,
    private val importer: ContentImporter,
    private val retainedStore: RetainedPackageStore,
    private val transferDirectory: File,
    private val storagePolicy: ContentStoragePolicy,
    private val mutationMutex: ContentWriteLock = ContentWriteLock(),
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val transferLocks = KeyedLock<String>()

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
            entityIds.distinct().chunked(500).flatMap { dao.pinRows(it) }.map { PinnedDownloadEntity(entityId = it.id, pinnedAt = now, pkgHash = it.pkgHash) },
        )
    }

    /** Drops every pin in one write, so lists update once; [unpin] then clears each one's files. */
    suspend fun removePins(entityIds: List<String>) = mutationMutex.withLock {
        entityIds.chunked(500).forEach { userDatabase.userDataDao().removePinnedDownloads(it) }
    }

    suspend fun unpin(entityId: String) {
        // The pin goes first and on its own: lists drop the row at once, and a running transfer
        // stops at its next frame (ensureStillPinned). The files wait for the transfer lock, which
        // that transfer holds until it stops; taking the lock first left a cancelled download
        // looking alive until it finished on its own.
        mutationMutex.withLock { userDatabase.userDataDao().removePinnedDownload(entityId) }
        removeUnpinnedFiles(entityId)
    }

    private suspend fun removeUnpinnedFiles(entityId: String) {
        transferLocks.withLock(entityId) {
            val removed = mutationMutex.withLock {
                // Pinned again while this waited: the new download owns the files now.
                if (userDatabase.userDataDao().pinnedDownload(entityId) != null) return@withLock false
                val entity = contentDatabase.catalogDao().entity(entityId)
                if (entity == null) {
                    removeTransferFiles(entityId)
                } else {
                    entity.toCatalogEntry().let {
                        retainedStore.remove(it)
                        removeTransferFiles(it)
                    }
                }
                contentDatabase.importDao().markTransfer(entityId, ContentTransferState.IDLE)
                true
            }
            if (removed) contentDatabase.importDao().purgeCopies(entityId) // a half-staged update is useless once unpinned
        }
    }

    suspend fun downloadPinned(
        entityId: String,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ): ContentTransferResult = transferLocks.withLock(entityId) {
        val dao = contentDatabase.importDao()
        val entity = requireNotNull(dao.entity(entityId)) { "catalog entity $entityId is missing" }
        val entry = entity.toCatalogEntry()
        val pin = requireNotNull(userDatabase.userDataDao().pinnedDownload(entityId)) {
            "download intent for $entityId is missing"
        }
        require(pin.pkgHash == entry.pkg.hash) { "download intent targets an older package" }

        if (dao.state(entityId)?.availability == ContentAvailability.COMPLETE && retainedStore.containsVerified(entry)) {
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
        // The package file (kept after import) plus its text in the database.
        val diskBytes = entry.pkg.size + entry.pkg.uncompressed
        var reserved = false
        try {
            mutationMutex.withLock { storagePolicy.prepareForDownload(diskBytes, reserve = true) }
            reserved = true
            val partFile = partFile(entry)
            // One bar for the whole wait: bytes fill the first half, frames written into the
            // database the second (over a fast link the import is most of the wait).
            val size = entry.pkg.size
            // The index and the package are independent requests: fetched together, one round trip.
            val (verifiedIndexBytes, download) = kotlinx.coroutines.coroutineScope {
                val indexBytes = async { loadOrFetchIndex(entry) }
                val download = client.downloadPackage(entry, partFile) { done, of -> onProgress(done, 2 * of) }
                indexBytes.await() to download
            }
            val index = FramedPackageDecoder.decodeIndex(verifiedIndexBytes, entry)
            val onFrame = { done: Int, of: Int -> onProgress(size + size * done / of.coerceAtLeast(1), 2 * size) }
            dao.markTransfer(entityId, ContentTransferState.VERIFYING)
            ensureStillPinned(entry)

            var importedFrames = 0
            val replacing = dao.state(entityId)?.localPackageHash.let { it != null && it != entry.pkg.hash }
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
                    dao.promoteStaged(entityId, staging)
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
        } finally {
            if (reserved) storagePolicy.release(diskBytes)
        }
    }

    /**
     * First downloads of small items (a مسألة, a poem, an article), several per commit. Through
     * [downloadPinned] each one wrote its state five times, each write re-running every query that
     * watches `entityState`, and synced four files: 0.13-0.32 core-seconds an item (#61, #70a).
     * Returns the ids it finished; the ones it does not take (too big, an update, already on the
     * phone) or that fail are left for [downloadPinned]. [bundled] holds packages already verified
     * from a bundle (package and index bytes by id); the rest are fetched.
     */
    suspend fun downloadSmallTogether(entityIds: List<String>, bundled: Map<String, Pair<ByteArray, ByteArray>> = emptyMap()): List<String> {
        val dao = contentDatabase.importDao()
        val userDao = userDatabase.userDataDao()
        val entries = entityIds.mapNotNull { id ->
            val entry = dao.entity(id)?.toCatalogEntry() ?: return@mapNotNull null
            val state = dao.state(id)
            entry.takeIf {
                it.pkg.size <= SMALL_PACKAGE_BYTES && state?.localPackageHash == null &&
                    userDao.pinnedDownload(id)?.pkgHash == it.pkg.hash
            }
        }
        if (entries.isEmpty()) return emptyList()
        val diskBytes = entries.sumOf { it.pkg.size + it.pkg.uncompressed }
        mutationMutex.withLock { storagePolicy.prepareForDownload(diskBytes, reserve = true) }
        try {
            // Fetched and decoded outside the lock, a few at a time; a failure drops only that item.
            val fetchSlots = kotlinx.coroutines.sync.Semaphore(4)
            val fetched = kotlinx.coroutines.coroutineScope {
                entries.map { entry ->
                    async {
                        fetchSlots.withPermit {
                            runCatching {
                                val inBundle = bundled[entry.id]
                                val partFile = if (inBundle == null) partFile(entry) else null
                                val indexBytes = inBundle?.second ?: loadOrFetchIndex(entry)
                                val packageBytes = inBundle?.first ?: run {
                                    client.downloadPackage(entry, partFile!!)
                                    partFile.readBytes() // small: at most SMALL_PACKAGE_BYTES
                                }
                                val index = FramedPackageDecoder.decodeIndex(indexBytes, entry)
                                val frames = index.frames.mapIndexed { number, frame ->
                                    val start = Math.toIntExact(frame.off)
                                    importer.prepareFrame(entry, number, frame, packageBytes.copyOfRange(start, start + frame.len))
                                }
                                SmallDownload(entry, partFile, packageBytes.takeIf { partFile == null }, indexBytes, frames)
                            }.onFailure { if (it is CancellationException) throw it }.getOrNull()
                        }
                    }
                }.awaitAll().filterNotNull()
            }
            val done = ArrayList<String>(fetched.size)
            var next = 0
            while (next < fetched.size) {
                val from = next
                mutationMutex.forBatch {
                    contentDatabase.withWriteTransaction {
                        val started = System.nanoTime()
                        do {
                            val item = fetched[next++]
                            if (userDao.pinnedDownload(item.entry.id)?.pkgHash == item.entry.pkg.hash) {
                                item.frames.forEach { importer.importPrepared(it) }
                            }
                        } while (next < fetched.size && System.nanoTime() - started < WRITE_BUDGET_NANOS)
                    }
                    for (item in fetched.subList(from, next)) {
                        if (dao.state(item.entry.id)?.availability != ContentAvailability.COMPLETE) continue
                        if (userDao.pinnedDownload(item.entry.id)?.pkgHash != item.entry.pkg.hash) continue
                        if (item.partFile != null) retainedStore.retainDownloaded(item.entry, item.partFile, item.indexBytes, durable = false)
                        else retainedStore.retainVerified(item.entry, item.packageBytes!!, item.indexBytes, durable = false)
                        removeTransferFiles(item.entry)
                        done += item.entry.id
                    }
                }
            }
            return done
        } finally {
            storagePolicy.release(diskBytes)
        }
    }

    /** One of [partFile] (fetched) or [packageBytes] (from a bundle) is set. */
    private class SmallDownload(val entry: CatalogEntry, val partFile: File?, val packageBytes: ByteArray?, val indexBytes: ByteArray, val frames: List<PreparedFrame>)

    /** Imports the frames not stored yet; [frameLock] is taken per batch, after any reader waiting for it. */
    private suspend fun importFrames(
        entry: CatalogEntry,
        index: com.atharchive.core.data.content.PackageIndex,
        partFile: File,
        frameLock: ContentWriteLock,
        onFrame: (done: Int, of: Int) -> Unit = { _, _ -> },
        into: String = entry.id,
    ): Int {
        val dao = contentDatabase.importDao()
        val stored = dao.frames(into).mapTo(hashSetOf()) { it.frameOrdinal }
        var imported = 0
        RandomAccessFile(partFile, "r").use { packageInput ->
            // Several frames per commit where they fit the write budget: a commit per frame (a disk
            // sync, and every open list re-queried) was a third of a big book's import. Still
            // bounded: a crash re-imports at most one commit, and SQLite never holds a whole book.
            val pending = index.frames.withIndex().filter { it.index !in stored }
            for (batch in pending.chunked(FRAMES_PREPARED_AHEAD)) {
                // Read, verified and decoded before the lock: only the inserts hold it. A bad frame
                // fails here, so the frames before it commit and a resume keeps them.
                val prepared = ArrayList<PreparedFrame>(batch.size)
                var failure: Throwable? = null
                for ((frameNumber, frame) in batch) {
                    try {
                        val bytes = ByteArray(frame.len)
                        packageInput.seek(frame.off)
                        packageInput.readFully(bytes)
                        prepared += importer.prepareFrame(entry, frameNumber, frame, bytes, into)
                    } catch (error: Throwable) {
                        failure = error
                        break
                    }
                }
                // Written in commits of about WRITE_BUDGET_NANOS each, not a fixed frame count: on
                // the Redmi 12 eight frames held the lock 250-420 ms, and a page opened meanwhile
                // waited that long. Pinning is checked once per commit, not per frame.
                var next = 0
                while (next < prepared.size) {
                    val from = next
                    frameLock.forBatch {
                        ensureStillPinned(entry)
                        contentDatabase.withWriteTransaction {
                            val started = System.nanoTime()
                            do {
                                importer.importPrepared(prepared[next++])
                            } while (next < prepared.size && System.nanoTime() - started < WRITE_BUDGET_NANOS)
                            dao.markTransfer(entry.id, ContentTransferState.IMPORTING)
                        }
                    }
                    imported += next - from
                    onFrame(batch[next - 1].index + 1, index.frames.size)
                }
                failure?.let { throw it }
            }
        }
        return imported
    }

    private suspend fun finishImport(entry: CatalogEntry, partFile: File, indexBytes: ByteArray) {
        val dao = contentDatabase.importDao()
        ensureStillPinned(entry)
        check(dao.state(entry.id)?.availability == ContentAvailability.COMPLETE) {
            "package import did not produce complete availability"
        }
        retainedStore.retainDownloaded(entry, partFile, indexBytes)
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

    private fun indexFile(entry: CatalogEntry): File {
        prepareTransferDirectory()
        return File(transferDirectory, "${transferStem(entry)}.${entry.pkg.idxHash}.idx")
    }

    /**
     * Saved books for a phone with no internet (Quick Share, Bluetooth): each retained package
     * and its index in one zip. The receiver trusts only its own signed catalog's hashes.
     */
    suspend fun exportShared(entityIds: Collection<String>, output: java.io.OutputStream): Int = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val wanted = entityIds.toSet()
        var count = 0
        java.util.zip.ZipOutputStream(output.buffered()).use { zip ->
            zip.setLevel(java.util.zip.Deflater.NO_COMPRESSION) // the frames are compressed already
            for ((manifest, entry) in retainedStore.manifests()) {
                if (entry.id !in wanted) continue
                val (pkg, idx) = retainedStore.filesFor(manifest)
                for ((file, ext) in listOf(pkg to "athar", idx to "idx")) {
                    zip.putNextEntry(java.util.zip.ZipEntry("${entry.id}.$ext"))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
                count++
            }
        }
        count
    }

    /**
     * Takes [exportShared]'s zip. A package is kept only if its size and hash match this phone's
     * own catalog entry; it is pinned and left where [downloadPinned] finds it complete, so the
     * import needs no network. Returns the entries to import.
     */
    suspend fun importShared(input: java.io.InputStream): List<CatalogEntry> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val dao = contentDatabase.importDao()
        val entries = mutableMapOf<String, CatalogEntry>()
        val ready = receivePackages(input) { id -> entries[id] ?: dao.entity(id)?.toCatalogEntry()?.also { entries[id] = it } }
            .mapNotNull(entries::get)
        requestPins(ready.map { it.id })
        ready
    }

    /**
     * «تنزيل الكل» through bundles (`bundles/<kind>/<name>.zip`, written by the site's
     * `src/lib/app-v2/bundles.ts`): one request per 8 MB of small packages instead of two per
     * item. Each package is checked in memory against this phone's catalog and imported through
     * [downloadSmallTogether], [onDone] getting the ids as they finish. A bundle that is missing,
     * stale or damaged only leaves its items for the one-by-one path.
     */
    suspend fun downloadBundled(pending: Collection<String>, stillWanted: (String) -> Boolean, onDone: (List<String>) -> Unit) {
        val want = pending.toHashSet()
        val dao = contentDatabase.importDao()
        for (coll in BUNDLED_KINDS) {
            for (part in bundleParts(dao.bundleRows(coll, SMALL_PACKAGE_BYTES))) {
                val needed = part.count { it.id in want }
                if (needed < 8 || needed * 4 < part.size) continue // a few items: cheaper one by one
                // each name is stored twice (local header and central directory): «<id>.athar» and «<id>.idx»
                val limit = part.sumOf { it.pkgSize + it.idxSize + 2 * ZIP_ENTRY_OVERHEAD + 2 * (it.id.length + 6) + 2 * (it.id.length + 4) } + 22
                val bytes = try {
                    client.fetchBundle("bundles/$coll/${bundleName(part)}.zip", limit.toInt())
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    android.util.Log.i("AtharDownload", "bundle $coll unavailable: ${error.message}")
                    continue
                }
                val packages = unpackBundle(bytes) { id -> if (id in want) dao.entity(id)?.toCatalogEntry() else null }
                // in groups, so a part's decoded text is never all in memory at once
                for (group in packages.keys.chunked(64)) {
                    val done = downloadSmallTogether(group.filter(stillWanted), packages) // paused meanwhile: left
                    if (done.isNotEmpty()) onDone(done)
                }
            }
        }
    }

    /** A bundle's `<id>.athar` / `<id>.idx` pairs whose size and hash match the entry [lookup] gives. */
    private suspend fun unpackBundle(bytes: ByteArray, lookup: suspend (String) -> CatalogEntry?): Map<String, Pair<ByteArray, ByteArray>> {
        val packages = HashMap<String, ByteArray>()
        val indexes = HashMap<String, ByteArray>()
        java.util.zip.ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) {
                val item = zip.nextEntry ?: break
                val (id, ext) = SharedName.matchEntire(item.name)?.destructured ?: continue
                val entry = lookup(id) ?: continue
                val (hash, size) = if (ext == "athar") entry.pkg.hash to entry.pkg.size else entry.pkg.idxHash to entry.pkg.idxSize
                if (size > SMALL_PACKAGE_BYTES * 4) continue
                val data = zip.readNBytes(size.toInt() + 1)
                // another edition or damaged: skipped, never trusted
                if (runCatching { ContentDigests.verify(data, hash, size, "bundled ${item.name}") }.isFailure) continue
                if (ext == "athar") packages[id] = data else indexes[id] = data
            }
        }
        return packages.mapNotNull { (id, data) -> indexes[id]?.let { id to (data to it) } }.toMap()
    }

    /**
     * Unpacks `<id>.athar` / `<id>.idx` pairs into the transfer directory, each only if its size
     * and hash match the entry [lookup] gives (null skips it). Returns the ids that arrived whole.
     */
    private suspend fun receivePackages(input: java.io.InputStream, lookup: suspend (String) -> CatalogEntry?): Set<String> {
        val received = mutableMapOf<String, MutableSet<String>>()
        java.util.zip.ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val item = zip.nextEntry ?: break
                val (id, ext) = SharedName.matchEntire(item.name)?.destructured ?: continue
                val entry = lookup(id) ?: continue
                val (target, hash, size) = if (ext == "athar") Triple(partFile(entry), entry.pkg.hash, entry.pkg.size)
                    else Triple(indexFile(entry), entry.pkg.idxHash, entry.pkg.idxSize)
                val temporary = File(transferDirectory, ".${target.name}.shared")
                try {
                    // Bounded by the catalog's size: a lying archive can't fill the disk.
                    temporary.outputStream().use { out ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var total = 0L
                        while (true) {
                            val read = zip.read(buffer)
                            if (read < 0) break
                            total += read
                            if (total > size) error("shared ${item.name} is larger than its catalog entry")
                            out.write(buffer, 0, read)
                        }
                    }
                    ContentDigests.verify(temporary, hash, size, "shared ${item.name}")
                    check(temporary.renameTo(target))
                    received.getOrPut(id) { mutableSetOf() } += ext
                } catch (error: CancellationException) {
                    temporary.delete()
                    throw error
                } catch (error: Exception) {
                    temporary.delete() // another edition, or damaged: skipped, never trusted
                }
            }
        }
        return received.filterValues { it.size == 2 }.keys
    }

    private suspend fun loadOrFetchIndex(entry: CatalogEntry): ByteArray {
        val target = indexFile(entry)
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
