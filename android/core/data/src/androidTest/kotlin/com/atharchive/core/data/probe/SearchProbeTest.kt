package com.atharchive.core.data.probe

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import com.atharchive.core.data.content.AppContentJson
import com.atharchive.core.data.content.CatalogDocument
import com.atharchive.core.data.db.content.AtharContentDatabase
import com.atharchive.core.data.repository.ContentImporter
import com.atharchive.core.data.repository.ContentSearchField
import com.atharchive.core.data.repository.ContentSearchRepository
import com.atharchive.core.data.repository.ContentSearchRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Test

class SearchProbeTest {
    @Test fun probe() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder<AtharContentDatabase>(ApplicationProvider.getApplicationContext<android.content.Context>())
            .setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).setSingleConnectionPool().build()
        val doc = AppContentJson.decodeFromString<CatalogDocument>(java.io.File("/data/local/tmp/catalog.json").readText())
        ContentImporter(db).applyCatalog(doc)
        val repo = ContentSearchRepository(db)
        for (q in listOf("نونية", "رائية", "لامية", "الأربعين النووية", "زاد المستقنع")) for (f in listOf(ContentSearchField.FullText, ContentSearchField.Title)) {
            val r = repo.search(ContentSearchRequest(q, field = f), 50)
            android.util.Log.i("PROBE", "$q $f -> ${r.catalog.size}: " + r.catalog.joinToString(" | ") { it.title })
        }
    }
}
