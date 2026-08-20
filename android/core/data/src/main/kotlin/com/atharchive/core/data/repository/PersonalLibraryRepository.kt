package com.atharchive.core.data.repository

import com.atharchive.core.data.db.user.AtharUserDatabase
import com.atharchive.core.data.db.user.CollectionItemEntity
import com.atharchive.core.data.db.user.LibraryEntryEntity
import com.atharchive.core.data.db.user.LibraryStatus
import com.atharchive.core.data.db.user.ReadingHistoryEntity
import com.atharchive.core.data.db.user.RecentSearchEntity
import com.atharchive.core.data.db.user.UserCollectionEntity
import java.util.UUID
import kotlinx.coroutines.flow.Flow

class PersonalLibraryRepository(
    private val database: AtharUserDatabase,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val dao = database.userDataDao()

    fun observeEntries(): Flow<List<LibraryEntryEntity>> = dao.observeLibraryEntries()
    fun observeCollections(): Flow<List<UserCollectionEntity>> = dao.observeUserCollections()
    fun observeCollectionItems(): Flow<List<CollectionItemEntity>> = dao.observeCollectionItems()
    fun observeHistory(): Flow<List<ReadingHistoryEntity>> = dao.observeReadingHistory()
    fun observeRecentSearches(): Flow<List<RecentSearchEntity>> = dao.observeRecentSearches()

    suspend fun setStatus(entityId: String, status: String?) {
        require(entityId.isNotBlank())
        if (status == null) {
            dao.removeLibraryEntry(entityId)
            return
        }
        require(status in LibraryStatus.all) { "unsupported library status $status" }
        val now = nowMillis()
        val previous = dao.libraryEntry(entityId)
        dao.saveLibraryEntry(
            LibraryEntryEntity(
                entityId = entityId,
                status = status,
                addedAt = previous?.addedAt ?: now,
                updatedAt = now,
            ),
        )
    }

    suspend fun toggleReadLater(entityId: String) {
        val existing = dao.libraryEntry(entityId)
        setStatus(entityId, if (existing == null) LibraryStatus.READ_LATER else null)
    }

    suspend fun createCollection(title: String): String {
        val clean = title.trim()
        require(clean.isNotEmpty()) { "collection title cannot be empty" }
        require(clean.length <= 80) { "collection title is too long" }
        val now = nowMillis()
        val id = newId()
        dao.saveUserCollection(UserCollectionEntity(id, clean, now, now))
        return id
    }

    suspend fun renameCollection(collection: UserCollectionEntity, title: String) {
        val clean = title.trim()
        require(clean.isNotEmpty()) { "collection title cannot be empty" }
        require(clean.length <= 80) { "collection title is too long" }
        dao.saveUserCollection(collection.copy(title = clean, updatedAt = nowMillis()))
    }

    suspend fun deleteCollection(collectionId: String) = dao.removeCollection(collectionId)

    suspend fun addToCollection(collectionId: String, entityId: String) {
        dao.addCollectionItem(CollectionItemEntity(collectionId, entityId, nowMillis()))
    }

    suspend fun removeFromCollection(collectionId: String, entityId: String) {
        dao.removeCollectionItem(collectionId, entityId)
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
