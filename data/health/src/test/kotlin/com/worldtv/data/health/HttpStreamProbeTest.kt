package com.worldtv.data.health

import com.worldtv.core.model.HealthErrorCode
import com.worldtv.core.model.StreamKind
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

class HttpStreamProbeTest {

    private lateinit var server: MockWebServer
    private lateinit var probe: HttpStreamProbe
    private val time = FakeTimeProvider()

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        probe = HttpStreamProbe(
            client = OkHttpClient.Builder()
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(2, TimeUnit.SECONDS)
                .callTimeout(4, TimeUnit.SECONDS)
                .build(),
            time = time,
        )
    }

    @AfterEach
    fun tearDown() = server.close()

    private fun target(
        path: String = "/live.m3u8",
        label: String? = null,
    ) = ProbeTarget(
        id = "s1",
        url = server.url(path).toString(),
        referrer = "https://example.test/",
        userAgent = "WorldTV-Test/1.0",
        label = label,
    )

    @Test
    fun `sends the catalog Referer and User-Agent`() = runTest {
        server.enqueue(MockResponse(body = "#EXTM3U\n#EXT-X-VERSION:3"))

        probe.checkManifest(target())

        val request = server.takeRequest()
        // Omitting either of these turns a working origin into a 403, which the
        // engine would then record as the stream being broken.
        assertEquals("https://example.test/", request.headers["Referer"])
        assertEquals("WorldTV-Test/1.0", request.headers["User-Agent"])
        assertTrue(request.headers["Range"]?.startsWith("bytes=0-") == true)
    }

    @Test
    fun `uses a ranged GET, never HEAD`() = runTest {
        server.enqueue(MockResponse(body = "#EXTM3U"))
        probe.checkManifest(target())
        // Most IPTV CDNs answer HEAD with 405 or 400.
        assertEquals("GET", server.takeRequest().method)
    }

    /**
     * The regression this whole class exists for.
     *
     * An origin that ignores `Range` and streams forever used to hang the probe: an
     * unbounded `readUtf8()` never reaches EOF on a live feed. The read is capped now,
     * so the verdict lands from the first few KB.
     */
    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    fun `an endless response that ignores Range still produces a verdict`() = runTest {
        val endless = Buffer().apply {
            writeUtf8("#EXTM3U\n#EXT-X-VERSION:3\n")
            // Far more than the 8 KB preview, as an origin streaming a live playlist
            // would send.
            repeat(20_000) { writeUtf8("#EXTINF:6.0,\nsegment$it.ts\n") }
        }
        server.enqueue(MockResponse.Builder().code(200).body(endless).build())

        val result = probe.checkManifest(target())

        assertInstanceOf(CheckResult.Alive::class.java, result)
    }

    @Test
    fun `a 200 that is really an HTML portal page is dead, not alive`() = runTest {
        server.enqueue(
            MockResponse(
                code = 200,
                body = "<html><body>Please log in</body></html>",
            ),
        )
        val result = probe.checkManifest(target())
        assertEquals(
            CheckResult.Dead(HealthErrorCode.NOT_A_PLAYLIST, "200 but not a playlist"),
            result,
        )
    }

    @Test
    fun `451 is a region lock`() = runTest {
        server.enqueue(MockResponse(code = 451))
        assertEquals(CheckResult.GeoBlocked(451), probe.checkManifest(target()))
    }

    @Test
    fun `a bare 403 is an ordinary failure, not a region lock`() = runTest {
        server.enqueue(MockResponse(code = 403))
        // Otherwise a permanently broken origin lives in the list forever, because
        // GEO_BLOCKED never eliminates anything.
        assertEquals(CheckResult.Dead(403, "forbidden"), probe.checkManifest(target()))
    }

    @Test
    fun `a 403 on a stream the catalog already labels geo-blocked is a region lock`() = runTest {
        server.enqueue(MockResponse(code = 403))
        assertEquals(
            CheckResult.GeoBlocked(403),
            probe.checkManifest(target(label = "Geo-blocked")),
        )
    }

    @Test
    fun `a finished playlist is alive but not live`() = runTest {
        server.enqueue(
            MockResponse(
                body = "#EXTM3U\n#EXTINF:6.0,\nseg1.ts\n#EXT-X-ENDLIST",
            ),
        )
        val result = probe.checkManifest(target())
        val alive = assertInstanceOf(CheckResult.Alive::class.java, result)
        assertTrue(!alive.isLive, "an ENDLIST playlist is VOD, not a dead stream")
    }

    @Test
    fun `a DASH manifest is judged as DASH, not failed for not being HLS`() = runTest {
        server.enqueue(
            MockResponse(
                body = """<?xml version="1.0"?><MPD xmlns="urn:mpeg:dash:schema:mpd:2011"/>""",
            ),
        )
        val result = probe.checkManifest(target(path = "/manifest.mpd"))
        assertInstanceOf(CheckResult.Alive::class.java, result)
    }

    @Test
    fun `a raw MPEG-TS feed is recognised by its sync byte`() = runTest {
        val ts = Buffer().apply {
            repeat(4) {
                writeByte(0x47)
                write(ByteArray(187))
            }
        }
        server.enqueue(MockResponse.Builder().code(200).body(ts).build())
        val result = probe.checkManifest(target(path = "/stream.ts"))
        assertInstanceOf(CheckResult.Alive::class.java, result)
    }

    @Test
    fun `an RTSP url is never probed over HTTP`() = runTest {
        val rtsp = ProbeTarget(
            id = "s2",
            url = "rtsp://example.test/live",
            referrer = null,
            userAgent = null,
            label = null,
        )
        assertEquals(StreamKind.NON_HTTP, rtsp.kind)
        // Reporting Dead here would eliminate every RTSP entry on the first sweep.
        assertInstanceOf(CheckResult.Inconclusive::class.java, probe.checkManifest(rtsp))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `tier 2 walks the master playlist to a real segment`() = runTest {
        val master = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=5000000
            hi/index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=400000
            lo/index.m3u8
        """.trimIndent()
        server.enqueue(MockResponse(body = "#EXTM3U\n#EXTINF:6.0,\nseg1.ts"))
        server.enqueue(MockResponse(code = 206, body = "binary-ish"))

        val result = probe.checkSegment(target(), master)

        assertInstanceOf(CheckResult.Alive::class.java, result)
        // The cheapest rendition is the one probed: less bandwidth on a metered box
        // and less likely to be rate-limited.
        assertTrue(server.takeRequest().target.endsWith("/lo/index.m3u8"))
        assertTrue(server.takeRequest().target.endsWith("/seg1.ts"))
    }

    @Test
    fun `a manifest that is 200 with no segments behind it is dead`() = runTest {
        // Extremely common: the origin serves a playlist shell long after the feed died.
        val result = probe.checkSegment(target(), "#EXTM3U\n#EXT-X-TARGETDURATION:6")
        assertEquals(
            CheckResult.Dead(HealthErrorCode.NO_SEGMENTS, "playlist has no segments"),
            result,
        )
    }

    @Test
    fun `a segment that 404s marks the stream dead even though the manifest served`() = runTest {
        server.enqueue(MockResponse(code = 404))
        val result = probe.checkSegment(target(), "#EXTM3U\n#EXTINF:6.0,\nseg1.ts")
        assertEquals(CheckResult.Dead(404, "segment 404"), result)
    }
}
