package com.worldtv.data.health

/**
 * Minimal, allocation-cheap HLS playlist reader.
 *
 * Only what the health engine needs: is this a playlist, is it live, which variant is
 * cheapest to probe, and what is the first segment URL. Media3 does the real parsing
 * at playback time.
 */
object HlsManifest {

    private const val TAG_HEADER = "#EXTM3U"
    private const val TAG_STREAM_INF = "#EXT-X-STREAM-INF"
    private const val TAG_ENDLIST = "#EXT-X-ENDLIST"
    private const val TAG_SEGMENT_DURATION = "#EXTINF"
    private const val TAG_MAP = "#EXT-X-MAP"

    fun isPlaylist(body: String): Boolean =
        body.trimStart('﻿', ' ', '\n', '\r', '\t').startsWith(TAG_HEADER)

    /** A playlist with `#EXT-X-ENDLIST` has finished: it is a VOD asset, not a live feed. */
    fun isEnded(body: String): Boolean = body.lineSequence().any { it.trim() == TAG_ENDLIST }

    /** True for a master playlist (one that only lists other playlists). */
    fun isMaster(body: String): Boolean = variantCount(body) > 0

    fun variantCount(body: String): Int =
        body.lineSequence().count { it.startsWith(TAG_STREAM_INF) }

    /**
     * Picks the lowest-bandwidth variant of a master playlist.
     *
     * Lowest on purpose: the probe only needs to prove the origin is serving, and the
     * cheapest rendition costs the least bandwidth on a metered TV box and is the one
     * least likely to be rate-limited.
     *
     * @return an absolute URL, or null when [body] is not a master playlist.
     */
    fun lowestBitrateVariant(body: String, baseUrl: String): String? {
        var bestBandwidth = Long.MAX_VALUE
        var bestUri: String? = null
        var pendingBandwidth: Long? = null

        for (rawLine in body.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            when {
                line.startsWith(TAG_STREAM_INF) -> {
                    pendingBandwidth = parseAttribute(line, "AVERAGE-BANDWIDTH")?.toLongOrNull()
                        ?: parseAttribute(line, "BANDWIDTH")?.toLongOrNull()
                        ?: Long.MAX_VALUE - 1
                }
                line.startsWith("#") -> Unit
                else -> {
                    val bandwidth = pendingBandwidth
                    pendingBandwidth = null
                    if (bandwidth != null && bandwidth <= bestBandwidth) {
                        bestBandwidth = bandwidth
                        bestUri = line
                    }
                }
            }
        }
        return bestUri?.let { resolve(it, baseUrl) }
    }

    /**
     * First media segment of a media playlist.
     *
     * `#EXT-X-MAP` (the fMP4 initialisation segment) is preferred when present: it is
     * the first byte range a real player would fetch, and it is small.
     */
    fun firstSegmentUrl(body: String, baseUrl: String): String? {
        var sawSegmentTag = false
        for (rawLine in body.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            when {
                line.startsWith(TAG_MAP) ->
                    parseAttribute(line, "URI")?.let { return resolve(it, baseUrl) }
                line.startsWith(TAG_SEGMENT_DURATION) -> sawSegmentTag = true
                line.startsWith("#") -> Unit
                sawSegmentTag -> return resolve(line, baseUrl)
                else -> Unit
            }
        }
        return null
    }

    /** Reads `KEY=value` / `KEY="value"` out of an EXT tag line. */
    internal fun parseAttribute(line: String, key: String): String? {
        val marker = "$key="
        var index = line.indexOf(marker)
        while (index >= 0) {
            // Guard against AVERAGE-BANDWIDTH matching a search for BANDWIDTH.
            val preceding = line.getOrNull(index - 1)
            if (preceding == null || preceding == ',' || preceding == ':' || preceding == ' ') {
                val valueStart = index + marker.length
                return if (line.getOrNull(valueStart) == '"') {
                    val end = line.indexOf('"', valueStart + 1)
                    if (end < 0) null else line.substring(valueStart + 1, end)
                } else {
                    val end = line.indexOf(',', valueStart).let { if (it < 0) line.length else it }
                    line.substring(valueStart, end).trim()
                }
            }
            index = line.indexOf(marker, index + 1)
        }
        return null
    }

    /** Resolves a possibly relative playlist URI against the playlist's own URL. */
    internal fun resolve(uri: String, baseUrl: String): String {
        if (uri.startsWith("http://", ignoreCase = true) ||
            uri.startsWith("https://", ignoreCase = true)
        ) {
            return uri
        }
        val schemeEnd = baseUrl.indexOf("://")
        if (schemeEnd < 0) return uri
        val authorityStart = schemeEnd + 3
        val pathStart = baseUrl.indexOf('/', authorityStart)
        val origin = if (pathStart < 0) baseUrl else baseUrl.substring(0, pathStart)

        if (uri.startsWith("//")) return baseUrl.substring(0, schemeEnd + 1) + uri
        if (uri.startsWith("/")) return origin + uri

        val basePath = if (pathStart < 0) "/" else {
            baseUrl.substring(pathStart).substringBefore('?').substringBefore('#')
        }
        val directory = basePath.substringBeforeLast('/', missingDelimiterValue = "") + "/"
        return origin + normalizePath(directory + uri)
    }

    /** Collapses `.` and `..` so relative variant URIs resolve the way a browser would. */
    private fun normalizePath(path: String): String {
        val segments = ArrayDeque<String>()
        for (segment in path.split('/')) {
            when (segment) {
                "", "." -> Unit
                ".." -> if (segments.isNotEmpty()) segments.removeLast()
                else -> segments.addLast(segment)
            }
        }
        val trailingSlash = path.endsWith("/")
        return "/" + segments.joinToString("/") + if (trailingSlash && segments.isNotEmpty()) "/" else ""
    }
}
