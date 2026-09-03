package com.worldtv.core.designsystem.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/*
 * Glyphs that material-icons-core does not ship, drawn from the Material path data.
 *
 * The packaged alternative is material-icons-extended: several thousand vectors for
 * the sake of four. These are neutral (plain ImageVectors), so both the TV and the
 * phone tree can use them; Icon() tints them, so the fill colour here is irrelevant.
 */

/** Material "shuffle": the random-channel / random-station action. */
val ShuffleIcon: ImageVector by lazy {
    icon(
        "Shuffle",
        "M10.59 9.17 5.41 4 4 5.41l5.17 5.17 1.42-1.41zM14.5 4l2.04 2.04L4 18.59 5.41 20 " +
            "17.96 7.46 20 9.5V4h-5.5zm.33 9.41-1.41 1.41 3.13 3.13L14.5 20H20v-5.5l-2.04 " +
            "2.04-3.13-3.13z",
    )
}

/** Material "radio". */
val RadioIcon: ImageVector by lazy {
    icon(
        "Radio",
        "M3.24 6.15C2.51 6.43 2 7.17 2 8v12c0 1.1.89 2 2 2h16c1.11 0 2-.9 2-2V8c0-1.11-.89-2-2-2" +
            "H8.3l8.26-3.34L15.88 1 3.24 6.15zM7 20c-1.66 0-3-1.34-3-3s1.34-3 3-3 3 1.34 3 3" +
            "-1.34 3-3 3zm13-8h-2v-2h-2v2H4V8h16v4z",
    )
}

/** Material "tv". */
val TvIcon: ImageVector by lazy {
    icon(
        "Tv",
        "M21 3H3c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h5v2h8v-2h5c1.1 0 1.99-.9 1.99-2L23 5" +
            "c0-1.1-.9-2-2-2zm0 14H3V5h18v12z",
    )
}

/** Material "wifi_off": the offline empty state. */
val WifiOffIcon: ImageVector by lazy {
    icon(
        "WifiOff",
        "M22.99 9C19.15 5.16 13.8 3.76 8.84 4.78l2.52 2.52c3.47-.17 6.99 1.05 9.63 3.7l2-2zm-4 " +
            "4c-1.29-1.29-2.84-2.13-4.49-2.56l3.53 3.53.96-.97zM2 3.05 5.07 6.1C3.6 6.82 2.22 " +
            "7.78 1 9l1.99 2c1.24-1.24 2.67-2.16 4.2-2.77l2.24 2.24C7.81 10.89 6.27 11.85 5 13" +
            "v.01L6.99 15c1.36-1.36 3.14-2.04 4.92-2.06L18.98 20l1.27-1.26L3.29 1.79 2 3.05z" +
            "M9 17l3 3 3-3c-1.65-1.66-4.34-1.66-6 0z",
    )
}

private fun icon(name: String, pathData: String): ImageVector = ImageVector.Builder(
    name = name,
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).addPath(pathData = addPathNodes(pathData), fill = SolidColor(Color.White)).build()
