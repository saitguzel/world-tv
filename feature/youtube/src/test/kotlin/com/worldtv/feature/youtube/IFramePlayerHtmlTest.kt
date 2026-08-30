package com.worldtv.feature.youtube

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IFramePlayerHtmlTest {

    @Test
    fun `embeds the video id and the bridge name`() {
        val html = IFramePlayerHtml.build("dQw4w9WgXcQ")
        assertTrue(html.contains("videoId: 'dQw4w9WgXcQ'"))
        assertTrue(html.contains("${IFramePlayerHtml.BRIDGE}.onReady()"))
        assertTrue(html.contains("${IFramePlayerHtml.BRIDGE}.onStateChange"))
    }

    @Test
    fun `uses the official IFrame API rather than a scraped manifest`() {
        val html = IFramePlayerHtml.build("abc")
        // Pulling an HLS manifest out of YouTube violates its terms of service and is
        // the kind of thing that gets an app removed.
        assertTrue(html.contains("https://www.youtube.com/iframe_api"))
    }

    @Test
    fun `hides YouTube's own chrome so Compose can draw the controls`() {
        val html = IFramePlayerHtml.build("abc")
        assertTrue(html.contains("controls: 0"))
        assertTrue(html.contains("iv_load_policy: 3"))
        assertTrue(html.contains("fs: 0"))
    }

    @Test
    fun `sends an origin, which the IFrame API requires`() {
        val html = IFramePlayerHtml.build("abc", originUrl = "https://example.test")
        assertTrue(html.contains("origin: 'https://example.test'"))
    }

    @Test
    fun `a malformed video id cannot break out of the JS string literal`() {
        val html = IFramePlayerHtml.build("abc'; alert(1); var x='")
        assertFalse(html.contains("'; alert(1); var x='"))
        assertTrue(html.contains("\\'"))
    }

    @Test
    fun `escaping neutralises quotes, angle brackets and newlines`() {
        assertEqualsEscaped("a'b", "a\\'b")
        assertEqualsEscaped("a\"b", "a\\\"b")
        assertEqualsEscaped("a\\b", "a\\\\b")
        assertEqualsEscaped("<script>", "\\u003Cscript\\u003E")
        assertEqualsEscaped("a\nb", "a\\nb")
        assertEqualsEscaped("a&b", "a\\u0026b")
    }

    @Test
    fun `ordinary ids pass through untouched`() {
        assertEqualsEscaped("dQw4w9WgXcQ", "dQw4w9WgXcQ")
        assertEqualsEscaped("", "")
    }

    private fun assertEqualsEscaped(input: String, expected: String) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, input.jsEscaped(), input)
    }
}
