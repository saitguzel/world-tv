package com.worldtv.feature.radio

/** What a tap on play/pause should actually do. */
sealed interface ToggleAction {
    data object Play : ToggleAction
    data object Pause : ToggleAction

    /**
     * Nothing at all: either there is no station to act on, or a video is holding audio
     * focus and playing now would silently kill the channel the user is watching from a
     * bar they are not even looking at.
     */
    data object Nothing : ToggleAction
}

/** Everything the radio UI needs. */
data class RadioUiState(
    val stationId: String? = null,
    val playing: Boolean = false,
    val buffering: Boolean = false,
)

/**
 * The rule for what the radio should be doing.
 *
 * Pure by design — no Android, no media3 — because this environment has no usable
 * emulator and a rule that can only be checked by hand on a device is worth much less
 * than one with a testable core. The controller translates media3 callbacks into the
 * vocabulary here and does as it is told.
 *
 * **The radio does not resume by itself.** An earlier version kept the user's standing
 * wish alive across a focus loss so that leaving a channel handed the radio back. It
 * worked exactly as designed and was wrong in use: the same event — video letting go of
 * audio focus — happens when you back out of the player, when you switch tabs, and when
 * you leave the app, and sound starting on its own in two of those three is startling
 * rather than helpful. Opening a channel now ends the radio session; starting it again
 * is one tap on a bar that never went away.
 *
 * What is left is only ever a report of what the session is doing, which is why every
 * field here is written from a session callback rather than from a command we issued.
 *
 * Not thread-safe, and it does not need to be: every caller is a media3 callback, and
 * those arrive on the application thread.
 */
class RadioPlaybackState {

    var current: RadioUiState = RadioUiState()
        private set

    fun onUserPlay(stationId: String): RadioUiState = set(current.copy(stationId = stationId))

    fun onUserToggle(videoActive: Boolean): ToggleAction = when {
        current.stationId == null -> ToggleAction.Nothing
        current.playing -> ToggleAction.Pause
        // Defensive: the bar is hidden while the player is on screen, and a player that
        // has been navigated away from has already let go of focus. If both of those
        // ever stop being true, the radio must still not take a channel's sound away.
        videoActive -> ToggleAction.Nothing
        else -> ToggleAction.Play
    }

    fun onUserStop(): RadioUiState = set(RadioUiState())

    fun onSessionPlayingChanged(playing: Boolean): RadioUiState = set(
        current.copy(
            playing = playing,
            buffering = if (playing) false else current.buffering,
        ),
    )

    fun onSessionBufferingChanged(buffering: Boolean): RadioUiState =
        set(current.copy(buffering = buffering))

    fun onSessionItemChanged(stationId: String?): RadioUiState =
        set(current.copy(stationId = stationId))

    /**
     * Restores state from a session that outlived the process.
     *
     * The service can keep playing after the activity process dies; a fresh controller
     * would otherwise start empty and show no bar while radio is audibly playing.
     *
     * An empty session changes nothing: connecting is also what a fresh `play()` does,
     * and the session has not been given its item yet at that point — reading it as
     * "nothing is on" would erase the station that call just recorded.
     */
    fun onSessionSeeded(stationId: String?, playing: Boolean, buffering: Boolean): RadioUiState {
        if (stationId == null) return current
        return set(current.copy(stationId = stationId, playing = playing, buffering = buffering))
    }

    /** A dead stream. Clearing buffering too, or the row claims to be connecting forever. */
    fun onSessionError(): RadioUiState = set(current.copy(playing = false, buffering = false))

    /** The controller lost its connection; playback state is unknown. */
    fun onDisconnected(): RadioUiState = set(current.copy(playing = false, buffering = false))

    /**
     * Could not reach the service.
     *
     * Cleared completely, station included: keeping the id left the mini player on
     * screen advertising something that would never play, with a play button that only
     * failed again.
     */
    fun onConnectFailed(): RadioUiState = set(RadioUiState())

    private fun set(next: RadioUiState): RadioUiState {
        current = next
        return next
    }
}
