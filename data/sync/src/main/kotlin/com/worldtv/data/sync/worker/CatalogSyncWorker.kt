package com.worldtv.data.sync.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.worldtv.data.repository.UserPreferencesRepository
import com.worldtv.data.sync.CatalogSynchronizer
import com.worldtv.data.sync.RadioSynchronizer
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/** Refreshes the iptv-org and Radio Browser catalogs. Daily, ETag-conditional. */
@HiltWorker
class CatalogSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val catalogSynchronizer: CatalogSynchronizer,
    private val radioSynchronizer: RadioSynchronizer,
    private val preferences: UserPreferencesRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val result = runCatching { catalogSynchronizer.sync() }
            .getOrElse { return Result.retry() }

        val homeCountry = preferences.preferences.first().homeCountry
        runCatching {
            radioSynchronizer.sync(listOfNotNull(homeCountry) + DEFAULT_RADIO_COUNTRIES)
        }

        return when {
            result.failures.isEmpty() -> Result.success()
            // Partial success still leaves a usable catalog; retry the rest on the
            // exponential schedule rather than failing outright.
            result.didAnything || result.skippedUnchanged > 0 -> Result.success()
            else -> Result.retry()
        }
    }

    private companion object {
        /**
         * Seeded so the radio mode is not empty before the user picks a home country.
         * Widened by the user's own selection on every later run.
         */
        val DEFAULT_RADIO_COUNTRIES = listOf("TR", "US", "GB", "DE", "FR")
    }
}
