package com.worldtv.core.designsystem.component

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ClickableSurfaceScale
import androidx.tv.material3.Glow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Size
import com.worldtv.core.designsystem.theme.LocalReduceMotion
import com.worldtv.core.designsystem.theme.WorldTvColors
import com.worldtv.core.designsystem.theme.WorldTvDimens
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
 * The focusable channel card.
 *
 * Focus is signalled three ways at once — scale, border and glow. One signal alone is
 * not legible from three metres, and a border-only treatment disappears entirely for a
 * viewer with reduced contrast sensitivity. None of the three is colour-coded, so
 * colour blindness does not cost the user the focus indicator.
 */
@Composable
fun ChannelCard(
    state: ChannelCardState,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    /** Reported so callers can drive focus-dependent behaviour such as preview. */
    onFocusChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val reduceMotion = LocalReduceMotion.current

    val scale by animateFloatAsState(
        targetValue = if (focused && !reduceMotion) WorldTvDimens.FocusScale else 1f,
        animationSpec = tween(WorldTvDimens.FocusAnimationMillis, easing = FastOutSlowInEasing),
        label = "cardScale",
    )

    val unavailable = state.badge == HealthBadge.UNAVAILABLE

    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier
            // graphicsLayer, not Modifier.scale: the former animates in the draw phase,
            // while the latter invalidates layout every frame and janks a full grid.
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged {
                focused = it.isFocused
                onFocusChanged(it.isFocused)
            }
            .semantics { contentDescription = state.contentDescription() },
        // The scale animation above is ours, so the built-in one is switched off.
        scale = ClickableSurfaceScale.None,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(WorldTvDimens.CardCorner)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = WorldTvColors.SurfaceVariant,
            focusedContainerColor = WorldTvColors.SurfaceElevated,
            pressedContainerColor = WorldTvColors.SurfaceElevated,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(WorldTvDimens.FocusBorderWidth, WorldTvColors.FocusRing),
                shape = RoundedCornerShape(WorldTvDimens.CardCorner),
            ),
        ),
        glow = ClickableSurfaceDefaults.glow(
            focusedGlow = Glow(
                elevationColor = Color.Black.copy(alpha = 0.6f),
                elevation = WorldTvDimens.FocusGlowRadius,
            ),
        ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                // A stream that died while the screen was open fades instead of
                // vanishing; removing it would jump the user's focus mid-browse.
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
                Text(
                    text = state.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = WorldTvColors.OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // The guide beats the latency reading when both exist: a viewer
                // wants to know what is on, not how fast the socket was.
                if (state.nowPlaying != null) {
                    NowPlayingLine(
                        programme = state.nowPlaying,
                        now = System.currentTimeMillis(),
                        modifier = Modifier.padding(top = 2.dp),
                    )
                } else {
                    state.subtitle?.let { subtitle ->
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelLarge,
                            color = WorldTvColors.OnSurfaceMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelLogo(state: ChannelCardState, modifier: Modifier = Modifier) {
    if (state.logoUrl == null) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(
                text = state.name.take(2).uppercase(),
                style = MaterialTheme.typography.headlineMedium,
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

private fun ChannelCardState.contentDescription(): String = buildString {
    append(name)
    if (isFavorite) append(", favori")
    nowPlaying?.let { append(", şu an: " + it.title) }
    append(
        when (badge) {
            HealthBadge.VERIFIED -> ", doğrulandı"
            HealthBadge.UNCHECKED -> ", henüz kontrol edilmedi"
            HealthBadge.GEO_BLOCKED -> ", bölgesel kısıtlı olabilir"
            HealthBadge.UNAVAILABLE -> ", şu an kullanılamıyor"
        },
    )
}
