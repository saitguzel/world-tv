package com.worldtv.core.designsystem.tv.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import com.worldtv.core.designsystem.theme.LocalReduceMotion
import com.worldtv.core.designsystem.theme.LocalWorldTvTypeScale
import com.worldtv.core.designsystem.theme.WorldTvColors
import com.worldtv.core.designsystem.theme.WorldTvTypeScales

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
    CompositionLocalProvider(
        LocalReduceMotion provides reduceMotion,
        // The shared leaves read this scale rather than the TV Typography below, so a
        // component rendered on both form factors still gets 10-foot sizing here.
        LocalWorldTvTypeScale provides WorldTvTypeScales.Tv,
    ) {
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
