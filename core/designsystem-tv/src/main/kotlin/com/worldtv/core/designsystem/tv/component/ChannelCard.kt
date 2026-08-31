package com.worldtv.core.designsystem.tv.component

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ClickableSurfaceScale
import androidx.tv.material3.Glow
import androidx.tv.material3.Surface
import com.worldtv.core.designsystem.theme.LocalReduceMotion
import com.worldtv.core.designsystem.theme.WorldTvColors
import com.worldtv.core.designsystem.theme.WorldTvDimens
import com.worldtv.core.designsystem.component.ChannelCardContent
import com.worldtv.core.designsystem.component.ChannelCardState
import com.worldtv.core.designsystem.component.channelCardDescriptionParts

/**
 * The focusable channel card.
 *
 * Focus is signalled three ways at once — scale, border and glow. One signal alone is
 * not legible from three metres, and a border-only treatment disappears entirely for a
 * viewer with reduced contrast sensitivity. None of the three is colour-coded, so
 * colour blindness does not cost the user the focus indicator.
 *
 * Only the wrapper lives here. What the card *shows* is [ChannelCardContent], shared
 * with the phone card so the two cannot drift into displaying different information.
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
    val description = channelCardDescriptionParts(state)

    val scale by animateFloatAsState(
        targetValue = if (focused && !reduceMotion) WorldTvDimens.FocusScale else 1f,
        animationSpec = tween(WorldTvDimens.FocusAnimationMillis, easing = FastOutSlowInEasing),
        label = "cardScale",
    )

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
            .semantics {
                // Joined here rather than eagerly so a grid of sixty cards is not
                // building strings on every focus change with nothing listening.
                contentDescription = description.build(state)
            },
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
        ChannelCardContent(state)
    }
}
