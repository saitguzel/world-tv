package com.worldtv.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.worldtv.core.database.entity.RadioStationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RadioDao {

    /**
     * Radio Browser runs its own health checks, so `serverSideOk` already filters most
     * of the noise. Our own state is still applied on top because their probe runs
     * from a different region than the user.
     */
    @Query(
        """
        SELECT * FROM radio_stations
        WHERE state != 'DEAD'
          AND (:country IS NULL OR countryCode = :country)
          AND (:tag IS NULL OR (',' || tags || ',') LIKE '%,' || :tag || ',%')
        ORDER BY (state = 'OK') DESC, serverSideOk DESC, clickCount DESC
        """,
    )
    fun stations(country: String?, tag: String?): PagingSource<Int, RadioStationEntity>

    @Query(
        """
        SELECT r.* FROM radio_stations r
        JOIN favorites f ON f.id = r.uuid AND f.kind = 'radio'
        WHERE r.state != 'DEAD'
        ORDER BY f.addedAt DESC
        """,
    )
    fun favoriteStations(): Flow<List<RadioStationEntity>>

    @Query(
        """
        SELECT * FROM radio_stations
        WHERE state != 'DEAD' AND searchText LIKE '%' || :normalizedQuery || '%'
        ORDER BY (searchText LIKE :normalizedQuery || '%') DESC, clickCount DESC
        LIMIT :limit
        """,
    )
    fun search(normalizedQuery: String, limit: Int): Flow<List<RadioStationEntity>>

    @Query("SELECT * FROM radio_stations WHERE uuid = :uuid")
    suspend fun byId(uuid: String): RadioStationEntity?

    @Query(
        """
        SELECT uuid AS id, url, NULL AS referrer, NULL AS userAgent, NULL AS label,
               'PROGRESSIVE' AS kind
        FROM radio_stations
        WHERE nextCheckAt <= :now AND state != 'DEAD'
        ORDER BY nextCheckAt ASC
        LIMIT :limit
        """,
    )
    suspend fun dueForCheck(now: Long, limit: Int): List<ProbeRow>

    @Query(
        """
        SELECT state, lastCheckedAt, lastOkAt, consecutiveFailures, lastLatencyMs,
               nextCheckAt, lastErrorCode, isVod
        FROM radio_stations WHERE uuid = :uuid
        """,
    )
    suspend fun healthOf(uuid: String): HealthRow?

    @Query(
        """
        UPDATE radio_stations SET
            state = :state, lastCheckedAt = :lastCheckedAt, lastOkAt = :lastOkAt,
            consecutiveFailures = :consecutiveFailures, lastLatencyMs = :lastLatencyMs,
            nextCheckAt = :nextCheckAt, lastErrorCode = :lastErrorCode, isVod = :isVod
        WHERE uuid = :uuid
        """,
    )
    suspend fun updateHealth(
        uuid: String,
        state: String,
        lastCheckedAt: Long,
        lastOkAt: Long,
        consecutiveFailures: Int,
        lastLatencyMs: Int,
        nextCheckAt: Long,
        lastErrorCode: Int,
        isVod: Boolean,
    ): Int

    @Query(
        """
        UPDATE radio_stations
        SET state = 'UNKNOWN', consecutiveFailures = 0, nextCheckAt = :now
        WHERE state = 'DEAD' AND nextCheckAt <= :now
        """,
    )
    suspend fun reviveExpired(now: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoringExisting(stations: List<RadioStationEntity>): List<Long>

    @Query(
        """
        UPDATE radio_stations SET
            name = :name, url = :url, faviconUrl = :faviconUrl, tags = :tags,
            countryCode = :countryCode, language = :language, codec = :codec,
            bitrate = :bitrate, serverSideOk = :serverSideOk, clickCount = :clickCount,
            votes = :votes, searchText = :searchText, updatedAt = :updatedAt
        WHERE uuid = :uuid
        """,
    )
    suspend fun updateCatalogFields(
        uuid: String,
        name: String,
        url: String,
        faviconUrl: String?,
        tags: String,
        countryCode: String,
        language: String?,
        codec: String?,
        bitrate: Int,
        serverSideOk: Boolean,
        clickCount: Int,
        votes: Int,
        searchText: String,
        updatedAt: Long,
    )

    @Query("SELECT DISTINCT countryCode FROM radio_stations ORDER BY countryCode")
    fun availableCountries(): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM radio_stations")
    suspend fun count(): Int
}
