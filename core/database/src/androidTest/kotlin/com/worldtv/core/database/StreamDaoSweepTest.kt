package com.worldtv.core.database

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.worldtv.core.database.dao.StreamDao
import com.worldtv.core.database.entity.ChannelEntity
import com.worldtv.core.database.entity.FavoriteEntity
import com.worldtv.core.database.entity.HealthColumns
import com.worldtv.core.database.entity.StreamEntity
import com.worldtv.core.model.StreamKind
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Guards the predicate that keeps the sweep moving.
 *
 * A `NON_HTTP` stream cannot be judged over HTTP, so its probe returns `Inconclusive`,
 * and an inconclusive probe writes nothing — not even `nextCheckAt`. Such a row stays
 * due forever, and because the sweep orders by `nextCheckAt ASC` and an unchecked row
 * carries 0, it sorts ahead of everything else. Before these queries excluded it, one
 * favourited RTSP stream re-served the same batch until the worker's eight-minute
 * budget ran out, and nothing else in the catalog was ever checked.
 */
@RunWith(AndroidJUnit4::class)
class StreamDaoSweepTest {

    private lateinit var database: WorldTvDatabase
    private lateinit var streams: StreamDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            WorldTvDatabase::class.java,
        ).build()
        streams = database.streamDao()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun sweepSkipsTransportsItCannotProbe() = runBlocking {
        streams.insertIgnoringExisting(
            listOf(
                stream("hls", "https://cdn.test/live.m3u8", StreamKind.HLS),
                stream("rtsp", "rtsp://cdn.test/live", StreamKind.NON_HTTP),
                stream("dash", "https://cdn.test/live.mpd", StreamKind.DASH),
            ),
        )

        val due = streams.dueForCheck(now = NOW, limit = 100)

        assertEquals(setOf("hls", "dash"), due.map { it.id }.toSet())
    }

    @Test
    fun everyPriorityBucketSkipsThem() = runBlocking {
        database.channelDao().upsertAll(listOf(channel("trt1", country = "TR")))
        database.userDataDao().addFavorite(FavoriteEntity("trt1", kind = "channel", addedAt = NOW))
        database.userDataDao().recordWatch(id = "trt1", kind = "channel", now = NOW)
        streams.insertIgnoringExisting(
            listOf(stream("rtsp", "rtsp://cdn.test/live", StreamKind.NON_HTTP, channelId = "trt1")),
        )

        // The favourites bucket runs first, so this is the one that used to consume the
        // whole sweep. The others would have inherited the same spin.
        assertEquals(emptyList<String>(), streams.dueForCheckFavorites(NOW, 100).map { it.id })
        assertEquals(emptyList<String>(), streams.dueForCheckRecents(NOW, 100).map { it.id })
        assertEquals(emptyList<String>(), streams.dueForCheckInCountry(NOW, "TR", 100).map { it.id })
        assertEquals(emptyList<String>(), streams.dueForChannels(listOf("trt1"), NOW, 100).map { it.id })
    }

    @Test
    fun aProbedStreamStopsBeingDue() = runBlocking {
        // The contrast that makes the exclusion necessary: an HTTP stream leaves the
        // queue as soon as a verdict is written, which is exactly what a NON_HTTP row
        // can never do.
        streams.insertIgnoringExisting(
            listOf(stream("hls", "https://cdn.test/live.m3u8", StreamKind.HLS)),
        )
        assertEquals(1, streams.dueForCheck(NOW, 100).size)

        streams.updateHealth(
            id = "hls",
            state = "OK",
            lastCheckedAt = NOW,
            lastOkAt = NOW,
            consecutiveFailures = 0,
            lastLatencyMs = 120,
            nextCheckAt = NOW + 12 * 60 * 60 * 1000,
            lastErrorCode = 0,
            isVod = false,
        )

        assertEquals(emptyList<String>(), streams.dueForCheck(NOW, 100).map { it.id })
    }

    private fun stream(
        id: String,
        url: String,
        kind: StreamKind,
        channelId: String? = null,
    ) = StreamEntity(
        id = id,
        channelId = channelId,
        url = url,
        title = id,
        quality = null,
        referrer = null,
        userAgent = null,
        label = null,
        kind = kind.name,
        // The default an unchecked row carries: due since the epoch, so it sorts to
        // the front of every sweep.
        health = HealthColumns(),
    )

    private fun channel(id: String, country: String) = ChannelEntity(
        id = id,
        name = id,
        country = country,
        categories = "",
        logoUrl = null,
        isNsfw = false,
        isClosed = false,
        replacedBy = null,
        searchText = id,
    )

    private companion object {
        const val NOW = 1_700_000_000_000L
    }
}
