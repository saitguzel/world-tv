package com.worldtv.feature.player

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private const val THRESHOLD = 100f

class PlayerGesturesTest {

    @Test
    fun `dragging up zaps forward, matching the remote`() {
        // Screen y grows downward, so up is negative — easy to invert, and hard to
        // notice afterwards because both directions still "work", just backwards.
        assertEquals(PlayerGesture.ZapUp, drag(dy = -150f))
        assertEquals(PlayerGesture.ZapDown, drag(dy = 150f))
    }

    @Test
    fun `dragging sideways opens the channel list`() {
        assertEquals(PlayerGesture.OpenChannels, drag(dx = 150f))
        assertEquals(PlayerGesture.OpenChannels, drag(dx = -150f))
    }

    @Test
    fun `a tap that wobbles is not a gesture`() {
        // The surface also takes taps to toggle the overlay. Without a threshold every
        // slightly imprecise tap would change the channel.
        assertNull(drag(dx = 10f, dy = 12f))
        assertNull(drag(dy = 99f))
        assertNull(drag())
    }

    @Test
    fun `a diagonal resolves to one axis rather than firing both`() {
        // A thumb never draws a straight line, so most real drags are diagonal.
        assertEquals(PlayerGesture.ZapUp, drag(dx = 120f, dy = -200f))
        assertEquals(PlayerGesture.OpenChannels, drag(dx = -200f, dy = 120f))
    }

    @Test
    fun `an exactly diagonal drag prefers zapping`() {
        // Arbitrary but pinned, so the behaviour cannot drift unnoticed.
        assertEquals(PlayerGesture.ZapUp, drag(dx = 150f, dy = -150f))
    }
}

class VideoAspectRatioTest {

    @Test
    fun `a normal stream keeps its shape`() {
        assertEquals(16f / 9f, videoAspectRatio(1920, 1080))
        assertEquals(4f / 3f, videoAspectRatio(640, 480))
    }

    @Test
    fun `non-square pixels are corrected`() {
        // Anamorphic SD is common in this catalog: 720x576 is 5:4 by pixel count but
        // 16:9 on screen.
        val ratio = videoAspectRatio(720, 576, pixelWidthHeightRatio = 1.42f)
        assertTrue(ratio > 1.7f && ratio < 1.8f, "got $ratio")
    }

    @Test
    fun `buffering never produces NaN`() {
        // ExoPlayer reports 0x0 before the first frame and every zap passes through it.
        // NaN does not degrade Modifier.aspectRatio — it crashes it.
        assertEquals(DEFAULT_ASPECT_RATIO, videoAspectRatio(0, 0))
        assertEquals(DEFAULT_ASPECT_RATIO, videoAspectRatio(1920, 0))
        assertEquals(DEFAULT_ASPECT_RATIO, videoAspectRatio(0, 1080))
        assertTrue(videoAspectRatio(0, 0).isFinite())
    }

    @Test
    fun `a nonsense pixel ratio falls back rather than collapsing the frame`() {
        assertEquals(DEFAULT_ASPECT_RATIO, videoAspectRatio(1920, 1080, 0f))
        assertEquals(DEFAULT_ASPECT_RATIO, videoAspectRatio(1920, 1080, Float.NaN))
    }
}

private fun drag(dx: Float = 0f, dy: Float = 0f) =
    PlayerGestures.fromDrag(dx, dy, THRESHOLD)
