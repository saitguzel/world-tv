package com.worldtv.data.health

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import java.util.concurrent.atomic.AtomicInteger
import org.junit.jupiter.api.Test

class HostThrottleTest {

    @Test
    fun `extracts the host from assorted URL shapes`() {
        val throttle = HostThrottle()
        assertEquals("a.example", throttle.hostOf("https://a.example/live.m3u8"))
        assertEquals("a.example", throttle.hostOf("http://a.example:8080/live.m3u8"))
        // Non-HTTP schemes fall through to the manual parser rather than returning null.
        assertEquals("a.example", throttle.hostOf("rtsp://a.example:554/live"))
    }

    @Test
    fun `never exceeds the per-host limit`() = runTest {
        val throttle = HostThrottle(maxPerHost = 2)
        val inFlight = AtomicInteger(0)
        val peak = AtomicInteger(0)

        (1..20).map { index ->
            async {
                throttle.withHostPermit("https://same.example/$index.m3u8") {
                    val now = inFlight.incrementAndGet()
                    peak.updateAndGet { maxOf(it, now) }
                    delay(5)
                    inFlight.decrementAndGet()
                }
            }
        }.awaitAll()

        assertEquals(0, inFlight.get())
        assertTrue(peak.get() <= 2, "peak per-host concurrency was ${peak.get()}")
    }

    @Test
    fun `releases the permit when the body throws`() = runTest {
        val throttle = HostThrottle(maxPerHost = 1)
        repeat(3) {
            runCatching {
                throttle.withHostPermit("https://a.example/x") { error("probe blew up") }
            }
        }
        // If the permit leaked, this would deadlock instead of completing.
        val completed = throttle.withHostPermit("https://a.example/x") { true }
        assertTrue(completed)
    }
}
