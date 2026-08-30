package com.worldtv.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.worldtv.data.repository.SyncTrigger
import com.worldtv.data.sync.worker.CatalogSyncWorker
import com.worldtv.data.sync.worker.CleanupWorker
import com.worldtv.data.sync.worker.FavoritesHealthWorker
import com.worldtv.data.sync.worker.HealthSweepWorker
import com.worldtv.data.sync.worker.YouTubeLiveWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Registers the background schedule.
 *
 * Every request is `KEEP` under a unique name, so calling this on every app start is
 * idempotent. WorkManager re-schedules itself after `BOOT_COMPLETED` with no extra code.
 */
@Singleton
class SyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : SyncTrigger {
    private val workManager get() = WorkManager.getInstance(context)

    fun scheduleAll() {
        // A TV box is mains-powered. A battery constraint here would simply never be
        // satisfied on devices that report no battery at all, and the worker would
        // never run.
        val networkOnly = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        workManager.enqueueUniquePeriodicWork(
            CATALOG_SYNC,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<CatalogSyncWorker>(24, TimeUnit.HOURS)
                .setConstraints(networkOnly)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS)
                .build(),
        )

        workManager.enqueueUniquePeriodicWork(
            HEALTH_SWEEP,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<HealthSweepWorker>(6, TimeUnit.HOURS)
                .setConstraints(networkOnly)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build(),
        )

        workManager.enqueueUniquePeriodicWork(
            FAVORITES_HEALTH,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<FavoritesHealthWorker>(1, TimeUnit.HOURS)
                .setConstraints(networkOnly)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build(),
        )

        workManager.enqueueUniquePeriodicWork(
            YOUTUBE_LIVE,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<YouTubeLiveWorker>(6, TimeUnit.HOURS)
                .setConstraints(networkOnly)
                // No aggressive backoff: a refused call is almost always quota
                // exhaustion, and retrying sooner only spends more of it.
                .setBackoffCriteria(BackoffPolicy.LINEAR, 2, TimeUnit.HOURS)
                .build(),
        )

        workManager.enqueueUniquePeriodicWork(
            CLEANUP,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<CleanupWorker>(7, TimeUnit.DAYS)
                .build(),
        )
    }

    /** First-run and "resync now" from settings. REPLACE so the user sees it happen. */
    override fun syncNow() {
        workManager.enqueueUniqueWork(
            CATALOG_SYNC_NOW,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<CatalogSyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build(),
        )
    }

    /** True while a catalog sync is running, for the first-run progress state. */
    override val isSyncing: Flow<Boolean> = workManager
        .getWorkInfosForUniqueWorkFlow(CATALOG_SYNC_NOW)
        .map { infos -> infos.any { it.state == WorkInfo.State.RUNNING } }

    private companion object {
        const val CATALOG_SYNC = "catalog-sync"
        const val CATALOG_SYNC_NOW = "catalog-sync-now"
        const val HEALTH_SWEEP = "health-sweep"
        const val FAVORITES_HEALTH = "favorites-health"
        const val YOUTUBE_LIVE = "youtube-live"
        const val CLEANUP = "cleanup"
    }
}
