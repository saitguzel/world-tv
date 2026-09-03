package com.worldtv.feature.radio

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * What the radio reports about itself, and what a play/pause tap does.
 *
 * This class used to be about one thing: whether the radio should come back after a
 * video took audio focus. It no longer comes back at all — see [RadioPlaybackState] —
 * so what is pinned here is the other half: the state never claims something the
 * session has not reported, and a tap never takes sound away from a channel.
 */
class RadioPlaybackStateTest {

    @Test
    fun `a station is only playing once the session says so`() {
        val state = RadioPlaybackState()
        state.onUserPlay("trt-fm")

        // The regression that started all of this: the old controller set isPlaying at
        // the moment it issued the command, so the bar showed a pause icon over silence.
        assertEquals("trt-fm", state.current.stationId)
        assertEquals(false, state.current.playing)

        state.onSessionPlayingChanged(true)
        assertEquals(true, state.current.playing)
    }

    @Test
    fun `a video holding focus makes the play button do nothing at all`() {
        // Taking focus here would silently stop the channel the user is watching, from
        // a bar they are not looking at.
        val state = playing("trt-fm")
        state.onSessionPlayingChanged(false)

        assertEquals(ToggleAction.Nothing, state.onUserToggle(videoActive = true))
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
    }

    @Test
    fun `nothing to toggle before a station has been chosen`() {
        assertEquals(ToggleAction.Nothing, RadioPlaybackState().onUserToggle(videoActive = false))
    }

    @Test
    fun `stopping clears the station, so the bar goes away`() {
        val state = playing("trt-fm")
        state.onUserStop()

        assertEquals(null, state.current.stationId)
        assertEquals(false, state.current.playing)
    }

    @Test
    fun `a dead stream stops claiming to be connecting`() {
        val state = playing("trt-fm")
        state.onSessionBufferingChanged(true)
        state.onSessionError()

        assertEquals(false, state.current.playing)
        assertEquals(false, state.current.buffering)
    }

    @Test
    fun `a failed connection leaves nothing for the bar to advertise`() {
        val state = RadioPlaybackState()
        state.onUserPlay("trt-fm")
        state.onConnectFailed()

        assertEquals(null, state.current.stationId)
    }

    @Test
    fun `a session found already playing is adopted whole`() {
        // Process death, service still alive: the bar has to appear over sound that is
        // already coming out of the speakers.
        val state = RadioPlaybackState()
        state.onSessionSeeded(stationId = "trt-fm", playing = true, buffering = false)

        assertEquals("trt-fm", state.current.stationId)
        assertEquals(true, state.current.playing)
    }

    @Test
    fun `seeding an empty session does not erase a play still in flight`() {
        // Connecting is what play() itself does, and the session has no item yet at
        // that moment; treating that as "nothing is on" would drop the user's request.
        val state = RadioPlaybackState()
        state.onUserPlay("trt-fm")
        state.onSessionSeeded(stationId = null, playing = false, buffering = false)

        assertEquals("trt-fm", state.current.stationId)
    }

    @Test
    fun `losing the connection keeps the station but stops claiming it plays`() {
        val state = playing("trt-fm")
        state.onDisconnected()

        assertEquals("trt-fm", state.current.stationId)
        assertEquals(false, state.current.playing)
    }

    private fun playing(id: String) = RadioPlaybackState().apply {
        onUserPlay(id)
        onSessionPlayingChanged(true)
    }
}
