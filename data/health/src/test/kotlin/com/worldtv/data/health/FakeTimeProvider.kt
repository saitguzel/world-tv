package com.worldtv.data.health

import com.worldtv.core.model.TimeProvider

/** Deterministic clock, so latency and back-off assertions are not flaky. */
class FakeTimeProvider(
    private var now: Long = 1_700_000_000_000L,
    private var elapsed: Long = 0L,
) : TimeProvider {
    override fun nowMillis(): Long = now
    override fun elapsedMillis(): Long = elapsed

    fun advance(millis: Long) {
        now += millis
        elapsed += millis
    }
}
