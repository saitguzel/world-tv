package com.worldtv.data.repository

import com.worldtv.core.common.di.IoDispatcher
import com.worldtv.core.database.dao.HealthRow
import com.worldtv.core.database.dao.ProbeRow
import com.worldtv.core.database.dao.RadioDao
import com.worldtv.core.database.dao.StreamDao
import com.worldtv.core.model.HealthInfo
import com.worldtv.core.model.StreamKind
import com.worldtv.core.model.StreamState
import com.worldtv.data.health.CheckPriority
import com.worldtv.data.health.HealthStore
import com.worldtv.data.health.ProbeTarget
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Room-backed [HealthStore].
 *
 * This class is the only place Room and the health engine meet, which is what keeps
 * `:data:health` a plain JVM module with fast tests.
 */
@Singleton
class RoomHealthStore @Inject constructor(
    private val streamDao: StreamDao,
    private val radioDao: RadioDao,
    @IoDispatcher private val io: CoroutineDispatcher,
    private val preferences: UserPreferencesRepository,
) : HealthStore {

    override suspend fun dueForCheck(now: Long, limit: Int): List<ProbeTarget> =
        withContext(io) { streamDao.dueForCheck(now, limit).map(ProbeRow::toTarget) }

    override suspend fun dueForCheck(
        now: Long,
        limit: Int,
        priority: CheckPriority,
    ): List<ProbeTarget> = withContext(io) {
        val rows = when (priority) {
            CheckPriority.FAVORITES -> streamDao.dueForCheckFavorites(now, limit)
            CheckPriority.RECENTS -> streamDao.dueForCheckRecents(now, limit)
            CheckPriority.HOME_COUNTRY -> {
                val country = preferences.homeCountry()
                if (country == null) emptyList() else {
                    streamDao.dueForCheckInCountry(now, country, limit)
                }
            }
            // Radio stations ride along in the general bucket; their probe is the same
            // ranged GET, and Radio Browser's own health data only covers so much.
            CheckPriority.EVERYTHING_ELSE ->
                streamDao.dueForCheck(now, limit) + radioDao.dueForCheck(now, limit / 4)
        }
        rows.map(ProbeRow::toTarget)
    }

    override suspend fun healthOf(streamId: String): HealthInfo? = withContext(io) {
        (streamDao.healthOf(streamId) ?: radioDao.healthOf(streamId))?.toHealthInfo()
    }

    /**
     * Writes health back through targeted column updates.
     *
     * Deliberately not a row upsert: a sweep can overlap a catalog sync and a playback
     * report, and rewriting whole rows would restore stale URLs and drop the strongest
     * signal the app has.
     */
    override suspend fun updateHealth(updates: Map<String, HealthInfo>) = withContext(io) {
        for ((id, health) in updates) {
            val rows = streamDao.updateHealthOf(id, health)
            if (rows == 0) radioDao.updateHealthOf(id, health)
        }
    }

    override suspend fun reviveExpired(now: Long): Int = withContext(io) {
        streamDao.reviveExpired(now) + radioDao.reviveExpired(now)
    }

    /** Targets for the channels currently on screen — the lazy-verification path. */
    suspend fun dueForChannels(channelIds: List<String>, now: Long, limit: Int): List<ProbeTarget> =
        withContext(io) {
            if (channelIds.isEmpty()) emptyList()
            else streamDao.dueForChannels(channelIds, now, limit).map(ProbeRow::toTarget)
        }
}

internal fun ProbeRow.toTarget(): ProbeTarget = ProbeTarget(
    id = id,
    url = url,
    referrer = referrer,
    userAgent = userAgent,
    label = label,
    kind = runCatching { StreamKind.valueOf(kind) }.getOrDefault(StreamKind.UNKNOWN_HTTP),
)

internal fun HealthRow.toHealthInfo(): HealthInfo = HealthInfo(
    state = runCatching { StreamState.valueOf(state) }.getOrDefault(StreamState.UNKNOWN),
    lastCheckedAt = lastCheckedAt,
    lastOkAt = lastOkAt,
    consecutiveFailures = consecutiveFailures,
    lastLatencyMs = lastLatencyMs,
    nextCheckAt = nextCheckAt,
    lastErrorCode = lastErrorCode,
    isVod = isVod,
)

private suspend fun StreamDao.updateHealthOf(id: String, health: HealthInfo): Int =
    updateHealth(
        id = id,
        state = health.state.name,
        lastCheckedAt = health.lastCheckedAt,
        lastOkAt = health.lastOkAt,
        consecutiveFailures = health.consecutiveFailures,
        lastLatencyMs = health.lastLatencyMs,
        nextCheckAt = health.nextCheckAt,
        lastErrorCode = health.lastErrorCode,
        isVod = health.isVod,
    )

private suspend fun RadioDao.updateHealthOf(uuid: String, health: HealthInfo): Int =
    updateHealth(
        uuid = uuid,
        state = health.state.name,
        lastCheckedAt = health.lastCheckedAt,
        lastOkAt = health.lastOkAt,
        consecutiveFailures = health.consecutiveFailures,
        lastLatencyMs = health.lastLatencyMs,
        nextCheckAt = health.nextCheckAt,
        lastErrorCode = health.lastErrorCode,
        isVod = health.isVod,
    )
