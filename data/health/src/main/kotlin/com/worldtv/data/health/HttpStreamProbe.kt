package com.worldtv.data.health

import com.worldtv.core.model.HealthErrorCode
import com.worldtv.core.model.StreamKind
import com.worldtv.core.model.TimeProvider
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.BufferedSource

/**
 * Tiered HTTP health probe.
 *
 * Design notes that differ from the naive version:
 *  - **Ranged GET, never HEAD.** Most IPTV CDNs answer HEAD with 405 or 400.
 *  - **Bounded reads.** A ranged request whose `Range` header the origin ignores comes
 *    back as an endless live stream; reading it to EOF never returns. Every read here
 *    is capped at [PREVIEW_BYTES].
 *  - **Transport-aware verdicts.** A DASH manifest, a raw MPEG-TS feed and an RTSP URL
 *    are each judged by their own rules instead of being failed for not being HLS.
 */
@Singleton
class HttpStreamProbe @Inject constructor(
    private val client: OkHttpClient,
    private val time: TimeProvider,
) : StreamProbe {

    override suspend fun checkManifest(target: ProbeTarget): CheckResult {
        if (target.kind == StreamKind.NON_HTTP) {
            // RTSP/RTMP/UDP cannot be judged over HTTP. Reporting Dead here would
            // eliminate every one of them on the first sweep.
            return CheckResult.Inconclusive("non-http transport")
        }

        val started = time.elapsedMillis()
        return try {
            execute(target, target.url, PREVIEW_BYTES).use { response ->
                val latency = (time.elapsedMillis() - started).toInt()
                classify(target, response, latency)
            }
        } catch (e: SocketTimeoutException) {
            CheckResult.Dead(HealthErrorCode.TIMEOUT, "timeout")
        } catch (e: UnknownHostException) {
            // Could be a dead domain, could be the user's DNS. Retried, not punished.
            CheckResult.Inconclusive("dns: ${e.message}")
        } catch (e: IOException) {
            CheckResult.Inconclusive("io: ${e.message}")
        }
    }

    override suspend fun checkSegment(target: ProbeTarget, manifest: String): CheckResult {
        if (target.kind != StreamKind.HLS && !HlsManifest.isPlaylist(manifest)) {
            // Tier 2 is HLS-specific. For other transports tier 1 is the whole verdict.
            return CheckResult.Alive(latencyMs = 0, manifest = manifest)
        }

        val mediaPlaylistUrl: String
        val mediaPlaylist: String
        var accumulatedLatency = 0

        if (HlsManifest.isMaster(manifest)) {
            val variantUrl = HlsManifest.lowestBitrateVariant(manifest, target.url)
                ?: return CheckResult.Dead(HealthErrorCode.EMPTY_PLAYLIST, "master without variants")
            val started = time.elapsedMillis()
            val fetched = try {
                execute(target, variantUrl, PREVIEW_BYTES).use { response ->
                    when {
                        isGeoStatus(response.code) -> return geoOrDead(target, response.code)
                        !response.isSuccessful ->
                            return CheckResult.Dead(response.code, "variant ${response.code}")
                        else -> readPreview(response)
                    }
                }
            } catch (e: SocketTimeoutException) {
                return CheckResult.Dead(HealthErrorCode.TIMEOUT, "variant timeout")
            } catch (e: IOException) {
                return CheckResult.Inconclusive("variant io: ${e.message}")
            }
            accumulatedLatency = (time.elapsedMillis() - started).toInt()
            mediaPlaylistUrl = variantUrl
            mediaPlaylist = fetched
        } else {
            mediaPlaylistUrl = target.url
            mediaPlaylist = manifest
        }

        val segmentUrl = HlsManifest.firstSegmentUrl(mediaPlaylist, mediaPlaylistUrl)
            ?: return CheckResult.Dead(HealthErrorCode.NO_SEGMENTS, "playlist has no segments")

        val started = time.elapsedMillis()
        return try {
            execute(target, segmentUrl, SEGMENT_PROBE_BYTES).use { response ->
                val latency = (time.elapsedMillis() - started).toInt() + accumulatedLatency
                when {
                    isGeoStatus(response.code) -> geoOrDead(target, response.code)
                    response.isSuccessful -> CheckResult.Alive(
                        latencyMs = latency,
                        variantCount = HlsManifest.variantCount(manifest),
                        isLive = !HlsManifest.isEnded(mediaPlaylist),
                        manifest = manifest,
                    )
                    else -> CheckResult.Dead(response.code, "segment ${response.code}")
                }
            }
        } catch (e: SocketTimeoutException) {
            CheckResult.Dead(HealthErrorCode.TIMEOUT, "segment timeout")
        } catch (e: IOException) {
            CheckResult.Inconclusive("segment io: ${e.message}")
        }
    }

    private fun classify(target: ProbeTarget, response: Response, latencyMs: Int): CheckResult {
        if (isGeoStatus(response.code)) return geoOrDead(target, response.code)
        if (!response.isSuccessful) return CheckResult.Dead(response.code, "http ${response.code}")

        return when (target.kind) {
            StreamKind.HLS -> {
                val body = readPreview(response)
                if (HlsManifest.isPlaylist(body)) {
                    CheckResult.Alive(
                        latencyMs = latencyMs,
                        variantCount = HlsManifest.variantCount(body),
                        // A finished playlist is a VOD asset, not a dead stream.
                        isLive = !HlsManifest.isEnded(body),
                        manifest = body,
                    )
                } else {
                    CheckResult.Dead(HealthErrorCode.NOT_A_PLAYLIST, "200 but not a playlist")
                }
            }

            StreamKind.DASH -> {
                val body = readPreview(response)
                if (body.contains("<MPD", ignoreCase = true)) {
                    CheckResult.Alive(latencyMs = latencyMs, manifest = body)
                } else {
                    CheckResult.Dead(HealthErrorCode.NOT_A_PLAYLIST, "200 but not an MPD")
                }
            }

            StreamKind.PROGRESSIVE, StreamKind.UNKNOWN_HTTP -> {
                // No manifest to inspect. A 2xx with a media-ish content type, or an
                // MPEG-TS sync byte, is as much as can be established cheaply.
                val contentType = response.header("Content-Type").orEmpty().lowercase()
                val bytes = peekBytes(response, TS_SNIFF_BYTES)
                when {
                    bytes.isEmpty() && contentType.isEmpty() ->
                        CheckResult.Dead(HealthErrorCode.EMPTY_PLAYLIST, "empty response")
                    looksLikeMpegTs(bytes) -> CheckResult.Alive(latencyMs = latencyMs)
                    contentType.startsWith("video/") ||
                        contentType.startsWith("audio/") ||
                        contentType.contains("mpegurl") ||
                        contentType.contains("octet-stream") ||
                        contentType.contains("dash+xml") ->
                        CheckResult.Alive(latencyMs = latencyMs)
                    contentType.startsWith("text/html") ->
                        // A portal or captive login page pretending to be a stream.
                        CheckResult.Dead(HealthErrorCode.NOT_A_PLAYLIST, "html, not media")
                    bytes.isNotEmpty() -> CheckResult.Alive(latencyMs = latencyMs)
                    else -> CheckResult.Dead(HealthErrorCode.EMPTY_PLAYLIST, "no bytes")
                }
            }

            StreamKind.NON_HTTP -> CheckResult.Inconclusive("non-http transport")
        }
    }

    /**
     * 451 is unambiguous. A bare 403 is far more often a missing token or a dead
     * origin than a region lock, so it only counts as geo-blocked when the catalog
     * label already said so — otherwise it is a normal failure and can eventually
     * eliminate the stream.
     */
    private fun geoOrDead(target: ProbeTarget, code: Int): CheckResult = when {
        code == HTTP_UNAVAILABLE_LEGAL -> CheckResult.GeoBlocked(code)
        code == HTTP_FORBIDDEN && target.labelHintsGeoBlock -> CheckResult.GeoBlocked(code)
        code == HTTP_FORBIDDEN -> CheckResult.Dead(code, "forbidden")
        else -> CheckResult.Dead(code, "http $code")
    }

    private fun isGeoStatus(code: Int) = code == HTTP_FORBIDDEN || code == HTTP_UNAVAILABLE_LEGAL

    private suspend fun execute(target: ProbeTarget, url: String, byteCount: Long): Response {
        val request = Request.Builder()
            .url(url)
            .header("Range", "bytes=0-${byteCount - 1}")
            .header("Accept", "*/*")
            .header("User-Agent", target.userAgent ?: DEFAULT_USER_AGENT)
            .apply { target.referrer?.let { header("Referer", it) } }
            .build()
        return client.newCall(request).awaitResponse()
    }

    /**
     * Reads at most [PREVIEW_BYTES] as UTF-8.
     *
     * `response.body.string()` would block until EOF, and for a live stream whose
     * origin ignored the Range header there is no EOF.
     */
    private fun readPreview(response: Response): String {
        val source = response.body.source()
        source.request(PREVIEW_BYTES)
        val available = minOf(source.buffer.size, PREVIEW_BYTES)
        return source.buffer.readUtf8(available)
    }

    private fun peekBytes(response: Response, count: Long): ByteArray {
        val source: BufferedSource = response.body.source()
        source.request(count)
        val available = minOf(source.buffer.size, count)
        return source.buffer.readByteArray(available)
    }

    /** MPEG-TS packets are 188 bytes and start with 0x47. */
    private fun looksLikeMpegTs(bytes: ByteArray): Boolean =
        bytes.size >= TS_PACKET_SIZE + 1 &&
            bytes[0] == TS_SYNC_BYTE &&
            bytes[TS_PACKET_SIZE] == TS_SYNC_BYTE

    private companion object {
        const val PREVIEW_BYTES = 8L * 1024
        const val SEGMENT_PROBE_BYTES = 2L * 1024
        const val TS_SNIFF_BYTES = 400L
        const val TS_PACKET_SIZE = 188
        const val TS_SYNC_BYTE: Byte = 0x47
        const val HTTP_FORBIDDEN = 403
        const val HTTP_UNAVAILABLE_LEGAL = 451
        const val DEFAULT_USER_AGENT = "WorldTV/1.0 (Android TV)"
    }
}

/**
 * Bridges an OkHttp [Call] to a coroutine.
 *
 * Cancellable on purpose: when the user leaves a screen mid-sweep the in-flight
 * probes must actually release their sockets, or a fast browse through ten countries
 * exhausts the connection pool on a cheap box.
 */
internal suspend fun Call.awaitResponse(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { runCatching { cancel() } }
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
            continuation.resumeWith(Result.success(response))
        }

        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isActive) continuation.resumeWith(Result.failure(e))
        }
    })
}
