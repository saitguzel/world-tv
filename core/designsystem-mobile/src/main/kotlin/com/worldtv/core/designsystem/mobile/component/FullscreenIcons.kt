package com.worldtv.core.designsystem.mobile.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Enter- and exit-fullscreen glyphs, drawn by hand.
 *
 * Neither is in `material-icons-core`, and pulling in `material-icons-extended` for two
 * icons would add thousands of unused vectors. Four corner brackets each, pointing out
 * to expand and in to collapse.
 */
val FullscreenIcon: ImageVector by lazy { corners(outward = true) }

val FullscreenExitIcon: ImageVector by lazy { corners(outward = false) }

private fun corners(outward: Boolean): ImageVector =
    ImageVector.Builder(
        name = if (outward) "Fullscreen" else "FullscreenExit",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.White)) {
            if (outward) {
                // Brackets in the four corners, opening outwards.
                moveTo(4f, 4f); horizontalLineToRelative(6f); verticalLineToRelative(2f)
                horizontalLineToRelative(-4f); verticalLineToRelative(4f)
                horizontalLineToRelative(-2f); close()
                moveTo(20f, 4f); verticalLineToRelative(6f); horizontalLineToRelative(-2f)
                verticalLineToRelative(-4f); horizontalLineToRelative(-4f)
                verticalLineToRelative(-2f); close()
                moveTo(4f, 20f); verticalLineToRelative(-6f); horizontalLineToRelative(2f)
                verticalLineToRelative(4f); horizontalLineToRelative(4f)
                verticalLineToRelative(2f); close()
                moveTo(20f, 20f); horizontalLineToRelative(-6f); verticalLineToRelative(-2f)
                horizontalLineToRelative(4f); verticalLineToRelative(-4f)
                horizontalLineToRelative(2f); close()
            } else {
                // The same brackets pulled inwards.
                moveTo(10f, 4f); verticalLineToRelative(6f); horizontalLineToRelative(-6f)
                verticalLineToRelative(-2f); horizontalLineToRelative(4f)
                verticalLineToRelative(-4f); close()
                moveTo(14f, 4f); horizontalLineToRelative(2f); verticalLineToRelative(4f)
                horizontalLineToRelative(4f); verticalLineToRelative(2f)
                horizontalLineToRelative(-6f); close()
                moveTo(4f, 14f); horizontalLineToRelative(6f); verticalLineToRelative(6f)
                horizontalLineToRelative(-2f); verticalLineToRelative(-4f)
                horizontalLineToRelative(-4f); close()
                moveTo(14f, 20f); verticalLineToRelative(-6f); horizontalLineToRelative(6f)
                verticalLineToRelative(2f); horizontalLineToRelative(-4f)
                verticalLineToRelative(4f); close()
            }
        }
    }.build()
