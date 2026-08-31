package com.worldtv.core.designsystem.mobile.theme

import androidx.compose.material3.Typography
import com.worldtv.core.designsystem.theme.WorldTvTypeScales

/**
 * The phone framework typography.
 *
 * Derived from [WorldTvTypeScales.Mobile] rather than declaring its own sizes, for the
 * same reason the TV one is: the shared components read the scale directly while
 * phone-only components read this `Typography`, and hand-copying the numbers into both
 * is how the two would drift apart.
 */
val MobileTypography = Typography(
    displayLarge = WorldTvTypeScales.Mobile.displayLarge,
    headlineLarge = WorldTvTypeScales.Mobile.headlineLarge,
    headlineMedium = WorldTvTypeScales.Mobile.headlineMedium,
    titleLarge = WorldTvTypeScales.Mobile.titleLarge,
    titleMedium = WorldTvTypeScales.Mobile.titleMedium,
    bodyLarge = WorldTvTypeScales.Mobile.bodyLarge,
    bodyMedium = WorldTvTypeScales.Mobile.bodyMedium,
    labelLarge = WorldTvTypeScales.Mobile.labelLarge,
)
