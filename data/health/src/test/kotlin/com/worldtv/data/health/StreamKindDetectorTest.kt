package com.worldtv.data.health

import com.worldtv.core.model.StreamKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class StreamKindDetectorTest {

    @Test
    fun `classifies HLS including query-suffixed URLs`() {
        assertEquals(StreamKind.HLS, StreamKindDetector.detect("https://a.example/live.m3u8"))
        assertEquals(
            StreamKind.HLS,
            StreamKindDetector.detect("http://a.example/live.m3u8?token=x&ext=.ts"),
        )
    }

    @Test
    fun `classifies DASH`() {
        assertEquals(StreamKind.DASH, StreamKindDetector.detect("https://a.example/manifest.mpd"))
    }

    @Test
    fun `classifies raw transport streams as progressive, not broken`() {
        assertEquals(StreamKind.PROGRESSIVE, StreamKindDetector.detect("http://a.example:8080/1.ts"))
        assertEquals(StreamKind.PROGRESSIVE, StreamKindDetector.detect("http://a.example/x.mp4"))
        assertEquals(StreamKind.PROGRESSIVE, StreamKindDetector.detect("http://a.example/radio.aac"))
    }

    @Test
    fun `flags non-HTTP transports as unprobeable rather than dead`() {
        for (url in listOf(
            "rtsp://a.example/live",
            "rtmp://a.example/live",
            "rtmps://a.example/live",
            "udp://@239.0.0.1:1234",
        )) {
            val kind = StreamKindDetector.detect(url)
            assertEquals(StreamKind.NON_HTTP, kind, url)
            assertFalse(kind.isHttpProbeable, url)
        }
    }

    @Test
    fun `extensionless HTTP URLs stay probeable`() {
        // Very common with restreamers: /live/user/pass/1234
        assertEquals(
            StreamKind.UNKNOWN_HTTP,
            StreamKindDetector.detect("http://portal.example:8000/live/u/p/1234"),
        )
    }
}
