package com.worldtv.data.repository

import com.worldtv.core.database.dao.ChannelWithHealth
import com.worldtv.core.database.entity.HealthColumns
import com.worldtv.core.database.entity.StreamEntity
import com.worldtv.core.model.StreamKind
import com.worldtv.core.model.StreamState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The Room-row-to-model mappers.
 *
 * Small functions, but every channel the grid shows goes through them, and two of the
 * decisions here are easy to get subtly wrong: the category split (a stray comma in the
 * catalog must not become an empty chip) and the geo-blocked rule (a channel with no
 * streams at all is *unavailable*, not *blocked*, and the two render differently).
 */
class ChannelMappersTest {

    @Test
    fun `categories are split on commas and blanks are dropped`() {
        assertEquals(listOf("news", "kids"), row(categories = "news,,kids").toSummary().channel.categories)
        assertEquals(emptyList<String>(), row(categories = "").toSummary().channel.categories)
    }

    @Test
    fun `a channel is geo-blocked only when every available stream is`() {
        assertTrue(row(available = 2, geoBlocked = 2).toSummary().geoBlockedOnly)
        assertFalse(row(available = 2, geoBlocked = 1).toSummary().geoBlockedOnly)
        // No streams is not "blocked"; it is a channel that should not be listed.
        assertFalse(row(available = 0, geoBlocked = 0).toSummary().geoBlockedOnly)
    }

    @Test
    fun `the summary carries the row through unchanged`() {
        val summary = row(available = 3, geoBlocked = 0).copy(
            verifiedStreams = 2,
            bestLatencyMs = 140,
            isFavorite = true,
            replacedBy = "trt1-hd",
        ).toSummary()

        assertEquals("trt1", summary.channel.id)
        assertEquals("TRT 1", summary.channel.name)
        assertEquals("TR", summary.channel.country)
        assertEquals("https://logo/trt1.png", summary.channel.logoUrl)
        assertEquals("trt1-hd", summary.channel.replacedBy)
        assertEquals(3, summary.availableStreams)
        assertEquals(2, summary.verifiedStreams)
        assertEquals(140, summary.bestLatencyMs)
        assertTrue(summary.isFavorite)
    }

    @Test
    fun `an unmeasured latency stays null rather than becoming zero`() {
        // Zero would sort an unchecked channel as the fastest one.
        assertNull(row().copy(bestLatencyMs = null).toSummary().bestLatencyMs)
    }

    @Test
    fun `a stream's kind is read back from its persisted name`() {
        assertEquals(StreamKind.HLS, stream(kind = "HLS").toModel().kind)
        assertEquals(StreamKind.NON_HTTP, stream(kind = "NON_HTTP").toModel().kind)
    }

    @Test
    fun `an unrecognised kind falls back instead of crashing the grid`() {
        // A row written by a newer build with a kind this build does not know.
        assertEquals(StreamKind.UNKNOWN_HTTP, stream(kind = "SMOOTH").toModel().kind)
    }

    @Test
    fun `stream fields and health columns reach the model`() {
        val model = stream(kind = "HLS").copy(
            health = HealthColumns(state = "OK", lastLatencyMs = 120, consecutiveFailures = 0, lastOkAt = 5L),
        ).toModel()

        assertEquals("s1", model.id)
        assertEquals("trt1", model.channelId)
        assertEquals("https://example/trt1.m3u8", model.url)
        assertEquals("TRT 1 HD", model.title)
        assertEquals("1080p", model.quality)
        assertEquals("https://example/", model.referrer)
        assertEquals("WorldTV/1.0", model.userAgent)
        assertEquals("Not 24/7", model.label)
        assertEquals(StreamState.OK, model.health.state)
        assertEquals(120, model.health.lastLatencyMs)
        assertEquals(5L, model.health.lastOkAt)
    }

    private fun row(
        categories: String = "news",
        available: Int = 1,
        geoBlocked: Int = 0,
    ) = ChannelWithHealth(
        id = "trt1",
        name = "TRT 1",
        country = "TR",
        categories = categories,
        logoUrl = "https://logo/trt1.png",
        isNsfw = false,
        isClosed = false,
        replacedBy = null,
        availableStreams = available,
        verifiedStreams = 0,
        geoBlockedStreams = geoBlocked,
        bestLatencyMs = null,
        isFavorite = false,
    )

    private fun stream(kind: String) = StreamEntity(
        id = "s1",
        channelId = "trt1",
        url = "https://example/trt1.m3u8",
        title = "TRT 1 HD",
        quality = "1080p",
        referrer = "https://example/",
        userAgent = "WorldTV/1.0",
        label = "Not 24/7",
        kind = kind,
    )
}
