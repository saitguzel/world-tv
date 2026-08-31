package com.worldtv.feature.player

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The rule that decides when a playback attempt has proven itself.
 *
 * The case worth guarding is the audio-only stream: it plays fine, never renders a
 * frame, and used to be reported as a playback failure by the slow-load watchdog —
 * worth two strikes, so the second viewing eliminated it.
 */
class PlaybackConfirmationTest {

    @Test
    fun `a video stream is confirmed by its first frame`() {
        val confirmation = PlaybackConfirmation()
        confirmation.onTracksKnown(hasVideoTrack = true)

        // READY alone proves nothing while a frame is still expected: a stalled video
        // stream reaches READY too, and the watchdog exists precisely for that.
        assertFalse(confirmation.onReady())
        assertTrue(confirmation.onRenderedFirstFrame())
    }

    @Test
    fun `an audio-only stream is confirmed by reaching ready`() {
        val confirmation = PlaybackConfirmation()
        confirmation.onTracksKnown(hasVideoTrack = false)

        assertTrue(confirmation.onReady(), "an audio-only stream never renders a frame")
    }

    @Test
    fun `an audio-only stream is confirmed whichever order the two events arrive in`() {
        // Media3 promises no order between onTracksChanged and the move to READY.
        val confirmation = PlaybackConfirmation()

        assertFalse(confirmation.onReady(), "video is assumed until the tracks say otherwise")
        assertTrue(confirmation.onTracksKnown(hasVideoTrack = false))
    }

    @Test
    fun `an attempt is confirmed exactly once`() {
        val confirmation = PlaybackConfirmation()
        confirmation.onTracksKnown(hasVideoTrack = false)

        // A video stream reaches READY and then renders a frame; the health store must
        // hear about that once, not twice.
        assertTrue(confirmation.onReady())
        assertFalse(confirmation.onReady())
        assertFalse(confirmation.onRenderedFirstFrame())
    }

    @Test
    fun `an unconfirmed attempt stays unconfirmed until something positive happens`() {
        val confirmation = PlaybackConfirmation()

        // Nothing has been observed, so the watchdog must remain free to fire.
        assertFalse(confirmation.onTracksKnown(hasVideoTrack = true))
    }

    @Test
    fun `resetting starts the next stream from scratch`() {
        val confirmation = PlaybackConfirmation()
        confirmation.onTracksKnown(hasVideoTrack = false)
        assertTrue(confirmation.onReady())

        // Walking to the next alternative must not inherit the previous stream's
        // verdict, or a broken fallback would be recorded healthy without playing.
        confirmation.reset()
        assertFalse(confirmation.onReady(), "video is expected again until tracks arrive")
        assertTrue(confirmation.onRenderedFirstFrame())
    }
}
