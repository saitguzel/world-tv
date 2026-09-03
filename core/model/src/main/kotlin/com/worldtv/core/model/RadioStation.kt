package com.worldtv.core.model

/** A Radio Browser station. */
data class RadioStation(
    val uuid: String,
    val name: String,
    /** Always the `url_resolved` value — redirects are already followed there. */
    val url: String,
    val faviconUrl: String?,
    val tags: List<String>,
    val countryCode: String,
    val language: String?,
    val codec: String?,
    val bitrate: Int,
    /** Radio Browser's own last check result. Their probe runs from a different region. */
    val serverSideOk: Boolean,
    val clickCount: Int,
    val votes: Int,
    val health: HealthInfo,
)

/**
 * How a station is described in a list: codec, bitrate, first two tags.
 *
 * Shared by every screen that lists stations rather than copied into each.
 */
fun RadioStation.describe(): String = buildList {
    codec?.let(::add)
    if (bitrate > 0) add("$bitrate kbps")
    if (tags.isNotEmpty()) add(tags.take(2).joinToString(", "))
}.joinToString(" · ")

/**
 * Which dot a station gets. Not cosmetic: two implementations of this rule would
 * eventually disagree about the same station.
 *
 * Only our own probe counts. Radio Browser runs its checks from another region, so its
 * verdict ([RadioStation.serverSideOk]) is deliberately ignored — a station it calls
 * fine may be dead from here, and vice versa. Until we have probed, it is unchecked.
 */
fun RadioStation.badge(): HealthBadge = when (health.state) {
    StreamState.DEAD -> HealthBadge.UNAVAILABLE
    StreamState.OK -> HealthBadge.VERIFIED
    StreamState.GEO_BLOCKED -> HealthBadge.GEO_BLOCKED
    else -> HealthBadge.UNCHECKED
}
