package com.atharchive.core.data.db

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.atharchive.core.data.db.content.AtharContentDatabase
import com.atharchive.core.data.db.user.AtharUserDatabase
import com.atharchive.core.data.repository.ContentSearchSchema
import kotlinx.coroutines.Dispatchers

object AtharDatabases {
    const val USER_DATABASE_NAME = "athar_user.db"
    const val CONTENT_DATABASE_NAME = "athar_content.db"

    fun openUser(context: Context): AtharUserDatabase =
        Room.databaseBuilder<AtharUserDatabase>(context.applicationContext, USER_DATABASE_NAME)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .setSingleConnectionPool()
            .addMigrations(UserMigration1To2)
            .build()

    /** The catalog database built by `pnpm app:bundle-catalog`, absent from builds made without it. */
    private const val BUNDLED_CATALOG_ASSET = "app-content/catalog.db"

    fun openContent(context: Context): AtharContentDatabase =
        Room.databaseBuilder<AtharContentDatabase>(context.applicationContext, CONTENT_DATABASE_NAME)
            .apply {
                // A fresh install starts from the shipped catalog: decoding and applying the
                // catalog JSON took ~6.5 s on a Redmi 12. Room copies it only when no content
                // database exists yet, so an installed library is never replaced.
                if (context.assets.list("app-content")?.contains("catalog.db") == true) createFromAsset(BUNDLED_CATALOG_ASSET)
            }
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            // Room's default pool: WAL, several readers beside the one writer, so a download's
            // import no longer queues every page read behind it. (Writes still go one at a time.)
            .addMigrations(*ContentMigrations)
            // Only an older build installed over a newer one (dev, benchmark) wipes the content cache.
            .fallbackToDestructiveMigrationOnDowngrade(true)
            .addCallback(ContentSearchSchema.callback)
            .build()
}

/**
 * Every content-DB step since v1. No destructive fallback: a missing step must fail the
 * migration test (`ContentMigrationTest`), not silently wipe a reader's downloads on update.
 */
internal val ContentMigrations = arrayOf(
    object : androidx.room3.migration.Migration(1, 2) {
        override suspend fun migrate(connection: androidx.sqlite.SQLiteConnection) {
            connection.prepare("ALTER TABLE entity ADD COLUMN verseCount INTEGER").use { it.step() }
        }
    }, object : androidx.room3.migration.Migration(2, 3) {
        override suspend fun migrate(connection: androidx.sqlite.SQLiteConnection) {
            connection.prepare("ALTER TABLE entity ADD COLUMN f0Json TEXT").use { it.step() }
        }
    }, object : androidx.room3.migration.Migration(3, 4) {
        override suspend fun migrate(connection: androidx.sqlite.SQLiteConnection) {
            connection.prepare("ALTER TABLE footnote ADD COLUMN marker TEXT").use { it.step() }
        }
    }, object : androidx.room3.migration.Migration(4, 5) {
        override suspend fun migrate(connection: androidx.sqlite.SQLiteConnection) {
            connection.prepare("ALTER TABLE entity ADD COLUMN cardJson TEXT").use { it.step() }
        }
    }, object : androidx.room3.migration.Migration(5, 6) {
        override suspend fun migrate(connection: androidx.sqlite.SQLiteConnection) {
            connection.prepare("ALTER TABLE contentGeneration ADD COLUMN personsJson TEXT").use { it.step() }
        }
    }, object : androidx.room3.migration.Migration(6, 7) {
        override suspend fun migrate(connection: androidx.sqlite.SQLiteConnection) {
            for (column in listOf("editionOf", "edition", "editionsJson")) {
                connection.prepare("ALTER TABLE entity ADD COLUMN $column TEXT").use { it.step() }
            }
        }
    }, object : androidx.room3.migration.Migration(7, 8) {
        override suspend fun migrate(connection: androidx.sqlite.SQLiteConnection) {
            for (column in listOf("level INTEGER NOT NULL DEFAULT 2", "page INTEGER", "vol INTEGER")) {
                connection.prepare("ALTER TABLE chapter ADD COLUMN $column").use { it.step() }
            }
        }
    }, object : androidx.room3.migration.Migration(8, 9) {
        override suspend fun migrate(connection: androidx.sqlite.SQLiteConnection) {
            for (column in listOf("pageFrom", "pageTo", "volumes")) {
                connection.prepare("ALTER TABLE entity ADD COLUMN $column INTEGER").use { it.step() }
            }
            // The next sync re-applies the catalog so the new columns fill in (the row stays: deleting it would re-seed).
            connection.prepare("UPDATE contentGeneration SET generationId = ''").use { it.step() }
        }
    }, object : androidx.room3.migration.Migration(9, 10) {
        override suspend fun migrate(connection: androidx.sqlite.SQLiteConnection) {
            connection.prepare("ALTER TABLE entity ADD COLUMN volumeUnit TEXT").use { it.step() }
            connection.prepare("UPDATE contentGeneration SET generationId = ''").use { it.step() }
        }
    }, ContentMigration10To11, object : androidx.room3.migration.Migration(11, 12) {
        // Packed block text (BlockText) for frames imported from now on; rows already stored keep
        // theirs as plain text. Two indexes no query used: 11% and ~4% of a downloaded book.
        override suspend fun migrate(connection: androidx.sqlite.SQLiteConnection) {
            for (sql in listOf(
                "ALTER TABLE block ADD COLUMN packed BLOB",
                "DROP INDEX IF EXISTS index_block_entityId_chapterAnchor",
                "DROP INDEX IF EXISTS index_block_fp64",
            )) connection.prepare(sql).use { it.step() }
        }
    }, object : androidx.room3.migration.Migration(12, 13) {
        // 12.6: lessons whose transcript is not reviewed yet; 12.4: a poet's era. The next catalog sets both.
        override suspend fun migrate(connection: androidx.sqlite.SQLiteConnection) {
            connection.prepare("ALTER TABLE entity ADD COLUMN review INTEGER NOT NULL DEFAULT 0").use { it.step() }
            // 12.4: a poet's era, for the poetry filter.
            connection.prepare("ALTER TABLE entity ADD COLUMN era TEXT").use { it.step() }
        }
    }, object : androidx.room3.migration.Migration(13, 14) {
        // A ديوان's poems, listed one row each in الشعر. The next sync re-applies the catalog to fill them in.
        override suspend fun migrate(connection: androidx.sqlite.SQLiteConnection) {
            connection.prepare("ALTER TABLE entity ADD COLUMN poemsJson TEXT").use { it.step() }
            connection.prepare("UPDATE contentGeneration SET generationId = ''").use { it.step() }
        }
    }, object : androidx.room3.migration.Migration(14, 15) {
        // Each entity's catalog line hash; empty, so the next sync reads every line once and fills it.
        override suspend fun migrate(connection: androidx.sqlite.SQLiteConnection) {
            connection.prepare("CREATE TABLE IF NOT EXISTS `catalogLine` (`id` TEXT NOT NULL, `sha` TEXT NOT NULL, PRIMARY KEY(`id`))").use { it.step() }
        }
    },
)

