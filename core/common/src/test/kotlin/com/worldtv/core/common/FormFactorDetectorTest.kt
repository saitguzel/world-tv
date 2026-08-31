package com.worldtv.core.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The whole point of extracting this rule was to test it without an emulator, so the
 * cases below are the ones that would otherwise only show up on real hardware.
 */
class FormFactorDetectorTest {

    @Test
    fun `a television reporting its ui mode is a television`() {
        assertEquals(
            FormFactor.TV,
            detect(isTelevisionUiMode = true, hasLeanback = true, hasTelevisionFeature = true),
        )
    }

    @Test
    fun `the ui mode wins even when nothing else says television`() {
        // A Google TV device that somehow reports neither static feature still runs in
        // television mode, and that is the signal that reflects reality.
        assertEquals(
            FormFactor.TV,
            detect(isTelevisionUiMode = true, hasTouchscreen = true),
        )
    }

    @Test
    fun `a tablet that also advertises leanback is still a phone-class device`() {
        // This is the case a plain `hasLeanback` check gets wrong, and getting it wrong
        // hands a touch device a UI whose components only respond to D-pad keys.
        assertEquals(
            FormFactor.MOBILE,
            detect(isNormalUiMode = true, hasTouchscreen = true, hasLeanback = true),
        )
    }

    @Test
    fun `an ordinary phone is mobile`() {
        assertEquals(FormFactor.MOBILE, detect(isNormalUiMode = true, hasTouchscreen = true))
    }

    @Test
    fun `a box with no touchscreen and no readable ui mode falls back to its features`() {
        // Bare AOSP boxes at API 23 often report an undefined ui mode; the television
        // hardware feature is the only marker left.
        assertEquals(
            FormFactor.TV,
            detect(hasTelevisionFeature = true),
        )
        assertEquals(FormFactor.TV, detect(hasLeanback = true))
    }

    @Test
    fun `a device in normal mode without a touchscreen is not treated as a phone`() {
        // Whatever this is, it is not something a thumb drives. Leanback decides.
        assertEquals(
            FormFactor.TV,
            detect(isNormalUiMode = true, hasTouchscreen = false, hasLeanback = true),
        )
    }

    @Test
    fun `nothing known at all defaults to mobile`() {
        // Mobile is the safer default for an unknown device: its components respond to
        // both taps and D-pad, whereas the TV ones ignore taps entirely.
        assertEquals(FormFactor.MOBILE, detect())
    }

    @Test
    fun `other special ui modes are not televisions`() {
        // Car and desk modes report neither television nor normal.
        assertEquals(
            FormFactor.MOBILE,
            detect(isTelevisionUiMode = false, isNormalUiMode = false, hasTouchscreen = true),
        )
    }

    private fun detect(
        isTelevisionUiMode: Boolean = false,
        isNormalUiMode: Boolean = false,
        hasTouchscreen: Boolean = false,
        hasLeanback: Boolean = false,
        hasTelevisionFeature: Boolean = false,
    ) = FormFactorDetector.detect(
        isTelevisionUiMode = isTelevisionUiMode,
        isNormalUiMode = isNormalUiMode,
        hasTouchscreen = hasTouchscreen,
        hasLeanback = hasLeanback,
        hasTelevisionFeature = hasTelevisionFeature,
    )
}
