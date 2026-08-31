package com.worldtv.core.designsystem.theme

import androidx.compose.runtime.staticCompositionLocalOf
import com.worldtv.core.common.FormFactor

/**
 * Which experience the surrounding tree is rendering.
 *
 * Provided once, by `MainActivity`, from the injected `DeviceCapabilities`. Screens do
 * not read it — they are already in the tree that matches them — but a shared leaf
 * occasionally needs to know, and prop-drilling it through four layers of grid would be
 * worse.
 *
 * Defaults to [FormFactor.TV] for the same reason the type scale does: an unprovided
 * path, such as a `@Preview`, should behave like the app does today.
 */
val LocalFormFactor = staticCompositionLocalOf { FormFactor.TV }
