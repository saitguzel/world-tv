package com.worldtv.core.designsystem.mobile.component

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.worldtv.core.designsystem.component.ChannelCardContent
import com.worldtv.core.designsystem.component.ChannelCardState
import com.worldtv.core.designsystem.component.channelCardDescriptionParts
import com.worldtv.core.designsystem.mobile.theme.MobileDimens
import com.worldtv.core.designsystem.theme.WorldTvColors

/**
 * The tappable channel card.
 *
 * The TV card signals focus three ways — scale, border and glow — because a remote has
 * no pointer. None of that applies here: a thumb knows where it is, and the press
 * ripple is the whole feedback story. What the card *shows* is [ChannelCardContent],
 * shared with the TV card so the two cannot drift into displaying different things.
 *
 * `onLongClick` toggles the favourite, matching TV. On a remote that gesture is
 * discoverable because it is the only thing a long press does; on a phone it is not,
 * so callers should pair this with a visible affordance rather than relying on it.
 */
@Composable
fun MobileChannelCard(
    state: ChannelCardState,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val description = channelCardDescriptionParts(state)

    Surface(
        modifier = modifier
            .sizeIn(minHeight = MobileDimens.MinTouchTarget)
            .aspectRatio(MobileDimens.CardAspectRatio)
            .clip(RoundedCornerShape(MobileDimens.CardCorner))
            // Ripple comes from LocalIndication, which the Material 3 theme supplies.
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .semantics { contentDescription = description.build(state) },
        color = WorldTvColors.SurfaceVariant,
        shape = RoundedCornerShape(MobileDimens.CardCorner),
    ) {
        ChannelCardContent(state)
    }
}
