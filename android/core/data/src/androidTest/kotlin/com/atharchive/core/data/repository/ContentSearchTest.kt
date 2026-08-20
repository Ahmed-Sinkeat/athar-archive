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
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContentSearchTest {
    private lateinit var database: AtharContentDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder<AtharContentDatabase>(
            ApplicationProvider.getApplicationContext<Context>(),
        )
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .setSingleConnectionPool()
            .addCallback(ContentSearchSchema.callback)
            .build()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun exactPhraseAfterCandidateEightyMapsTheWholeVocalisedRangeAndEvictsAtomically() = runBlocking {
        val records = buildList {
            add(
                """{"t":"header","schema":2,"coll":"book","id":"search-book","v":1,"blocks":82,"chapters":[{"a":"main","title":"كتاب العلم","block":0}],"footnotes":0}""",
            )
            repeat(81) { ordinal ->
                add(blockJson(ordinal, "طلب، العلم"))
            }
            add(blockJson(81, "طَلَبُ العِلْمِ"))
        }
        val bytes = gzip(records)
        val entry = entry(bytes.size.toLong())
        val importer = ContentImporter(database) { 123L }
        importer.applyCatalog(CatalogDocument(2, listOf(entry)))
        importer.importVerifiedFrame(
            entry = entry,
            frameNumber = 0,
            frame = FrameIndexEntry(0, bytes.size, 0, 82, ContentDigests.sha256Hex(bytes)),
            compressedBytes = bytes,
        )

        val result = ContentSearchRepository(database).search(
            ContentSearchRequest("\"طلب العلم\""),
        )

        assertEquals(1, result.blocks.size)
        val hit = result.blocks.single()
        assertEquals(81, hit.ordinal)
        assertEquals("طَلَبُ العِلْمِ", hit.excerpt)
        assertEquals(0, hit.matchStart)
        assertEquals(hit.excerpt.length, hit.matchEnd)

        database.importDao().evictFrame(database.importDao().evictionCandidates().single())
        assertTrue(
            ContentSearchRepository(database)
                .search(ContentSearchRequest("\"طلب العلم\""))
                .blocks
                .isEmpty(),
        )
    }

    @Test
    fun catalogSearchUsesStructuredFiltersRatherThanMatchInterpolation() = runBlocking {
        val first = entry(10, id = "first", title = "كتاب الإيمان", author = "أحمد بن حنبل")
        val second = entry(10, id = "second", title = "شرح الإيمان", author = "محمد بن إسماعيل")
        ContentImporter(database).applyCatalog(CatalogDocument(2, listOf(first, second)))

        val result = ContentSearchRepository(database).search(
            ContentSearchRequest(
                query = "الإيمان",
                field = ContentSearchField.Title,
                filter = ContentSearchFilter(authors = setOf("أحمد بن حنبل")),
            ),
        )

        assertEquals(listOf("first"), result.catalog.map(CatalogSearchHit::entityId))
        assertTrue(result.blocks.isEmpty())
    }

    private fun entry(
        packageSize: Long,
        id: String = "search-book",
        title: String = "كتاب العلم",
        author: String = "أحمد بن حنبل",
    ) = CatalogEntry(
        id = id,
        coll = "book",
        v = 1,
        hash = "1".repeat(64),
        title = title,
        personName = author,
        topics = listOf("العقيدة"),
        kind = "كتاب",
        pkg = PackageReference(
            path = "content/book/$id/${"2".repeat(64)}.athar",
            hash = "2".repeat(64),
            size = packageSize,
            idxPath = "content/book/$id/${"3".repeat(64)}.athar.idx",
            idxHash = "3".repeat(64),
            idxSize = 100,
            uncompressed = 300,
            blocks = if (id == "search-book") 82 else 0,
            chapters = 1,
        ),
    )

    private fun blockJson(ordinal: Int, text: String): String {
        val id = (ordinal + 1).toString(16).padStart(32, '0')
        val fp = (ordinal + 1).toString(16).padStart(16, '0')
        return """{"t":"p","a":"main","i":$ordinal,"id":"$id","fp":"$fp","x":"$text"}"""
    }

    private fun gzip(records: List<String>): ByteArray {
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
