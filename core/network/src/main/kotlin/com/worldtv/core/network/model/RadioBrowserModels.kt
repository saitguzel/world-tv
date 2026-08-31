package com.worldtv.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ApiRadioServer(
    val name: String = "",
    val ip: String = "",
)

@Serializable
data class ApiRadioStation(
    val stationuuid: String,
    val name: String = "",
    val url: String = "",
    /** Prefer this over `url`: redirects are already followed server-side. */
    @SerialName("url_resolved") val urlResolved: String = "",
    val favicon: String = "",
    val tags: String = "",
    val countrycode: String = "",
    val language: String = "",
    val codec: String = "",
    val bitrate: Int = 0,
    /** 1 when Radio Browser's own probe last succeeded. */
    val lastcheckok: Int = 0,
    val lastchecktime: String? = null,
    val clickcount: Int = 0,
    val votes: Int = 0,
) {
    /** `url_resolved` when present, falling back to the raw URL. */
    val playbackUrl: String get() = urlResolved.ifBlank { url }
}
