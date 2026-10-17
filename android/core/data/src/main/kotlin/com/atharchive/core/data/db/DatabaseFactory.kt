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
            .build()

    fun openContent(context: Context): AtharContentDatabase =
        Room.databaseBuilder<AtharContentDatabase>(context.applicationContext, CONTENT_DATABASE_NAME)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            // Room's default pool: WAL, several readers beside the one writer, so a download's
            // import no longer queues every page read behind it. (Writes still go one at a time.)
            .addMigrations(object : androidx.room3.migration.Migration(1, 2) {
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
            })
            .addCallback(ContentSearchSchema.callback)
            .fallbackToDestructiveMigration(true)
            .build()
}
