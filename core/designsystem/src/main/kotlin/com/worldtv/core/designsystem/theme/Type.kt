package com.worldtv.core.designsystem.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Typography

/**
 * Type scale for a 3-metre viewing distance.
 *
 * Body text bottoms out at 18sp and titles at 24sp. Everything is in `sp` rather than
 * `dp` so the platform font-scale setting is respected — TV accessibility settings are
 * used more than phone ones, not less.
 */
val WorldTvTypography = Typography(
    displayLarge = TextStyle(fontSize = 48.sp, fontWeight = FontWeight.Bold, lineHeight = 56.sp),
    headlineLarge = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.SemiBold, lineHeight = 42.sp),
    headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.SemiBold, lineHeight = 36.sp),
    titleLarge = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold, lineHeight = 32.sp),
    titleMedium = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Medium, lineHeight = 28.sp),
    bodyLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Normal, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Normal, lineHeight = 26.sp),
    labelLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, lineHeight = 22.sp),
)
