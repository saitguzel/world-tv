package com.worldtv.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.worldtv.core.database.entity.ChannelEntity
import kotlinx.coroutines.flow.Flow

/** One row of the channel grid: the channel plus the aggregate health of its streams. */
data class ChannelWithHealth(
    val id: String,
    val name: String,
    val country: String,
    val categories: String,
    val logoUrl: String?,
    val isNsfw: Boolean,
    val isClosed: Boolean,
    val replacedBy: String?,
    val availableStreams: Int,
    val verifiedStreams: Int,
    val geoBlockedStreams: Int,
    /** Null when nothing has been measured — see the NULLIF in the query. */
    val bestLatencyMs: Int?,
    val isFavorite: Boolean,
)

@Dao
interface ChannelDao {

    /**
     * The catalog query.
     *
     * Notes on the parts that are easy to get wrong:
     *  - `state` is compared against names, which only works because it is stored as
     *    TEXT rather than an enum ordinal.
     *  - `NULLIF(lastLatencyMs, 0)` keeps unmeasured streams out of `MIN()`; without
     *    it every unchecked channel reports a best latency of 0 and sorts first.
     *  - The join to `blocklist` is an anti-join, so a DMCA-listed channel disappears
     *    even if a later catalog sync re-adds it.
     *  - Channels whose streams are all DEAD produce no rows and drop out naturally.
     */
    @Transaction
    @Query(
        """
        SELECT c.id, c.name, c.country, c.categories, c.logoUrl, c.isNsfw, c.isClosed,
               c.replacedBy,
               COUNT(s.id) AS availableStreams,
               SUM(CASE WHEN s.state = 'OK' THEN 1 ELSE 0 END) AS verifiedStreams,
               SUM(CASE WHEN s.state = 'GEO_BLOCKED' THEN 1 ELSE 0 END) AS geoBlockedStreams,
               MIN(NULLIF(s.lastLatencyMs, 0)) AS bestLatencyMs,
               (f.id IS NOT NULL) AS isFavorite
        FROM channels c
        JOIN streams s ON s.channelId = c.id
        LEFT JOIN favorites f ON f.id = c.id AND f.kind = 'channel'
        WHERE c.isClosed = 0
          AND c.id NOT IN (SELECT channelId FROM blocklist)
          AND (:showNsfw OR c.isNsfw = 0)
          AND s.state != 'DEAD'
          AND (:showGeoBlocked OR s.state != 'GEO_BLOCKED')
          AND (:showUnchecked OR s.state != 'UNKNOWN')
          AND (:country IS NULL OR c.country = :country)
          AND (:category IS NULL OR (',' || c.categories || ',') LIKE '%,' || :category || ',%')
        GROUP BY c.id
        ORDER BY isFavorite DESC,
                 (verifiedStreams > 0) DESC,
                 c.name COLLATE NOCASE ASC
        """,
    )
    fun channels(
        country: String?,
        category: String?,
        showNsfw: Boolean,
        showGeoBlocked: Boolean,
        showUnchecked: Boolean,
    ): PagingSource<Int, ChannelWithHealth>

    /**
     * Same projection as [channels] but as a Flow, for the short lists on the home
     * screen where paging is overkill.
     */
    @Transaction
    @Query(
        """
        SELECT c.id, c.name, c.country, c.categories, c.logoUrl, c.isNsfw, c.isClosed,
               c.replacedBy,
               COUNT(s.id) AS availableStreams,
               SUM(CASE WHEN s.state = 'OK' THEN 1 ELSE 0 END) AS verifiedStreams,
               SUM(CASE WHEN s.state = 'GEO_BLOCKED' THEN 1 ELSE 0 END) AS geoBlockedStreams,
               MIN(NULLIF(s.lastLatencyMs, 0)) AS bestLatencyMs,
               1 AS isFavorite
        FROM channels c
        JOIN streams s ON s.channelId = c.id
        JOIN favorites f ON f.id = c.id AND f.kind = 'channel'
        WHERE c.isClosed = 0
          AND c.id NOT IN (SELECT channelId FROM blocklist)
          AND s.state != 'DEAD'
        GROUP BY c.id
        ORDER BY f.addedAt DESC
        """,
    )
    fun favoriteChannels(): Flow<List<ChannelWithHealth>>

    @Transaction
    @Query(
        """
        SELECT c.id, c.name, c.country, c.categories, c.logoUrl, c.isNsfw, c.isClosed,
               c.replacedBy,
               COUNT(s.id) AS availableStreams,
               SUM(CASE WHEN s.state = 'OK' THEN 1 ELSE 0 END) AS verifiedStreams,
               SUM(CASE WHEN s.state = 'GEO_BLOCKED' THEN 1 ELSE 0 END) AS geoBlockedStreams,
               MIN(NULLIF(s.lastLatencyMs, 0)) AS bestLatencyMs,
               (fav.id IS NOT NULL) AS isFavorite
        FROM channels c
        JOIN streams s ON s.channelId = c.id
        JOIN recents r ON r.id = c.id AND r.kind = 'channel'
        LEFT JOIN favorites fav ON fav.id = c.id AND fav.kind = 'channel'
        WHERE c.isClosed = 0
          AND c.id NOT IN (SELECT channelId FROM blocklist)
          AND s.state != 'DEAD'
        GROUP BY c.id
        ORDER BY r.watchedAt DESC
        LIMIT :limit
        """,
    )
    fun recentChannels(limit: Int): Flow<List<ChannelWithHealth>>

