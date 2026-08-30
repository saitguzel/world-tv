package com.worldtv.data.sync.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.worldtv.data.health.CheckPriority
import com.worldtv.data.repository.HealthRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlin.time.Duration.Companion.minutes

/**
 * The periodic background sweep.
 *
 * Budgets itself to [SWEEP_BUDGET] rather than running until the queue drains:
 * WorkManager kills a worker at ten minutes, and a killed worker loses the batch it
 * was mid-way through. Finishing early and resuming next period is strictly better,
 * and the priority order means the streams the user will actually open are done first.
 */
@HiltWorker
class HealthSweepWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val healthRepository: HealthRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val checked = runCatching {
            healthRepository.sweep(
                budgetMillis = SWEEP_BUDGET.inWholeMilliseconds,
                priorities = listOf(
                    CheckPriority.FAVORITES,
                    CheckPriority.RECENTS,
                    CheckPriority.HOME_COUNTRY,
                    CheckPriority.EVERYTHING_ELSE,
                ),
            )
        }.getOrElse { return Result.retry() }

        return Result.success(
            androidx.work.Data.Builder().putInt(KEY_CHECKED, checked).build(),
        )
    }

    companion object {
        const val KEY_CHECKED = "checked"
        private val SWEEP_BUDGET = 8.minutes
    }
}

/** Favourites only, hourly, always with tier 2 — the list the user notices breaking. */
@HiltWorker
class FavoritesHealthWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val healthRepository: HealthRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result =
        runCatching { healthRepository.refreshFavorites() }
            .fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
}
