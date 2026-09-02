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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
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
 * Threading: media3 delivers listener callbacks on the application thread and requires
 * the controller to be touched there. The resume collector runs on the application
 * scope, which is `Dispatchers.Default`, so every command it issues hops to Main first.
 * That hop is not optional; skipping it is an exception at runtime.
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

        // The whole resume feature rests on this reason code. A focus loss is the
        // system taking the radio away, and the user still wants it; a user request is
        // the user changing their mind. Both look identical from outside.
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (playWhenReady) return
            val cause = when (reason) {
                Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS -> PauseCause.FOCUS_LOSS
                Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
                Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE -> PauseCause.USER
                else -> return
            }
            publish(rules.onSessionPaused(cause))
        }

        override fun onPlaybackSuppressionReasonChanged(reason: Int) {
            // A transient duck is media3's to undo. Recording it keeps the icon honest
            // without letting the resume collector claim it.
            if (reason == Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS) {
                publish(rules.onSessionPaused(PauseCause.TRANSIENT))
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            publish(rules.onSessionPaused(PauseCause.ERROR))
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

    init {
        scope.launch {
            videoSignal.videoActive
                .filter { !it }
                .collect { onVideoReleased() }
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
                if (ready == null) {
                    waiting.clear()
                    publish(rules.onConnectFailed())
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
        publish(rules.onSessionItemChanged(id))
        publish(rules.onSessionPlayingChanged(ready.isPlaying))
        publish(rules.onSessionBufferingChanged(ready.playbackState == Player.STATE_BUFFERING))
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
            ToggleAction.Play -> connect { it.prepare(); it.play() }
            ToggleAction.Pause -> connect { it.pause() }
            // Video holds focus. Playing now would silently kill the channel the user is
            // watching, from a bar they are not looking at. The collector starts us
            // once the video goes away.
            ToggleAction.DeferUntilVideoEnds -> Unit
            ToggleAction.Nothing -> Unit
        }
        publish(rules.current)
    }

    fun stop() {
        if (rules.current.stationId == null) return
        publish(rules.onUserStop())
        _nowPlaying.value = null
        controller?.stop()
    }

    fun release() {
        controller?.removeListener(playerListener)
        controller?.release()
        controller = null
    }

    private suspend fun onVideoReleased() {
        if (rules.onVideoReleased() != ResumeDecision.RESUME) return
        // Abandoning focus is not synchronous with the video player's release. A
        // request that lands before it has let go collides with a player that still
        // holds focus. This is a guess, and a named one.
        delay(RESUME_SETTLE_MS)
        withContext(Dispatchers.Main.immediate) {
            if (rules.onVideoReleased() != ResumeDecision.RESUME) return@withContext
            // prepare, not just play: these are live streams, and after any real pause
            // the origin has dropped the connection and the buffer is stale. Preparing
            // reopens at the live edge — which is what "where it left off" means here.
            connect { c -> c.prepare(); c.play() }
        }
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

    private companion object {
        const val RESUME_SETTLE_MS = 250L
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
