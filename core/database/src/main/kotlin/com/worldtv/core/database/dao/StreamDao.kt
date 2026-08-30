package com.worldtv.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.worldtv.core.database.entity.StreamEntity
import kotlinx.coroutines.flow.Flow

/** Just the fields a health probe needs — avoids loading whole rows during a sweep. */
data class ProbeRow(
    val id: String,
    val url: String,
    val referrer: String?,
    val userAgent: String?,
    val label: String?,
    val kind: String,
)

/** Health snapshot without the catalog columns. */
data class HealthRow(
    val state: String,
    val lastCheckedAt: Long,
    val lastOkAt: Long,
    val consecutiveFailures: Int,
    val lastLatencyMs: Int,
    val nextCheckAt: Long,
    val lastErrorCode: Int,
    val isVod: Boolean,
)

@Dao
interface StreamDao {

    /**
     * Streams a channel can be played from, best first.
     *
     * Ordering mirrors the doc's selection policy: verified before unknown before
     * geo-blocked, then fastest, then highest quality. Device-blacklisted streams
     * (a decoder this box lacks) are excluded here rather than at playback time so
     * the fallback chain never walks into the same failure twice.
     */
    @Query(
        """
        SELECT s.* FROM streams s
        WHERE s.channelId = :channelId
          AND s.state != 'DEAD'
          AND s.id NOT IN (SELECT streamId FROM device_blacklist)
        ORDER BY CASE s.state
                     WHEN 'OK' THEN 0
                     WHEN 'UNKNOWN' THEN 1
                     WHEN 'GEO_BLOCKED' THEN 2
                     ELSE 3
                 END ASC,
                 CASE WHEN s.lastLatencyMs = 0 THEN 999999 ELSE s.lastLatencyMs END ASC,
                 CAST(COALESCE(NULLIF(REPLACE(s.quality, 'p', ''), ''), '0') AS INTEGER) DESC
        """,
    )
    suspend fun playableStreams(channelId: String): List<StreamEntity>

    @Query("SELECT * FROM streams WHERE id = :id")
    suspend fun byId(id: String): StreamEntity?

    /**
     * The sweep query. Backed by the `(state, nextCheckAt)` index.
     *
     * DEAD rows are excluded here; they come back only through [reviveExpired].
     */
    @Query(
        """
        SELECT id, url, referrer, userAgent, label, kind FROM streams
        WHERE nextCheckAt <= :now AND state != 'DEAD'
        ORDER BY nextCheckAt ASC
        LIMIT :limit
        """,
    )
    suspend fun dueForCheck(now: Long, limit: Int): List<ProbeRow>

    @Query(
        """
        SELECT s.id, s.url, s.referrer, s.userAgent, s.label, s.kind FROM streams s
        JOIN favorites f ON f.id = s.channelId AND f.kind = 'channel'
        WHERE s.nextCheckAt <= :now AND s.state != 'DEAD'
        ORDER BY s.nextCheckAt ASC
        LIMIT :limit
        """,
    )
    suspend fun dueForCheckFavorites(now: Long, limit: Int): List<ProbeRow>

    @Query(
        """
        SELECT s.id, s.url, s.referrer, s.userAgent, s.label, s.kind FROM streams s
        JOIN recents r ON r.id = s.channelId AND r.kind = 'channel'
        WHERE s.nextCheckAt <= :now AND s.state != 'DEAD'
        ORDER BY r.watchedAt DESC, s.nextCheckAt ASC
        LIMIT :limit
        """,
    )
    suspend fun dueForCheckRecents(now: Long, limit: Int): List<ProbeRow>

    @Query(
        """
        SELECT s.id, s.url, s.referrer, s.userAgent, s.label, s.kind FROM streams s
        JOIN channels c ON c.id = s.channelId
        WHERE s.nextCheckAt <= :now AND s.state != 'DEAD' AND c.country = :country
        ORDER BY s.nextCheckAt ASC
        LIMIT :limit
        """,
    )
    suspend fun dueForCheckInCountry(now: Long, country: String, limit: Int): List<ProbeRow>

