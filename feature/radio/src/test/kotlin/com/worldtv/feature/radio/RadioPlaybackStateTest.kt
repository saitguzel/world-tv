package com.worldtv.feature.radio

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The resume rule.
 *
 * This exists because the alternative is checking it by hand on a device, and the
 * distinction it encodes — the system took the radio away versus the user put it down —
 * is invisible from the outside: both look like "paused".
 */
class RadioPlaybackStateTest {

    @Test
    fun `the system taking focus leaves the user still wanting radio`() {
        val state = playing("trt-fm")
        state.onSessionPaused(PauseCause.FOCUS_LOSS)

        assertEquals(ResumeDecision.RESUME, state.onVideoReleased())
    }

    @Test
    fun `a user pause is not undone by a video ending`() {
        // The regression that will actually happen: someone pauses the radio, watches a
        // channel, comes back, and the radio starts talking at them again.
        val state = playing("trt-fm")
        state.onUserToggle(videoActive = false)

        assertEquals(ResumeDecision.DO_NOTHING, state.onVideoReleased())
    }

    @Test
    fun `stopping clears the station, so nothing resumes`() {
        val state = playing("trt-fm")
        state.onUserStop()

        assertEquals(ResumeDecision.DO_NOTHING, state.onVideoReleased())
    }

    @Test
    fun `nothing resumes when the radio was never started`() {
        assertEquals(ResumeDecision.DO_NOTHING, RadioPlaybackState().onVideoReleased())
    }

    @Test
    fun `radio still playing is not resumed again`() {
        val state = playing("trt-fm")
        assertEquals(ResumeDecision.DO_NOTHING, state.onVideoReleased())
    }

    @Test
    fun `a transient duck is left to media3`() {
        // media3 resumes these itself. Claiming it would make us request focus twice.
        val state = playing("trt-fm")
        state.onSessionPaused(PauseCause.TRANSIENT)

        assertEquals(ResumeDecision.RESUME, state.onVideoReleased())
        // ...but the flag says it was not a focus loss we own.
        assertEquals(false, state.current.interruptedByFocusLoss)
    }

    @Test
    fun `a dead stream does not queue a resume forever`() {
        val state = playing("trt-fm")
        state.onSessionPaused(PauseCause.ERROR)

        assertEquals(ResumeDecision.DO_NOTHING, state.onVideoReleased())
    }

    @Test
    fun `pressing play while a video holds focus defers instead of killing it`() {
        // Taking focus here would silently stop the channel the user is watching, from
        // a bar they are not looking at.
        val state = RadioPlaybackState()
        state.onUserPlay("trt-fm")
        state.onSessionPlayingChanged(true)
        state.onSessionPaused(PauseCause.FOCUS_LOSS)

        assertEquals(ToggleAction.DeferUntilVideoEnds, state.onUserToggle(videoActive = true))
        assertEquals(ResumeDecision.RESUME, state.onVideoReleased())
    }

    @Test
    fun `a failed connection drops the intent rather than queueing a resume`() {
        val state = RadioPlaybackState()
        state.onUserPlay("trt-fm")
        state.onConnectFailed()

        assertEquals(ResumeDecision.DO_NOTHING, state.onVideoReleased())
    }

    @Test
    fun `toggling twice with the session echoing back lands where expected`() {
        // The direct regression test for the old inverted flag: the controller used to
        // read isPlaying straight after issuing an async command.
        val state = playing("trt-fm")

        assertEquals(ToggleAction.Pause, state.onUserToggle(videoActive = false))
        state.onSessionPlayingChanged(false)
        assertEquals(ToggleAction.Play, state.onUserToggle(videoActive = false))
        state.onSessionPlayingChanged(true)

        assertEquals(true, state.current.playing)
        assertEquals(true, state.current.userWantsPlayback)
    }

    private fun playing(id: String) = RadioPlaybackState().apply {
        onUserPlay(id)
        onSessionPlayingChanged(true)
    }
}
