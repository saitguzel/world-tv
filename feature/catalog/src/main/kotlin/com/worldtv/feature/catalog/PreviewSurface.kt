package com.worldtv.feature.catalog

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds the throwaway player used for grid previews.
 *
 * Deliberately separate from `:feature:player`'s factory rather than shared: browsing
 * must not depend on the full-screen player module, and the two have opposite tuning —
 * this one trades buffer for latency, because a preview that takes four seconds to
 * appear has already missed its moment.
 */
@Singleton
class PreviewPlayerFactory @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun create(): ExoPlayer {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 2_000,
                /* maxBufferMs = */ 6_000,
                /* bufferForPlaybackMs = */ 800,
                /* bufferForPlaybackAfterRebufferMs = */ 1_500,
            )
            .build()

        return ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .build()
            .apply {
                volume = 0f
                // Audio is disabled at the selector, not merely muted: a muted player
                // still decodes audio and still takes audio focus, which would silence
                // the radio service the user may be listening to while browsing.
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .build()
                repeatMode = ExoPlayer.REPEAT_MODE_OFF
            }
    }
}

/**
 * The muted preview drawn behind the grid once focus settles on a channel.
 *
 * A TextureView rather than the default SurfaceView: a SurfaceView punches a hole
 * through the window and would draw over the channel grid instead of behind it.
 */
@Composable
fun PreviewSurface(
    player: ExoPlayer,
    streamUrl: String?,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(streamUrl) {
        if (streamUrl == null) {
            player.stop()
            player.clearMediaItems()
            return@LaunchedEffect
        }
        player.setMediaItem(MediaItem.fromUri(streamUrl))
        player.prepare()
        player.playWhenReady = true
    }

    DisposableEffect(Unit) {
        onDispose {
            player.stop()
            player.clearMediaItems()
        }
    }

    PlayerSurface(
        player = player,
        surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
        modifier = modifier,
    )
}
