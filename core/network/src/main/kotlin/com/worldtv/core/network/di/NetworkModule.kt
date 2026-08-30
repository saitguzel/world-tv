package com.worldtv.core.network.di

import com.worldtv.core.network.api.IptvOrgApi
import com.worldtv.core.network.api.RadioBrowserApi
import com.worldtv.core.network.api.YouTubeApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** The client used to probe and play arbitrary IPTV origins. Tolerates cleartext. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class MediaClient

/** The client used for catalog APIs. HTTPS only. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ApiClient

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        // The catalog gains fields without notice; an unknown key must not fail a sync.
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        explicitNulls = false
    }

    /**
     * Shared connection pool and dispatcher.
     *
     * 32 idle connections held for a minute: the sweep hits the same handful of CDNs
     * over and over, and re-establishing TLS each time is most of the cost of a probe.
     */
    @Provides
    @Singleton
    @MediaClient
    fun provideMediaClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        // Hard ceiling per probe. Without it a slow-drip origin holds a permit for
        // the whole sweep window.
        .callTimeout(8, TimeUnit.SECONDS)
        .connectionPool(ConnectionPool(32, 60, TimeUnit.SECONDS))
        .dispatcher(
            Dispatcher().apply {
                maxRequests = 32
                maxRequestsPerHost = 4
            },
        )
        // Redirects between HTTP and HTTPS are routine for these origins.
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    @Provides
    @Singleton
    @ApiClient
    fun provideApiClient(@MediaClient mediaClient: OkHttpClient): OkHttpClient =
        // Shares the media client's pool and thread pool rather than starting a second
        // one — two OkHttp clients on a 1 GB box is pure waste.
        mediaClient.newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            // Catalog files are ~10 MB; no call timeout that a slow link would trip.
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .addInterceptor(HttpsOnlyInterceptor)
            .addInterceptor(UserAgentInterceptor(RadioBrowserApi.USER_AGENT))
            .build()

    @Provides
    @Singleton
    fun provideIptvOrgApi(@ApiClient client: OkHttpClient, json: Json): IptvOrgApi =
        Retrofit.Builder()
            .baseUrl(IptvOrgApi.BASE_URL)
            .callFactory(Call.Factory { request -> client.newCall(request) })
            .addConverterFactory(json.asConverterFactory(JSON_MEDIA_TYPE))
            .build()
            .create(IptvOrgApi::class.java)

    @Provides
    @Singleton
    fun provideRadioBrowserApi(@ApiClient client: OkHttpClient, json: Json): RadioBrowserApi =
        Retrofit.Builder()
            // Every path is either @Url or carries an explicit {server}, so the base
            // URL is only here to satisfy Retrofit's builder.
            .baseUrl("https://all.api.radio-browser.info/")
            .callFactory(Call.Factory { request -> client.newCall(request) })
            .addConverterFactory(json.asConverterFactory(JSON_MEDIA_TYPE))
            .build()
            .create(RadioBrowserApi::class.java)

    @Provides
    @Singleton
    fun provideYouTubeApi(@ApiClient client: OkHttpClient, json: Json): YouTubeApi =
        Retrofit.Builder()
            .baseUrl(YouTubeApi.BASE_URL)
            .callFactory(Call.Factory { request -> client.newCall(request) })
            .addConverterFactory(json.asConverterFactory(JSON_MEDIA_TYPE))
            .build()
            .create(YouTubeApi::class.java)

    private val JSON_MEDIA_TYPE = "application/json".toMediaType()
}

/**
 * Refuses cleartext on the API client.
 *
 * The manifest has to permit cleartext app-wide because IPTV origins are arbitrary
 * hosts and a domain allowlist cannot express "everything except". That permission
 * must not extend to the catalog APIs, which are all HTTPS — hence this check in code.
 */
internal object HttpsOnlyInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!request.url.isHttps) {
            throw IOException("Refusing cleartext API request to ${request.url.host}")
        }
        return chain.proceed(request)
    }
}

/** Radio Browser rate-limits requests without a meaningful User-Agent. */
internal class UserAgentInterceptor(private val userAgent: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("User-Agent", userAgent)
            .build()
        return chain.proceed(request)
    }
}
