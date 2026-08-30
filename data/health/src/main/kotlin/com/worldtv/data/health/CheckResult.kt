package com.worldtv.data.health

/** Outcome of one health probe. */
sealed interface CheckResult {

    /**
     * The stream answered and looks like real media.
     *
     * @param latencyMs round-trip of the request that produced the verdict
     * @param variantCount number of HLS variants (0 for non-HLS or media playlists)
     * @param isLive false when the playlist carried `#EXT-X-ENDLIST` (a VOD asset)
     * @param manifest the fetched playlist text, so tier 2 does not refetch it
     */
    data class Alive(
        val latencyMs: Int,
        val variantCount: Int = 0,
        val isLive: Boolean = true,
        val manifest: String? = null,
    ) : CheckResult

    /** Refused with a status that reads as a region lock. Listed, never eliminated on its own. */
    data class GeoBlocked(val code: Int) : CheckResult

    /** Definitively broken for this attempt. Increments the failure counter. */
    data class Dead(val code: Int, val reason: String) : CheckResult

    /**
     * The probe learned nothing: no network, DNS failure, or a transport this app
     * cannot probe over HTTP (RTSP/RTMP/UDP).
     *
     * Critically this does **not** touch the failure counter. Without it, one flight
     * with the Wi-Fi off would mark the entire catalog dead.
     */
    data class Inconclusive(val reason: String) : CheckResult
}
