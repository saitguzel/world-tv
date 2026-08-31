package com.worldtv.core.designsystem.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.isUnspecified
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pins the TV type scale.
 *
 * This exists to guard the phone port. The design system is about to be split so a TV
 * theme and a Material 3 theme can coexist, and the failure mode of that split is
 * invisible at compile time: a standard-M3 `Text` placed under the TV `MaterialTheme`
 * compiles, runs, and renders at Material's 14sp default instead of this scale's 18sp,
 * with no warning anywhere. Nothing else in the build would catch a silent shrink of
 * every caption on a 3-metre screen.
 *
 * The numbers are asserted literally rather than compared against another constant — a
 * test that reads the value it is checking would pass just as happily after the value
 * changed. When the scale moves to a form-factor-neutral holder, retarget these
 * assertions at the TV scale; do not relax them.
 */
class TypeScaleTest {

    @Test
    fun `the tv scale is sized for a three metre viewing distance`() {
        // Body text bottoming out at 18sp is the point of the scale: it is where type
        // stops being legible from a sofa, and roughly 1.3x what Material assumes.
        assertSlot(WorldTvTypeScales.Tv.bodyLarge, size = 18f, line = 26f, weight = FontWeight.Normal)
        assertSlot(WorldTvTypeScales.Tv.bodyMedium, size = 18f, line = 26f, weight = FontWeight.Normal)
        assertSlot(WorldTvTypeScales.Tv.labelLarge, size = 16f, line = 22f, weight = FontWeight.Medium)
    }

    @Test
    fun `the tv title and headline slots keep their sizes`() {
        assertSlot(WorldTvTypeScales.Tv.titleMedium, size = 20f, line = 28f, weight = FontWeight.Medium)
        assertSlot(WorldTvTypeScales.Tv.titleLarge, size = 24f, line = 32f, weight = FontWeight.SemiBold)
        assertSlot(WorldTvTypeScales.Tv.headlineMedium, size = 28f, line = 36f, weight = FontWeight.SemiBold)
        assertSlot(WorldTvTypeScales.Tv.headlineLarge, size = 34f, line = 42f, weight = FontWeight.SemiBold)
        assertSlot(WorldTvTypeScales.Tv.displayLarge, size = 48f, line = 56f, weight = FontWeight.Bold)
    }

    @Test
    fun `every slot the app renders is actually set`() {
        // An unset slot inherits Material's default rather than failing, so a slot
        // dropped during the split would silently fall back to phone sizing.
        for ((name, style) in namedSlots()) {
            assertFalse(style.fontSize.isUnspecified, "$name has no font size")
            assertFalse(style.lineHeight.isUnspecified, "$name has no line height")
        }
    }

    @Test
    fun `line height always leaves room above the font size`() {
        // Turkish descenders and diacritics (ğ, ş, İ) clip when line height crowds the
        // font size, and the catalog is full of them.
        for ((name, style) in namedSlots()) {
            val ratio = style.lineHeight.value / style.fontSize.value
            assertTrue(ratio >= 1.15f, "$name: line height ${style.lineHeight.value} too tight for ${style.fontSize.value}")
        }
    }

    private fun namedSlots(): List<Pair<String, TextStyle>> = listOf(
        "displayLarge" to WorldTvTypeScales.Tv.displayLarge,
        "headlineLarge" to WorldTvTypeScales.Tv.headlineLarge,
        "headlineMedium" to WorldTvTypeScales.Tv.headlineMedium,
        "titleLarge" to WorldTvTypeScales.Tv.titleLarge,
        "titleMedium" to WorldTvTypeScales.Tv.titleMedium,
        "bodyLarge" to WorldTvTypeScales.Tv.bodyLarge,
        "bodyMedium" to WorldTvTypeScales.Tv.bodyMedium,
        "labelLarge" to WorldTvTypeScales.Tv.labelLarge,
    )

    private fun assertSlot(style: TextStyle, size: Float, line: Float, weight: FontWeight) {
        assertEquals(size, style.fontSize.value, "font size")
        assertEquals(line, style.lineHeight.value, "line height")
        assertEquals(weight, style.fontWeight, "font weight")
    }
}
