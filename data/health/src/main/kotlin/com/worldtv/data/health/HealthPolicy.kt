package com.worldtv.data.health

import com.worldtv.core.model.HealthErrorCode
import com.worldtv.core.model.HealthInfo
import com.worldtv.core.model.StreamState
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/**
 * The health state machine. Pure, synchronous, and the single place where a stream's
 * fate is decided — the probes only report facts, this decides what they mean.
 */
object HealthPolicy {

    /** Consecutive failures before a stream is hidden. */
    const val FAIL_THRESHOLD = 3

    /**
     * A real playback error is worth more than a failed HTTP probe: the user actually
     * tried to watch and it did not work. Two of those eliminate a stream.
     */
    const val PLAYBACK_FAILURE_WEIGHT = 2

    /**
     * How many times a stream may answer 403 before it stops being treated as merely
     * region-locked and starts accumulating failures like any other broken stream.
     *
     * Without this, a permanently broken 403 origin stays visible forever, because
     * GEO_BLOCKED never eliminates anything.
     */
    const val GEO_TOLERANCE = 4

    private val OK_INTERVAL = 12.hours.inWholeMilliseconds
    private val GEO_INTERVAL = 3.days.inWholeMilliseconds
    private val VOD_INTERVAL = 2.days.inWholeMilliseconds

    /** How long a DEAD stream rests before it is given another chance. */
    val DEAD_RETRY_INTERVAL = 7.days.inWholeMilliseconds

    /**
     * Exponential back-off after `failures` consecutive failures.
     *
     * 1h then 6h then 24h spreads the three strikes over roughly 31 hours, which is
     * wide enough that a CDN maintenance window or an origin restart does not
     * eliminate a working stream.
     */
    fun backoff(failures: Int): Long = when (failures) {
        0, 1 -> 1.hours.inWholeMilliseconds
        2 -> 6.hours.inWholeMilliseconds
        3 -> 24.hours.inWholeMilliseconds
        else -> DEAD_RETRY_INTERVAL
    }

    /** Applies a probe result to a stream's health record. */
    fun apply(current: HealthInfo, result: CheckResult, now: Long): HealthInfo = when (result) {
        is CheckResult.Alive -> current.copy(
            state = StreamState.OK,
            consecutiveFailures = 0,
            lastOkAt = now,
            lastCheckedAt = now,
            lastLatencyMs = result.latencyMs.coerceAtLeast(1),
            lastErrorCode = HealthErrorCode.NONE,
            isVod = !result.isLive,
            // A finished VOD playlist is not what this app is for, but it is also not
            // broken. Keep it, just look at it far less often.
            nextCheckAt = now + if (result.isLive) OK_INTERVAL else VOD_INTERVAL,
        )

        is CheckResult.GeoBlocked -> {
            val failures = current.consecutiveFailures + 1
            if (failures > GEO_TOLERANCE) {
                // It has claimed "region locked" too many times to still be plausible.
                current.copy(
                    state = StreamState.DEAD,
                    consecutiveFailures = failures,
                    lastCheckedAt = now,
                    lastErrorCode = result.code,
                    nextCheckAt = now + DEAD_RETRY_INTERVAL,
                )
            } else {
                current.copy(
                    state = StreamState.GEO_BLOCKED,
                    consecutiveFailures = failures,
                    lastCheckedAt = now,
                    lastErrorCode = result.code,
                    nextCheckAt = now + GEO_INTERVAL,
                )
            }
        }

        is CheckResult.Dead -> {
            val failures = current.consecutiveFailures + 1
            current.copy(
                state = if (failures >= FAIL_THRESHOLD) StreamState.DEAD else demote(current.state),
                consecutiveFailures = failures,
                lastCheckedAt = now,
                lastErrorCode = result.code,
                nextCheckAt = now + backoff(failures),
            )
        }

        // Nothing was learned, so nothing changes — not even `lastCheckedAt`, or an
        // offline device would push every stream's next check a full interval out.
        is CheckResult.Inconclusive -> current
    }

    /**
     * Folds a playback outcome observed by the player into the health record.
     *
     * This is the highest-quality signal the app has: the user pressed OK and either
     * saw a frame or did not.
     */
    fun applyPlayback(current: HealthInfo, signal: PlaybackSignal, now: Long): HealthInfo =
        when (signal) {
            is PlaybackSignal.RenderedFirstFrame -> current.copy(
                state = StreamState.OK,
                consecutiveFailures = 0,
                lastOkAt = now,
                lastCheckedAt = now,
                lastLatencyMs = signal.timeToFirstFrameMs.coerceAtLeast(1),
                lastErrorCode = HealthErrorCode.NONE,
                nextCheckAt = now + OK_INTERVAL,
            )

            is PlaybackSignal.Failed -> {
                val failures = current.consecutiveFailures + PLAYBACK_FAILURE_WEIGHT
                current.copy(
                    state = if (failures >= FAIL_THRESHOLD) StreamState.DEAD else demote(current.state),
                    consecutiveFailures = failures,
                    lastCheckedAt = now,
                    lastErrorCode = signal.errorCode,
                    nextCheckAt = now + backoff(failures),
                )
            }

            // Region locks and device-local codec faults never eliminate a stream.
            is PlaybackSignal.GeoBlocked -> apply(current, CheckResult.GeoBlocked(signal.code), now)
            is PlaybackSignal.DeviceLocalFailure,
            is PlaybackSignal.NetworkFailure,
            -> current
        }

    /**
     * Resets a DEAD stream that has served its cool-off back to UNKNOWN so it is
     * probed again. Roughly one in eight comes back.
     */
    fun reviveIfDue(current: HealthInfo, now: Long): HealthInfo =
        if (current.state == StreamState.DEAD && now >= current.nextCheckAt) {
            current.copy(state = StreamState.UNKNOWN, consecutiveFailures = 0)
        } else {
            current
        }

    /**
     * A stream that had been verified keeps its OK badge through the first failures
     * rather than flickering to UNKNOWN and back — the user should not watch cards
     * change colour while a sweep runs.
     */
    private fun demote(state: StreamState): StreamState =
        if (state == StreamState.DEAD) StreamState.UNKNOWN else state
}

/** What the player observed while trying to play a stream. */
sealed interface PlaybackSignal {
    data class RenderedFirstFrame(val timeToFirstFrameMs: Int) : PlaybackSignal

    /** A genuine source failure: bad status, parse error, timeout. */
    data class Failed(val errorCode: Int) : PlaybackSignal

    data class GeoBlocked(val code: Int) : PlaybackSignal

    /**
     * A decoder fault. Device-specific — the same stream may be fine on other
     * hardware — so it is blacklisted locally and never counted against the stream.
     */
    data class DeviceLocalFailure(val errorCode: Int) : PlaybackSignal

    /** The user's own connectivity failed. Tells us nothing about the stream. */
    data class NetworkFailure(val errorCode: Int) : PlaybackSignal
}
