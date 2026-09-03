package com.worldtv.core.designsystem.component

import com.worldtv.core.model.HealthBadge
import com.worldtv.core.model.RadioStation
import com.worldtv.core.model.badge
import com.worldtv.core.model.describe

/**
 * What a station row is doing, as the session reports it.
 *
 * Deliberately not "is this the selected row": every screen used to mark the current
 * station as playing, so a station that was paused, ducked, or had died on a dead
 * stream still showed as on air — the one thing the row exists to tell you.
 */
enum class StationPlayback { IDLE, BUFFERING, PLAYING, PAUSED }

/**
 * Everything a station row needs, flattened so it can be rendered without a session.
 *
 * The four screens that list stations — radio, favourites, search, home — each grew
 * their own near-identical row, which is how the playing indicator came to be wrong in
 * all of them at once.
 */
data class StationRowState(
    val id: String,
    val name: String,
    val subtitle: String,
    val badge: HealthBadge,
    val isFavorite: Boolean = false,
    val playback: StationPlayback = StationPlayback.IDLE,
)

fun RadioStation.toRowState(
    isFavorite: Boolean = false,
    playback: StationPlayback = StationPlayback.IDLE,
): StationRowState = StationRowState(
    id = uuid,
    name = name,
    subtitle = describe(),
    badge = badge(),
    isFavorite = isFavorite,
    playback = playback,
)

/**
 * The row's playback state.
 *
 * Pure, and the single place the question is answered, so the rule cannot drift
 * between the lists again.
 */
fun stationPlayback(
    isCurrent: Boolean,
    isPlaying: Boolean,
    isBuffering: Boolean,
): StationPlayback = when {
    !isCurrent -> StationPlayback.IDLE
    isPlaying -> StationPlayback.PLAYING
    isBuffering -> StationPlayback.BUFFERING
    else -> StationPlayback.PAUSED
}

/**
 * A station as a card, for the home shelves.
 *
 * Home is a wall of cards and a station is not different enough to earn a second card
 * component: the favicon takes the logo slot, and the codec/bitrate line the subtitle.
 */
fun RadioStation.toCardState(): ChannelCardState = ChannelCardState(
    id = uuid,
    name = name,
    logoUrl = faviconUrl,
    badge = badge(),
    isFavorite = false,
    subtitle = describe(),
)
