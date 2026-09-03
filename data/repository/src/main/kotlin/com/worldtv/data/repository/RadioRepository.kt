package com.worldtv.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.worldtv.core.common.di.IoDispatcher
import com.worldtv.core.database.dao.RadioDao
import com.worldtv.core.database.entity.RadioStationEntity
import com.worldtv.core.database.entity.toModel
import com.worldtv.core.model.Category
import com.worldtv.core.model.RadioStation
import com.worldtv.core.model.TextNormalizer
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest

@Singleton
class RadioRepository @Inject constructor(
    private val dao: RadioDao,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    fun stations(country: String?, tag: String? = null): Flow<PagingData<RadioStation>> = Pager(
        config = PagingConfig(pageSize = 60, prefetchDistance = 30, enablePlaceholders = false),
        pagingSourceFactory = { dao.stations(country, tag) },
    ).flow.map { paging -> paging.map(RadioStationEntity::toModel) }

    /**
     * Radio Browser carries no category table of its own — stations tag themselves —
     * so the categories are the station tags, aggregated and ranked by how many
     * stations actually use each one.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun categories(): Flow<List<Category>> = dao.availableCountries()
        // Recomputed when the set of countries changes — in practice, when a catalog
        // sync lands — rather than on every write to the table. The health engine
        // touches station rows constantly, and splitting tens of thousands of tag cells
        // after each probe is real work on the boxes this app targets.
        .map { it.size }
        .distinctUntilChanged()
        .mapLatest { RadioCategories.aggregate(dao.tagsByStation()) }
        .flowOn(io)

    fun favorites(): Flow<List<RadioStation>> =
        dao.favoriteStations().map { list -> list.map(RadioStationEntity::toModel) }

    fun search(query: String, country: String? = null, limit: Int = 60): Flow<List<RadioStation>> =
        dao.search(TextNormalizer.normalize(query), country, limit)
            .map { list -> list.map(RadioStationEntity::toModel) }

    /** Stations the user has actually listened to, newest first. */
    fun recents(limit: Int = 12): Flow<List<RadioStation>> =
        dao.recentStations(limit).map { list -> list.map(RadioStationEntity::toModel) }

    /**
     * The most-played stations, optionally narrowed to one country.
     *
     * Radio Browser's click count is the only popularity signal in the catalog, and it
     * is the same ordering the full list uses — this is its head, for a home shelf.
     */
    fun popular(country: String? = null, limit: Int = 12): Flow<List<RadioStation>> =
        dao.popularStations(country, limit).map { list -> list.map(RadioStationEntity::toModel) }

    /** Whether any stations have been downloaded; drives the home screen's empty state. */
    fun stationCount(): Flow<Int> = dao.countFlow()

    fun availableCountries(): Flow<List<String>> = dao.availableCountries()

    /** One station at random from the current country/tag filter, if any exists. */
    suspend fun randomStation(country: String?, tag: String?): RadioStation? =
        withContext(io) { dao.randomStation(country, tag)?.toModel() }

    suspend fun station(uuid: String): RadioStation? =
        withContext(io) { dao.byId(uuid)?.toModel() }
}

internal fun RadioStationEntity.toModel(): RadioStation = RadioStation(
    uuid = uuid,
    name = name,
    url = url,
    faviconUrl = faviconUrl,
    tags = tags.split(',').filter { it.isNotBlank() },
    countryCode = countryCode,
    language = language,
    codec = codec,
    bitrate = bitrate,
    serverSideOk = serverSideOk,
    clickCount = clickCount,
    votes = votes,
    health = health.toModel(),
)
