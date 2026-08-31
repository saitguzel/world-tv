package com.worldtv.core.designsystem.tv.focus

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Throttles held-down D-pad repeats.
 *
 * Holding *down* on a 500-channel list fires at the system repeat rate (~50/s), which
 * scrolls hundreds of rows before the user can react and drops frames doing it. This
 * ramps: deliberate at first, faster once the user is clearly seeking.
 */
class KeyRepeatLimiter(private val clock: () -> Long = SystemClock::uptimeMillis) {

    private var lastAcceptedAt = 0L

    fun accept(repeatCount: Int): Boolean {
        // The first press is always honoured — latency here is felt immediately.
        if (repeatCount == 0) {
            lastAcceptedAt = clock()
            return true
        }
        val minInterval = when {
            repeatCount < 5 -> 120L
            repeatCount < 15 -> 70L
            else -> 40L
        }
        val now = clock()
        if (now - lastAcceptedAt < minInterval) return false
        lastAcceptedAt = now
        return true
    }
}

@Composable
fun rememberKeyRepeatLimiter(): KeyRepeatLimiter = remember { KeyRepeatLimiter() }
