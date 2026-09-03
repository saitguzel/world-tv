package com.worldtv.feature.radio

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.worldtv.core.common.di.ApplicationScope
import com.worldtv.core.common.playback.VideoPlaybackSignal
import com.worldtv.core.model.RadioStation
import com.worldtv.data.repository.RadioRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Talks to [RadioPlaybackService] through a MediaController.
 *
 * A controller rather than a directly-held player: playback has to survive this screen
 * and the whole activity going away, which is the entire point of radio mode, and the
 * session is also what puts a "now playing" card on the TV home screen.
 *
 * Every fact this exposes comes from the session, not from what we last asked it to
 * do. The previous version wrote `isPlaying = true` the moment it called `play()` and
 * never registered a listener, so a focus loss — the video player starting — left the
 * bar showing a pause icon over silence. [RadioPlaybackState] now decides everything
 * and this class only translates media3 callbacks into its vocabulary.
 *
 * Opening a channel ends the radio session: the video takes audio focus, the session
 * stops, and nothing brings it back on its own. See [RadioPlaybackState] for why that
 * is deliberate.
 *
 * Threading: media3 delivers listener callbacks on the application thread and requires
 * the controller to be touched there. Every caller of [play], [togglePlayPause] and
 * [stop] is already on Main; the one background hop left is [resolveStation], which
 * reads the database and hops back before it touches any state.
 */
@Singleton
class RadioController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val videoSignal: VideoPlaybackSignal,
    private val radioRepository: RadioRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val rules = RadioPlaybackState()

    private val _state = MutableStateFlow(rules.current)

    /** Authoritative state, driven by the session. */
    val state: StateFlow<RadioUiState> = _state.asStateFlow()

    /** Kept so the bar can show a name; the id alone lives in [state]. */
    private val _nowPlaying = MutableStateFlow<RadioStation?>(null)
    val nowPlaying: StateFlow<RadioStation?> = _nowPlaying.asStateFlow()

    val isPlaying: StateFlow<Boolean> = _state.map { it.playing }
        .stateIn(scope, SharingStarted.Eagerly, false)

    private var controller: MediaController? = null
    private var pending: ListenableFuture<MediaController>? = null

    /**
     * Set once the app is closing, so a connection still in flight is stopped and
     * released on arrival instead of becoming a live session nobody is left to stop.
     */
    private var closing = false
    private val waiting = mutableListOf<(MediaController) -> Unit>()

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            publish(rules.onSessionPlayingChanged(isPlaying))
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            publish(rules.onSessionBufferingChanged(playbackState == Player.STATE_BUFFERING))
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val id = mediaItem?.mediaId
            publish(rules.onSessionItemChanged(id))
            if (id != null && _nowPlaying.value?.uuid != id) resolveStation(id)
            if (id == null) _nowPlaying.value = null
        }

        // No onPlayWhenReadyChanged, and no suppression handler: those existed to tell
        // a focus loss from a user pause, and only the resume rule ever needed that
        // distinction. isPlaying already goes false for both — and for a transient duck,
        // which media3 undoes by itself.
        override fun onPlayerError(error: PlaybackException) {
            publish(rules.onSessionError())
        }
    }

    private val controllerListener = object : MediaController.Listener {
        override fun onDisconnected(controller: MediaController) {
            // Without this a dead service left a corpse in the cached field, and the
            // fast path in connect() handed every later play() to it.
            this@RadioController.controller = null
            pending = null
            publish(rules.onDisconnected())
        }
    }

    /**
     * Connects lazily and runs [onReady] on the application thread once connected.
     *
     * Concurrent callers before the first connection completes are queued rather than
     * each building their own controller. A failed connection drains the queue without
     * invoking anything and drops the user's intent, so nothing sits waiting to resume
     * a session that will never arrive.
     */
    fun connect(onReady: (MediaController) -> Unit = {}) {
        controller?.let { onReady(it); return }
        waiting += onReady
        if (pending != null) return

        val token = SessionToken(context, ComponentName(context, RadioPlaybackService::class.java))
        val future = MediaController.Builder(context, token)
            .setListener(controllerListener)
            .buildAsync()
        pending = future
        future.addListener(
            {
                pending = null
                val ready = runCatching { future.get() }.getOrNull()
                if (closing) {
                    // The app asked to stop while this was connecting; the connection
                    // arrives only to be used for that.
                    ready?.stop()
                    ready?.release()
                    waiting.clear()
                    return@addListener
                }
                if (ready == null) {
                    waiting.clear()
                    publish(rules.onConnectFailed())
                    // The name too, not just the rule: play() sets this optimistically,
                    // and leaving it behind is what kept a dead station on the bar.
                    _nowPlaying.value = null
                    return@addListener
                }
                controller = ready
                ready.addListener(playerListener)
                seedFrom(ready)
                val callbacks = waiting.toList()
                waiting.clear()
                callbacks.forEach { it(ready) }
            },
            // Explicitly the main executor: listener registration and every command
            // below must happen on the application thread.
            ContextCompat.getMainExecutor(context),
        )
    }

    /**
     * Restores state from a session that outlived the process.
     *
     * The service can keep playing after the activity process dies; a fresh controller
     * would otherwise start empty and show no bar while radio is audibly playing.
     */
    private fun seedFrom(ready: MediaController) {
        val id = ready.currentMediaItem?.mediaId
        publish(
            rules.onSessionSeeded(
                stationId = id,
                playing = ready.isPlaying,
                buffering = ready.playbackState == Player.STATE_BUFFERING,
            ),
        )
        if (id != null && _nowPlaying.value?.uuid != id) resolveStation(id)
    }

    fun play(station: RadioStation) {
        _nowPlaying.value = station
        publish(rules.onUserPlay(station.uuid))
        connect { c ->
            c.setMediaItem(station.toMediaItem())
            c.prepare()
            c.play()
        }
    }

    fun togglePlayPause() {
        when (rules.onUserToggle(videoActive = videoSignal.videoActive.value)) {
            // prepare, not just play: these are live streams, and after any real pause
            // the origin has dropped the connection and the buffer is stale. Preparing
            // reopens at the live edge, which is the only "where it left off" a live
            // stream has.
            ToggleAction.Play -> connect { it.prepare(); it.play() }
            ToggleAction.Pause -> connect { it.pause() }
            ToggleAction.Nothing -> Unit
        }
        publish(rules.current)
    }

    /**
     * The app is closing: end playback and let the service go.
     *
     * Stopping alone left the session bound and idle. Releasing the controller as well
     * means the last client is gone, so the MediaSessionService can shut down instead
     * of lingering with a notification for something that is no longer playing.
     */
    fun stop() {
        closing = true
        publish(rules.onUserStop())
        _nowPlaying.value = null
        waiting.clear()
        controller?.let { live ->
            live.stop()
            live.removeListener(playerListener)
            live.release()
        }
        controller = null
    }

    private fun resolveStation(uuid: String) {
        scope.launch {
            val station = radioRepository.station(uuid) ?: return@launch
            withContext(Dispatchers.Main.immediate) {
                if (rules.current.stationId == uuid) _nowPlaying.value = station
            }
        }
    }

    private fun publish(next: RadioUiState) {
        _state.value = next
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
