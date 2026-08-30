package com.worldtv.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.worldtv.core.common.di.IoDispatcher
import com.worldtv.core.database.dao.RadioDao
import com.worldtv.core.database.entity.RadioStationEntity
import com.worldtv.core.database.entity.toModel
import com.worldtv.core.model.RadioStation
import com.worldtv.core.model.TextNormalizer
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class RadioRepository @Inject constructor(
    private val dao: RadioDao,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    fun stations(country: String?, tag: String? = null): Flow<PagingData<RadioStation>> = Pager(
        config = PagingConfig(pageSize = 60, prefetchDistance = 30, enablePlaceholders = false),
        pagingSourceFactory = { dao.stations(country, tag) },
    ).flow.map { paging -> paging.map(RadioStationEntity::toModel) }

    fun favorites(): Flow<List<RadioStation>> =
        dao.favoriteStations().map { list -> list.map(RadioStationEntity::toModel) }

    fun search(query: String, limit: Int = 60): Flow<List<RadioStation>> =
        dao.search(TextNormalizer.normalize(query), limit)
            .map { list -> list.map(RadioStationEntity::toModel) }

    fun availableCountries(): Flow<List<String>> = dao.availableCountries()

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
