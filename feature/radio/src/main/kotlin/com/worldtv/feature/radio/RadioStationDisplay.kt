package com.worldtv.feature.radio

import com.worldtv.core.model.HealthBadge
import com.worldtv.core.model.RadioStation
import com.worldtv.core.model.StreamState

/**
 * How a station is described in a list.
 *
 * Shared by the TV and phone screens rather than copied into each. The description is
 * cosmetic, but the badge is not: it decides which dot the user sees, and two
 * implementations of that rule would eventually disagree about the same station.
 */
internal fun RadioStation.describe(): String = buildList {
    codec?.let(::add)
    if (bitrate > 0) add("$bitrate kbps")
    if (tags.isNotEmpty()) add(tags.take(2).joinToString(", "))
}.joinToString(" · ")

/**
 * Radio Browser runs its own health checks from a different region, so a station it
 * calls broken is shown as unchecked rather than verified until our own probe agrees.
 */
internal fun RadioStation.badge(): HealthBadge = when {
    health.state == StreamState.DEAD -> HealthBadge.UNAVAILABLE
    health.state == StreamState.OK -> HealthBadge.VERIFIED
    health.state == StreamState.GEO_BLOCKED -> HealthBadge.GEO_BLOCKED
    else -> HealthBadge.UNCHECKED
}
