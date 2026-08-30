package com.worldtv.data.repository

import com.worldtv.core.common.DeviceCapabilities
import com.worldtv.core.common.di.ApplicationScope
import com.worldtv.core.common.di.IoDispatcher
import com.worldtv.core.common.network.NetworkMonitor
import com.worldtv.core.database.dao.StreamDao
import com.worldtv.core.database.dao.UserDataDao
import com.worldtv.core.database.entity.DeviceBlacklistEntity
import com.worldtv.core.model.StreamState
import com.worldtv.core.model.TimeProvider
import com.worldtv.data.health.CheckPriority
import com.worldtv.data.health.HealthCheckConfig
import com.worldtv.data.health.HealthChecker
import com.worldtv.data.health.PlaybackSignal
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Application-facing entry point to the health engine. */
@Singleton
class HealthRepository @Inject constructor(
    private val checker: HealthChecker,
    private val store: RoomHealthStore,
    private val streamDao: StreamDao,
    private val userDataDao: UserDataDao,
    private val preferences: UserPreferencesRepository,
    private val deviceCapabilities: DeviceCapabilities,
    private val networkMonitor: NetworkMonitor,
    private val time: TimeProvider,
    @IoDispatcher private val io: CoroutineDispatcher,
    @ApplicationScope private val appScope: CoroutineScope,
) {

    val verifiedCount: Flow<Int> = streamDao.countByState(StreamState.OK.name)
    val deadCount: Flow<Int> = streamDao.countByState(StreamState.DEAD.name)

    /** Applies the device's capability ceiling and the user's preference to the engine. */
    suspend fun applyConfig() {
        val deviceDefault = HealthCheckConfig.forDeviceMemory(deviceCapabilities.totalRamMb)
        val aggressiveness = preferences.preferences.first().healthAggressiveness
        checker.config = aggressiveness.toConfig(deviceDefault)
    }

    /**
     * Lazy verification for the channels currently on screen.
     *
     * Fire-and-forget: results land in the database and reach the grid through its
     * Flow. Called as the user opens a country or category, which is what makes the
     * app spend its probe budget on what the user is actually looking at.
     */
    fun verifyVisibleChannels(scope: CoroutineScope, channelIds: List<String>) {
        if (channelIds.isEmpty()) return
        scope.launch {
            if (!networkMonitor.isOnline.first()) return@launch
            applyConfig()
            val targets = store.dueForChannels(channelIds, time.nowMillis(), limit = VISIBLE_LIMIT)
            checker.checkBatch(targets)
        }
    }

    /** One bounded background sweep. Called by `HealthSweepWorker`. */
    suspend fun sweep(budgetMillis: Long, priorities: List<CheckPriority> = CheckPriority.entries): Int {
        if (!networkMonitor.isOnline.first()) return 0
        applyConfig()
        return checker.sweep(budgetMillis, priorities)
    }

    /**
     * Records what happened when the user actually pressed play.
     *
     * Runs on the application scope, not the screen's: the report must survive the
     * user immediately zapping away from the broken channel, which is exactly what
     * they do.
     */
    fun reportPlayback(streamId: String, signal: PlaybackSignal) {
        appScope.launch {
            // A decoder fault says something about this box, not about the stream.
            // Hide it here and leave the shared health record untouched.
            if (signal is PlaybackSignal.DeviceLocalFailure) {
                withContext(io) {
                    userDataDao.blacklistOnThisDevice(
                        DeviceBlacklistEntity(
                            streamId = streamId,
                            errorCode = signal.errorCode,
                            blockedAt = time.nowMillis(),
                        ),
                    )
                }
            }
            checker.reportPlayback(streamId, signal)
        }
    }

    /**
     * Makes every stream due for another check, including ones currently hidden.
     *
     * Reschedules rather than probing: the sweep worker already paces itself within a
     * budget and honours the priority order, so the user's own country is re-verified
     * long before the tail of the catalog.
     */
    suspend fun recheckAll() = withContext(io) {
        val now = time.nowMillis()
        streamDao.markAllDue(now)
        streamDao.reviveExpired(now)
    }

    /** Explicit "check my favourites now" from settings or a long-press. */
    suspend fun refreshFavorites(): Int {
        applyConfig()
        val targets = store.dueForCheck(time.nowMillis(), limit = 200, CheckPriority.FAVORITES)
        return checker.checkBatch(targets).size
    }

    private companion object {
        /** One screenful plus prefetch. More than this is work the user will not see. */
        const val VISIBLE_LIMIT = 40
    }
}
