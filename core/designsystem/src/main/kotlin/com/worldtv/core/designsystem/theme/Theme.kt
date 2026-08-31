package com.worldtv.core.designsystem.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

/**
 * True when the user has asked for reduced motion.
 *
 * Read by every focus animation, so turning it on genuinely removes the scale and
 * fade transitions rather than merely shortening them.
 */
val LocalReduceMotion = staticCompositionLocalOf { false }

private val WorldTvColorScheme = darkColorScheme(
    primary = WorldTvColors.Accent,
    onPrimary = WorldTvColors.Surface,
    surface = WorldTvColors.Surface,
    onSurface = WorldTvColors.OnSurface,
    surfaceVariant = WorldTvColors.SurfaceVariant,
    onSurfaceVariant = WorldTvColors.OnSurfaceMuted,
    background = WorldTvColors.Surface,
    onBackground = WorldTvColors.OnSurface,
    border = WorldTvColors.FocusRing,
    error = WorldTvColors.Error,
)

@Composable
fun WorldTvTheme(
    reduceMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalReduceMotion provides reduceMotion) {
        MaterialTheme(
            colorScheme = WorldTvColorScheme,
            typography = WorldTvTypography,
        ) {
            Box(Modifier.fillMaxSize().background(WorldTvColors.Surface)) {
                content()
            }
        }
    }
}
