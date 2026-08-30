package com.worldtv.data.sync.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.worldtv.data.sync.YouTubeSynchronizer
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Refreshes the curated YouTube channels' live broadcasts.
 *
 * Never retries on quota exhaustion: the quota resets on a daily boundary, so backing
 * off by minutes just burns the remainder of the budget on calls that will be refused.
 * The next scheduled run is the right time to try again.
 */
@HiltWorker
class YouTubeLiveWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val synchronizer: YouTubeSynchronizer,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val result = runCatching { synchronizer.sync() }.getOrElse { return Result.retry() }
        return if (result.quotaExhausted) Result.success() else Result.success()
    }
}
