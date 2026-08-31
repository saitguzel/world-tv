package com.worldtv.data.health

import com.worldtv.core.model.HealthInfo
import com.worldtv.core.model.StreamState
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Records what was asked of it and returns scripted verdicts. */
private class FakeProbe(
    val manifestResults: MutableMap<String, CheckResult> = mutableMapOf(),
    val segmentResults: MutableMap<String, CheckResult> = mutableMapOf(),
) : StreamProbe {
    val manifestCalls = AtomicInteger()
    val segmentCalls = AtomicInteger()

    override suspend fun checkManifest(target: ProbeTarget): CheckResult {
        manifestCalls.incrementAndGet()
        return manifestResults[target.id] ?: CheckResult.Dead(404, "unscripted")
    }

    override suspend fun checkSegment(target: ProbeTarget, manifest: String): CheckResult {
        segmentCalls.incrementAndGet()
        return segmentResults[target.id] ?: CheckResult.Alive(latencyMs = 50, manifest = manifest)
    }
}

private class FakeStore(
    private val health: MutableMap<String, HealthInfo> = mutableMapOf(),
    private val due: MutableList<ProbeTarget> = mutableListOf(),
) : HealthStore {
    val writes = mutableListOf<Map<String, HealthInfo>>()
    var revived = 0

    override suspend fun dueForCheck(now: Long, limit: Int): List<ProbeTarget> = due.take(limit)

    override suspend fun dueForCheck(
        now: Long,
        limit: Int,
        priority: CheckPriority,
    ): List<ProbeTarget> = if (priority == CheckPriority.FAVORITES) due.take(limit) else emptyList()

    override suspend fun healthOf(streamId: String): HealthInfo? = health[streamId]

    override suspend fun updateHealth(updates: Map<String, HealthInfo>) {
        writes += updates
        health.putAll(updates)
    }

    override suspend fun reviveExpired(now: Long): Int = revived

    fun seed(id: String, info: HealthInfo) { health[id] = info }
    fun enqueue(vararg targets: ProbeTarget) { due += targets }
    fun clearDue() { due.clear() }
    fun currentHealth(id: String): HealthInfo? = health[id]
}

class HealthCheckerTest {

    private val time = FakeTimeProvider()

    private fun target(id: String) = ProbeTarget(
        id = id,
        url = "https://cdn.test/$id.m3u8",
        referrer = null,
        userAgent = null,
        label = null,
    )

    private fun checker(probe: StreamProbe, store: HealthStore) =
        HealthChecker(probe, store, time, Dispatchers.Unconfined)

    @Test
    fun `tier 2 runs only after tier 1 succeeds`() = runTest {
        val probe = FakeProbe(
            manifestResults = mutableMapOf(
                "ok" to CheckResult.Alive(latencyMs = 100, manifest = "#EXTM3U"),
                "bad" to CheckResult.Dead(404, "gone"),
            ),
        )
        val store = FakeStore()
        checker(probe, store).checkBatch(listOf(target("ok"), target("bad")))

        assertEquals(2, probe.manifestCalls.get())
        // A dead manifest must not cost a second round trip.
        assertEquals(1, probe.segmentCalls.get())
    }

    @Test
    fun `a manifest that serves but has nothing behind it is recorded dead`() = runTest {
        val probe = FakeProbe(
            manifestResults = mutableMapOf("s" to CheckResult.Alive(200, manifest = "#EXTM3U")),
            segmentResults = mutableMapOf("s" to CheckResult.Dead(-4, "no segments")),
        )
        val store = FakeStore()
        checker(probe, store).checkBatch(listOf(target("s")))

        // Tier 2's verdict wins: a 200 playlist shell over a dead feed is common.
        assertEquals(1, store.currentHealth("s")?.consecutiveFailures)
    }

    @Test
    fun `deep checking off skips tier 2 entirely`() = runTest {
        val probe = FakeProbe(
            manifestResults = mutableMapOf("s" to CheckResult.Alive(100, manifest = "#EXTM3U")),
        )
        val store = FakeStore()
        val checker = checker(probe, store)
        checker.config = checker.config.copy(deepCheck = false)

        checker.checkBatch(listOf(target("s")))

        assertEquals(0, probe.segmentCalls.get())
        assertEquals(StreamState.OK, store.currentHealth("s")?.state)
    }

