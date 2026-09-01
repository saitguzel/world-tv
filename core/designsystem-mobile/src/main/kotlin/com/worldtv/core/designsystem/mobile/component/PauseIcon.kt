package com.worldtv.core.designsystem.mobile.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * A pause glyph, drawn by hand.
 *
 * `material-icons-core` ships about fifty icons and pause is not among them; the only
 * packaged alternative is `material-icons-extended`, which carries several thousand
 * vectors for the sake of this one. Two rectangles are cheaper.
 */
val PauseIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Pause",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(6f, 5f); horizontalLineToRelative(4f); verticalLineToRelative(14f)
            horizontalLineToRelative(-4f); close()
            moveTo(14f, 5f); horizontalLineToRelative(4f); verticalLineToRelative(14f)
            horizontalLineToRelative(-4f); close()
        }
    }.build()
}
