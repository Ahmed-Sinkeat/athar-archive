package com.atharchive.core.data.db

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.atharchive.core.data.content.CatalogEntry
import com.atharchive.core.data.content.ContentDigests
import com.atharchive.core.data.content.FrameIndexEntry
import com.atharchive.core.data.content.PackageReference
import com.atharchive.core.data.content.CatalogDocument
import com.atharchive.core.data.db.content.AtharContentDatabase
import com.atharchive.core.data.db.content.ContentAvailability
import com.atharchive.core.data.db.content.ContentBlockEntity
import com.atharchive.core.data.db.content.EntityFrameEntity
import com.atharchive.core.data.db.user.AtharUserDatabase
import com.atharchive.core.data.db.user.PinnedDownloadEntity
import com.atharchive.core.data.repository.ContentImporter
import com.atharchive.core.data.repository.ContentCacheManager
import com.atharchive.core.data.repository.ContentStoragePolicy
import com.atharchive.core.data.repository.OfflineContentRebuilder
import com.atharchive.core.data.repository.RetainedPackageStore
import com.atharchive.core.data.db.content.ContentGenerationEntity
import java.io.File
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContentDatabaseImportTest {
    private lateinit var content: AtharContentDatabase
    private lateinit var user: AtharUserDatabase

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
    }

    @After
    fun tearDown() {
        content.close()
        user.close()
    }

    @Test
    fun verifiedFrameCommitsBlocksAndAvailabilityAtomically() = runBlocking {
        val bytes = gzip(
            """{"t":"header","schema":2,"coll":"book","id":"test-book","v":1,"blocks":2,"chapters":[{"a":"main","title":"المقدمة","block":0}],"footnotes":0}""",
            """{"t":"h2","a":"main","i":0,"id":"00000000000000000000000000000001","fp":"0000000000000001","x":"المقدمة"}""",
            """{"t":"p","a":"main","i":1,"id":"00000000000000000000000000000002","fp":"0000000000000002","x":"الحمد لله رب العالمين"}""",
        )
        val entry = entry(bytes.size.toLong())
        val importer = ContentImporter(content) { 1234L }
        importer.applyCatalog(CatalogDocument(2, listOf(entry)))
        val frame = FrameIndexEntry(0, bytes.size, 0, 2, ContentDigests.sha256Hex(bytes))

        val result = importer.importVerifiedFrame(entry, 0, frame, bytes)

        assertEquals(ContentAvailability.COMPLETE, result.availability)
        assertEquals(listOf("المقدمة", "الحمد لله رب العالمين"), content.importDao().blocks(entry.id).map { it.text })
        assertEquals(1, content.importDao().frames(entry.id).size)
        assertEquals(ContentAvailability.COMPLETE, content.importDao().entity(entry.id)?.availability)
    }

    @Test
    fun failedContentTransactionCannotDamageUserPinOrLeaveAPartialFrame() = runBlocking {
        val entry = entry(100)
        ContentImporter(content).applyCatalog(CatalogDocument(2, listOf(entry)))
        user.userDataDao().savePinnedDownload(PinnedDownloadEntity(entry.id, 99L, entry.pkg.hash))
        val duplicateOrdinal = block(entry.id, ordinal = 0)
        var failed = false
        try {
            content.importDao().importFrame(
                entityId = entry.id,
                frame = EntityFrameEntity(entry.id, 0, 0, 2, 100, 1000),
                blocks = listOf(duplicateOrdinal, duplicateOrdinal.copy(blockIdLo = 2)),
                chapters = emptyList(),
                footnotes = emptyList(),
                now = 1000,
            )
        } catch (_: Throwable) {
            failed = true
        }

        assertTrue("duplicate ordinals must abort the transaction", failed)
        assertTrue(content.importDao().blocks(entry.id).isEmpty())
        assertTrue(content.importDao().frames(entry.id).isEmpty())
        assertEquals(ContentAvailability.ABSENT, content.importDao().entity(entry.id)?.availability)
        assertNotNull(user.userDataDao().pinnedDownload(entry.id))
    }

    @Test
    fun signedGenerationRetainsProtectedTombstoneAndPurgesUnprotectedOne() = runBlocking {
        val protected = entry(100, "protected-book")
        val disposable = entry(100, "disposable-book")
        val importer = ContentImporter(content) { 10L }
        importer.applyCatalog(CatalogDocument(2, listOf(protected, disposable)))
        user.userDataDao().savePinnedDownload(PinnedDownloadEntity(protected.id, 9L, protected.pkg.hash))

        val protectedIds = user.userDataDao().protectedEntityIds().toSet()
        content.catalogDao().applyGeneration(
            incoming = emptyList(),
            tombstonedIds = setOf(protected.id, disposable.id),
            protectedIds = protectedIds,
            generation = ContentGenerationEntity(
                generationId = "generation-2",
                catalogHash = "a".repeat(64),
                tombstonesHash = "b".repeat(64),
                appliedAt = 20L,
            ),
        )
        content.catalogDao().purgeUnprotectedTombstones(user.userDataDao().protectedEntityIds().toSet())

        assertEquals(false, content.catalogDao().entity(protected.id)?.catalogPresent)
        assertEquals(null, content.catalogDao().entity(disposable.id))
        assertEquals("generation-2", content.catalogDao().generation()?.generationId)
    }

    @Test
    fun lruEvictionNeverRemovesPinnedFrames() = runBlocking {
        val pinned = entry(400L * 1024 * 1024, "pinned-book")
        val cached = entry(400L * 1024 * 1024, "cached-book")
        val importer = ContentImporter(content)
        importer.applyCatalog(CatalogDocument(2, listOf(pinned, cached)))
        user.userDataDao().savePinnedDownload(PinnedDownloadEntity(pinned.id, 1L, pinned.pkg.hash))
        content.importDao().importFrame(
            pinned.id,
            EntityFrameEntity(pinned.id, 0, 0, 1, pinned.pkg.size, 1),
            listOf(block(pinned.id, 0)), emptyList(), emptyList(), 1,
        )
        content.importDao().importFrame(
            cached.id,
            EntityFrameEntity(cached.id, 0, 0, 1, cached.pkg.size, 2),
            listOf(block(cached.id, 0)), emptyList(), emptyList(), 2,
        )

        val removed = ContentCacheManager(content, user).evictToBudget(500L * 1024 * 1024)

        assertEquals(cached.pkg.size, removed)
        assertEquals(ContentAvailability.COMPLETE, content.importDao().entity(pinned.id)?.availability)
        assertEquals(ContentAvailability.ABSENT, content.importDao().entity(cached.id)?.availability)
    }

    @Test
    fun lowStorageEvictsToHalfBudgetButLeavesPinnedFrames() = runBlocking {
        val pinned = entry(400L * 1024 * 1024, "pinned-low-storage")
        val cached = entry(400L * 1024 * 1024, "cached-low-storage")
        val importer = ContentImporter(content)
        importer.applyCatalog(CatalogDocument(2, listOf(pinned, cached)))
        user.userDataDao().savePinnedDownload(PinnedDownloadEntity(pinned.id, 1L, pinned.pkg.hash))
        content.importDao().importFrame(
            pinned.id,
            EntityFrameEntity(pinned.id, 0, 0, 1, pinned.pkg.size, 1),
            listOf(block(pinned.id, 0)), emptyList(), emptyList(), 1,
        )
        content.importDao().importFrame(
            cached.id,
            EntityFrameEntity(cached.id, 0, 0, 1, cached.pkg.size, 2),
            listOf(block(cached.id, 0)), emptyList(), emptyList(), 2,
        )
        val budget = 500L * 1024 * 1024
        val policy = ContentStoragePolicy(
            storageRoot = ApplicationProvider.getApplicationContext<Context>().filesDir,
            cacheManager = ContentCacheManager(content, user),
            availableBytes = { 400L * 1024 * 1024 },
            configuredBudgetBytes = { budget },
        )

        val status = policy.prepareForReadThrough()

        assertTrue(status.lowStorage)
        assertEquals(cached.pkg.size, status.cacheBytesRemoved)
        assertEquals(ContentAvailability.COMPLETE, content.importDao().entity(pinned.id)?.availability)
        assertEquals(ContentAvailability.ABSENT, content.importDao().entity(cached.id)?.availability)
    }

    @Test
    fun retainedPinnedPackageRebuildsContentDatabaseWithoutNetwork() = runBlocking {
        val bytes = gzip(
            """{"t":"header","schema":2,"coll":"book","id":"offline-book","v":1,"blocks":1,"chapters":[{"a":"main","title":"المقدمة","block":0}],"footnotes":0}""",
            """{"t":"p","a":"main","i":0,"id":"00000000000000000000000000000001","fp":"0000000000000001","x":"نص يعمل بلا شبكة"}""",
        )
        val packageHash = ContentDigests.sha256Hex(bytes)
        val provisional = entry(bytes.size.toLong(), "offline-book", packageHash)
        val indexTemplate = """{"schema":2,"coll":"book","entityId":"offline-book","v":1,"frames":[{"off":0,"len":${bytes.size},"ord":0,"n":1,"sha256":"$packageHash"}]}""".encodeToByteArray()
        val indexHash = ContentDigests.sha256Hex(indexTemplate)
        val entry = provisional.copy(
            pkg = provisional.pkg.copy(
                idxPath = "content/book/offline-book/$indexHash.athar.idx",
                idxHash = indexHash,
                idxSize = indexTemplate.size.toLong(),
            ),
        )
        // Decode once here so the fixture itself is held to the production contract.
        com.atharchive.core.data.content.FramedPackageDecoder.decodeIndex(indexTemplate, entry)
        user.userDataDao().savePinnedDownload(PinnedDownloadEntity(entry.id, 1L, entry.pkg.hash))
        val root = File(ApplicationProvider.getApplicationContext<Context>().cacheDir, "m5-rebuild-${System.nanoTime()}")
        val store = RetainedPackageStore(root)
        try {
            store.retainVerified(entry, bytes, indexTemplate)
            val result = OfflineContentRebuilder(content, user, store, ContentImporter(content)).rebuild()

            assertEquals(1, result.rebuiltPackages)
            assertEquals(1, result.rebuiltFrames)
            assertTrue(result.failures.isEmpty())
            assertEquals("نص يعمل بلا شبكة", content.importDao().blocks(entry.id).single().text)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun entry(
        packageSize: Long,
        id: String = "test-book",
        packageHash: String = "2".repeat(64),
    ) = CatalogEntry(
        id = id,
        coll = "book",
        v = 1,
        hash = "1".repeat(64),
        title = "كتاب اختبار",
        pkg = PackageReference(
            path = "content/book/$id/$packageHash.athar",
            hash = packageHash,
            size = packageSize,
            idxPath = "content/book/$id/${"3".repeat(64)}.athar.idx",
            idxHash = "3".repeat(64),
            idxSize = 100,
            uncompressed = 300,
            blocks = if (id == "test-book") 2 else 1,
            chapters = 1,
        ),
    )

    private fun block(entityId: String, ordinal: Int) = ContentBlockEntity(
        entityId = entityId,
        ordinal = ordinal,
        blockIdHi = 0,
        blockIdLo = 1,
        fp64 = 1,
        chapterAnchor = "main",
        type = "p",
        printedPage = null,
        vol = null,
        text = "نص",
        attrs = "{}".encodeToByteArray(),
        inlineSpans = "[]".encodeToByteArray(),
    )

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
