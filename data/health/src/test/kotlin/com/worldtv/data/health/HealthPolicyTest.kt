package com.worldtv.data.health

import com.worldtv.core.model.HealthErrorCode
import com.worldtv.core.model.HealthInfo
import com.worldtv.core.model.StreamState
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HealthPolicyTest {

    private val t0 = 1_700_000_000_000L

    @Test
    fun `alive marks OK and clears the failure counter`() {
        val current = HealthInfo(state = StreamState.UNKNOWN, consecutiveFailures = 2)
        val result = HealthPolicy.apply(current, CheckResult.Alive(latencyMs = 340), t0)

        assertEquals(StreamState.OK, result.state)
        assertEquals(0, result.consecutiveFailures)
        assertEquals(340, result.lastLatencyMs)
        assertEquals(t0, result.lastOkAt)
        assertEquals(t0 + 12.hours.inWholeMilliseconds, result.nextCheckAt)
    }

    @Test
    fun `a single failure does not eliminate a working stream`() {
        val ok = HealthInfo(state = StreamState.OK, lastOkAt = t0)
        val after = HealthPolicy.apply(ok, CheckResult.Dead(502, "bad gateway"), t0)

        assertEquals(StreamState.OK, after.state, "one CDN hiccup must not hide a channel")
        assertEquals(1, after.consecutiveFailures)
        assertEquals(t0 + 1.hours.inWholeMilliseconds, after.nextCheckAt)
    }

    @Test
    fun `three consecutive failures eliminate the stream`() {
        var health = HealthInfo(state = StreamState.OK)
        repeat(HealthPolicy.FAIL_THRESHOLD) {
            health = HealthPolicy.apply(health, CheckResult.Dead(404, "gone"), t0)
        }
        assertEquals(StreamState.DEAD, health.state)
        assertEquals(3, health.consecutiveFailures)
    }

    @Test
    fun `backoff widens the observation window to roughly 31 hours`() {
        val total = HealthPolicy.backoff(1) + HealthPolicy.backoff(2) + HealthPolicy.backoff(3)
        assertEquals(31.hours.inWholeMilliseconds, total)
    }

    @Test
    fun `inconclusive changes nothing at all`() {
        val current = HealthInfo(
            state = StreamState.OK,
            consecutiveFailures = 1,
            lastCheckedAt = t0 - 5_000,
            nextCheckAt = t0 + 1_000,
        )
        val after = HealthPolicy.apply(current, CheckResult.Inconclusive("airplane mode"), t0)
        assertEquals(current, after, "an offline device must not mark the catalog dead")
    }

    @Test
    fun `geo blocked stays visible but is not retried often`() {
        val after = HealthPolicy.apply(HealthInfo(), CheckResult.GeoBlocked(451), t0)
        assertEquals(StreamState.GEO_BLOCKED, after.state)
        assertTrue(after.state.isPlayable)
        assertEquals(t0 + 3.days.inWholeMilliseconds, after.nextCheckAt)
    }

    @Test
    fun `a permanently forbidden stream eventually stops claiming to be geo blocked`() {
        var health = HealthInfo()
        repeat(HealthPolicy.GEO_TOLERANCE) {
            health = HealthPolicy.apply(health, CheckResult.GeoBlocked(403), t0)
            assertEquals(StreamState.GEO_BLOCKED, health.state)
        }
        health = HealthPolicy.apply(health, CheckResult.GeoBlocked(403), t0)
        assertEquals(
            StreamState.DEAD,
            health.state,
            "GEO_BLOCKED must not be an immortality badge",
        )
    }

    @Test
    fun `a finished VOD playlist is kept, not eliminated`() {
        val after = HealthPolicy.apply(
            HealthInfo(),
            CheckResult.Alive(latencyMs = 100, isLive = false),
            t0,
        )
        assertEquals(StreamState.OK, after.state)
        assertTrue(after.isVod)
        assertEquals(t0 + 2.days.inWholeMilliseconds, after.nextCheckAt)
    }

    @Test
    fun `two playback failures eliminate a stream`() {
        var health = HealthInfo(state = StreamState.OK)
        health = HealthPolicy.applyPlayback(health, PlaybackSignal.Failed(2004), t0)
        assertEquals(StreamState.OK, health.state)
        assertEquals(2, health.consecutiveFailures)

        health = HealthPolicy.applyPlayback(health, PlaybackSignal.Failed(2004), t0)
        assertEquals(StreamState.DEAD, health.state)
    }

    @Test
    fun `a decoder failure is never held against the stream`() {
        val current = HealthInfo(state = StreamState.OK, consecutiveFailures = 0)
        val after = HealthPolicy.applyPlayback(
            current,
            PlaybackSignal.DeviceLocalFailure(4003),
            t0,
        )
        assertEquals(current, after, "missing HEVC on one box says nothing about the stream")
    }

    @Test
    fun `a rendered first frame is the strongest possible success`() {
        val current = HealthInfo(state = StreamState.UNKNOWN, consecutiveFailures = 2)
        val after = HealthPolicy.applyPlayback(
            current,
            PlaybackSignal.RenderedFirstFrame(timeToFirstFrameMs = 1_800),
            t0,
        )
        assertEquals(StreamState.OK, after.state)
        assertEquals(0, after.consecutiveFailures)
        assertEquals(1_800, after.lastLatencyMs)
        assertEquals(HealthErrorCode.NONE, after.lastErrorCode)
    }

    @Test
    fun `dead streams are revived after the cool-off, never deleted`() {
        val dead = HealthInfo(
            state = StreamState.DEAD,
            consecutiveFailures = 3,
            nextCheckAt = t0 + HealthPolicy.DEAD_RETRY_INTERVAL,
        )
        assertEquals(dead, HealthPolicy.reviveIfDue(dead, t0), "too early to retry")

        val revived = HealthPolicy.reviveIfDue(dead, t0 + HealthPolicy.DEAD_RETRY_INTERVAL)
        assertEquals(StreamState.UNKNOWN, revived.state)
        assertEquals(0, revived.consecutiveFailures)
        assertFalse(revived.state == StreamState.DEAD)
    }
}
