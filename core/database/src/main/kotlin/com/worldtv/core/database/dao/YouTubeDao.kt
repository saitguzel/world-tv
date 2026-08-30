package com.worldtv.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.worldtv.core.database.entity.YouTubeStreamEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface YouTubeDao {

    /**
     * Broadcasts still within their freshness window.
     *
     * The expiry filter is the whole point: YouTube does not tell us when a stream
     * ends, so anything past its window is treated as gone rather than shown as live.
     */
    @Query(
        """
        SELECT * FROM youtube_streams
        WHERE expiresAt > :now
        ORDER BY channelTitle COLLATE NOCASE ASC, fetchedAt DESC
        """,
    )
    fun liveNow(now: Long): Flow<List<YouTubeStreamEntity>>

    @Query("SELECT * FROM youtube_streams WHERE videoId = :videoId")
    suspend fun byId(videoId: String): YouTubeStreamEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(streams: List<YouTubeStreamEntity>)

    /**
     * Clears a channel's previous results before writing new ones.
     *
     * Without this, a channel that has stopped broadcasting keeps its last row until
     * expiry — REPLACE only overwrites ids that come back.
     */
    @Query("DELETE FROM youtube_streams WHERE channelId = :channelId")
    suspend fun clearChannel(channelId: String)

    @Query("DELETE FROM youtube_streams WHERE expiresAt <= :now")
    suspend fun purgeExpired(now: Long): Int

    @Query("SELECT COUNT(*) FROM youtube_streams WHERE expiresAt > :now")
    suspend fun liveCount(now: Long): Int
}
