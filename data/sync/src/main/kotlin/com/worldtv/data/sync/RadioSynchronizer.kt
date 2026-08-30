package com.worldtv.data.sync

import com.worldtv.core.common.di.IoDispatcher
import com.worldtv.core.database.dao.RadioDao
import com.worldtv.core.database.entity.RadioStationEntity
import com.worldtv.core.model.TextNormalizer
import com.worldtv.core.model.TimeProvider
import com.worldtv.core.network.api.RadioBrowserApi
import com.worldtv.core.network.model.ApiRadioStation
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Pulls Radio Browser stations for the countries the user cares about.
 *
 * Radio Browser asks clients to discover a mirror and spread load themselves, so a
 * server is picked at random per sync rather than pinning one.
 */
@Singleton
class RadioSynchronizer @Inject constructor(
    private val api: RadioBrowserApi,
    private val dao: RadioDao,
    private val time: TimeProvider,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    @Volatile
    private var cachedServers: List<String> = emptyList()

    suspend fun sync(countryCodes: List<String>): Int = withContext(io) {
        val server = pickServer() ?: return@withContext 0
        val syncedAt = time.nowMillis()
        var written = 0

        for (code in countryCodes.distinct()) {
            val stations = runCatching {
                api.stationsByCountry(server = server, countryCode = code)
            }.getOrElse { continue }

            val entities = stations
                // A station with no resolvable URL cannot be played, so it is not
                // worth a row or a health-check slot.
                .filter { it.playbackUrl.isNotBlank() }
                .map { it.toEntity(syncedAt) }

            if (entities.isNotEmpty()) {
                upsertPreservingHealth(entities, syncedAt)
                written += entities.size
            }
        }
        written
    }

    /** Same insert-then-patch shape as the TV catalog: health survives a resync. */
    private suspend fun upsertPreservingHealth(entities: List<RadioStationEntity>, syncedAt: Long) {
        val results = dao.insertIgnoringExisting(entities)
        entities.forEachIndexed { index, entity ->
            if (results.getOrNull(index) == -1L) {
                dao.updateCatalogFields(
                    uuid = entity.uuid,
                    name = entity.name,
                    url = entity.url,
                    faviconUrl = entity.faviconUrl,
                    tags = entity.tags,
                    countryCode = entity.countryCode,
                    language = entity.language,
                    codec = entity.codec,
                    bitrate = entity.bitrate,
                    serverSideOk = entity.serverSideOk,
                    clickCount = entity.clickCount,
                    votes = entity.votes,
                    searchText = entity.searchText,
                    updatedAt = syncedAt,
                )
            }
        }
    }

    private suspend fun pickServer(): String? {
        if (cachedServers.isEmpty()) {
            cachedServers = runCatching { api.servers(RadioBrowserApi.DISCOVERY_URL) }
                .getOrDefault(emptyList())
                .mapNotNull { it.name.takeIf(String::isNotBlank) }
                .map { "https://$it" }
        }
        return cachedServers.randomOrNull()
    }

    private fun ApiRadioStation.toEntity(syncedAt: Long) = RadioStationEntity(
        uuid = stationuuid,
        name = name,
        // url_resolved, never url: redirects are already followed server-side.
        url = playbackUrl,
        faviconUrl = favicon.takeIf(String::isNotBlank),
        tags = tags,
        countryCode = countrycode,
        language = language.takeIf(String::isNotBlank),
        codec = codec.takeIf(String::isNotBlank),
        bitrate = bitrate,
        serverSideOk = lastcheckok == 1,
        clickCount = clickcount,
        votes = votes,
        searchText = TextNormalizer.searchText(name, emptyList(), stationuuid),
        updatedAt = syncedAt,
    )
}
