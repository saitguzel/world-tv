package com.worldtv.feature.radio

import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dagger.hilt.android.AndroidEntryPoint

/**
 * Background radio playback.
 *
 * A separate ExoPlayer instance from the video player, but sharing audio focus with
 * it: the two must never play at once, and `AudioAttributes` with
 * `handleAudioFocus = true` is what makes the system arbitrate that for us.
 *
 * Notably this service does *not* keep the screen on. Radio with a lit screen burns a
 * TV panel for no reason — the MediaSession alone is enough to keep playback alive and
 * to surface a "now playing" card on the home screen.
 */
@AndroidEntryPoint
class RadioPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            // A radio stream that drops should reconnect rather than stop; unlike
            // video, there is nothing on screen to tell the user to press play again.
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player?.playWhenReady != true) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
