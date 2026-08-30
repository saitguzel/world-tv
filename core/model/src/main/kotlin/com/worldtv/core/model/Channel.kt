package com.worldtv.core.model

/** A TV channel as the user sees it: one logical channel, one or more stream URLs. */
data class Channel(
    val id: String,
    val name: String,
    val country: String,
    val categories: List<String>,
    val logoUrl: String?,
    val isNsfw: Boolean,
    val isClosed: Boolean,
    val replacedBy: String? = null,
)

/** A channel plus the aggregate health of its streams, as rendered in the grid. */
data class ChannelSummary(
    val channel: Channel,
    /** Streams that are not DEAD. A channel with none of these is not listed at all. */
    val availableStreams: Int,
    /** Streams in [StreamState.OK]. */
    val verifiedStreams: Int,
    /** Lowest measured latency across verified streams, or null when none is measured. */
    val bestLatencyMs: Int?,
    val isFavorite: Boolean,
    val geoBlockedOnly: Boolean,
) {
    val healthBadge: HealthBadge
        get() = when {
            availableStreams == 0 -> HealthBadge.UNAVAILABLE
            verifiedStreams > 0 -> HealthBadge.VERIFIED
            geoBlockedOnly -> HealthBadge.GEO_BLOCKED
            else -> HealthBadge.UNCHECKED
        }
}

/**
 * What the small dot on a card means. Deliberately four distinguishable states —
 * the user should be able to tell "we have not looked yet" from "we looked and it
 * is broken", otherwise a slow first sweep reads as a broken app.
 */
enum class HealthBadge { VERIFIED, UNCHECKED, GEO_BLOCKED, UNAVAILABLE }

/** A single playable stream URL belonging to (at most) one channel. */
data class Stream(
    val id: String,
    val channelId: String?,
    val url: String,
    val title: String,
    val quality: String?,
    /** Sent as `Referer`. Omitting it turns a working stream into a 403. */
    val referrer: String?,
    /** Sent as `User-Agent`. Same story. */
    val userAgent: String?,
    /** Free-text from the catalog, e.g. "Geo-blocked", "Not 24/7". */
    val label: String?,
    val kind: StreamKind,
    val health: HealthInfo,
) {
    /** Numeric height parsed out of `quality` ("1080p" -> 1080), for ranking. */
    val qualityRank: Int
        get() = quality?.takeWhile { it.isDigit() }?.toIntOrNull() ?: 0
}
