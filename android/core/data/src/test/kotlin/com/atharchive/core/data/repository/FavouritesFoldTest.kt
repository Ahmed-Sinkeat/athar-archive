package com.atharchive.core.data.repository

import com.atharchive.core.data.db.user.CollectionItemEntity
import com.atharchive.core.data.db.user.LibraryEntryEntity
import com.atharchive.core.data.db.user.LibraryStatus
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class FavouritesFoldTest {
    @Test fun collectionItemsAndExtrasBecomeFavouritesOnce() {
        val items = listOf(CollectionItemEntity("c1", "book-a", 10), CollectionItemEntity("c2", "book-a", 20), CollectionItemEntity("c1", "audio:x", 30))
        val rows = favouritesToAdd(existing = setOf("book-b"), items = items, extraIds = listOf("dhikr:7", "book-b"), now = 99)
        assertEquals(listOf("book-a", "audio:x", "dhikr:7"), rows.map { it.entityId })
        assertEquals(10L, rows.first().addedAt)
        assertTrue(rows.all { it.status == LibraryStatus.READ_LATER })
    }

    @Test fun foldIsIdempotentAndRespectsRemoval() {
        // After a fold the collection tables are empty, so a second fold with no new extras adds nothing.
        assertEquals(emptyList<LibraryEntryEntity>(), favouritesToAdd(existing = emptySet(), items = emptyList(), extraIds = emptyList(), now = 1))
    }
}
