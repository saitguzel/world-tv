package com.worldtv.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.worldtv.core.designsystem.theme.WorldTvColors
import com.worldtv.core.model.HealthBadge

/**
 * The small health indicator on a card.
 *
 * Colour carries the meaning fastest, but never alone: verified is a filled dot,
 * unchecked is hollow, geo-blocked is a ring, unavailable is a dimmed dot. A viewer
 * who cannot separate green from amber can still separate filled from hollow.
 */
@Composable
fun HealthDot(badge: HealthBadge, modifier: Modifier = Modifier) {
    val size = 10.dp
    when (badge) {
        HealthBadge.VERIFIED -> Box(
            modifier
                .size(size)
                .clip(CircleShape)
                .background(WorldTvColors.HealthVerified),
        )

        HealthBadge.UNCHECKED -> Box(
            modifier
                .size(size)
                .clip(CircleShape)
                .border(1.5.dp, WorldTvColors.HealthUnchecked, CircleShape),
        )

        HealthBadge.GEO_BLOCKED -> Box(
            modifier
                .size(size)
                .clip(CircleShape)
                .border(2.5.dp, WorldTvColors.HealthGeoBlocked, CircleShape),
        )

        HealthBadge.UNAVAILABLE -> Box(
            modifier
                .size(size)
                .clip(CircleShape)
                .background(WorldTvColors.HealthDead),
        )
    }
}
