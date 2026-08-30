package com.worldtv.core.model

data class Country(
    /** ISO 3166-1 alpha-2. */
    val code: String,
    val name: String,
    /** Emoji flag supplied by the catalog. */
    val flag: String,
    val languages: List<String>,
    val channelCount: Int = 0,
)

data class Category(
    val id: String,
    val name: String,
    val channelCount: Int = 0,
)

/** Top-level mode of the app. YouTube is present in the model but gated off until phase 4. */
enum class MediaMode { TV, RADIO, YOUTUBE }
