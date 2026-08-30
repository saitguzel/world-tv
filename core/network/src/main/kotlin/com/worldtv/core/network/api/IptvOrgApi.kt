package com.worldtv.core.network.api

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Streaming

/**
 * iptv-org catalog endpoints.
 *
 * All of them return the raw body rather than a parsed list: `channels.json` and
 * `streams.json` are ~10 MB and ~8 MB, and building a full JSON tree for either
 * blows past the heap budget on a 1 GB TV box. [CatalogDownloader] streams them
 * instead.
 *
 * `If-None-Match` is threaded through by hand so a 304 costs one round trip instead
 * of 20 MB — GitHub Pages honours ETags.
 */
interface IptvOrgApi {

    @Streaming
    @GET("channels.json")
    suspend fun channels(@Header("If-None-Match") etag: String?): Response<ResponseBody>

    @Streaming
    @GET("streams.json")
    suspend fun streams(@Header("If-None-Match") etag: String?): Response<ResponseBody>

    @Streaming
    @GET("logos.json")
    suspend fun logos(@Header("If-None-Match") etag: String?): Response<ResponseBody>

    @Streaming
    @GET("countries.json")
    suspend fun countries(@Header("If-None-Match") etag: String?): Response<ResponseBody>

    @Streaming
    @GET("categories.json")
    suspend fun categories(@Header("If-None-Match") etag: String?): Response<ResponseBody>

    @Streaming
    @GET("blocklist.json")
    suspend fun blocklist(@Header("If-None-Match") etag: String?): Response<ResponseBody>

    companion object {
        const val BASE_URL = "https://iptv-org.github.io/api/"
    }
}
