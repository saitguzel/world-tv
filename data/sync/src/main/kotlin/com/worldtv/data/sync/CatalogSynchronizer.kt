package com.worldtv.data.sync

import com.worldtv.core.common.di.IoDispatcher
import com.worldtv.core.database.StreamIdFactory
import com.worldtv.core.database.dao.ChannelDao
import com.worldtv.core.database.dao.StreamDao
import com.worldtv.core.database.dao.UserDataDao
import com.worldtv.core.database.entity.BlocklistEntity
import com.worldtv.core.database.entity.CategoryEntity
import com.worldtv.core.database.entity.ChannelEntity
import com.worldtv.core.database.entity.CountryEntity
import com.worldtv.core.database.entity.StreamEntity
import com.worldtv.core.database.entity.SyncStateEntity
import com.worldtv.core.model.TextNormalizer
import com.worldtv.core.model.TimeProvider
import com.worldtv.core.network.CatalogDownloader
import com.worldtv.core.network.CatalogFetch
import com.worldtv.core.network.api.IptvOrgApi
import com.worldtv.core.network.model.ApiBlocklistEntry
import com.worldtv.core.network.model.ApiCategory
import com.worldtv.core.network.model.ApiChannel
import com.worldtv.core.network.model.ApiCountry
import com.worldtv.core.network.model.ApiLogo
import com.worldtv.core.network.model.ApiStream
import com.worldtv.data.health.StreamKindDetector
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Pulls the iptv-org catalog into Room.
 *
 * Two properties matter more than speed here:
 *
 *  1. **Bounded memory.** The files total ~20 MB of JSON. Every one is consumed as a
 *     lazy sequence and written in [CatalogDownloader.BATCH_SIZE] batches, so peak
 *     heap is one batch rather than the whole array.
 *  2. **Health is never reset.** A resync refreshes catalog columns through targeted
 *     updates and only inserts rows that are genuinely new. Replacing rows wholesale
 *     would throw away everything the app has learned about which streams work — the
 *     one asset it cannot re-download.
 */
