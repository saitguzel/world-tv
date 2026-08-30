package com.worldtv.core.network.api

import com.worldtv.core.network.model.ApiYouTubeSearchResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * YouTube Data API v3.
 *
 * Only `search.list` filtered to live broadcasts on one channel at a time. That call
 * costs **100 quota units** against a default daily budget of 10,000, so the app can
 * afford roughly a hundred of them per key per day. Everything about how this is used
 * follows from that number: a curated channel list rather than open search, a six-hour
 * refresh cycle, and results cached in Room.
 *
 * If this ever needs to scale past a handful of channels, the right move is a small
 * server that performs the searches once and serves the app a static JSON — the quota
 * is then spent centrally instead of once per installation.
 */
interface YouTubeApi {

    @GET("youtube/v3/search")
    suspend fun liveBroadcasts(
        @Query("key") apiKey: String,
        @Query("channelId") channelId: String,
        @Query("part") part: String = "snippet",
        @Query("eventType") eventType: String = "live",
        @Query("type") type: String = "video",
        @Query("maxResults") maxResults: Int = 5,
    ): ApiYouTubeSearchResponse

    companion object {
        const val BASE_URL = "https://www.googleapis.com/"

        /** `search.list` quota cost, for the budget arithmetic above. */
        const val SEARCH_QUOTA_COST = 100
        const val DEFAULT_DAILY_QUOTA = 10_000
    }
}
