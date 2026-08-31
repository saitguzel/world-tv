package com.worldtv.core.designsystem.mobile.theme

import androidx.compose.ui.unit.dp

/**
 * Phone metrics.
 *
 * Separate from `WorldTvDimens` rather than scaled from it, because the two are sized
 * by different constraints. The TV numbers exist to survive overscan cropping and to be
 * read from three metres; these exist to fit a thumb and a 360dp-wide screen, where the
 * TV's 48dp screen padding alone would eat 27% of the width.
 */
object MobileDimens {
    /** No overscan on a phone — this is ordinary Material gutter. */
    val ScreenPadding = 16.dp

    /**
     * Smallest a card may be before the grid drops a column.
     *
     * Yields roughly 2 columns in portrait, 4 in landscape and 6 on a tablet, without
     * anyone having to pick a column count per screen.
     */
    val CardMinWidth = 148.dp

    /** Material's minimum touch target. The TV file's 48dp coincides, by luck. */
    val MinTouchTarget = 48.dp

    val CardSpacing = 8.dp
    val CardCorner = 12.dp

    /** Cards keep the TV's 5:3 proportion so logos crop identically on both. */
    const val CardAspectRatio = 5f / 3f
}