@Singleton
class CatalogSynchronizer @Inject constructor(
    private val api: IptvOrgApi,
    private val downloader: CatalogDownloader,
    private val channelDao: ChannelDao,
    private val streamDao: StreamDao,
    private val userDataDao: UserDataDao,
    private val time: TimeProvider,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    data class Result(
        val channels: Int = 0,
        val streams: Int = 0,
        val skippedUnchanged: Int = 0,
        val failures: List<String> = emptyList(),
    ) {
        val didAnything: Boolean get() = channels > 0 || streams > 0
    }

    suspend fun sync(): Result = withContext(io) {
        val startedAt = time.nowMillis()
        val failures = mutableListOf<String>()
        var skipped = 0

        // Reference data first: countries and categories are tiny and the channel
        // rows are more useful once they can be grouped.
        runCatching { syncCountries() }.onFailure { failures += "countries: ${it.message}" }
        runCatching { syncCategories() }.onFailure { failures += "categories: ${it.message}" }

        // The block list gates everything the user can see, so a failure here is not
        // recoverable by carrying on — better to keep the previous list than to risk
        // listing a DMCA-removed channel.
        runCatching { syncBlocklist() }.onFailure { failures += "blocklist: ${it.message}" }

        val logos = runCatching { fetchLogos() }
            .onFailure { failures += "logos: ${it.message}" }
            .getOrDefault(emptyMap())

        val channelCount = runCatching { syncChannels(logos, startedAt) }
            .onFailure { failures += "channels: ${it.message}" }
            .getOrElse { -1 }
        if (channelCount == SKIPPED) skipped++

        val streamCount = runCatching { syncStreams(startedAt) }
            .onFailure { failures += "streams: ${it.message}" }
            .getOrElse { -1 }
        if (streamCount == SKIPPED) skipped++

        Result(
            channels = channelCount.coerceAtLeast(0),
            streams = streamCount.coerceAtLeast(0),
            skippedUnchanged = skipped,
            failures = failures,
        )
    }

    private suspend fun syncChannels(logos: Map<String, String>, syncedAt: Long): Int {
        val previous = userDataDao.syncState(RESOURCE_CHANNELS)
        var written = 0
        var result = SKIPPED

        downloader.parse<ApiChannel>(api.channels(previous?.etag)) { fetch ->
            when (fetch) {
                is CatalogFetch.NotModified -> result = SKIPPED
                is CatalogFetch.Failed -> error("HTTP ${fetch.code} ${fetch.message}")
                is CatalogFetch.Changed -> {
                    val batch = ArrayList<ChannelEntity>(CatalogDownloader.BATCH_SIZE)
                    for (channel in fetch.items) {
                        // A closed channel is kept in the table but flagged, so a
                        // favourite pointing at it can still explain itself.
                        batch += ChannelEntity(
                            id = channel.id,
                            name = channel.name,
                            country = channel.country,
                            categories = channel.categories.joinToString(","),
                            logoUrl = logos[channel.id],
                            isNsfw = channel.isNsfw,
                            isClosed = channel.closed != null,
                            replacedBy = channel.replacedBy,
                            searchText = TextNormalizer.searchText(
                                name = channel.name,
                                altNames = channel.altNames,
                                channelId = channel.id,
                            ),
                            updatedAt = syncedAt,
                        )
                        if (batch.size >= CatalogDownloader.BATCH_SIZE) {
                            channelDao.upsertAll(batch)
                            written += batch.size
                            batch.clear()
                        }
                    }
                    if (batch.isNotEmpty()) {
                        channelDao.upsertAll(batch)
                        written += batch.size
                    }
                    recordSyncState(RESOURCE_CHANNELS, fetch, syncedAt, written)
                    result = written
                }
            }
        }
        return result
    }

    private suspend fun syncStreams(syncedAt: Long): Int {
        val previous = userDataDao.syncState(RESOURCE_STREAMS)
        var seen = 0
        var result = SKIPPED

        downloader.parse<ApiStream>(api.streams(previous?.etag)) { fetch ->
            when (fetch) {
                is CatalogFetch.NotModified -> result = SKIPPED
                is CatalogFetch.Failed -> error("HTTP ${fetch.code} ${fetch.message}")
                is CatalogFetch.Changed -> {
                    val batch = ArrayList<StreamEntity>(CatalogDownloader.BATCH_SIZE)
                    for (stream in fetch.items) {
                        if (stream.url.isBlank()) continue
                        batch += stream.toEntity(syncedAt)
                        if (batch.size >= CatalogDownloader.BATCH_SIZE) {
                            streamDao.upsertPreservingHealth(batch, syncedAt)
                            seen += batch.size
                            batch.clear()
                        }
                    }
                    if (batch.isNotEmpty()) {
                        streamDao.upsertPreservingHealth(batch, syncedAt)
                        seen += batch.size
                    }
                    recordSyncState(RESOURCE_STREAMS, fetch, syncedAt, seen)
                    result = seen
                }
            }
        }
        return result
    }

    private suspend fun fetchLogos(): Map<String, String> {
        val previous = userDataDao.syncState(RESOURCE_LOGOS)
        val logos = HashMap<String, String>(16_384)
        downloader.parse<ApiLogo>(api.logos(previous?.etag)) { fetch ->
            if (fetch is CatalogFetch.Changed) {
                // First logo per channel wins; the file is ordered with the primary
                // feed first and re-ranking would mean holding all of them.
                // getOrPut, not putIfAbsent: the latter is a Java 8 default method on
                // Map and only exists from API 24, four levels above this app's minimum.
                for (logo in fetch.items) logos.getOrPut(logo.channel) { logo.url }
                recordSyncState(RESOURCE_LOGOS, fetch, time.nowMillis(), logos.size)
            }
        }
        return logos
    }

    private suspend fun syncCountries() {
        downloader.parse<ApiCountry>(api.countries(null)) { fetch ->
            if (fetch is CatalogFetch.Changed) {
                userDataDao.upsertCountries(
                    fetch.items.map { country ->
                        CountryEntity(
                            code = country.code,
                            name = country.name,
                            flag = country.flag,
                            languages = country.languages.joinToString(","),
                        )
                    }.toList(),
                )
            }
        }
    }

    private suspend fun syncCategories() {
        downloader.parse<ApiCategory>(api.categories(null)) { fetch ->
            if (fetch is CatalogFetch.Changed) {
                userDataDao.upsertCategories(
                    fetch.items.map { CategoryEntity(id = it.id, name = it.name) }.toList(),
                )
            }
        }
    }

    /**
     * DMCA removals. Replaced wholesale rather than merged: an entry dropped upstream
     * means the claim was withdrawn, and keeping a stale one hides a legal channel.
     */
    private suspend fun syncBlocklist() {
        downloader.parse<ApiBlocklistEntry>(api.blocklist(null)) { fetch ->
            if (fetch is CatalogFetch.Changed) {
                val entries = fetch.items.map { entry ->
                    BlocklistEntity(
                        channelId = entry.channel,
                        reason = entry.reason,
                        reference = entry.ref,
                    )
                }.toList()
                userDataDao.clearBlocklist()
                userDataDao.upsertBlocklist(entries)
            }
        }
    }

    private suspend fun <T> recordSyncState(
        resource: String,
        fetch: CatalogFetch.Changed<T>,
        syncedAt: Long,
        itemCount: Int,
    ) {
        userDataDao.upsertSyncState(
            SyncStateEntity(
                resource = resource,
                etag = fetch.etag,
                lastModified = fetch.lastModified,
                lastSyncedAt = syncedAt,
                lastSuccessAt = syncedAt,
                itemCount = itemCount,
            ),
        )
    }

    private fun ApiStream.toEntity(syncedAt: Long) = StreamEntity(
        id = StreamIdFactory.idFor(url, channel),
        channelId = channel,
        url = url,
        title = title,
        quality = quality,
        referrer = referrer,
        userAgent = userAgent,
        label = label,
        // Classified once at sync time so the sweep does not re-parse 100k URLs.
        kind = StreamKindDetector.detect(url).name,
        updatedAt = syncedAt,
    )

    companion object {
        const val RESOURCE_CHANNELS = "channels"
        const val RESOURCE_STREAMS = "streams"
        const val RESOURCE_LOGOS = "logos"

        /** Sentinel for "server said 304, nothing to do". */
        const val SKIPPED = -2
    }
}
