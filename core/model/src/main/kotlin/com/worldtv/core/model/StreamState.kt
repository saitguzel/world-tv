package com.worldtv.core.model

/**
 * Health state of a single stream URL.
 *
 * Persisted **by name**, never by ordinal — the catalog queries filter on
 * `state IN ('OK', 'UNKNOWN', ...)`, which silently matches nothing if the column
 * holds an integer. See `StreamStateConverter` in :core:database.
 */
enum class StreamState {
    /** Never checked, or reset after a long DEAD cool-off. Shown, but not promoted. */
    UNKNOWN,

    /** Verified reachable within the retention window. */
    OK,

    /**
     * Refused with a status that looks region-locked (451, or 403 that survived a
     * retry with the stream's own Referer/User-Agent). Still listed — it may work
     * from the user's own network — but marked with a lock badge.
     */
    GEO_BLOCKED,

    /** Failed [HealthPolicy.FAIL_THRESHOLD] consecutive checks. Hidden, never deleted. */
    DEAD,
    ;

    val isPlayable: Boolean get() = this != DEAD
}

/**
 * Transport family of a stream URL, decided from the URL alone.
 *
 * This exists because the naive "fetch it and look for #EXTM3U" check misclassifies
 * every non-HLS entry in the iptv-org catalog as dead — the directory carries plain
 * MPEG-TS, DASH and RTSP/RTMP entries alongside HLS.
 */
enum class StreamKind {
    /** `.m3u8` — an HLS master or media playlist. */
    HLS,

    /** `.mpd` — a DASH manifest. */
    DASH,

    /** A progressive/segmented byte stream: `.ts`, `.mp4`, `.mkv`, MPEG-TS over HTTP. */
    PROGRESSIVE,

    /** `rtsp://`, `rtmp://`, `rtmps://`, `udp://` — not reachable over HTTP at all. */
    NON_HTTP,

    /** Scheme is HTTP(S) but the shape is unrecognisable; probed leniently. */
    UNKNOWN_HTTP,
    ;

    /** True when an HTTP probe can say anything meaningful about this URL. */
    val isHttpProbeable: Boolean get() = this != NON_HTTP
}

/** Health bookkeeping shared by TV streams and radio stations. */
data class HealthInfo(
    val state: StreamState = StreamState.UNKNOWN,
    /** Epoch millis of the last completed check. 0 = never checked. */
    val lastCheckedAt: Long = 0L,
    /** Epoch millis of the last successful check. 0 = never succeeded. */
    val lastOkAt: Long = 0L,
    val consecutiveFailures: Int = 0,
    /**
     * Round-trip of the last successful probe, in millis. 0 means "no measurement",
     * which is why ordering queries have to use `NULLIF(lastLatencyMs, 0)` — a raw
     * `MIN()` would otherwise rank every unchecked stream as the fastest one.
     */
    val lastLatencyMs: Int = 0,
    /** Epoch millis at which this entry becomes due for another check. */
    val nextCheckAt: Long = 0L,
    /** HTTP status, or a negative [HealthErrorCode] for transport-level failures. */
    val lastErrorCode: Int = 0,
    /** True when the last check found a playlist that had already ended (VOD, not live). */
    val isVod: Boolean = false,
)

/** Internal, non-HTTP error codes stored in [HealthInfo.lastErrorCode]. */
object HealthErrorCode {
    const val NONE = 0
    const val TIMEOUT = -1
    const val NOT_A_PLAYLIST = -2
    const val EMPTY_PLAYLIST = -3
    const val NO_SEGMENTS = -4
    const val UNPARSEABLE = -5
    const val PLAYBACK_FAILED = -6
    const val UNREACHABLE_HOST = -7
}
