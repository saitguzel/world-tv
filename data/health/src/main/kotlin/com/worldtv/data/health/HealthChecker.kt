package com.worldtv.data.health

import com.worldtv.core.model.HealthInfo
import com.worldtv.core.model.TimeProvider
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** How hard the engine is allowed to work. Surfaced in settings. */
data class HealthCheckConfig(
    /** Global concurrent probes. Cheap TV boxes exhaust sockets well before 32. */
    val maxParallel: Int = 8,
    val maxPerHost: Int = HostThrottle.DEFAULT_MAX_PER_HOST,
    /** Run tier 2 (segment fetch) as well as tier 1. */
    val deepCheck: Boolean = true,
    val batchSize: Int = 100,
) {
    companion object {
        /**
         * Picked from device RAM at runtime. 32 parallel sockets is where the
         * 1–2 GB boxes this app targets start refusing connections.
         */
        fun forDeviceMemory(totalRamMb: Long): HealthCheckConfig = when {
            totalRamMb < 1_536 -> HealthCheckConfig(maxParallel = 4, deepCheck = false)
            totalRamMb < 3_072 -> HealthCheckConfig(maxParallel = 8)
            else -> HealthCheckConfig(maxParallel = 16)
        }
    }
}

/**
 * Runs batches of health probes and writes the verdicts back.
 *
 * Two levels of gating: a global semaphore bounds total sockets, and [HostThrottle]
 * bounds per-origin pressure. The global permit is taken first so a slow origin
 * cannot pin the whole pool while its queue drains.
 */
@Singleton
class HealthChecker @Inject constructor(
    private val probe: StreamProbe,
    private val store: HealthStore,
    private val time: TimeProvider,
    private val ioDispatcher: CoroutineDispatcher,
) {

    @Volatile
    private var currentConfig: HealthCheckConfig = HealthCheckConfig()

    @Volatile
    private var hostThrottle: HostThrottle = HostThrottle(currentConfig.maxPerHost)

    /**
     * Setting this rebuilds the per-host throttle: the semaphores hold the old limit,
     * so reusing them would silently ignore a change to the aggressiveness setting.
     */
    var config: HealthCheckConfig
        get() = currentConfig
        set(value) {
            if (value.maxPerHost != currentConfig.maxPerHost) {
                hostThrottle = HostThrottle(value.maxPerHost)
            }
            currentConfig = value
        }

    /**
     * Probes [targets] and persists the results.
     *
     * @return the health each target ended up with, so callers can update UI without
     *   re-reading the database.
     */
    suspend fun checkBatch(targets: List<ProbeTarget>): Map<String, HealthInfo> {
        if (targets.isEmpty()) return emptyMap()
        return withContext(ioDispatcher) {
            val gate = Semaphore(config.maxParallel)
            val now = time.nowMillis()

            val results: List<Pair<String, HealthInfo>?> = coroutineScope {
                targets.map { target ->
                    async {
                        val current = store.healthOf(target.id) ?: HealthInfo()
                        val result = gate.withPermit {
                            hostThrottle.withHostPermit(target.url) { probeOnce(target) }
                        }
                        // Inconclusive means "we learned nothing" — skip the write
                        // entirely rather than persisting an unchanged row.
                        if (result is CheckResult.Inconclusive) {
                            null
                        } else {
                            target.id to HealthPolicy.apply(current, result, now)
                        }
                    }
                }.awaitAll()
            }

            val updates = results.filterNotNull().toMap()
            if (updates.isNotEmpty()) store.updateHealth(updates)
            updates
        }
    }

    /** Tier 1, then tier 2 when tier 1 succeeded and deep checking is enabled. */
    private suspend fun probeOnce(target: ProbeTarget): CheckResult {
        val tier1 = probe.checkManifest(target)
        if (tier1 !is CheckResult.Alive) return tier1
        if (!config.deepCheck) return tier1

        val manifest = tier1.manifest ?: return tier1
        return when (val tier2 = probe.checkSegment(target, manifest)) {
            // A 200 manifest with nothing behind it is common; trust tier 2's verdict.
            is CheckResult.Alive -> tier2.copy(
                latencyMs = maxOf(tier1.latencyMs, tier2.latencyMs),
                variantCount = tier1.variantCount,
            )
            // Tier 2 could not reach the segment for network reasons — tier 1 still
            // proved the origin is up, so keep the weaker but real positive.
            is CheckResult.Inconclusive -> tier1
            else -> tier2
        }
    }

    /**
     * One periodic sweep, bounded by [budgetMillis] so a `CoroutineWorker` finishes
     * well inside WorkManager's 10-minute execution window and resumes next period.
     *
     * @return number of streams checked.
     */
    suspend fun sweep(budgetMillis: Long, priorities: List<CheckPriority> = CheckPriority.entries): Int {
        val deadline = time.elapsedMillis() + budgetMillis
        var checked = 0
        store.reviveExpired(time.nowMillis())

        for (priority in priorities) {
            while (time.elapsedMillis() < deadline) {
                val batch = store.dueForCheck(time.nowMillis(), config.batchSize, priority)
                if (batch.isEmpty()) break
                checkBatch(batch)
                checked += batch.size
            }
            if (time.elapsedMillis() >= deadline) break
        }
        hostThrottle.clear()
        return checked
    }

    /** Folds a playback outcome into the store. The strongest signal the app gets. */
    suspend fun reportPlayback(streamId: String, signal: PlaybackSignal) {
        val current = store.healthOf(streamId) ?: return
        val updated = HealthPolicy.applyPlayback(current, signal, time.nowMillis())
        if (updated != current) store.updateHealth(mapOf(streamId to updated))
    }

    /**
     * Lazy verification for whatever list the user is looking at right now.
     *
     * Fire-and-forget on [scope]: results reach the UI through the database Flow, so
     * there is nothing for the caller to await. Cancelling the scope (leaving the
     * screen) cancels the in-flight probes with it.
     */
    fun verifyVisible(scope: CoroutineScope, targets: List<ProbeTarget>): Job? {
        if (targets.isEmpty()) return null
        return scope.launch { checkBatch(targets) }
    }
}
