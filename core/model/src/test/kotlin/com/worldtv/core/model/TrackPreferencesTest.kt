package com.worldtv.core.model

import java.util.Locale
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TrackPreferencesTest {

    @Test
    fun `the system caption language wins over the device language`() {
        // A user who turned captions on and chose a language has said what they want.
        assertEquals(
            "en",
            TrackPreferences.preferredLanguage(
                available = listOf("tr", "en"),
                systemCaptionLanguage = "en",
                deviceLanguage = "tr",
            ),
        )
    }

    @Test
    fun `falls back to the device language when captions specify nothing`() {
        assertEquals(
            "tr",
            TrackPreferences.preferredLanguage(
                available = listOf("tr", "en"),
                systemCaptionLanguage = null,
                deviceLanguage = "tr",
            ),
        )
    }

    @Test
    fun `three-letter tags match two-letter ones`() {
        // IPTV streams use tur/tr and eng/en interchangeably; exact matching alone
        // picks nothing on most real streams.
        assertEquals(
            "tur",
            TrackPreferences.preferredLanguage(listOf("tur", "eng"), null, "tr"),
        )
        assertEquals("deu", TrackPreferences.preferredLanguage(listOf("deu"), null, "de"))
        assertEquals("ger", TrackPreferences.preferredLanguage(listOf("ger"), null, "de"))
    }

    @Test
    fun `region subtags do not block a match`() {
        assertEquals("en-GB", TrackPreferences.preferredLanguage(listOf("en-GB"), null, "en"))
        assertEquals("tr", TrackPreferences.preferredLanguage(listOf("tr"), null, "tr-TR"))
        assertEquals("pt_BR", TrackPreferences.preferredLanguage(listOf("pt_BR"), null, "pt"))
    }

    @Test
    fun `an exact tag beats a primary-subtag match`() {
        assertEquals(
            "en-US",
            TrackPreferences.preferredLanguage(listOf("en-GB", "en-US"), "en-US", null),
        )
    }

    @Test
    fun `no match leaves subtitles off rather than picking something arbitrary`() {
        assertNull(TrackPreferences.preferredLanguage(listOf("fr", "de"), null, "tr"))
        assertNull(TrackPreferences.preferredLanguage(emptyList(), "en", "en"))
    }

    @Test
    fun `normalisation is case and separator insensitive`() {
        assertEquals("tr", TrackPreferences.normalize("TR"))
        assertEquals("tr", TrackPreferences.normalize("tr-TR"))
        assertEquals("tr", TrackPreferences.normalize("tur"))
        assertEquals("", TrackPreferences.normalize("  "))
    }

    @Test
    fun `labels are human-readable but never hide an unrecognised tag`() {
        assertEquals("English", TrackPreferences.labelFor("en", Locale.ENGLISH))
        assertEquals("Turkish", TrackPreferences.labelFor("tr", Locale.ENGLISH))
        // A stream tagged qaa tells the user nothing, but the raw tag at least shows
        // that the tracks differ.
        assertEquals("qaa", TrackPreferences.labelFor("qaa", Locale.ENGLISH))
        assertEquals("Bilinmeyen", TrackPreferences.labelFor(null))
        assertEquals("Bilinmeyen", TrackPreferences.labelFor("  "))
    }
}
