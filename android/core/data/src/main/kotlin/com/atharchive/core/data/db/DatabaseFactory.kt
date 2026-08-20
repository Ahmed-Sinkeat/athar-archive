package com.atharchive.core.data.db

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.atharchive.core.data.db.content.AtharContentDatabase
import com.atharchive.core.data.db.user.AtharUserDatabase
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
            .setSingleConnectionPool()
            .fallbackToDestructiveMigration(true)
            .build()
}
