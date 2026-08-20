package com.atharchive.core.data.db.user

import androidx.room3.Dao
import androidx.room3.Database
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.RoomDatabase
import androidx.room3.Transaction
import kotlinx.coroutines.flow.Flow

object LibraryStatus {
    const val READ_LATER = "readLater"
    const val READING = "reading"
    const val FINISHED = "finished"

    val all = setOf(READ_LATER, READING, FINISHED)
}

@Entity(
    tableName = "annotation",
    indices = [Index(value = ["entityId", "startOrdinalHint"])],
)
data class AnnotationEntity(
    @PrimaryKey val id: String,
    val entityId: String,
    val kind: String,
    val startBlockHi: Long,
    val startBlockLo: Long,
    val startOffset: Int,
    val endBlockHi: Long,
    val endBlockLo: Long,
    val endOffset: Int,
    val startOrdinalHint: Int,
    val endOrdinalHint: Int,
    val startFp64: Long,
    val endFp64: Long,
    val quotedText: String,
    val prefixContext: String,
    val suffixContext: String,
    val chapterAnchor: String,
    val printedPage: Int?,
    val colorId: Int,
    val noteText: String?,
    val state: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "readingPosition")
data class ReadingPositionEntity(
    @PrimaryKey val entityId: String,
    val chapterAnchor: String,
    val blockIdHi: Long,
    val blockIdLo: Long,
    val ordinalHint: Int,
    val offsetInBlock: Int,
    val progressPct: Double,
    val updatedAt: Long,
)

@Entity(
    tableName = "readingHistory",
    indices = [Index(value = ["openedAt"])],
)
data class ReadingHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entityId: String,
    val openedAt: Long,
    val secondsRead: Long,
)

@Entity(tableName = "libraryEntry")
data class LibraryEntryEntity(
    @PrimaryKey val entityId: String,
    val status: String,
    val addedAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "userCollection")
data class UserCollectionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "collectionItem",
    primaryKeys = ["collectionId", "entityId"],
    indices = [Index(value = ["entityId"])],
)
data class CollectionItemEntity(
    val collectionId: String,
    val entityId: String,
    val addedAt: Long,
)

@Entity(tableName = "pinnedDownload")
data class PinnedDownloadEntity(
    @PrimaryKey val entityId: String,
    val pinnedAt: Long,
    val pkgHash: String,
)

@Entity(tableName = "recentSearch")
data class RecentSearchEntity(
    @PrimaryKey val q: String,
    val at: Long,
)

@Dao
interface UserDataDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveReadingPosition(position: ReadingPositionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveLibraryEntry(entry: LibraryEntryEntity)

    @Query("DELETE FROM libraryEntry WHERE entityId = :entityId")
    suspend fun removeLibraryEntry(entityId: String)

    @Query("SELECT * FROM libraryEntry WHERE entityId = :entityId")
    suspend fun libraryEntry(entityId: String): LibraryEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveUserCollection(collection: UserCollectionEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addCollectionItem(item: CollectionItemEntity)

    @Query("DELETE FROM collectionItem WHERE collectionId = :collectionId AND entityId = :entityId")
    suspend fun removeCollectionItem(collectionId: String, entityId: String)

    @Query("DELETE FROM collectionItem WHERE collectionId = :collectionId")
    suspend fun removeCollectionItems(collectionId: String)

    @Query("DELETE FROM userCollection WHERE id = :collectionId")
    suspend fun removeUserCollection(collectionId: String)

    @Transaction
    suspend fun removeCollection(collectionId: String) {
        removeCollectionItems(collectionId)
        removeUserCollection(collectionId)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveRecentSearch(search: RecentSearchEntity)

    @Query("DELETE FROM recentSearch")
    suspend fun clearRecentSearches()

    @Insert
    suspend fun saveReadingHistory(history: ReadingHistoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePinnedDownload(download: PinnedDownloadEntity)

    @Query("DELETE FROM pinnedDownload WHERE entityId = :entityId")
    suspend fun removePinnedDownload(entityId: String)

    @Query("SELECT * FROM pinnedDownload WHERE entityId = :entityId")
    suspend fun pinnedDownload(entityId: String): PinnedDownloadEntity?

    @Query("SELECT * FROM pinnedDownload")
    fun observePinnedDownloads(): Flow<List<PinnedDownloadEntity>>

    @Query("SELECT entityId FROM pinnedDownload")
    suspend fun pinnedEntityIds(): List<String>

    @Query("SELECT * FROM libraryEntry")
    fun observeLibraryEntries(): Flow<List<LibraryEntryEntity>>

    @Query("SELECT * FROM libraryEntry ORDER BY entityId")
    suspend fun libraryEntries(): List<LibraryEntryEntity>

    @Query("SELECT * FROM userCollection ORDER BY createdAt, id")
    fun observeUserCollections(): Flow<List<UserCollectionEntity>>

    @Query("SELECT * FROM userCollection ORDER BY createdAt, id")
    suspend fun userCollections(): List<UserCollectionEntity>

    @Query("SELECT * FROM collectionItem ORDER BY addedAt, collectionId, entityId")
    fun observeCollectionItems(): Flow<List<CollectionItemEntity>>

    @Query("SELECT * FROM collectionItem ORDER BY addedAt, collectionId, entityId")
    suspend fun collectionItems(): List<CollectionItemEntity>

    @Query("SELECT * FROM readingHistory ORDER BY openedAt DESC LIMIT :limit")
    fun observeReadingHistory(limit: Int = 100): Flow<List<ReadingHistoryEntity>>

    @Query("SELECT * FROM recentSearch ORDER BY at DESC LIMIT :limit")
    fun observeRecentSearches(limit: Int = 8): Flow<List<RecentSearchEntity>>

    @Query("SELECT * FROM readingPosition")
    fun observeReadingPositions(): Flow<List<ReadingPositionEntity>>

    @Query("SELECT * FROM readingPosition WHERE entityId = :entityId")
    fun observeReadingPosition(entityId: String): Flow<ReadingPositionEntity?>

    @Query("SELECT * FROM readingPosition WHERE entityId = :entityId")
    suspend fun readingPosition(entityId: String): ReadingPositionEntity?

    @Query(
        "SELECT entityId FROM pinnedDownload " +
            "UNION SELECT entityId FROM annotation " +
            "UNION SELECT entityId FROM readingPosition " +
            "UNION SELECT entityId FROM libraryEntry " +
            "UNION SELECT entityId FROM collectionItem",
    )
    suspend fun protectedEntityIds(): List<String>
}

@Database(
    entities = [
        AnnotationEntity::class,
        ReadingPositionEntity::class,
        ReadingHistoryEntity::class,
        LibraryEntryEntity::class,
        UserCollectionEntity::class,
        CollectionItemEntity::class,
        PinnedDownloadEntity::class,
        RecentSearchEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AtharUserDatabase : RoomDatabase() {
    abstract fun userDataDao(): UserDataDao
}
