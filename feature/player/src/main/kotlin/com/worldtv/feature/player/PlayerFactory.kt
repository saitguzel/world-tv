package com.worldtv.feature.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.worldtv.core.model.Stream
import com.worldtv.core.network.di.MediaClient
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.OkHttpClient

/**
 * Builds ExoPlayer instances configured for live IPTV.
 *
 * Shares the health engine's OkHttp client on purpose: the sweep has usually already
 * opened a connection to the same origin, and reusing the pooled TLS session is worth
 * roughly a second on time-to-first-frame.
 */
@Singleton
class PlayerFactory @Inject constructor(
    @ApplicationContext private val context: Context,
    @MediaClient private val client: OkHttpClient,
) {

    fun create(): ExoPlayer {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 15_000,
                /* maxBufferMs = */ 50_000,
                // Start playing after 2.5s of buffer — a news channel that lags the
                // broadcast by a minute is worse than one that occasionally rebuffers.
                /* bufferForPlaybackMs = */ 2_500,
                /* bufferForPlaybackAfterRebufferMs = */ 5_000,
            )
            .build()

        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(context))
            .setLoadControl(loadControl)
            .setHandleAudioBecomingNoisy(true)
            .build()
    }

    /**
     * Per-stream data source.
     *
     * Referer and User-Agent are echoed from the catalog entry: a large share of
     * origins answer 403 without them, and that 403 would otherwise be recorded as
     * the stream being broken.
     *
     * Cross-protocol redirects are allowed because HTTPS-to-HTTP hops are routine here
     * and blocking them turns a working stream into a failure.
     */
    fun dataSourceFactory(stream: Stream): DataSource.Factory {
        val httpFactory = OkHttpDataSource.Factory(client)
            .setUserAgent(stream.userAgent ?: DEFAULT_USER_AGENT)
            .setDefaultRequestProperties(
                buildMap {
                    stream.referrer?.let { put("Referer", it) }
                },
            )
        return DefaultDataSource.Factory(context, httpFactory)
    }

    fun mediaSourceFactory(stream: Stream): DefaultMediaSourceFactory =
        DefaultMediaSourceFactory(dataSourceFactory(stream))

    fun mediaItem(stream: Stream): MediaItem = MediaItem.Builder()
        .setUri(stream.url)
        .setMediaId(stream.id)
        .build()

    private companion object {
        const val DEFAULT_USER_AGENT = "WorldTV/1.0 (Android TV)"
    }
}
