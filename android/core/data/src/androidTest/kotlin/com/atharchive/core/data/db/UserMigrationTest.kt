package com.atharchive.core.data.db

import androidx.room3.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.atharchive.core.data.db.user.AtharUserDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class UserMigrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val file = File(instrumentation.targetContext.cacheDir, "user-migration-test.db").also { it.delete() }

    @get:Rule
    val helper = MigrationTestHelper(instrumentation, file, BundledSQLiteDriver(), AtharUserDatabase::class)

    /** The reader's own rows are untouched; the download tables are new and writable. */
    @Test
    fun downloadTablesAreAddedBesideUserData() = runBlocking {
        helper.createDatabase(1).use { db ->
            db.execSQL("INSERT INTO pinnedDownload (entityId, pinnedAt, pkgHash) VALUES ('book', 7, 'h')")
        }
        helper.runMigrationsAndValidate(2, listOf(UserMigration1To2)).use { db ->
            val pin = db.prepare("SELECT entityId, pinnedAt FROM pinnedDownload").use { it.step(); it.getText(0) to it.getLong(1) }
            assertEquals("book" to 7L, pin)
            db.execSQL("INSERT INTO pausedDownload (pauseKey) VALUES ('audio:a')")
            db.execSQL("INSERT INTO downloadBatchItem (label, pauseKey) VALUES ('الكتب', 'book')")
            val paused = db.prepare("SELECT COUNT(*) FROM pausedDownload").use { it.step(); it.getLong(0) }
            assertEquals(1L, paused)
        }
    }
}
