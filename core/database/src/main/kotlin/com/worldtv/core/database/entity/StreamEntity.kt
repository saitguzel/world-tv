package com.worldtv.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.worldtv.core.model.HealthInfo
import com.worldtv.core.model.StreamKind

/**
 * Shared health columns.
 *
 * `@Embedded` with no prefix so the column names are identical across `streams` and
 * `radio_stations` — the sweep queries are otherwise duplicated per table.
 */
data class HealthColumns(
    @ColumnInfo(defaultValue = "'UNKNOWN'") val state: String = "UNKNOWN",
    @ColumnInfo(defaultValue = "0") val lastCheckedAt: Long = 0L,
    @ColumnInfo(defaultValue = "0") val lastOkAt: Long = 0L,
    @ColumnInfo(defaultValue = "0") val consecutiveFailures: Int = 0,
    @ColumnInfo(defaultValue = "0") val lastLatencyMs: Int = 0,
    @ColumnInfo(defaultValue = "0") val nextCheckAt: Long = 0L,
    @ColumnInfo(defaultValue = "0") val lastErrorCode: Int = 0,
    @ColumnInfo(defaultValue = "0") val isVod: Boolean = false,
)

@Entity(
    tableName = "streams",
    indices = [
        Index("channelId"),
        Index("state"),
        // The sweep's only hot query: `WHERE nextCheckAt <= ? AND state != 'DEAD'
        // ORDER BY nextCheckAt`. Leading column must be `state` for the equality
        // predicate, then `nextCheckAt` for the range scan and the ordering.
        Index(value = ["state", "nextCheckAt"]),
    ],
)
data class StreamEntity(
    /** Stable hash of `url` + `channelId`; see `StreamIdFactory`. */
    @PrimaryKey val id: String,
    val channelId: String?,
    val url: String,
    val title: String,
    val quality: String?,
    val referrer: String?,
    val userAgent: String?,
    val label: String?,
    /** [StreamKind] name. Persisted so the sweep does not re-parse every URL. */
    @ColumnInfo(defaultValue = "'UNKNOWN_HTTP'") val kind: String = StreamKind.UNKNOWN_HTTP.name,
    @Embedded val health: HealthColumns = HealthColumns(),
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0L,
)

@Entity(
    tableName = "radio_stations",
    indices = [
        Index("countryCode"),
        Index("state"),
        Index(value = ["state", "nextCheckAt"]),
        Index("searchText"),
    ],
)
data class RadioStationEntity(
    @PrimaryKey val uuid: String,
    val name: String,
    /** Always Radio Browser's `url_resolved`; redirects are already followed. */
    val url: String,
    val faviconUrl: String?,
    val tags: String,
    val countryCode: String,
    val language: String?,
    val codec: String?,
    val bitrate: Int,
    /** Radio Browser's own `lastcheckok`; their probe runs from another region. */
    val serverSideOk: Boolean,
    val clickCount: Int,
    val votes: Int,
    val searchText: String,
    @Embedded val health: HealthColumns = HealthColumns(),
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0L,
)

/**
 * Cached YouTube live results.
 *
 * `expiresAt` is load-bearing rather than decorative: a live broadcast ends without
 * telling anyone, and a stale row shown as "live" sends the user to a dead player.
 * Entries past their expiry are hidden until the next refresh confirms them.
 */
@Entity(tableName = "youtube_streams", indices = [Index("channelId"), Index("expiresAt")])
data class YouTubeStreamEntity(
    @PrimaryKey val videoId: String,
    val channelId: String,
    @ColumnInfo(defaultValue = "''") val channelTitle: String = "",
    val title: String,
    val thumbnailUrl: String?,
    val fetchedAt: Long,
    val expiresAt: Long,
    @Embedded val health: HealthColumns = HealthColumns(),
)

fun HealthColumns.toModel(): HealthInfo = HealthInfo(
    state = enumValueOfOrDefault(state),
    lastCheckedAt = lastCheckedAt,
    lastOkAt = lastOkAt,
    consecutiveFailures = consecutiveFailures,
    lastLatencyMs = lastLatencyMs,
    nextCheckAt = nextCheckAt,
    lastErrorCode = lastErrorCode,
    isVod = isVod,
)

fun HealthInfo.toColumns(): HealthColumns = HealthColumns(
    state = state.name,
    lastCheckedAt = lastCheckedAt,
    lastOkAt = lastOkAt,
    consecutiveFailures = consecutiveFailures,
    lastLatencyMs = lastLatencyMs,
    nextCheckAt = nextCheckAt,
    lastErrorCode = lastErrorCode,
    isVod = isVod,
)

/** Unknown names decay to UNKNOWN rather than throwing, so a bad row cannot crash a sweep. */
private fun enumValueOfOrDefault(name: String): com.worldtv.core.model.StreamState =
    runCatching { com.worldtv.core.model.StreamState.valueOf(name) }
        .getOrDefault(com.worldtv.core.model.StreamState.UNKNOWN)
