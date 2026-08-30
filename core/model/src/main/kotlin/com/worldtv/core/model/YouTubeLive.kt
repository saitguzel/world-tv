package com.worldtv.core.model

/** A YouTube live broadcast discovered for one of the curated channels. */
data class YouTubeLive(
    val videoId: String,
    /** The YouTube channel this broadcast belongs to. */
    val channelId: String,
    val channelTitle: String,
    val title: String,
    val thumbnailUrl: String?,
    val fetchedAt: Long,
    /** After this, the entry is stale and must not be shown as live. */
    val expiresAt: Long,
) {
    fun isFresh(now: Long): Boolean = now < expiresAt
}

/**
 * A hand-picked YouTube channel worth polling for live broadcasts.
 *
 * A curated list, not open search, because `search.list` costs 100 quota units against
 * a default daily budget of 10,000 — roughly a hundred calls a day for the entire user
 * base if the key ships in the app. Polling twenty known channels on a six-hour cycle
 * fits; letting users search does not.
 */
data class YouTubeSource(
    val channelId: String,
    val title: String,
    val category: String,
)
