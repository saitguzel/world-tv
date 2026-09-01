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
import com.worldtv.data.sync.worker.EpgSyncWorker
import com.worldtv.data.sync.worker.FavoritesHealthWorker
import com.worldtv.data.sync.worker.HealthSweepWorker
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
            EPG_SYNC,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<EpgSyncWorker>(12, TimeUnit.HOURS)
                .setConstraints(networkOnly)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS)
                .build(),
        )

        workManager.enqueueUniquePeriodicWork(
            CLEANUP,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<CleanupWorker>(7, TimeUnit.DAYS)
                .build(),
        )
    }

    /**
     * First-run and "resync now" from settings. REPLACE so the user sees it happen.
     *
     * A chain rather than one job: the guide is useless before the channels exist, and
     * `EpgSyncWorker` reads the favourites, which are channel ids. Chaining is also what
     * makes the guide appear on a fresh install at all — the periodic EPG worker's first
     * run is scheduled somewhere inside its twelve-hour window, so without this the
     * now/next line stays empty for most of a day.
     */
    override fun syncNow() {
        val networkOnly = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        workManager
            .beginUniqueWork(
                CATALOG_SYNC_NOW,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<CatalogSyncWorker>()
                    .setConstraints(networkOnly)
                    // Tagged so the progress flag below can watch this step alone.
                    .addTag(CATALOG_STEP)
                    .build(),
            )
            .then(
                OneTimeWorkRequestBuilder<EpgSyncWorker>()
                    .setConstraints(networkOnly)
                    .build(),
            )
            .enqueue()
    }

    /**
     * True while the *catalog* step is running.
     *
     * Deliberately not the whole chain: the guide download that follows can take a
     * while, and a spinner labelled "fetching the catalog" that stays up through it is
     * telling the user something untrue.
     */
    override val isSyncing: Flow<Boolean> = workManager
        .getWorkInfosForUniqueWorkFlow(CATALOG_SYNC_NOW)
        .map { infos ->
            infos.any { it.state == WorkInfo.State.RUNNING && CATALOG_STEP in it.tags }
        }

    private companion object {
        const val CATALOG_SYNC = "catalog-sync"
        const val CATALOG_SYNC_NOW = "catalog-sync-now"
        const val CATALOG_STEP = "catalog-step"
        const val HEALTH_SWEEP = "health-sweep"
        const val FAVORITES_HEALTH = "favorites-health"
        const val EPG_SYNC = "epg-sync"
        const val CLEANUP = "cleanup"
    }
}
