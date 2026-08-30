package com.worldtv.core.network.api

import com.worldtv.core.network.model.ApiRadioServer
import com.worldtv.core.network.model.ApiRadioStation
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

/**
 * Radio Browser.
 *
 * The service expects clients to discover a mirror and spread load themselves, and it
 * rate-limits anonymous user agents, so every request carries `WorldTV/1.0` via an
 * interceptor.
 */
interface RadioBrowserApi {

    /** Mirror list. Called against the fixed discovery host, then cached for a day. */
    @GET
    suspend fun servers(@Url url: String): List<ApiRadioServer>

    @GET("{server}/json/stations/bycountrycodeexact/{code}")
    suspend fun stationsByCountry(
        @Path("server", encoded = true) server: String,
        @Path("code") countryCode: String,
        @Query("hidebroken") hideBroken: Boolean = true,
        @Query("order") order: String = "clickcount",
        @Query("reverse") reverse: Boolean = true,
        @Query("limit") limit: Int = 200,
    ): List<ApiRadioStation>

    @GET("{server}/json/stations/search")
    suspend fun search(
        @Path("server", encoded = true) server: String,
        @Query("name") name: String,
        @Query("hidebroken") hideBroken: Boolean = true,
        @Query("limit") limit: Int = 100,
    ): List<ApiRadioStation>

    companion object {
        const val DISCOVERY_URL = "https://all.api.radio-browser.info/json/servers"
        const val USER_AGENT = "WorldTV/1.0"
    }
}
