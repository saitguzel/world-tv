package com.worldtv.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.worldtv.core.common.di.IoDispatcher
import com.worldtv.core.database.dao.ChannelDao
import com.worldtv.core.database.dao.ChannelWithHealth
import com.worldtv.core.database.dao.StreamDao
import com.worldtv.core.database.dao.UserDataDao
import com.worldtv.core.database.entity.StreamEntity
import com.worldtv.core.database.entity.toModel
import com.worldtv.core.model.Channel
import com.worldtv.core.model.ChannelSummary
import com.worldtv.core.model.Category
import com.worldtv.core.model.Country
import com.worldtv.core.model.Stream
import com.worldtv.core.model.StreamKind
import com.worldtv.core.model.TextNormalizer
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class ChannelRepository @Inject constructor(
    private val channelDao: ChannelDao,
    private val streamDao: StreamDao,
    private val userDataDao: UserDataDao,
    private val preferences: UserPreferencesRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun channels(country: String?, category: String?): Flow<PagingData<ChannelSummary>> =
        preferences.preferences.flatMapLatest { prefs ->
            Pager(
                config = PagingConfig(
                    // A 5-wide TV grid shows ~15 cards; three screens of prefetch keeps
                    // fast D-pad scrolling from ever hitting a placeholder.
                    pageSize = 60,
                    prefetchDistance = 30,
                    enablePlaceholders = false,
                ),
                pagingSourceFactory = {
                    channelDao.channels(
                        country = country,
                        category = category,
                        showNsfw = prefs.showNsfw,
                        showGeoBlocked = prefs.showGeoBlocked,
                        showUnchecked = prefs.showUnchecked,
                    )
                },
            ).flow.map { paging -> paging.map(ChannelWithHealth::toSummary) }
        }

    fun favorites(): Flow<List<ChannelSummary>> =
        channelDao.favoriteChannels().map { rows -> rows.map(ChannelWithHealth::toSummary) }

    fun recents(limit: Int = 20): Flow<List<ChannelSummary>> =
        channelDao.recentChannels(limit).map { rows -> rows.map(ChannelWithHealth::toSummary) }

    fun categories(): Flow<List<Category>> = userDataDao.categories().map { rows ->
        rows.map { Category(id = it.id, name = it.name) }
    }

    fun countries(): Flow<List<Country>> = channelDao.countriesWithCounts().map { rows ->
        rows.map { row ->
            Country(
                code = row.code,
                name = row.name,
                flag = row.flag,
                languages = row.languages.split(',').filter { it.isNotBlank() },
                channelCount = row.channelCount,
            )
        }
    }

    /**
     * Search over the folded haystack.
     *
     * The query is normalised with the same function used at write time — otherwise a
     * user typing "türk" would never match the stored "turk".
     */
    fun search(query: String, limit: Int = 60): Flow<List<ChannelSummary>> {
        val normalized = TextNormalizer.normalize(query)
        return combine(
            preferences.preferences,
            channelDao.search(normalized, showNsfw = false, limit = limit),
        ) { prefs, rows ->
            if (prefs.showNsfw) rows else rows.filterNot { it.isNsfw }
        }.map { rows -> rows.map(ChannelWithHealth::toSummary) }
    }

    /**
     * Playable streams for a channel, best first.
     *
     * The player walks this list on failure, so ordering here is what turns
     * "the channel is broken" into "the first URL was broken".
     */
    suspend fun streamsFor(channelId: String): List<Stream> = withContext(io) {
        streamDao.playableStreams(channelId).map(StreamEntity::toModel)
    }

    /**
     * Summaries for the given ids, returned in the order requested rather than the
     * order SQLite happened to produce.
     */
    fun summaries(ids: List<String>): Flow<List<ChannelSummary>> {
        if (ids.isEmpty()) return flowOf(emptyList())
        return channelDao.summariesByIds(ids).map { rows ->
            val byId = rows.associateBy { it.id }
            ids.mapNotNull { id -> byId[id]?.toSummary() }
        }
    }

    suspend fun channel(id: String): Channel? = withContext(io) {
        channelDao.channelById(id)?.let { entity ->
            Channel(
                id = entity.id,
                name = entity.name,
                country = entity.country,
                categories = entity.categories.split(',').filter { it.isNotBlank() },
                logoUrl = entity.logoUrl,
                isNsfw = entity.isNsfw,
                isClosed = entity.isClosed,
                replacedBy = entity.replacedBy,
            )
        }
    }
}

internal fun ChannelWithHealth.toSummary(): ChannelSummary = ChannelSummary(
    channel = Channel(
        id = id,
        name = name,
        country = country,
        categories = categories.split(',').filter { it.isNotBlank() },
        logoUrl = logoUrl,
        isNsfw = isNsfw,
        isClosed = isClosed,
        replacedBy = replacedBy,
    ),
    availableStreams = availableStreams,
    verifiedStreams = verifiedStreams,
    bestLatencyMs = bestLatencyMs,
    isFavorite = isFavorite,
    geoBlockedOnly = availableStreams > 0 && geoBlockedStreams == availableStreams,
)

internal fun StreamEntity.toModel(): Stream = Stream(
    id = id,
    channelId = channelId,
    url = url,
    title = title,
    quality = quality,
    referrer = referrer,
    userAgent = userAgent,
    label = label,
    kind = runCatching { StreamKind.valueOf(kind) }.getOrDefault(StreamKind.UNKNOWN_HTTP),
    health = health.toModel(),
)
