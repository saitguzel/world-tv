package com.worldtv.data.repository

import com.worldtv.core.common.di.IoDispatcher
import com.worldtv.core.database.dao.YouTubeDao
import com.worldtv.core.database.entity.YouTubeStreamEntity
import com.worldtv.core.model.TimeProvider
import com.worldtv.core.model.YouTubeLive
import com.worldtv.core.model.YouTubeSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class YouTubeRepository @Inject constructor(
    private val dao: YouTubeDao,
    private val time: TimeProvider,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    /**
     * Broadcasts currently believed live.
     *
     * `now` is captured per collection rather than per emission, so the list does not
     * silently reshuffle while the user is looking at it — expiry is applied at the
     * next refresh instead.
     */
    fun liveNow(): Flow<List<YouTubeLive>> =
        dao.liveNow(time.nowMillis()).map { rows -> rows.map(YouTubeStreamEntity::toModel) }

    suspend fun byId(videoId: String): YouTubeLive? =
        withContext(io) { dao.byId(videoId)?.toModel() }

    suspend fun liveCount(): Int = withContext(io) { dao.liveCount(time.nowMillis()) }

    /** Replaces one source's results wholesale — see [YouTubeDao.clearChannel]. */
    suspend fun replaceChannelResults(channelId: String, live: List<YouTubeLive>) =
        withContext(io) {
            dao.clearChannel(channelId)
            if (live.isNotEmpty()) dao.upsertAll(live.map(YouTubeLive::toEntity))
        }

    suspend fun purgeExpired(): Int = withContext(io) { dao.purgeExpired(time.nowMillis()) }

    /**
     * The curated channel list.
     *
     * Deliberately small and in code rather than user-editable: every entry costs 100
     * quota units per refresh, and twenty channels on a six-hour cycle is already 8,000
     * of the 10,000 daily units. Growing this list past that needs the server-side
     * proxy described in [com.worldtv.core.network.api.YouTubeApi], not more entries.
     */
    fun curatedSources(): List<YouTubeSource> = CURATED_SOURCES

    private companion object {
        val CURATED_SOURCES = listOf(
            YouTubeSource("UC16niRr50-MSBwiO3YDb3RA", "BBC News", "news"),
            YouTubeSource("UCupvZG-5ko_eiXAupbDfxWw", "CNN", "news"),
            YouTubeSource("UCknLrEdhRCp1aegoMqRaCZg", "DW News", "news"),
            YouTubeSource("UC7fWeaHhqgM4Ry-RMpM2YYw", "TRT World", "news"),
            YouTubeSource("UCNye-wNBqNL5ZzHSJj3l8Bg", "Al Jazeera English", "news"),
            YouTubeSource("UCYfdidRxbB8Qhf0Nx7ioOYw", "Euronews", "news"),
            YouTubeSource("UCoMdktPbSTixAyNGwb-UYkQ", "Sky News", "news"),
            YouTubeSource("UCvJJ_dzjViJCoLf5uKUTwoA", "CNBC", "business"),
            YouTubeSource("UCIALMKvObZNtJ6AmdCLP7Lg", "Bloomberg", "business"),
            YouTubeSource("UCUMZ7gohGI9HcU9VNsr2FJQ", "Bloomberg Quicktake", "business"),
            YouTubeSource("UC4R8DWoMoI7CAwX8_LjQHig", "NASA", "science"),
            YouTubeSource("UCEWpbFLzoYGPfuWUMFPSaoA", "The Verge", "tech"),
            YouTubeSource("UCwmZiChSryoWQCZMIQezgTg", "BBC Earth", "nature"),
            YouTubeSource("UC0k238zFx-Z8xFH0sxCrPJg", "Nature Relaxation", "nature"),
            YouTubeSource("UCFhXFikryT4aFcLkLw2LBLA", "NPR Music", "music"),
            YouTubeSource("UCHnyfMqiRRG1u-2MsSQLbXA", "Veritasium", "science"),
        )
    }
}

internal fun YouTubeStreamEntity.toModel(): YouTubeLive = YouTubeLive(
    videoId = videoId,
    channelId = channelId,
    channelTitle = channelTitle,
    title = title,
    thumbnailUrl = thumbnailUrl,
    fetchedAt = fetchedAt,
    expiresAt = expiresAt,
)

internal fun YouTubeLive.toEntity(): YouTubeStreamEntity = YouTubeStreamEntity(
    videoId = videoId,
    channelId = channelId,
    channelTitle = channelTitle,
    title = title,
    thumbnailUrl = thumbnailUrl,
    fetchedAt = fetchedAt,
    expiresAt = expiresAt,
)
