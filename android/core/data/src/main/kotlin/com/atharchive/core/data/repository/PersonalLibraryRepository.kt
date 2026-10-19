package com.atharchive.core.data.repository

import com.atharchive.core.data.db.user.AtharUserDatabase
import com.atharchive.core.data.db.user.CollectionItemEntity
import com.atharchive.core.data.db.user.LibraryEntryEntity
import com.atharchive.core.data.db.user.LibraryStatus
import com.atharchive.core.data.db.user.ReadingHistoryEntity
import com.atharchive.core.data.db.user.RecentSearchEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PersonalLibraryRepository(
    private val database: AtharUserDatabase,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val dao = database.userDataDao()

    fun observeEntries(): Flow<List<LibraryEntryEntity>> = dao.observeLibraryEntries()
    fun observeHistory(): Flow<List<ReadingHistoryEntity>> = dao.observeReadingHistory()
    fun observeRecentSearches(): Flow<List<RecentSearchEntity>> = dao.observeRecentSearches()

    fun observeFavouriteIds(): Flow<Set<String>> = dao.observeLibraryEntries().map { rows -> rows.mapTo(HashSet()) { it.entityId } }

    suspend fun toggleFavourite(entityId: String) {
        require(entityId.isNotBlank())
        if (dao.libraryEntry(entityId) != null) {
            dao.removeLibraryEntry(entityId)
            return
        }
        val now = nowMillis()
        dao.saveLibraryEntry(LibraryEntryEntity(entityId, LibraryStatus.READ_LATER, now, now))
    }

    /** One-time, idempotent: collection items + [extraIds] become favourites, then collections are cleared. */
    suspend fun foldIntoFavourites(extraIds: Collection<String>): Int {
        val existing = dao.libraryEntries().mapTo(HashSet()) { it.entityId }
        val rows = favouritesToAdd(existing, dao.collectionItems(), extraIds, nowMillis())
        dao.foldFavourites(rows)
        return rows.size
    }

    suspend fun rememberSearch(query: String) {
        val clean = query.trim().take(200)
        if (clean.length >= 2) dao.saveRecentSearch(RecentSearchEntity(clean, nowMillis()))
    }

    suspend fun clearRecentSearches() = dao.clearRecentSearches()

    suspend fun recordOpened(entityId: String) {
        dao.saveReadingHistory(ReadingHistoryEntity(entityId = entityId, openedAt = nowMillis(), secondsRead = 0))
    }
}

internal fun favouritesToAdd(
    existing: Set<String>,
    items: List<CollectionItemEntity>,
    extraIds: Collection<String>,
    now: Long,
): List<LibraryEntryEntity> {
    val seen = HashSet(existing)
    val out = ArrayList<LibraryEntryEntity>()
    fun add(id: String, at: Long) {
        if (id.isNotBlank() && seen.add(id)) out += LibraryEntryEntity(id, LibraryStatus.READ_LATER, at, at)
    }
    items.forEach { add(it.entityId, it.addedAt) }
    extraIds.forEach { add(it, now) }
    return out
}
