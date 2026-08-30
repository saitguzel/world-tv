package com.worldtv.data.health

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HlsManifestTest {

    private val master = """
        #EXTM3U
        #EXT-X-STREAM-INF:BANDWIDTH=2500000,AVERAGE-BANDWIDTH=2200000,RESOLUTION=1280x720
        720/index.m3u8
        #EXT-X-STREAM-INF:BANDWIDTH=800000,AVERAGE-BANDWIDTH=640000,RESOLUTION=640x360
        360/index.m3u8
        #EXT-X-STREAM-INF:BANDWIDTH=5000000,RESOLUTION=1920x1080
        1080/index.m3u8
    """.trimIndent()

    private val media = """
        #EXTM3U
        #EXT-X-VERSION:3
        #EXT-X-TARGETDURATION:6
        #EXT-X-MEDIA-SEQUENCE:41235
        #EXTINF:6.000,
        segment41235.ts
        #EXTINF:6.000,
        segment41236.ts
    """.trimIndent()

    @Test
    fun `recognises a playlist even with a BOM`() {
        assertTrue(HlsManifest.isPlaylist("﻿#EXTM3U\n#EXT-X-VERSION:3"))
        assertTrue(HlsManifest.isPlaylist("\n  #EXTM3U"))
        assertFalse(HlsManifest.isPlaylist("<html><body>404</body></html>"))
    }

    @Test
    fun `counts variants and identifies a master playlist`() {
        assertEquals(3, HlsManifest.variantCount(master))
        assertTrue(HlsManifest.isMaster(master))
        assertFalse(HlsManifest.isMaster(media))
    }

    @Test
    fun `picks the lowest average bandwidth variant, not the first`() {
        val chosen = HlsManifest.lowestBitrateVariant(master, "https://cdn.example/live/master.m3u8")
        assertEquals("https://cdn.example/live/360/index.m3u8", chosen)
    }

    @Test
    fun `AVERAGE-BANDWIDTH is not confused with BANDWIDTH`() {
        val line = "#EXT-X-STREAM-INF:AVERAGE-BANDWIDTH=640000,BANDWIDTH=800000"
        assertEquals("800000", HlsManifest.parseAttribute(line, "BANDWIDTH"))
        assertEquals("640000", HlsManifest.parseAttribute(line, "AVERAGE-BANDWIDTH"))
    }

    @Test
    fun `finds the first media segment`() {
        val segment = HlsManifest.firstSegmentUrl(media, "https://cdn.example/live/360/index.m3u8")
        assertEquals("https://cdn.example/live/360/segment41235.ts", segment)
    }

    @Test
    fun `prefers the fMP4 init segment when present`() {
        val fmp4 = """
            #EXTM3U
            #EXT-X-MAP:URI="init.mp4"
            #EXTINF:6.000,
            seg1.m4s
        """.trimIndent()
        val segment = HlsManifest.firstSegmentUrl(fmp4, "https://cdn.example/a/b/index.m3u8")
        assertEquals("https://cdn.example/a/b/init.mp4", segment)
    }

    @Test
    fun `returns null when a playlist carries no segments`() {
        val empty = "#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:6"
        assertNull(HlsManifest.firstSegmentUrl(empty, "https://cdn.example/x.m3u8"))
    }

    @Test
    fun `detects an ended playlist as VOD`() {
        assertTrue(HlsManifest.isEnded("$media\n#EXT-X-ENDLIST"))
        assertFalse(HlsManifest.isEnded(media))
    }

    @Test
    fun `resolves absolute, root-relative, protocol-relative and dotted URIs`() {
        val base = "https://cdn.example/live/hd/index.m3u8?token=abc"
        assertEquals(
            "https://other.example/x.m3u8",
            HlsManifest.resolve("https://other.example/x.m3u8", base),
        )
        assertEquals("https://cdn.example/root.m3u8", HlsManifest.resolve("/root.m3u8", base))
        assertEquals("https://cdn2.example/x", HlsManifest.resolve("//cdn2.example/x", base))
        assertEquals("https://cdn.example/live/hd/720/i.m3u8", HlsManifest.resolve("720/i.m3u8", base))
        assertEquals("https://cdn.example/live/sd/i.m3u8", HlsManifest.resolve("../sd/i.m3u8", base))
    }
}
