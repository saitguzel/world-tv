package com.worldtv.core.designsystem.tv.theme

import androidx.tv.material3.Typography
import com.worldtv.core.designsystem.theme.WorldTvTypeScales

/**
 * The TV framework typography.
 *
 * Derived from [WorldTvTypeScales.Tv] rather than declaring its own sizes: the shared
 * components read the scale directly while TV-only components read this `Typography`,
 * and hand-copying the numbers into both is exactly how the two would drift apart.
 * `TypeScaleTest` pins the scale itself.
 */
val WorldTvTypography = Typography(
    displayLarge = WorldTvTypeScales.Tv.displayLarge,
    headlineLarge = WorldTvTypeScales.Tv.headlineLarge,
    headlineMedium = WorldTvTypeScales.Tv.headlineMedium,
    titleLarge = WorldTvTypeScales.Tv.titleLarge,
    titleMedium = WorldTvTypeScales.Tv.titleMedium,
    bodyLarge = WorldTvTypeScales.Tv.bodyLarge,
    bodyMedium = WorldTvTypeScales.Tv.bodyMedium,
    labelLarge = WorldTvTypeScales.Tv.labelLarge,
)
