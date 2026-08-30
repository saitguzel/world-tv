package com.worldtv.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ApiYouTubeSearchResponse(
    val items: List<ApiYouTubeSearchItem> = emptyList(),
    @SerialName("nextPageToken") val nextPageToken: String? = null,
)

@Serializable
data class ApiYouTubeSearchItem(
    val id: ApiYouTubeId = ApiYouTubeId(),
    val snippet: ApiYouTubeSnippet = ApiYouTubeSnippet(),
)

@Serializable
data class ApiYouTubeId(
    @SerialName("videoId") val videoId: String? = null,
    val kind: String? = null,
)

@Serializable
data class ApiYouTubeSnippet(
    val title: String = "",
    val description: String = "",
    @SerialName("channelId") val channelId: String = "",
    @SerialName("channelTitle") val channelTitle: String = "",
    val thumbnails: ApiYouTubeThumbnails = ApiYouTubeThumbnails(),
    @SerialName("liveBroadcastContent") val liveBroadcastContent: String = "",
)

@Serializable
data class ApiYouTubeThumbnails(
    val medium: ApiYouTubeThumbnail? = null,
    val high: ApiYouTubeThumbnail? = null,
) {
    /** Prefer `high` on a TV; `medium` is 320px and visibly soft at 10 feet. */
    val best: String? get() = high?.url ?: medium?.url
}

@Serializable
data class ApiYouTubeThumbnail(val url: String = "", val width: Int = 0, val height: Int = 0)
