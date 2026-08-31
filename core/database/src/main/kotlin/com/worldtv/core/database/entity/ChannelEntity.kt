package com.worldtv.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "channels",
    indices = [
        Index("country"),
        Index("isClosed"),
        Index("searchText"),
    ],
)
data class ChannelEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** ISO 3166-1 alpha-2. */
    val country: String,
    /** Comma-delimited category ids; matched with `LIKE '%,news,%'` against a padded value. */
    val categories: String,
    val logoUrl: String?,
    val isNsfw: Boolean,
    val isClosed: Boolean,
    val replacedBy: String?,
    /** Accent-free, lowercase haystack built by `TextNormalizer.searchText`. */
    val searchText: String,
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0L,
)

@Entity(tableName = "countries")
data class CountryEntity(
    @PrimaryKey val code: String,
    val name: String,
    val flag: String,
    val languages: String,
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
)

/**
 * Channels removed after a DMCA notice.
 *
 * A separate table rather than a flag on `channels`, because the block list is
 * authoritative and must survive a catalog sync that re-adds the channel.
 */
@Entity(tableName = "blocklist")
data class BlocklistEntity(
    @PrimaryKey val channelId: String,
    val reason: String,
    val reference: String?,
)

@Entity(tableName = "favorites", indices = [Index("addedAt")])
data class FavoriteEntity(
    @PrimaryKey val id: String,
    /** Discriminates a channel id from a radio station uuid. */
    val kind: String,
    val addedAt: Long,
)

@Entity(tableName = "recents", indices = [Index("watchedAt")])
data class RecentEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val watchedAt: Long,
    val watchCount: Int,
)

/**
 * Streams that failed to decode **on this device**.
 *
 * Decoder faults are hardware-specific: a stream that will not open here because the
 * box has no HEVC decoder plays fine elsewhere. Those are hidden locally and never
 * reported into the shared health state.
 */
@Entity(tableName = "device_blacklist")
data class DeviceBlacklistEntity(
    @PrimaryKey val streamId: String,
    val errorCode: Int,
    val blockedAt: Long,
)

/** ETag/Last-Modified bookkeeping so an unchanged 20 MB catalog is never re-downloaded. */
@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val resource: String,
    val etag: String?,
    val lastModified: String?,
    val lastSyncedAt: Long,
    val lastSuccessAt: Long,
    val itemCount: Int,
)
