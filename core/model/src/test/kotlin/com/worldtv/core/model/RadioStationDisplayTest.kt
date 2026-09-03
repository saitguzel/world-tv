package com.worldtv.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The badge rule decides which dot a station gets on four screens; the description is
 * what sits under its name. Both are pure, so both are pinned here.
 */
class RadioStationDisplayTest {

    @Test
    fun `our own probe decides the badge`() {
        assertEquals(HealthBadge.VERIFIED, station(StreamState.OK).badge())
        assertEquals(HealthBadge.UNAVAILABLE, station(StreamState.DEAD).badge())
        assertEquals(HealthBadge.GEO_BLOCKED, station(StreamState.GEO_BLOCKED).badge())
        assertEquals(HealthBadge.UNCHECKED, station(StreamState.UNKNOWN).badge())
    }

    @Test
    fun `radio browser's verdict does not make a station verified`() {
        // Their probe runs from another region; a station they call fine is merely
        // unchecked here until our own probe agrees.
        assertEquals(HealthBadge.UNCHECKED, station(StreamState.UNKNOWN, serverSideOk = true).badge())
    }

    @Test
    fun `the description lists codec, bitrate and the first two tags`() {
        assertEquals("MP3 · 128 kbps · pop, rock", station(StreamState.OK).describe())
    }

    @Test
    fun `missing parts are left out rather than shown as blanks`() {
        val bare = station(StreamState.OK).copy(codec = null, bitrate = 0, tags = emptyList())
        assertEquals("", bare.describe())
        assertEquals("64 kbps", bare.copy(bitrate = 64).describe())
    }

    private fun station(state: StreamState, serverSideOk: Boolean = false) = RadioStation(
        uuid = "u1",
        name = "Radyo",
        url = "https://example/stream",
        faviconUrl = null,
        tags = listOf("pop", "rock", "90s"),
        countryCode = "TR",
        language = "tr",
        codec = "MP3",
        bitrate = 128,
        serverSideOk = serverSideOk,
        clickCount = 0,
        votes = 0,
        health = HealthInfo(state = state),
    )
}
