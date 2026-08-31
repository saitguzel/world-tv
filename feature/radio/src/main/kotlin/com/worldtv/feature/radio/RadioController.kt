package com.worldtv.feature.radio

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.worldtv.core.model.RadioStation
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Talks to [RadioPlaybackService] through a MediaController.
 *
 * A controller rather than a directly-held player: playback has to survive this screen
 * and the whole activity going away, which is the entire point of radio mode, and the
 * session is also what puts a "now playing" card on the TV home screen.
 */
@Singleton
class RadioController @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var controller: MediaController? = null

    private val _nowPlaying = MutableStateFlow<RadioStation?>(null)
    val nowPlaying: StateFlow<RadioStation?> = _nowPlaying.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    /** Connects lazily; safe to call repeatedly. */
    fun connect(onReady: (MediaController) -> Unit = {}) {
        controller?.let { onReady(it); return }

        val token = SessionToken(
            context,
            ComponentName(context, RadioPlaybackService::class.java),
        )
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            {
                val newController = runCatching { future.get() }.getOrNull() ?: return@addListener
                controller = newController
                onReady(newController)
            },
            MoreExecutors.directExecutor(),
        )
    }

    fun play(station: RadioStation) {
        connect { mediaController ->
            mediaController.setMediaItem(station.toMediaItem())
            mediaController.prepare()
            mediaController.play()
            _nowPlaying.value = station
            _isPlaying.value = true
        }
    }

    fun togglePlayPause() {
        connect { mediaController ->
            if (mediaController.isPlaying) mediaController.pause() else mediaController.play()
            _isPlaying.value = mediaController.isPlaying
        }
    }

    fun stop() {
        controller?.stop()
        _isPlaying.value = false
        _nowPlaying.value = null
    }

    fun release() {
        controller?.release()
        controller = null
    }
}

private fun RadioStation.toMediaItem(): MediaItem = MediaItem.Builder()
    .setUri(url)
    .setMediaId(uuid)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(name)
            .setArtist(countryCode)
            .setArtworkUri(faviconUrl?.let(android.net.Uri::parse))
            // Marks the item as a live radio stream so the session surfaces the right
            // transport controls — no seek bar on something with no duration.
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .build(),
    )
    .build()
