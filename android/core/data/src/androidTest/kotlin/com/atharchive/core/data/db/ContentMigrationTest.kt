package com.atharchive.core.data.db

import androidx.room3.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.atharchive.core.data.db.content.AtharContentDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ContentMigrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val file = File(instrumentation.targetContext.cacheDir, "migration-test.db").also { it.delete() }

    @get:Rule
    val helper = MigrationTestHelper(instrumentation, file, BundledSQLiteDriver(), AtharContentDatabase::class)

    /** A downloaded book and a partly read one keep their state, and the catalog is not marked for a re-apply. */
    @Test
    fun downloadStateMovesToItsOwnTable() = runBlocking {
        helper.createDatabase(10).use { db ->
            fun insert(id: String, availability: String, transfer: String, pkg: String?) = db.execSQL(
                "INSERT INTO entity (id, coll, v, hash, title, titleNorm, openingVersesJson, topicsCsv, pkgPath, " +
                    "pkgHash, pkgSize, pkgUncompressed, idxPath, idxHash, idxSize, pkgBlocks, pkgChapters, audioJson, " +
                    "catalogPresent, availability, transferState, updateAvailable, bytesOnDisk, importProgress, localPackageHash) " +
                    "VALUES ('$id', 'book', 1, 'h', 't', 't', '[]', '', 'p', 'ph', 1, 1, 'i', 'ih', 1, 1, 1, '[]', " +
                    "1, '$availability', '$transfer', 0, 5, 1, ${pkg?.let { "'$it'" } ?: "NULL"})",
            )
            insert("downloaded", "complete", "idle", "ph")
            insert("failed", "partial", "failed", "ph")
            insert("untouched", "absent", "idle", null)
            db.execSQL(
                "INSERT INTO contentGeneration (singleton, generationId, catalogHash, tombstonesHash, appliedAt) " +
                    "VALUES (0, 'gen-1', 'c', 't', 1)",
            )
        }

        helper.runMigrationsAndValidate(11, listOf(ContentMigration10To11)).use { db ->
            val rows = buildList {
                db.prepare("SELECT id, availability, transferState, localPackageHash FROM entityState ORDER BY id").use {
                    while (it.step()) add(listOf(it.getText(0), it.getText(1), it.getText(2), if (it.isNull(3)) null else it.getText(3)))
                }
            }
            assertEquals(
                listOf(
                    listOf("downloaded", "complete", "idle", "ph"),
                    listOf("failed", "partial", "failed", "ph"),
                    listOf("untouched", "absent", "idle", null),
                ),
                rows,
            )
            val generation = db.prepare("SELECT generationId FROM contentGeneration").use { it.step(); it.getText(0) }
            assertEquals("gen-1", generation)
        }
    }

    /** With no destructive fallback, every version a phone may hold must reach the current one. */
    @Test
    fun everyOldVersionMigratesToTheCurrentSchema() = runBlocking {
        for (version in 1..10) {
            file.delete()
            helper.createDatabase(version).close()
            helper.runMigrationsAndValidate(11, ContentMigrations.toList()).close()
        }
    }
}
