package com.worldtv.core.model

import java.util.Locale

enum class TrackType { AUDIO, TEXT }

/** A selectable audio or subtitle track, flattened for the UI. */
data class MediaTrack(
    /** Opaque handle the player layer uses to re-select this track. */
    val id: String,
    val type: TrackType,
    /** BCP-47 tag as the stream declares it, e.g. "tr", "en-GB". */
    val language: String?,
    val label: String,
    val isSelected: Boolean,
    /** True for the synthetic "off" entry in the subtitle list. */
    val isOff: Boolean = false,
)

/**
 * Chooses which subtitle and audio track to start with.
 *
 * Kept pure and separate from Media3 because the rule is the part worth getting right
 * and the part worth testing: an IPTV stream's language tags are inconsistent
 * (`tur`, `tr`, `tr-TR`, sometimes the channel's country instead of the audio's
 * language), and a naive exact-match picks nothing on most real streams.
 */
object TrackPreferences {

    /**
     * @param available language tags present on the stream
     * @param systemCaptionLanguage the language the platform's captioning settings ask
     *   for, when the user has captions turned on. Respected first: a user who set
     *   that has stated a preference explicitly.
     * @param deviceLanguage fallback, from the device locale
     * @return the tag to request, or null to leave subtitles off
     */
    fun preferredLanguage(
        available: List<String>,
        systemCaptionLanguage: String?,
        deviceLanguage: String?,
    ): String? {
        if (available.isEmpty()) return null
        for (candidate in listOfNotNull(systemCaptionLanguage, deviceLanguage)) {
            match(available, candidate)?.let { return it }
        }
        return null
    }

    /**
     * Matches a wanted tag against what the stream offers.
     *
     * Compares on the primary subtag only, so `tr` matches `tr-TR` and vice versa, and
     * normalises the three-letter forms that IPTV streams use interchangeably with the
     * two-letter ones.
     */
    internal fun match(available: List<String>, wanted: String): String? {
        val target = normalize(wanted)
        if (target.isEmpty()) return null
        // Prefer an exact tag match before falling back to the primary subtag, so a
        // stream carrying both en-GB and en-US honours a specific request.
        available.firstOrNull { it.equals(wanted, ignoreCase = true) }?.let { return it }
        return available.firstOrNull { normalize(it) == target }
    }

    /** Folds a language tag to a comparable two-letter primary subtag. */
    internal fun normalize(tag: String): String {
        val primary = tag.trim().substringBefore('-').substringBefore('_').lowercase(Locale.ROOT)
        if (primary.isEmpty()) return ""
        // ISO 639-2 to ISO 639-1 for the tags that actually turn up in IPTV catalogs.
        return THREE_TO_TWO[primary] ?: primary
    }

    private val THREE_TO_TWO = mapOf(
        "tur" to "tr", "eng" to "en", "deu" to "de", "ger" to "de", "fra" to "fr",
        "fre" to "fr", "spa" to "es", "ita" to "it", "rus" to "ru", "ara" to "ar",
        "por" to "pt", "nld" to "nl", "dut" to "nl", "pol" to "pl", "ell" to "el",
        "gre" to "el", "zho" to "zh", "chi" to "zh", "jpn" to "ja", "kor" to "ko",
        "fas" to "fa", "per" to "fa", "ukr" to "uk", "ron" to "ro", "rum" to "ro",
        "swe" to "sv", "nor" to "no", "dan" to "da", "fin" to "fi", "ces" to "cs",
        "cze" to "cs", "hun" to "hu", "bul" to "bg", "srp" to "sr", "hrv" to "hr",
        "aze" to "az", "kur" to "ku", "hin" to "hi", "urd" to "ur", "ind" to "id",
    )

    /**
     * A human label for a language tag, or null when the tag says nothing useful.
     *
     * Returns the raw tag rather than inventing a word for it: on a stream tagged
     * `mul` or `qaa`, showing the tag at least tells the user the tracks differ.
     * Null means "no label at all" — the UI supplies its own wording for that, since
     * this module is plain Kotlin and has no resources.
     */
    fun labelFor(tag: String?, displayLocale: Locale = Locale.getDefault()): String? {
        if (tag.isNullOrBlank()) return null
        val locale = Locale.forLanguageTag(tag.replace('_', '-'))
        val display = locale.getDisplayLanguage(displayLocale)
        return display.takeIf { it.isNotBlank() && !it.equals(tag, ignoreCase = true) } ?: tag
    }
}
