package com.worldtv.core.designsystem.mobile.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.worldtv.core.designsystem.theme.LocalReduceMotion
import com.worldtv.core.designsystem.theme.LocalWorldTvTypeScale
import com.worldtv.core.designsystem.theme.WorldTvColors
import com.worldtv.core.designsystem.theme.WorldTvTypeScales

/**
 * Note the missing `border` slot — the TV scheme carries one for its focus ring and
 * Material 3 has no equivalent, which is the concrete reason these two themes cannot be
 * one file.
 *
 * Dark only, like the TV theme: the palette was chosen for a dark room and #0F0F0F
 * rather than pure black because OLED panels band on #000. A light scheme is a real
 * phone expectation but it is a design decision, not a port decision, so it is
 * deliberately out of scope here rather than invented.
 */
private val MobileColorScheme = darkColorScheme(
    primary = WorldTvColors.Accent,
    onPrimary = WorldTvColors.Surface,
    surface = WorldTvColors.Surface,
    onSurface = WorldTvColors.OnSurface,
    surfaceVariant = WorldTvColors.SurfaceVariant,
    onSurfaceVariant = WorldTvColors.OnSurfaceMuted,
    background = WorldTvColors.Surface,
    onBackground = WorldTvColors.OnSurface,
    error = WorldTvColors.Error,
)

@Composable
fun WorldTvMobileTheme(
    reduceMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalReduceMotion provides reduceMotion,
        // Without this the shared leaves would fall back to the local's default, which
        // is the TV scale — 18sp body text on a phone.
        LocalWorldTvTypeScale provides WorldTvTypeScales.Mobile,
    ) {
        MaterialTheme(
            colorScheme = MobileColorScheme,
            typography = MobileTypography,
        ) {
            // No fillMaxSize background here, unlike the TV theme: on a phone the
            // Scaffold owns the background and the window insets, and painting behind
            // it would defeat edge-to-edge.
            Box(Modifier.background(WorldTvColors.Surface)) {
                content()
            }
        }
    }
}
