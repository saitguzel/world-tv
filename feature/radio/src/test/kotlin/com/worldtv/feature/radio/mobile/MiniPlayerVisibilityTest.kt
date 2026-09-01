package com.worldtv.feature.radio.mobile

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MiniPlayerVisibilityTest {

    @Test
    fun `the bar shows while a station is playing`() {
        assertTrue(shouldShowMiniPlayer(hasStation = true, isPlayerRoute = false))
    }

    @Test
    fun `nothing playing means nothing to show`() {
        assertFalse(shouldShowMiniPlayer(hasStation = false, isPlayerRoute = false))
    }

    @Test
    fun `the video player never carries the radio bar`() {
        // It would cover full-bleed video, and worse, put a stop button one mis-tap
        // from the surface the user is trying to tap to show the controls.
        assertFalse(shouldShowMiniPlayer(hasStation = true, isPlayerRoute = true))
    }
}
