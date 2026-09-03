package com.worldtv.core.designsystem.component

import com.worldtv.core.model.HealthBadge
import com.worldtv.core.model.HealthInfo
import com.worldtv.core.model.RadioStation
import com.worldtv.core.model.StreamState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The rule behind the playing indicator, and the two mappings that feed the rows and
 * shelves.
 *
 * Worth pinning because the bug being fixed here was four screens each deciding for
 * themselves that "this is the current station" meant "this station is playing".
 */
class StationRowStateTest {

    @Test
    fun `a station that is not the current one is idle, whatever the session is doing`() {
        assertEquals(
            StationPlayback.IDLE,
            stationPlayback(isCurrent = false, isPlaying = true, isBuffering = false),
        )
    }

    @Test
    fun `the current station is only playing when the session says so`() {
        assertEquals(
            StationPlayback.PLAYING,
            stationPlayback(isCurrent = true, isPlaying = true, isBuffering = false),
        )
        // The regression: paused by a focus loss, a notification, or a dead stream, and
        // the row went on claiming it was on air.
        assertEquals(
            StationPlayback.PAUSED,
            stationPlayback(isCurrent = true, isPlaying = false, isBuffering = false),
        )
        assertEquals(
            StationPlayback.BUFFERING,
            stationPlayback(isCurrent = true, isPlaying = false, isBuffering = true),
        )
    }

    @Test
    fun `playing wins over buffering, so a connected station never flickers`() {
        assertEquals(
            StationPlayback.PLAYING,
            stationPlayback(isCurrent = true, isPlaying = true, isBuffering = true),
        )
    }

    @Test
    fun `a row carries the shared description and badge rules`() {
        val row = station().toRowState(isFavorite = true, playback = StationPlayback.PLAYING)

        assertEquals("u1", row.id)
        assertEquals("Radyo", row.name)
        assertEquals("MP3 · 128 kbps · pop, rock", row.subtitle)
        assertEquals(HealthBadge.VERIFIED, row.badge)
        assertEquals(true, row.isFavorite)
    }

    @Test
    fun `a card takes the favicon as its logo and never claims to be a favourite`() {
        // Home shelves render stations with the channel card, and nothing on Home knows
        // the favourite state of a station it is merely listing.
        val card = station().toCardState()

        assertEquals("u1", card.id)
        assertEquals("https://example/favicon.png", card.logoUrl)
        assertEquals("MP3 · 128 kbps · pop, rock", card.subtitle)
        assertEquals(false, card.isFavorite)
    }

    private fun station() = RadioStation(
        uuid = "u1",
        name = "Radyo",
        url = "https://example/stream",
        faviconUrl = "https://example/favicon.png",
        tags = listOf("pop", "rock", "90s"),
        countryCode = "TR",
        language = "tr",
        codec = "MP3",
        bitrate = 128,
        serverSideOk = false,
        clickCount = 0,
        votes = 0,
        health = HealthInfo(state = StreamState.OK),
    )
}
