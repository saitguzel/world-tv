package com.worldtv.core.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * The country-defaulting rule, tested without a device.
 *
 * The Android side ([DeviceCountry]) only reads Locale and TimeZone; the decision is
 * entirely here, so a locale misread or a zone without a mapping is a pure-function
 * question — exactly the kind of thing that should not need an emulator.
 */
class DeviceCountryDetectorTest {

    @Test
    fun `a two-letter locale wins outright`() {
        assertEquals("TR", DeviceCountryDetector.detect("tr", "Europe/Paris"))
        assertEquals("US", DeviceCountryDetector.detect("US", "Asia/Tokyo"))
    }

    @Test
    fun `a two-letter string is trusted even when it reads like a language code`() {
        // The device hands over Locale.getDefault().country, which is a bare ISO
        // alpha-2 when present at all — "de" is Germany, never a language tag.
        assertEquals("DE", DeviceCountryDetector.detect("de", "America/New_York"))
    }

    @Test
    fun `an empty or malformed locale falls back to the timezone`() {
        assertEquals("TR", DeviceCountryDetector.detect("", "Europe/Istanbul"))
        assertEquals("GB", DeviceCountryDetector.detect(null, "Europe/London"))
        // A three-letter code is not an ISO alpha-2; the timezone decides.
        assertEquals("US", DeviceCountryDetector.detect("USA", "America/New_York"))
    }

    @Test
    fun `an unknown timezone and locale yield nothing`() {
        // The caller falls back to its own default; guessing would be worse.
        assertNull(DeviceCountryDetector.detect("", "Etc/Unknown_Zone"))
        assertNull(DeviceCountryDetector.detect(null, "GMT+03:00"))
    }
}