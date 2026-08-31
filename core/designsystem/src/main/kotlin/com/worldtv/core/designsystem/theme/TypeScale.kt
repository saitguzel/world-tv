package com.worldtv.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * The type scale, expressed without a Material dependency.
 *
 * `androidx.tv.material3.Typography` and `androidx.compose.material3.Typography` are
 * different types living in different CompositionLocal trees, so neither can be read by
 * a component that has to serve both a TV and a phone. This holds the sizes themselves
 * in plain [TextStyle]s, and each theme derives its own framework `Typography` from the
 * matching scale — so the two can never drift apart.
 */
@Immutable
data class WorldTvTypeScale(
    val displayLarge: TextStyle,
    val headlineLarge: TextStyle,
    val headlineMedium: TextStyle,
    val titleLarge: TextStyle,
    val titleMedium: TextStyle,
    val bodyLarge: TextStyle,
    val bodyMedium: TextStyle,
    val labelLarge: TextStyle,
)

object WorldTvTypeScales {

    /**
     * Sized for a 3-metre viewing distance: body text bottoms out at 18sp, roughly 1.3x
     * what the Material defaults assume. Everything is in `sp` so the platform font
     * scale is respected — TV accessibility settings are used more than phone ones.
     *
     * `TypeScaleTest` pins these numbers literally.
     */
    val Tv = WorldTvTypeScale(
        displayLarge = TextStyle(fontSize = 48.sp, fontWeight = FontWeight.Bold, lineHeight = 56.sp),
        headlineLarge = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.SemiBold, lineHeight = 42.sp),
        headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.SemiBold, lineHeight = 36.sp),
        titleLarge = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold, lineHeight = 32.sp),
        titleMedium = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Medium, lineHeight = 28.sp),
        bodyLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Normal, lineHeight = 26.sp),
        bodyMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Normal, lineHeight = 26.sp),
        labelLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, lineHeight = 22.sp),
    )

    /**
     * Arm's length rather than three metres — close to the Material defaults.
     *
     * Line heights stay generously above the font size because Turkish descenders and
     * dotted capitals (ğ, ş, İ) clip when the two crowd each other, and the catalog is
     * full of them.
     */
    val Mobile = WorldTvTypeScale(
        displayLarge = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.Bold, lineHeight = 44.sp),
        headlineLarge = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.SemiBold, lineHeight = 36.sp),
        headlineMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold, lineHeight = 32.sp),
        titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, lineHeight = 28.sp),
        titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, lineHeight = 24.sp),
        bodyLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal, lineHeight = 24.sp),
        bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp),
        labelLarge = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, lineHeight = 18.sp),
    )
}

/**
 * Defaults to [WorldTvTypeScales.Tv] deliberately: any path that forgets to provide a
 * scale — a `@Preview`, a screen added to the wrong tree — renders exactly as the app
 * does today rather than silently shrinking to phone sizing.
 */
val LocalWorldTvTypeScale = staticCompositionLocalOf { WorldTvTypeScales.Tv }
