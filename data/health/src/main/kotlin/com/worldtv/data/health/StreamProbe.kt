package com.worldtv.data.health

import com.worldtv.core.model.StreamKind

/** What a probe needs to know about a stream. Deliberately not a Room entity. */
data class ProbeTarget(
    val id: String,
    val url: String,
    val referrer: String?,
    val userAgent: String?,
    val label: String?,
    val kind: StreamKind = StreamKindDetector.detect(url),
) {
    /**
     * True when the catalog itself already says this entry is region-restricted, which
     * makes a 403 far more likely to be a genuine geo-block than a broken origin.
     */
    val labelHintsGeoBlock: Boolean
        get() = label?.contains("geo", ignoreCase = true) == true ||
            label?.contains("block", ignoreCase = true) == true
}

/** Probes a stream URL over the network. Implemented by [HttpStreamProbe]. */
interface StreamProbe {
    /** Tier 1 — is the origin answering with something that looks like media? */
    suspend fun checkManifest(target: ProbeTarget): CheckResult

    /**
     * Tier 2 — does the playlist actually contain a fetchable segment?
     *
     * Only meaningful after [checkManifest] returned [CheckResult.Alive]; a manifest
     * that returns 200 with nothing inside is common enough to be worth the extra
     * round trip on anything the user is about to see.
     */
    suspend fun checkSegment(target: ProbeTarget, manifest: String): CheckResult
}
