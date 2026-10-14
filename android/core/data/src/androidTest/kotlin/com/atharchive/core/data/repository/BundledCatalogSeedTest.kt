package com.atharchive.core.data.repository

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.atharchive.core.data.content.APP_CONTENT_SCHEMA
import com.atharchive.core.data.content.AppContentJson
import com.atharchive.core.data.content.ArtifactReference
import com.atharchive.core.data.content.CatalogDocument
import com.atharchive.core.data.content.CatalogEntry
import com.atharchive.core.data.content.ContentDigests
import com.atharchive.core.data.content.PackageReference
import com.atharchive.core.data.content.RootPayload
import com.atharchive.core.data.content.RootSignature
import com.atharchive.core.data.content.SignedEnvelope
import com.atharchive.core.data.content.TombstoneDocument
import com.atharchive.core.data.db.content.AtharContentDatabase
import com.atharchive.core.data.db.user.AtharUserDatabase
import com.atharchive.core.data.network.AppContentHttpClient
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The catalog snapshot shipped in the app must seed an empty library and never replace a newer one. */
@RunWith(AndroidJUnit4::class)
class BundledCatalogSeedTest {
    private lateinit var content: AtharContentDatabase
    private lateinit var user: AtharUserDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        content = Room.inMemoryDatabaseBuilder<AtharContentDatabase>(context)
            .setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).setSingleConnectionPool().build()
        user = Room.inMemoryDatabaseBuilder<AtharUserDatabase>(context)
            .setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).setSingleConnectionPool().build()
    }

    @After
    fun tearDown() {
        content.close()
        user.close()
    }

    @Test
    fun seedsAnEmptyLibraryOnceAndNeverReplacesAnAppliedGeneration() = runBlocking {
        val keys = KeyPairGenerator.getInstance("RSA").apply { initialize(3072) }.generateKeyPair()
        val trusted = mapOf("seed-key" to keys.public)
        val repository = ReadThroughContentRepository(
            database = content,
            userDatabase = user,
            client = AppContentHttpClient("https://example.invalid/app/v2/".toHttpUrl(), OkHttpClient()),
            importer = ContentImporter(content),
        )

        val newer = snapshot("gen-2", "كتاب جديد", keys)
        assertTrue("an empty library is seeded", repository.seedCatalog(trusted, newer::get))
        assertEquals("gen-2", content.catalogDao().generation()?.generationId)
        assertNotNull(content.catalogDao().entity("seed-book"))

        val staler = snapshot("gen-1", "كتاب قديم", keys)
        assertFalse("an applied generation is never replaced", repository.seedCatalog(trusted, staler::get))
        assertEquals("gen-2", content.catalogDao().generation()?.generationId)
        assertEquals("كتاب جديد", content.catalogDao().entity("seed-book")?.title)
    }

    @Test
    fun anUntrustedSnapshotSeedsNothing() = runBlocking {
        val keys = KeyPairGenerator.getInstance("RSA").apply { initialize(3072) }.generateKeyPair()
        val repository = ReadThroughContentRepository(
            database = content,
            userDatabase = user,
            client = AppContentHttpClient("https://example.invalid/app/v2/".toHttpUrl(), OkHttpClient()),
            importer = ContentImporter(content),
        )
        val files = snapshot("gen-1", "كتاب", keys)
        val failure = runCatching { repository.seedCatalog(emptyMap(), files::get) }.exceptionOrNull()
        assertTrue(failure is com.atharchive.core.data.content.ContentIntegrityException)
        assertEquals(null, content.catalogDao().generation())
    }

    /** A signed `index.json` plus catalog and tombstones, keyed by their path in the assets. */
    private fun snapshot(generation: String, title: String, keys: java.security.KeyPair): Map<String, ByteArray> {
        val entry = CatalogEntry(
            id = "seed-book", coll = "book", v = 1, hash = "1".repeat(64), title = title,
            pkg = PackageReference(
                path = "content/book/seed-book/${"a".repeat(64)}.athar", hash = "a".repeat(64), size = 10,
                idxPath = "content/book/seed-book/${"b".repeat(64)}.athar.idx", idxHash = "b".repeat(64), idxSize = 10,
                uncompressed = 10, blocks = 1, chapters = 1,
            ),
        )
        val catalog = AppContentJson.encodeToString(CatalogDocument(APP_CONTENT_SCHEMA, listOf(entry))).toByteArray()
        val tombstones = AppContentJson.encodeToString(TombstoneDocument(APP_CONTENT_SCHEMA, "", emptyList())).toByteArray()
        val catalogHash = ContentDigests.sha256Hex(catalog)
        val tombstoneHash = ContentDigests.sha256Hex(tombstones)
        val payload = AppContentJson.encodeToString(
            RootPayload(
                schema = APP_CONTENT_SCHEMA,
                generationId = generation,
                catalog = ArtifactReference("catalog/$catalogHash.json", catalogHash, catalog.size.toLong()),
                tombstones = ArtifactReference("tombstones/$tombstoneHash.json", tombstoneHash, tombstones.size.toLong()),
                minAppSchema = APP_CONTENT_SCHEMA,
            ),
        ).toByteArray()
        val url = Base64.getUrlEncoder().withoutPadding()
        val signature = Signature.getInstance("SHA256withRSA").run {
            initSign(keys.private)
            update(payload)
            sign()
        }
        val envelope = AppContentJson.encodeToString(
            SignedEnvelope(1, url.encodeToString(payload), listOf(RootSignature("seed-key", "SHA256withRSA", url.encodeToString(signature)))),
        ).toByteArray()
        return mapOf(
            "index.json" to envelope,
            "catalog/$catalogHash.json" to catalog,
            "tombstones/$tombstoneHash.json" to tombstones,
        )
    }
}
