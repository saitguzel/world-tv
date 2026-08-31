package com.worldtv.data.health

import com.worldtv.core.model.HealthInfo

/**
 * Persistence port for the health engine.
 *
 * The engine deliberately does not take a Room DAO. Keeping the dependency pointed
 * this way is what lets `:data:health` stay a plain JVM module with millisecond unit
 * tests, and it is the seam the architecture doc asks for when it says the engine
 * must be independently testable.
 */
interface HealthStore {

    /** Streams whose `nextCheckAt` has come due, cheapest-to-recheck first. */
    suspend fun dueForCheck(now: Long, limit: Int): List<ProbeTarget>

    /** Due streams restricted to a priority bucket (favourites, recents, home country). */
    suspend fun dueForCheck(now: Long, limit: Int, priority: CheckPriority): List<ProbeTarget>

    /** Current health of a stream, or null if it is unknown to the store. */
    suspend fun healthOf(streamId: String): HealthInfo?

    /**
     * Writes back health for a batch.
     *
     * Implementations must update only the health columns. Rewriting whole rows would
     * clobber catalog fields a concurrent sync just wrote, and lose playback reports
     * that landed while the sweep was in flight.
     */
    suspend fun updateHealth(updates: Map<String, HealthInfo>)

    /** Moves DEAD streams past their cool-off back to UNKNOWN. Returns how many. */
    suspend fun reviveExpired(now: Long): Int
}

/**
 * Sweep ordering. Favourites first — a user notices a dead favourite immediately and
 * never notices a dead channel in a country they have not opened.
 */
enum class CheckPriority { FAVORITES, RECENTS, HOME_COUNTRY, EVERYTHING_ELSE }