/**
 * Download state moves from `entity` to `entityState` (see [com.atharchive.core.data.db.content.EntityStateEntity]),
 * copied over in SQL: the catalog is not re-applied. Columns nothing read are dropped, not moved.
 */
internal object ContentMigration10To11 : androidx.room3.migration.Migration(10, 11) {
    override suspend fun migrate(connection: androidx.sqlite.SQLiteConnection) {
        val statements = listOf(
            "CREATE TABLE IF NOT EXISTS `entityState` (`id` TEXT NOT NULL, `availability` TEXT NOT NULL, " +
                "`transferState` TEXT NOT NULL, `localPackageHash` TEXT, PRIMARY KEY(`id`))",
            "INSERT INTO entityState (id, availability, transferState, localPackageHash) " +
                "SELECT id, availability, transferState, localPackageHash FROM entity",
            "DROP INDEX IF EXISTS index_entity_availability",
            "DROP INDEX IF EXISTS index_entity_transferState",
            "DROP INDEX IF EXISTS index_entity_lastOpenedAt",
        ) + listOf(
            "availability", "transferState", "updateAvailable", "localVersion",
            "localPackageHash", "lastOpenedAt", "bytesOnDisk", "importProgress",
        ).map { "ALTER TABLE entity DROP COLUMN $it" }
        for (sql in statements) connection.prepare(sql).use { it.step() }
    }
}

/** Download bookkeeping (audio, pauses, «تنزيل الكل» rows) moves from SharedPreferences sets to rows; new tables only. */
internal object UserMigration1To2 : androidx.room3.migration.Migration(1, 2) {
    override suspend fun migrate(connection: androidx.sqlite.SQLiteConnection) {
        for (sql in listOf(
            "CREATE TABLE IF NOT EXISTS `audioDownload` (`audioId` TEXT NOT NULL, `requested` INTEGER NOT NULL, " +
                "`downloaded` INTEGER NOT NULL, PRIMARY KEY(`audioId`))",
            "CREATE TABLE IF NOT EXISTS `pausedDownload` (`pauseKey` TEXT NOT NULL, PRIMARY KEY(`pauseKey`))",
            "CREATE TABLE IF NOT EXISTS `downloadBatch` (`label` TEXT NOT NULL, `total` INTEGER NOT NULL, PRIMARY KEY(`label`))",
            "CREATE TABLE IF NOT EXISTS `downloadBatchItem` (`label` TEXT NOT NULL, `pauseKey` TEXT NOT NULL, PRIMARY KEY(`label`, `pauseKey`))",
            "CREATE INDEX IF NOT EXISTS `index_downloadBatchItem_pauseKey` ON `downloadBatchItem` (`pauseKey`)",
        )) connection.prepare(sql).use { it.step() }
    }
}
