package com.worldtv.data.sync

import com.worldtv.core.common.di.IoDispatcher
import com.worldtv.core.model.TimeProvider
import com.worldtv.core.model.YouTubeLive
import com.worldtv.core.network.api.YouTubeApi
import com.worldtv.core.network.model.ApiYouTubeSearchItem
import com.worldtv.data.repository.YouTubeRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Refreshes live broadcasts for the curated YouTube channels.
 *
 * Budgeted rather than exhaustive. `search.list` costs 100 quota units and the default
 * daily allowance is 10,000, so this refuses to spend more than [MAX_CALLS_PER_RUN]
 * per run and gives up quietly when the API starts refusing — a burned quota means no
 * YouTube for the rest of the day, which is worse than a slightly stale list.
 */
@Singleton
class YouTubeSynchronizer @Inject constructor(
    private val api: YouTubeApi,
    private val repository: YouTubeRepository,
    private val apiKeyProvider: YouTubeApiKeyProvider,
    private val time: TimeProvider,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    data class Result(val channelsPolled: Int, val liveFound: Int, val quotaExhausted: Boolean)

    suspend fun sync(): Result = withContext(io) {
        val apiKey = apiKeyProvider.apiKey()
            // No key configured is a normal state, not an error: the app ships without
            // one and YouTube mode simply stays empty until the user adds theirs.
            ?: return@withContext Result(0, 0, quotaExhausted = false)

        repository.purgeExpired()

        val now = time.nowMillis()
        val expiresAt = now + FRESHNESS.inWholeMilliseconds
        var polled = 0
        var found = 0

        for (source in repository.curatedSources().take(MAX_CALLS_PER_RUN)) {
            val response = runCatching {
                api.liveBroadcasts(apiKey = apiKey, channelId = source.channelId)
            }.getOrElse { error ->
                // 403 here is almost always quota exhaustion. Continuing would burn
                // nothing but would also achieve nothing, so stop the run.
                return@withContext Result(polled, found, quotaExhausted = true)
            }

            polled++
            val live = response.items.mapNotNull { item ->
                item.toLive(channelTitle = source.title, fetchedAt = now, expiresAt = expiresAt)
            }
            repository.replaceChannelResults(source.channelId, live)
            found += live.size
        }

        Result(polled, found, quotaExhausted = false)
    }

    private fun ApiYouTubeSearchItem.toLive(
        channelTitle: String,
        fetchedAt: Long,
        expiresAt: Long,
    ): YouTubeLive? {
        val videoId = id.videoId ?: return null
        // The API occasionally returns upcoming or ended broadcasts even with
        // eventType=live; only "live" is actually playable right now.
        if (snippet.liveBroadcastContent.isNotEmpty() &&
            snippet.liveBroadcastContent != "live"
        ) {
            return null
        }
        return YouTubeLive(
            videoId = videoId,
            channelId = snippet.channelId.ifBlank { return null },
            channelTitle = snippet.channelTitle.ifBlank { channelTitle },
            title = snippet.title,
            thumbnailUrl = snippet.thumbnails.best,
            fetchedAt = fetchedAt,
            expiresAt = expiresAt,
        )
    }

    private companion object {
        /**
         * How long a discovered broadcast is trusted.
         *
         * Matches the worker's period: a stream that ended right after a refresh is
         * shown as live for at most one cycle, and the player's own error handling
         * covers that window.
         */
        val FRESHNESS = 6.hours

        /**
         * 16 calls is 1,600 quota units per run; at four runs a day that is 6,400 of
         * the 10,000 daily budget, leaving headroom for manual refreshes.
         */
        const val MAX_CALLS_PER_RUN = 16
    }
}
