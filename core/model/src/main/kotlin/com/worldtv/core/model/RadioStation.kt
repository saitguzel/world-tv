package com.worldtv.core.model

/** A Radio Browser station. */
data class RadioStation(
    val uuid: String,
    val name: String,
    /** Always the `url_resolved` value — redirects are already followed there. */
    val url: String,
    val faviconUrl: String?,
    val tags: List<String>,
    val countryCode: String,
    val language: String?,
    val codec: String?,
    val bitrate: Int,
    /** Radio Browser's own last check result. Their probe runs from a different region. */
    val serverSideOk: Boolean,
    val clickCount: Int,
    val votes: Int,
    val health: HealthInfo,
)