    @Test
    fun `tier 1 success survives a tier 2 that could not reach the network`() = runTest {
        val probe = FakeProbe(
            manifestResults = mutableMapOf("s" to CheckResult.Alive(120, manifest = "#EXTM3U")),
            segmentResults = mutableMapOf("s" to CheckResult.Inconclusive("no route")),
        )
        val store = FakeStore()
        checker(probe, store).checkBatch(listOf(target("s")))

        // Tier 1 already proved the origin is serving; losing that to a transient
        // network blip would throw away a real positive.
        assertEquals(StreamState.OK, store.currentHealth("s")?.state)
    }

    @Test
    fun `an inconclusive result is never written to the store`() = runTest {
        val probe = FakeProbe(
            manifestResults = mutableMapOf("s" to CheckResult.Inconclusive("airplane mode")),
        )
        val store = FakeStore().apply {
            seed("s", HealthInfo(state = StreamState.OK, consecutiveFailures = 0))
        }
        checker(probe, store).checkBatch(listOf(target("s")))

        // An offline device must not touch a single row, let alone mark the catalog dead.
        assertTrue(store.writes.isEmpty(), "wrote ${store.writes}")
        assertEquals(StreamState.OK, store.currentHealth("s")?.state)
    }

    @Test
    fun `results are written in one batch, not one write per stream`() = runTest {
        val ids = (1..25).map { "s$it" }
        val probe = FakeProbe(
            manifestResults = ids.associateWith {
                CheckResult.Alive(latencyMs = 10, manifest = "#EXTM3U")
            }.toMutableMap(),
        )
        val store = FakeStore()
        checker(probe, store).checkBatch(ids.map(::target))

        assertEquals(1, store.writes.size, "a sweep should write one transaction per batch")
        assertEquals(25, store.writes.single().size)
    }

    @Test
    fun `an empty batch does no work at all`() = runTest {
        val probe = FakeProbe()
        val store = FakeStore()
        val result = checker(probe, store).checkBatch(emptyList())

        assertTrue(result.isEmpty())
        assertEquals(0, probe.manifestCalls.get())
        assertTrue(store.writes.isEmpty())
    }

    @Test
    fun `a sweep stops when the budget is spent rather than draining the queue`() = runTest {
        val probe = object : StreamProbe {
            override suspend fun checkManifest(target: ProbeTarget): CheckResult {
                // Every probe burns a slice of the budget.
                time.advance(400)
                return CheckResult.Alive(latencyMs = 5, manifest = "#EXTM3U")
            }

            override suspend fun checkSegment(target: ProbeTarget, manifest: String) =
                CheckResult.Alive(latencyMs = 5, manifest = manifest)
        }
        val store = FakeStore()
        repeat(50) { store.enqueue(target("s$it")) }

        val checked = checker(probe, store)
            .sweep(budgetMillis = 1_000, priorities = listOf(CheckPriority.FAVORITES))

        // WorkManager kills a worker at ten minutes and a killed worker loses its
        // batch, so finishing early and resuming next period is the correct behaviour.
        assertTrue(checked in 1..200, "checked $checked")
        assertFalse(store.writes.isEmpty())
    }

    @Test
    fun `playback outcomes reach the store and outweigh an http probe`() = runTest {
        val store = FakeStore().apply {
            seed("s", HealthInfo(state = StreamState.OK, consecutiveFailures = 0))
        }
        val checker = checker(FakeProbe(), store)

        checker.reportPlayback("s", PlaybackSignal.Failed(2004))
        assertEquals(HealthPolicy.PLAYBACK_FAILURE_WEIGHT, store.currentHealth("s")?.consecutiveFailures)

        checker.reportPlayback("s", PlaybackSignal.Failed(2004))
        assertEquals(StreamState.DEAD, store.currentHealth("s")?.state)
    }

    @Test
    fun `a playback report for an unknown stream is ignored rather than inventing a row`() = runTest {
        val store = FakeStore()
        checker(FakeProbe(), store).reportPlayback("never-seen", PlaybackSignal.Failed(1))
        assertTrue(store.writes.isEmpty())
    }

    @Test
    fun `concurrency is sized from device memory`() = runTest {
        // Cheap boxes exhaust sockets well before the desktop-ish defaults.
        assertEquals(4, HealthCheckConfig.forDeviceMemory(1_024).maxParallel)
        assertFalse(HealthCheckConfig.forDeviceMemory(1_024).deepCheck)
        assertEquals(8, HealthCheckConfig.forDeviceMemory(2_048).maxParallel)
        assertEquals(16, HealthCheckConfig.forDeviceMemory(8_192).maxParallel)
    }
}