    /**
     * Search over the normalised haystack.
     *
     * The caller must normalise the query with the same `TextNormalizer` used at write
     * time, otherwise "türk" never matches the stored "turk".
     */
    @Transaction
    @Query(
        """
        SELECT c.id, c.name, c.country, c.categories, c.logoUrl, c.isNsfw, c.isClosed,
               c.replacedBy,
               COUNT(s.id) AS availableStreams,
               SUM(CASE WHEN s.state = 'OK' THEN 1 ELSE 0 END) AS verifiedStreams,
               SUM(CASE WHEN s.state = 'GEO_BLOCKED' THEN 1 ELSE 0 END) AS geoBlockedStreams,
               MIN(NULLIF(s.lastLatencyMs, 0)) AS bestLatencyMs,
               (f.id IS NOT NULL) AS isFavorite
        FROM channels c
        JOIN streams s ON s.channelId = c.id
        LEFT JOIN favorites f ON f.id = c.id AND f.kind = 'channel'
        WHERE c.isClosed = 0
          AND c.id NOT IN (SELECT channelId FROM blocklist)
          AND (:showNsfw OR c.isNsfw = 0)
          AND s.state != 'DEAD'
          AND (:country IS NULL OR c.country = :country)
          AND c.searchText LIKE '%' || :normalizedQuery || '%'
        GROUP BY c.id
        ORDER BY (c.searchText LIKE :normalizedQuery || '%') DESC,
                 (verifiedStreams > 0) DESC,
                 c.name COLLATE NOCASE ASC
        LIMIT :limit
        """,
    )
    fun search(
        normalizedQuery: String,
        showNsfw: Boolean,
        country: String?,
        limit: Int,
    ): Flow<List<ChannelWithHealth>>

    /**
     * One live channel at random, honouring the same filters as [channels].
     *
     * The random button plays a channel the user could have reached by scrolling — no
     * special-casing, or it would surface dead weight the grid hides.
     */
    @Transaction
    @Query(
        """
        SELECT c.id, c.name, c.country, c.categories, c.logoUrl, c.isNsfw, c.isClosed,
               c.replacedBy,
               COUNT(s.id) AS availableStreams,
               SUM(CASE WHEN s.state = 'OK' THEN 1 ELSE 0 END) AS verifiedStreams,
               SUM(CASE WHEN s.state = 'GEO_BLOCKED' THEN 1 ELSE 0 END) AS geoBlockedStreams,
               MIN(NULLIF(s.lastLatencyMs, 0)) AS bestLatencyMs,
               (f.id IS NOT NULL) AS isFavorite
        FROM channels c
        JOIN streams s ON s.channelId = c.id
        LEFT JOIN favorites f ON f.id = c.id AND f.kind = 'channel'
        WHERE c.isClosed = 0
          AND c.id NOT IN (SELECT channelId FROM blocklist)
          AND (:showNsfw OR c.isNsfw = 0)
          AND s.state != 'DEAD'
          AND (:showGeoBlocked OR s.state != 'GEO_BLOCKED')
          AND (:showUnchecked OR s.state != 'UNKNOWN')
          AND (:country IS NULL OR c.country = :country)
          AND (:category IS NULL OR (',' || c.categories || ',') LIKE '%,' || :category || ',%')
        GROUP BY c.id
        ORDER BY RANDOM()
        LIMIT 1
        """,
    )
    suspend fun randomChannel(
        country: String?,
        category: String?,
        showNsfw: Boolean,
        showGeoBlocked: Boolean,
        showUnchecked: Boolean,
    ): ChannelWithHealth?

    @Query("SELECT COUNT(*) FROM channels")
    fun channelCountFlow(): Flow<Int>

    /**
     * Summaries for a specific set of channels, used by the player's channel drawer.
     *
     * SQLite does not preserve the order of an `IN` list, so the caller re-sorts into
     * queue order — the drawer must match what the user was scrolling.
     */
    @Transaction
    @Query(
        """
        SELECT c.id, c.name, c.country, c.categories, c.logoUrl, c.isNsfw, c.isClosed,
               c.replacedBy,
               COUNT(s.id) AS availableStreams,
               SUM(CASE WHEN s.state = 'OK' THEN 1 ELSE 0 END) AS verifiedStreams,
               SUM(CASE WHEN s.state = 'GEO_BLOCKED' THEN 1 ELSE 0 END) AS geoBlockedStreams,
               MIN(NULLIF(s.lastLatencyMs, 0)) AS bestLatencyMs,
               (f.id IS NOT NULL) AS isFavorite
        FROM channels c
        JOIN streams s ON s.channelId = c.id
        LEFT JOIN favorites f ON f.id = c.id AND f.kind = 'channel'
        WHERE c.id IN (:ids)
          AND c.id NOT IN (SELECT channelId FROM blocklist)
          AND s.state != 'DEAD'
        GROUP BY c.id
        """,
    )
    fun summariesByIds(ids: List<String>): Flow<List<ChannelWithHealth>>

    @Query("SELECT * FROM channels WHERE id = :id")
    suspend fun channelById(id: String): ChannelEntity?

    @Query(
        """
        SELECT c.code, c.name, c.flag, c.languages,
               (SELECT COUNT(DISTINCT ch.id) FROM channels ch
                JOIN streams s ON s.channelId = ch.id
                WHERE ch.country = c.code AND ch.isClosed = 0 AND s.state != 'DEAD') AS channelCount
        FROM countries c
        ORDER BY channelCount DESC, c.name COLLATE NOCASE ASC
        """,
    )
    fun countriesWithCounts(): Flow<List<CountryWithCount>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(channels: List<ChannelEntity>)

    @Query("SELECT COUNT(*) FROM channels")
    suspend fun count(): Int
}

data class CountryWithCount(
    val code: String,
    val name: String,
    val flag: String,
    val languages: String,
    val channelCount: Int,
)
