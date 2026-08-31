package com.worldtv.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * A dark, low-luminance palette.
 *
 * Not pure black: OLED panels band and smear on #000 during the slow fades a TV UI is
 * full of. Not pure white either — #E8E8EA on #0F0F0F still clears 4.5:1 while cutting
 * the glare that pure white throws in a dark room.
 */
object WorldTvColors {
    val Surface = Color(0xFF0F0F0F)
    val SurfaceVariant = Color(0xFF1C1C1E)
    val SurfaceElevated = Color(0xFF262629)
    val OnSurface = Color(0xFFE8E8EA)
    val OnSurfaceMuted = Color(0xFF9A9AA0)

    /** The focus ring is the only pure white in the UI, so it can never be mistaken. */
    val FocusRing = Color(0xFFFFFFFF)

    val Accent = Color(0xFF4DA3FF)

    /** Health indicators. Each is paired with a shape or icon — never colour alone. */
    val HealthVerified = Color(0xFF4ADE80)
    val HealthUnchecked = Color(0xFF6B6B70)
    val HealthGeoBlocked = Color(0xFFFBBF24)
    val HealthDead = Color(0xFF4A4A4E)

    val Error = Color(0xFFF87171)
    val Scrim = Color(0xCC000000)
}
