package com.worldtv.data.health

import com.worldtv.core.model.StreamKind
import java.util.Locale

/**
 * Classifies a stream URL by transport, from the URL alone.
 *
 * Required because a single "does the body contain #EXTM3U" test marks every DASH,
 * MPEG-TS and RTSP entry in the iptv-org catalog as dead. Those are a meaningful
 * slice of the directory, and silently deleting them is the most damaging thing the
 * health engine could do.
 */
object StreamKindDetector {

    private val NON_HTTP_SCHEMES = setOf("rtsp", "rtsps", "rtmp", "rtmps", "udp", "rtp", "mms")
    private val PROGRESSIVE_EXTENSIONS =
        setOf("ts", "mp4", "m4v", "mkv", "webm", "flv", "mp3", "aac", "ogg", "opus", "m4a")

    fun detect(url: String): StreamKind {
        val scheme = url.substringBefore("://", missingDelimiterValue = "")
            .lowercase(Locale.ROOT)
        if (scheme in NON_HTTP_SCHEMES) return StreamKind.NON_HTTP
        if (scheme != "http" && scheme != "https") {
            // Schemeless or exotic. Nothing sensible to probe.
            return if (scheme.isEmpty()) StreamKind.UNKNOWN_HTTP else StreamKind.NON_HTTP
        }

        // Strip query and fragment before looking at the extension: plenty of catalog
        // URLs look like ".../live.m3u8?token=abc&ext=.ts".
        val path = url.substringAfter("://")
            .substringAfter('/', missingDelimiterValue = "")
            .substringBefore('?')
            .substringBefore('#')
        val extension = path.substringAfterLast('.', missingDelimiterValue = "")
            .lowercase(Locale.ROOT)

        return when {
            extension == "m3u8" || extension == "m3u" -> StreamKind.HLS
            extension == "mpd" -> StreamKind.DASH
            extension in PROGRESSIVE_EXTENSIONS -> StreamKind.PROGRESSIVE
            // Path-shaped hints used by a lot of restreamers.
            path.contains("/manifest.mpd", ignoreCase = true) -> StreamKind.DASH
            path.contains(".m3u8", ignoreCase = true) -> StreamKind.HLS
            else -> StreamKind.UNKNOWN_HTTP
        }
    }
}
