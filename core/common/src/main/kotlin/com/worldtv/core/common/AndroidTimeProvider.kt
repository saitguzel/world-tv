package com.worldtv.core.common

import android.os.SystemClock
import com.worldtv.core.model.TimeProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android-backed [TimeProvider].
 *
 * `elapsedRealtime()` rather than `currentTimeMillis()` for latency: TV boxes commonly
 * have no RTC battery and jump the wall clock by years once NTP lands, which would
 * otherwise produce absurd latency measurements right after boot.
 */
@Singleton
class AndroidTimeProvider @Inject constructor() : TimeProvider {
    override fun nowMillis(): Long = System.currentTimeMillis()
    override fun elapsedMillis(): Long = SystemClock.elapsedRealtime()
}
