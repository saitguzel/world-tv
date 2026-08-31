package com.worldtv.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.worldtv.core.designsystem.R
import com.worldtv.core.model.ChannelSummary
import com.worldtv.core.model.HealthBadge
import com.worldtv.core.model.Programme

/** Everything the card needs, flattened so it can be previewed without a database. */
data class ChannelCardState(
    val id: String,
    val name: String,
    val logoUrl: String?,
    val badge: HealthBadge,
    val isFavorite: Boolean,
    val subtitle: String? = null,
    /** What is on right now, when the guide has been fetched for this channel. */
    val nowPlaying: Programme? = null,
)

/**
 * Maps a [ChannelSummary] to its card state.
 *
 * Composable so the latency subtitle can be resolved from resources. It lives here
 * rather than in a feature module because every grid in the app renders these cards,
 * and a per-screen copy is how Home and Search silently lost the subtitle once.
 */
@Composable
fun ChannelSummary.toCardState(nowPlaying: Programme? = null): ChannelCardState =
    ChannelCardState(
        id = channel.id,
        name = channel.name,
        logoUrl = channel.logoUrl,
        badge = healthBadge,
        isFavorite = isFavorite,
        // Shown only when this channel has no guide data; the card prefers nowPlaying.
        subtitle = bestLatencyMs?.let { stringResource(R.string.latency_ms, it) },
        nowPlaying = nowPlaying,
    )

/**
 * The card's spoken description, built once and shared by both form factors.
 *
 * Single-sourced so TalkBack wording cannot drift between the TV card and the phone
 * card — the strings live in this module and there is no reason for two copies.
 *
 * Resolved eagerly because `stringResource` needs a composition, but joined by the
 * caller inside its `semantics` lambda so a grid of sixty cards is not concatenating
 * strings on every focus change when no accessibility service is even listening.
 */
@Composable
fun channelCardDescriptionParts(state: ChannelCardState): ChannelCardDescription =
    ChannelCardDescription(
        favoriteWord = stringResource(R.string.a11y_favorite),
        nowPlayingWord = state.nowPlaying?.let { stringResource(R.string.a11y_now_playing, it.title) },
        badgeWord = stringResource(
            when (state.badge) {
                HealthBadge.VERIFIED -> R.string.a11y_state_verified
                HealthBadge.UNCHECKED -> R.string.a11y_state_unchecked
                HealthBadge.GEO_BLOCKED -> R.string.a11y_state_geo_blocked
                HealthBadge.UNAVAILABLE -> R.string.a11y_state_unavailable
            },
        ),
    )

/** Pre-resolved wording for [ChannelCardDescription.build]. */
data class ChannelCardDescription(
    val favoriteWord: String,
    val nowPlayingWord: String?,
    val badgeWord: String,
) {
    fun build(state: ChannelCardState): String = buildString {
        append(state.name)
        if (state.isFavorite) append(", ").append(favoriteWord)
        nowPlayingWord?.let { append(", ").append(it) }
        append(", ").append(badgeWord)
    }
}
