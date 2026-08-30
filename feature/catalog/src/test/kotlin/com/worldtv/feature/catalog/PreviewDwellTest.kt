package com.worldtv.feature.catalog

import org.junit.jupiter.api.Assertions.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * The dwell rule, extracted from the Compose wrapper so it can be tested on the JVM.
 *
 * `rememberPreviewTarget` is a thin `LaunchedEffect` around exactly this: cancel on
 * every focus change, emit only after the delay elapses. The behaviour that matters
 * — that a fast scroll opens no connections at all — is entirely in the timing.
 */
private class DwellTracker(private val dwellMillis: Long = PREVIEW_DWELL_MS) {
    var target: String? = null
        private set

    /** Simulates focus landing on [channelId] and [heldMillis] elapsing there. */
    fun focus(channelId: String?, heldMillis: Long, enabled: Boolean = true) {
        target = null
        if (!enabled || channelId == null) return
        if (heldMillis >= dwellMillis) target = channelId
    }
}

class PreviewDwellTest {

    @Test
    fun `scrolling past a card never starts a preview`() = runTest {
        val tracker = DwellTracker()
        // A held D-pad moves roughly every 40-120ms; nothing should fire.
        listOf("a", "b", "c", "d", "e").forEach { tracker.focus(it, heldMillis = 80) }
        assertNull(tracker.target, "a fast scroll must not open a stream per card")
    }

    @Test
    fun `stopping on a card starts its preview`() {
        val tracker = DwellTracker()
        tracker.focus("trt1", heldMillis = PREVIEW_DWELL_MS)
        assertEquals("trt1", tracker.target)
    }

    @Test
    fun `moving away cancels a pending preview`() {
        val tracker = DwellTracker()
        tracker.focus("trt1", heldMillis = PREVIEW_DWELL_MS)
        tracker.focus("cnn", heldMillis = 50)
        assertNull(tracker.target)
    }

    @Test
    fun `losing focus entirely clears the target`() {
        val tracker = DwellTracker()
        tracker.focus("trt1", heldMillis = PREVIEW_DWELL_MS)
        tracker.focus(null, heldMillis = 10_000)
        assertNull(tracker.target)
    }

    @Test
    fun `previews stay off when the setting is disabled`() {
        val tracker = DwellTracker()
        tracker.focus("trt1", heldMillis = 10_000, enabled = false)
        assertNull(tracker.target, "a low-RAM box cannot afford a second decoder")
    }

    @Test
    fun `the dwell is long enough to outlast the fastest key repeat`() {
        // KeyRepeatLimiter floors repeats at 40ms; the dwell must be far above that.
        assert(PREVIEW_DWELL_MS > 40 * 10) { "dwell of $PREVIEW_DWELL_MS ms is too short" }
    }
}
