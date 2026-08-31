package com.worldtv.core.model

/**
 * Clock seam for the health engine.
 *
 * The architecture doc calls the health module "pure Kotlin, no Android dependency"
 * but then reaches for `SystemClock.elapsedRealtime()`. This interface is that
 * dependency inverted: production wires it to `System.currentTimeMillis()` /
 * `SystemClock.elapsedRealtime()`, tests wire it to a fake and get deterministic
 * back-off assertions.
 */
interface TimeProvider {
    /** Wall clock, epoch millis. Used for scheduling fields persisted to the DB. */
    fun nowMillis(): Long

    /** Monotonic millis. Used for latency measurement; unaffected by clock changes. */
    fun elapsedMillis(): Long

    companion object {
        /** JVM default. Android overrides [elapsedMillis] with `SystemClock.elapsedRealtime()`. */
        val System: TimeProvider = object : TimeProvider {
            override fun nowMillis(): Long = java.lang.System.currentTimeMillis()
            override fun elapsedMillis(): Long = java.lang.System.nanoTime() / 1_000_000L
        }
    }
}
