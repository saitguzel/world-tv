package com.worldtv.core.designsystem.theme

import androidx.compose.ui.unit.dp

object WorldTvDimens {
    /** Overscan margin. Older panels crop the outer edges of the signal. */
    val ScreenPadding = 48.dp

    /** D-pad targets need height even though nothing is ever tapped. */
    val MinFocusTarget = 48.dp

    val CardWidth = 200.dp
    val CardHeight = 120.dp
    val CardSpacing = 16.dp
    val CardCorner = 12.dp

    val FocusBorderWidth = 3.dp
    val FocusGlowRadius = 12.dp

    val DrawerWidthCollapsed = 88.dp
    val DrawerWidthExpanded = 280.dp

    /** Kept under 250ms: past that, focus movement starts to feel like remote lag. */
    const val FocusAnimationMillis = 180
    const val FocusScale = 1.08f
}
