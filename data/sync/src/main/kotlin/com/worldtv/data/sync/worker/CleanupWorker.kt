package com.worldtv.data.sync.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.worldtv.core.database.dao.StreamDao
import com.worldtv.core.model.TimeProvider
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlin.time.Duration.Companion.days

/**
 * Weekly housekeeping.
 *
 * The only rows removed are streams that have been DEAD and untouched for 90 days —
 * long past the point where the seven-day revive window would have brought them back.
 * Everything else is kept: a stream the app decided against is still evidence, and
 * re-downloading the catalog to rediscover it costs 20 MB.
 */
@HiltWorker
class CleanupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val streamDao: StreamDao,
    private val time: TimeProvider,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val cutoff = time.nowMillis() - ARCHIVE_AFTER.inWholeMilliseconds
        runCatching { streamDao.purgeLongDead(cutoff) }.getOrElse { return Result.retry() }
        return Result.success()
    }

    private companion object {
        val ARCHIVE_AFTER = 90.days
    }
}
