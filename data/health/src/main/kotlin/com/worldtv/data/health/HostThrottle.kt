package com.worldtv.data.health

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Caps concurrent requests per origin host.
 *
 * A batch of 100 streams routinely contains 40 URLs on the same restreamer. Firing
 * those at once is the fastest way to get the app's whole user base IP-banned, which
 * is indistinguishable from every stream on that host dying at once.
 */
class HostThrottle(private val maxPerHost: Int = DEFAULT_MAX_PER_HOST) {

    private val semaphores = ConcurrentHashMap<String, Semaphore>()

    /**
     * Runs [block] holding a permit for [url]'s host.
     *
     * Uses `withPermit`, so the permit is released even when [block] throws or the
     * coroutine is cancelled — a manual acquire/release pair leaks a permit on the
     * first timeout and eventually wedges the sweep.
     */
    suspend fun <T> withHostPermit(url: String, block: suspend () -> T): T {
        val host = hostOf(url) ?: return block()
        val semaphore = semaphores.computeIfAbsent(host) { Semaphore(maxPerHost) }
        return semaphore.withPermit { block() }
    }

    /** Frees per-host bookkeeping between sweeps; the catalog has thousands of hosts. */
    fun clear() = semaphores.clear()

    internal fun hostOf(url: String): String? =
        url.toHttpUrlOrNull()?.host
            ?: url.substringAfter("://", missingDelimiterValue = "")
                .substringBefore('/')
                .substringBefore(':')
                .takeIf { it.isNotEmpty() }

    companion object {
        const val DEFAULT_MAX_PER_HOST = 2
    }
}
