package com.worldtv.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * iptv-org API shapes.
 *
 * Every field that is not a hard identifier is nullable with a default. The directory
 * changes shape without notice — it has already dropped `status`, `checked_at`,
 * `width`, `height` and `bitrate`, and moved logos into their own file — and a parse
 * error on one field must never take down a 20 MB sync.
 */
@Serializable
data class ApiChannel(
    val id: String,
    val name: String = "",
    @SerialName("alt_names") val altNames: List<String> = emptyList(),
    val network: String? = null,
    val owners: List<String> = emptyList(),
    val country: String = "",
    val subdivision: String? = null,
    val city: String? = null,
    val categories: List<String> = emptyList(),
    @SerialName("is_nsfw") val isNsfw: Boolean = false,
    val launched: String? = null,
    /** Non-null means the channel has shut down and must not be listed. */
    val closed: String? = null,
    @SerialName("replaced_by") val replacedBy: String? = null,
    val website: String? = null,
)

@Serializable
data class ApiStream(
    /** Nullable: the catalog carries streams that match no known channel. */
    val channel: String? = null,
    val feed: String? = null,
    val title: String = "",
    val url: String,
    /** Must be echoed as `Referer` or the origin answers 403. */
    val referrer: String? = null,
    @SerialName("user_agent") val userAgent: String? = null,
    val quality: String? = null,
    /** Free text, e.g. "Geo-blocked", "Not 24/7". */
    val label: String? = null,
)

/** Logos moved out of `channels.json` into their own file. */
@Serializable
data class ApiLogo(
    val channel: String,
    val feed: String? = null,
    val url: String,
    val width: Int = 0,
    val height: Int = 0,
    val format: String? = null,
)

@Serializable
data class ApiCountry(
    val name: String = "",
    val code: String,
    val languages: List<String> = emptyList(),
    val flag: String = "",
)

@Serializable
data class ApiCategory(
    val id: String,
    val name: String = "",
)

/** DMCA removals. Filtering these is not optional. */
@Serializable
data class ApiBlocklistEntry(
    val channel: String,
    val reason: String = "",
    val ref: String? = null,
)
