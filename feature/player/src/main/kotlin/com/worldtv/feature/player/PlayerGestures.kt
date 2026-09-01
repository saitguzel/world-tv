package com.worldtv.feature.player

import kotlin.math.abs

/** What a drag on the video surface means. */
sealed interface PlayerGesture {
    /** Next channel in the queue — the phone's answer to the remote's UP. */
    data object ZapUp : PlayerGesture

    data object ZapDown : PlayerGesture

    /** The channel list, which a remote opens with LEFT or RIGHT. */
    data object OpenChannels : PlayerGesture
}

/**
 * Maps a drag to a player action.
 *
 * The touch counterpart of `RemoteKey`, pulled out for the same reason
 * `PlaybackConfirmation` was: this is the most bug-prone new code on the phone player,
 * and the only other way to test it is on a device.
 *
 * Two rules carry it. A drag under the threshold is nothing at all — the surface also
 * takes taps to toggle the overlay, and a tap that wobbles a few pixels must not change
 * the channel. And a diagonal resolves to its dominant axis rather than firing both,
 * because a thumb never draws a straight line.
 */
object PlayerGestures {

    /**
     * Roughly a finger's width: large enough that the slop in a tap does not register,
     * small enough that a deliberate flick does.
     */
    const val DEFAULT_THRESHOLD_DP = 48

    fun fromDrag(dx: Float, dy: Float, thresholdPx: Float): PlayerGesture? {
        val horizontal = abs(dx)
        val vertical = abs(dy)
        if (horizontal < thresholdPx && vertical < thresholdPx) return null

        return if (vertical >= horizontal) {
            // Screen coordinates grow downward, so an upward drag is negative. Up moves
            // forward through the queue, matching the remote's UP key.
            if (dy < 0) PlayerGesture.ZapUp else PlayerGesture.ZapDown
        } else {
            PlayerGesture.OpenChannels
        }
    }
}

/**
 * The aspect ratio to give the video surface.
 *
 * A phone in portrait cannot fill the screen the way a television does, so the surface
 * needs a ratio — and the guard here is not defensive habit. ExoPlayer reports 0x0
 * while buffering, `0f / 0f` is NaN, and NaN does not degrade `Modifier.aspectRatio`,
 * it crashes it. Every zap passes through that state.
 */
fun videoAspectRatio(
    width: Int,
    height: Int,
    pixelWidthHeightRatio: Float = 1f,
): Float {
    if (width <= 0 || height <= 0) return DEFAULT_ASPECT_RATIO
    val ratio = width * pixelWidthHeightRatio / height
    return if (ratio.isFinite() && ratio > 0f) ratio else DEFAULT_ASPECT_RATIO
}

/** What almost every IPTV stream is, and a safe frame for anything unknown. */
const val DEFAULT_ASPECT_RATIO = 16f / 9f