    /** Streams of the channels currently on screen, whether or not they are due. */
    @Query(
        """
        SELECT id, url, referrer, userAgent, label, kind FROM streams
        WHERE channelId IN (:channelIds) AND state != 'DEAD' AND nextCheckAt <= :now
        ORDER BY nextCheckAt ASC
        LIMIT :limit
        """,
    )
    suspend fun dueForChannels(channelIds: List<String>, now: Long, limit: Int): List<ProbeRow>

    @Query(
        """
        SELECT state, lastCheckedAt, lastOkAt, consecutiveFailures, lastLatencyMs,
               nextCheckAt, lastErrorCode, isVod
        FROM streams WHERE id = :id
        """,
    )
    suspend fun healthOf(id: String): HealthRow?

    /**
     * Writes back only the health columns.
     *
     * A whole-row upsert here would race the catalog sync and the player: a sweep that
     * started before a sync finished would restore the old URL, and a playback report
     * landing mid-sweep would be silently overwritten.
     */
    @Query(
        """
        UPDATE streams SET
            state = :state,
            lastCheckedAt = :lastCheckedAt,
            lastOkAt = :lastOkAt,
            consecutiveFailures = :consecutiveFailures,
            lastLatencyMs = :lastLatencyMs,
            nextCheckAt = :nextCheckAt,
            lastErrorCode = :lastErrorCode,
            isVod = :isVod
        WHERE id = :id
        """,
    )
    suspend fun updateHealth(
        id: String,
        state: String,
        lastCheckedAt: Long,
        lastOkAt: Long,
        consecutiveFailures: Int,
        lastLatencyMs: Int,
        nextCheckAt: Long,
        lastErrorCode: Int,
        isVod: Boolean,
    ): Int

    /** DEAD streams past their cool-off go back to UNKNOWN. Roughly one in eight returns. */
    @Query(
        """
        UPDATE streams
        SET state = 'UNKNOWN', consecutiveFailures = 0, nextCheckAt = :now
        WHERE state = 'DEAD' AND nextCheckAt <= :now
        """,
    )
    suspend fun reviveExpired(now: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoringExisting(streams: List<StreamEntity>): List<Long>

    /**
     * Catalog upsert that preserves everything the health engine has learned.
     *
     * `INSERT OR IGNORE` returns -1 for a row that already existed; those get a
     * targeted catalog-column update instead of a replace, so a resync never resets
     * `state`, `consecutiveFailures` or `nextCheckAt`. That history is the one thing
     * the app cannot re-download.
     *
     * `@Transaction` on a default method keeps the whole batch atomic without pulling
     * room-ktx into the sync module.
     */
    @Transaction
    suspend fun upsertPreservingHealth(streams: List<StreamEntity>, syncedAt: Long) {
        val insertResults = insertIgnoringExisting(streams)
        streams.forEachIndexed { index, entity ->
            if (insertResults.getOrNull(index) == -1L) {
                updateCatalogFields(
                    id = entity.id,
                    channelId = entity.channelId,
                    title = entity.title,
                    quality = entity.quality,
                    referrer = entity.referrer,
                    userAgent = entity.userAgent,
                    label = entity.label,
                    kind = entity.kind,
                    updatedAt = syncedAt,
                )
            }
        }
    }

    /**
     * Refreshes catalog columns for streams that already exist, leaving their health
     * history intact. A resync must never reset the app's accumulated knowledge.
     */
    @Query(
        """
        UPDATE streams SET
            channelId = :channelId, title = :title, quality = :quality,
            referrer = :referrer, userAgent = :userAgent, label = :label,
            kind = :kind, updatedAt = :updatedAt
        WHERE id = :id
        """,
    )
    suspend fun updateCatalogFields(
        id: String,
        channelId: String?,
        title: String,
        quality: String?,
        referrer: String?,
        userAgent: String?,
        label: String?,
        kind: String,
        updatedAt: Long,
    )

    @Query("SELECT COUNT(*) FROM streams WHERE state = :state")
    fun countByState(state: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM streams")
    suspend fun count(): Int

    /** Archive rather than delete: a stream long dead is still evidence. */
    @Query("DELETE FROM streams WHERE state = 'DEAD' AND lastCheckedAt < :before")
    suspend fun purgeLongDead(before: Long): Int
}
