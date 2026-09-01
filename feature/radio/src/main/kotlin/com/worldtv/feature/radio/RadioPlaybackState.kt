package com.worldtv.feature.radio

/** Why playback stopped, translated from media3's constants at the boundary. */
enum class PauseCause {
    /** The user pressed pause, here or in the notification. Intent is gone. */
    USER,

    /** Something else took audio focus for good — the video player. Intent survives. */
    FOCUS_LOSS,

    /** A notification ducked us. media3 resumes this itself; do not touch it. */
    TRANSIENT,

    ERROR,
}

/** What to do when the video player goes away. */
enum class ResumeDecision { RESUME, DO_NOTHING }

/** What a tap on play/pause should actually do. */
sealed interface ToggleAction {
    data object Play : ToggleAction
    data object Pause : ToggleAction

    /**
     * Video is holding focus. Record that the user wants radio, but issue no command —
     * playing now would take focus from the channel they are watching and kill it
     * silently, from a bar they are not even looking at.
     */
    data object DeferUntilVideoEnds : ToggleAction

    data object Nothing : ToggleAction
}

/** Everything the radio UI needs, and the whole basis for the resume decision. */
data class RadioUiState(
    val stationId: String? = null,
    val playing: Boolean = false,
    val buffering: Boolean = false,
    /** The user's standing wish, which outlives a focus loss but not a user pause. */
    val userWantsPlayback: Boolean = false,
    val interruptedByFocusLoss: Boolean = false,
)

/**
 * The rule for what the radio should be doing.
 *
 * Pure by design — no Android, no media3 — for the same reason `PlaybackConfirmation`
 * is: this environment has no usable emulator, so a rule that can only be checked by
 * hand on a device is worth much less than one with a testable core. The controller
 * translates media3 callbacks into the enums above and does as it is told.
 *
 * The distinction that carries the whole feature is *why* playback stopped. media3
 * reports it — a permanent focus loss is the system taking the radio away and the user
 * still wants it back; a user pause is the user changing their mind. Deriving that a
 * second time, worse, in our own flags would be the obvious mistake.
 *
 * Not thread-safe: every caller is a media3 callback, and those arrive on the
 * application thread.
 */
class RadioPlaybackState {

    var current: RadioUiState = RadioUiState()
        private set

    fun onUserPlay(stationId: String): RadioUiState = set(
        current.copy(
            stationId = stationId,
            userWantsPlayback = true,
            interruptedByFocusLoss = false,
        ),
    )

    fun onUserToggle(videoActive: Boolean): ToggleAction = when {
        current.stationId == null -> ToggleAction.Nothing
        current.playing -> {
            set(current.copy(userWantsPlayback = false))
            ToggleAction.Pause
        }
        videoActive -> {
            set(current.copy(userWantsPlayback = true, interruptedByFocusLoss = true))
            ToggleAction.DeferUntilVideoEnds
        }
        else -> {
            set(current.copy(userWantsPlayback = true))
            ToggleAction.Play
        }
    }

    fun onUserStop(): RadioUiState = set(RadioUiState())

    fun onSessionPlayingChanged(playing: Boolean): RadioUiState = set(
        current.copy(
            playing = playing,
            buffering = if (playing) false else current.buffering,
            // Playing again means whatever interrupted us is over.
            interruptedByFocusLoss = if (playing) false else current.interruptedByFocusLoss,
        ),
    )

    fun onSessionBufferingChanged(buffering: Boolean): RadioUiState =
        set(current.copy(buffering = buffering))

    fun onSessionPaused(cause: PauseCause): RadioUiState = when (cause) {
        // The user changed their mind. Nothing should bring it back by itself.
        PauseCause.USER -> set(current.copy(playing = false, userWantsPlayback = false))

        PauseCause.FOCUS_LOSS -> set(
            current.copy(playing = false, interruptedByFocusLoss = true),
        )

        // media3 resumes a transient duck on its own; claiming it as ours would make
        // us request focus a second time when it ends.
        PauseCause.TRANSIENT -> set(current.copy(playing = false))

        // A dead stream is not something to silently retry into.
        PauseCause.ERROR -> set(
            current.copy(playing = false, buffering = false, userWantsPlayback = false),
        )
    }

    fun onSessionItemChanged(stationId: String?): RadioUiState =
        set(current.copy(stationId = stationId))

    /** The controller lost its connection; playback state is unknown, intent is not. */
    fun onDisconnected(): RadioUiState =
        set(current.copy(playing = false, buffering = false))

    /** Could not reach the service. Drop the intent so no resume is queued forever. */
    fun onConnectFailed(): RadioUiState =
        set(current.copy(playing = false, buffering = false, userWantsPlayback = false))

    /**
     * Video has gone away. Resume only if the user still wants radio and it was the
     * system, not them, that stopped it.
     */
    fun onVideoReleased(): ResumeDecision = when {
        current.stationId == null -> ResumeDecision.DO_NOTHING
        !current.userWantsPlayback -> ResumeDecision.DO_NOTHING
        current.playing -> ResumeDecision.DO_NOTHING
        else -> ResumeDecision.RESUME
    }

    private fun set(next: RadioUiState): RadioUiState {
        current = next
        return next
    }
}
