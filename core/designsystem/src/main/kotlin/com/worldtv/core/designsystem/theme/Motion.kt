package com.worldtv.core.designsystem.theme

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * True when the user has asked for reduced motion.
 *
 * Read by every focus animation, so turning it on genuinely removes the scale and fade
 * transitions rather than merely shortening them.
 *
 * Lives outside the themes because both form factors honour it and neither owns it.
 */
val LocalReduceMotion = staticCompositionLocalOf { false }
