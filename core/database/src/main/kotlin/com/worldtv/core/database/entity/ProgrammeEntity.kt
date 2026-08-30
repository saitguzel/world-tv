package com.worldtv.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One EPG entry.
 *
 * The primary key is a composite of channel and start time rather than a generated id,
 * so re-importing a guide overwrites the same rows instead of accumulating duplicates
 * — guides are re-published in full, several times a day.
 */
@Entity(
    tableName = "programmes",
    primaryKeys = ["channelId", "startAt"],
    indices = [
        // The only hot query is "what is on channel X around now", which this serves
        // directly: equality on channelId, then a range scan on endAt.
        Index(value = ["channelId", "endAt"]),
        Index("endAt"),
    ],
)
data class ProgrammeEntity(
    val channelId: String,
    val startAt: Long,
    val endAt: Long,
    val title: String,
    val description: String?,
    val category: String?,
    val episode: String?,
)

/** Which EPG source covers which channel, from `guides.json`. */
@Entity(tableName = "epg_sources", indices = [Index("url")])
data class EpgSourceEntity(
    @PrimaryKey val id: String,
    val channelId: String,
    val url: String,
    val language: String?,
    val lastFetchedAt: Long,
)
