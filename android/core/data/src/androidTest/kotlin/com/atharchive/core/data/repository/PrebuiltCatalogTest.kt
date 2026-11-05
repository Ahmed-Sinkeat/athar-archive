package com.atharchive.core.data.repository

import android.content.Context
import androidx.room3.Room
import androidx.room3.useReaderConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.atharchive.core.data.content.AppContentJson
import com.atharchive.core.data.content.CatalogDocument
import com.atharchive.core.data.db.ContentMigrations
import com.atharchive.core.data.db.content.AtharContentDatabase
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The catalog database `pnpm app:bundle-catalog` builds must hold exactly what the app's own
 * apply writes; otherwise the next catalog update rewrites every row, or search misses.
 * Needs the real files on the device (skipped without them):
 *   adb push android/app/src/main/assets/app-content/catalog.db /data/local/tmp/
 *   adb push dist/app-content/app/v2/catalog/<hash>.json /data/local/tmp/catalog.json
 */
@RunWith(AndroidJUnit4::class)
class PrebuiltCatalogTest {
    @Test
    fun prebuiltCatalogMatchesTheAppsOwnApply(): Unit = runBlocking {
        val prebuiltFile = File("/data/local/tmp/catalog.db")
        val catalogFile = File("/data/local/tmp/catalog.json")
        assumeTrue("push catalog.db and catalog.json to /data/local/tmp", prebuiltFile.isFile && catalogFile.isFile)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dir = File(context.cacheDir, "prebuilt-${System.nanoTime()}").apply { mkdirs() }
        fun open(name: String, from: File?) = Room.databaseBuilder<AtharContentDatabase>(context, File(dir, name).absolutePath)
            .apply { if (from != null) createFromFile(from) }
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .addMigrations(*ContentMigrations)
            .addCallback(ContentSearchSchema.callback)
            .build()
        val prebuilt = open("prebuilt.db", prebuiltFile)
        val applied = open("applied.db", null)
        try {
            val started = System.nanoTime()
            val generation = requireNotNull(prebuilt.catalogDao().generation()) { "prebuilt catalog has no generation" }
            val openMs = (System.nanoTime() - started) / 1_000_000

            ContentImporter(applied).applyGeneration(
                generationId = generation.generationId,
                catalogHash = generation.catalogHash,
                tombstonesHash = generation.tombstonesHash,
                catalog = AppContentJson.decodeFromString<CatalogDocument>(catalogFile.readText()),
                tombstonedIds = emptySet(),
                protectedIds = emptySet(),
            )

            val expected = applied.catalogDao().allEntities().sortedBy { it.id }
            val actual = prebuilt.catalogDao().allEntities().sortedBy { it.id }
            assertEquals(expected.size, actual.size)
            expected.zip(actual).forEach { (want, got) -> assertEquals(want, got) }
            assertEquals(applied.catalogDao().generation()?.copy(appliedAt = 0), generation.copy(appliedAt = 0))
            for (sql in listOf(
                "SELECT id, availability, transferState, localPackageHash FROM entityState ORDER BY id",
                "SELECT id, titleNorm, personNorm, topicsNorm FROM catalog_fts ORDER BY id",
                "SELECT key, value FROM athar_search_meta ORDER BY key",
                // Every table, index and trigger, as written: a search table built differently would still hold equal rows.
                "SELECT type, name, sql FROM sqlite_master WHERE name NOT LIKE 'sqlite_%' AND name NOT GLOB '*_fts_[dic]*' ORDER BY name",
            )) assertEquals(sql, rows(applied, sql), rows(prebuilt, sql))
            android.util.Log.i("AtharPrebuilt", "${actual.size} entries match; first open ${openMs} ms")
        } finally {
            prebuilt.close()
            applied.close()
            dir.deleteRecursively()
        }
    }

    private suspend fun rows(db: AtharContentDatabase, sql: String): List<List<String?>> = db.useReaderConnection { connection ->
        connection.usePrepared(sql) { statement ->
            buildList {
                while (statement.step()) add((0 until statement.getColumnCount()).map { if (statement.isNull(it)) null else statement.getText(it) })
            }
        }
    }
}
