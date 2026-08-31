package com.worldtv.data.sync.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.worldtv.core.database.dao.UserDataDao
import com.worldtv.data.sync.epg.EpgSynchronizer
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Refreshes XMLTV guide data.
 *
 * Twice a day rather than hourly: guides are published on a slow cadence and each run
 * moves tens of megabytes. Favourites steer which sources are fetched first, so the
 * channels the user actually watches get a guide within the first run or two.
 */
@HiltWorker
class EpgSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val synchronizer: EpgSynchronizer,
    private val userDataDao: UserDataDao,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val favourites = runCatching { userDataDao.favoriteIds("channel").first().toSet() }
            .getOrDefault(emptySet())

        return runCatching { synchronizer.sync(favourites) }
            .fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
    }
}
