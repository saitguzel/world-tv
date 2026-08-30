package com.worldtv.data.sync.epg

import com.worldtv.core.common.di.IoDispatcher
import com.worldtv.core.database.dao.ChannelDao
import com.worldtv.core.database.dao.EpgDao
import com.worldtv.core.database.entity.EpgSourceEntity
import com.worldtv.core.model.Programme
import com.worldtv.core.model.TimeProvider
import com.worldtv.core.network.CatalogDownloader
import com.worldtv.core.network.CatalogFetch
import com.worldtv.core.network.api.IptvOrgApi
import com.worldtv.core.network.di.ApiClient
import com.worldtv.core.network.model.ApiGuide
import com.worldtv.data.repository.toEntity
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Downloads and imports XMLTV guides.
 *
 * Two things keep this from being ruinous on a TV box:
 *
 *  1. **Per-source, not per-channel.** One XMLTV file usually covers a whole country,
 *     so fetching by URL turns two hundred downloads into one.
 *  2. **Bounded scope.** Only guides for channels the catalog actually carries, only
 *     [MAX_SOURCES_PER_RUN] sources per run, and only programmes inside the retention
 *     window. A national guide is tens of megabytes and a fortnight deep; storing all
 *     of it for every country would dwarf the rest of the database several times over.
 */
@Singleton
class EpgSynchronizer @Inject constructor(
    private val api: IptvOrgApi,
    @ApiClient private val client: OkHttpClient,
    private val downloader: CatalogDownloader,
    private val epgDao: EpgDao,
    private val channelDao: ChannelDao,
    private val parser: XmltvParser,
    private val time: TimeProvider,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    data class Result(val sources: Int, val programmes: Int, val skipped: Int)

    /** Refreshes the source list from `guides.json`. Cheap; the file is small. */
    suspend fun syncSources(): Int = withContext(io) {
        var written = 0
        downloader.parse<ApiGuide>(api.guides(null)) { fetch ->
            if (fetch is CatalogFetch.Changed) {
                val sources = fetch.items
                    .filter { it.url.isNotBlank() && it.channel.isNotBlank() }
                    .map { guide ->
                        EpgSourceEntity(
                            // Composite id so one channel can have guides from several
                            // sites without them overwriting each other.
                            id = "${guide.channel}|${guide.url}",
                            channelId = guide.channel,
                            url = guide.url,
                            language = guide.lang,
                            lastFetchedAt = 0L,
                        )
                    }
                    .chunked(CatalogDownloader.BATCH_SIZE)

                for (batch in sources) {
                    epgDao.upsertSources(batch)
                    written += batch.size
                }
            }
        }
        written
    }

    /**
     * Fetches guide data.
     *
     * @param favouriteChannelIds channels to prioritise. The guide budget is small
     *   relative to the catalog, so it is spent on what the user actually watches.
     */
    suspend fun sync(favouriteChannelIds: Set<String> = emptySet()): Result = withContext(io) {
        val now = time.nowMillis()

        // Yesterday's schedule has no use, and programmes are by far the fastest-
        // growing table in the app.
        epgDao.purgeEnded(now - PAST_RETENTION.inWholeMilliseconds)

        if (epgDao.sourceCount() == 0) syncSources()

        val horizon = now + FUTURE_HORIZON.inWholeMilliseconds
        val urls = epgDao.distinctSourceUrls()
        val prioritised = urls.sortedByDescending { url ->
            epgDao.channelsForSource(url).count { it in favouriteChannelIds }
        }

        var sourcesDone = 0
        var programmesWritten = 0
        var skipped = 0

        for (url in prioritised.take(MAX_SOURCES_PER_RUN)) {
            val wanted = epgDao.channelsForSource(url).toSet()
            if (wanted.isEmpty()) continue

            val stats = runCatching { importSource(url, wanted, now, horizon) }
                .getOrElse { continue }

            sourcesDone++
            programmesWritten += stats.parsed
            skipped += stats.skipped
            epgDao.markSourceFetched(url, now)
        }

        Result(sourcesDone, programmesWritten, skipped)
    }

    private suspend fun importSource(
        url: String,
        wantedChannels: Set<String>,
        now: Long,
        horizon: Long,
    ): XmltvParser.Stats {
        val request = Request.Builder()
            .url(url)
            .header("Accept-Encoding", "gzip")
            .build()

        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return XmltvParser.Stats(0, 0)

            // Many guide hosts serve `.xml.gz` with no Content-Encoding header, so
            // OkHttp's transparent gzip does not kick in and the stream has to be
            // unwrapped here.
            val raw = response.body.byteStream()
            val stream = if (url.endsWith(".gz", ignoreCase = true)) {
                GZIPInputStream(raw)
            } else {
                raw
            }

            val batch = ArrayList<Programme>(BATCH_SIZE)
            val stats = parser.parse(
                input = stream,
                channelIdFilter = { it in wantedChannels },
            ) { programme ->
                // Drop anything already over or beyond the horizon before it reaches
                // the database — filtering after insert would mean writing it first.
                if (programme.endAt >= now && programme.startAt <= horizon) {
                    batch += programme
                    if (batch.size >= BATCH_SIZE) {
                        flushBlocking(batch)
                        batch.clear()
                    }
                }
            }
            if (batch.isNotEmpty()) flushBlocking(batch)
            stats
        }
    }

    /**
     * Writes a batch from inside the parser's callback.
     *
     * The SAX handler is not a coroutine, so the suspending DAO call is bridged with
     * `runBlocking`. Safe here: this whole function already runs on the IO dispatcher,
     * so the block is on a thread that is allowed to wait.
     */
    private fun flushBlocking(batch: List<Programme>) {
        kotlinx.coroutines.runBlocking {
            epgDao.upsertAll(batch.map(Programme::toEntity))
        }
    }

    private companion object {
        /** Kept so "what was on earlier" still resolves right after midnight. */
        val PAST_RETENTION = 1.days

        /** Two days ahead covers now/next and a browsable evening without the bulk. */
        val FUTURE_HORIZON = 2.days

        /**
         * Guide files are tens of megabytes. Six per run, prioritised by favourites,
         * fills in over a few cycles rather than saturating the link at once.
         */
        const val MAX_SOURCES_PER_RUN = 6
        const val BATCH_SIZE = 500
    }
}
