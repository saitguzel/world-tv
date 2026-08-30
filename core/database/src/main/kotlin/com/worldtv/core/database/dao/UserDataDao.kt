package com.worldtv.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.worldtv.core.database.entity.BlocklistEntity
import com.worldtv.core.database.entity.CategoryEntity
import com.worldtv.core.database.entity.CountryEntity
import com.worldtv.core.database.entity.DeviceBlacklistEntity
import com.worldtv.core.database.entity.FavoriteEntity
import com.worldtv.core.database.entity.RecentEntity
import com.worldtv.core.database.entity.SyncStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDataDao {

    @Query("SELECT id FROM favorites WHERE kind = :kind")
    fun favoriteIds(kind: String): Flow<List<String>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE id = :id AND kind = :kind)")
    fun isFavorite(id: String, kind: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE id = :id AND kind = :kind")
    suspend fun removeFavorite(id: String, kind: String)

    /**
     * Records a view, keeping a running count so "continue watching" can rank by how
     * much the user actually returns to a channel rather than by recency alone.
     */
    @Query(
        """
        INSERT INTO recents (id, kind, watchedAt, watchCount) VALUES (:id, :kind, :now, 1)
        ON CONFLICT(id) DO UPDATE SET watchedAt = :now, watchCount = watchCount + 1
        """,
    )
    suspend fun recordWatch(id: String, kind: String, now: Long)

    @Query("DELETE FROM recents WHERE id NOT IN (SELECT id FROM recents ORDER BY watchedAt DESC LIMIT :keep)")
    suspend fun trimRecents(keep: Int)

    @Query("SELECT * FROM recents ORDER BY watchedAt DESC LIMIT :limit")
    fun recents(limit: Int): Flow<List<RecentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun blacklistOnThisDevice(entry: DeviceBlacklistEntity)

    @Query("SELECT streamId FROM device_blacklist")
    suspend fun deviceBlacklist(): List<String>

    @Query("DELETE FROM device_blacklist")
    suspend fun clearDeviceBlacklist()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCountries(countries: List<CountryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategories(categories: List<CategoryEntity>)

    @Query("SELECT * FROM categories ORDER BY name COLLATE NOCASE")
    fun categories(): Flow<List<CategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBlocklist(entries: List<BlocklistEntity>)

    @Query("DELETE FROM blocklist")
    suspend fun clearBlocklist()

    @Query("SELECT * FROM sync_state WHERE resource = :resource")
    suspend fun syncState(resource: String): SyncStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSyncState(state: SyncStateEntity)

    @Query("SELECT * FROM sync_state")
    fun allSyncState(): Flow<List<SyncStateEntity>>
}
