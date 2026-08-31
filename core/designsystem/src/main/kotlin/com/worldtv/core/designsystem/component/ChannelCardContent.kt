package com.worldtv.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Size
import com.worldtv.core.designsystem.theme.LocalReduceMotion
import com.worldtv.core.designsystem.theme.LocalWorldTvTypeScale
import com.worldtv.core.designsystem.theme.WorldTvColors
import com.worldtv.core.designsystem.theme.WorldTvDimens
import com.worldtv.core.model.HealthBadge

/**
 * The inside of a channel card: logo, badges, name, and either the guide line or the
 * latency subtitle.
 *
 * Deliberately has no click handling, no focus handling and no Material dependency —
 * those differ entirely between a remote and a thumb, so each form factor supplies its
 * own wrapper (`ChannelCard` on TV, `MobileChannelCard` on a phone) and both render
 * this. Keeping the body here is what stops the two cards drifting into showing
 * different information.
 */
@Composable
fun ChannelCardContent(state: ChannelCardState, modifier: Modifier = Modifier) {
    val type = LocalWorldTvTypeScale.current
    val unavailable = state.badge == HealthBadge.UNAVAILABLE

    Box(
        modifier
            .fillMaxSize()
            // A stream that died while the screen was open fades instead of vanishing;
            // removing it would jump the user's focus mid-browse.
            .alpha(if (unavailable) 0.35f else 1f),
    ) {
        ChannelLogo(state, Modifier.fillMaxSize())

        Row(
            Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (state.isFavorite) FavoriteBadge()
            HealthDot(state.badge)
        }

        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            WorldTvText(
                text = state.name,
                style = type.bodyLarge,
                color = WorldTvColors.OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // The guide beats the latency reading when both exist: a viewer wants to
            // know what is on, not how fast the socket was.
            if (state.nowPlaying != null) {
                NowPlayingLine(
                    programme = state.nowPlaying,
                    now = System.currentTimeMillis(),
                    modifier = Modifier.padding(top = 2.dp),
                )
            } else {
                state.subtitle?.let { subtitle ->
                    WorldTvText(
                        text = subtitle,
                        style = type.labelLarge,
                        color = WorldTvColors.OnSurfaceMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelLogo(state: ChannelCardState, modifier: Modifier = Modifier) {
    if (state.logoUrl == null) {
        Box(modifier, contentAlignment = Alignment.Center) {
            WorldTvText(
                text = state.name.take(2).uppercase(),
                style = LocalWorldTvTypeScale.current.headlineMedium,
                color = WorldTvColors.OnSurfaceMuted,
            )
        }
        return
    }
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(state.logoUrl)
            // An explicit size is not optional on TV: full-resolution logos in a grid
            // of 60 cards exhausts the bitmap budget on a 1 GB box within a screen.
            .size(Size(240, 240))
            .crossfade(!LocalReduceMotion.current)
            .build(),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier
            .padding(20.dp)
            .clip(RoundedCornerShape(WorldTvDimens.CardCorner)),
    )
}

@Composable
private fun FavoriteBadge() {
    Box(
        Modifier
            .size(10.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(WorldTvColors.Accent),
    )
}
