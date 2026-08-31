package com.worldtv.feature.player

/**
 * Decides when a playback attempt has proven itself.
 *
 * Pulled out of [PlayerViewModel] because the rule is the part worth getting right,
 * and because the view model cannot be exercised on the JVM.
 *
 * The subtlety is that a first frame is not the only proof. A stream carrying no video
 * track — and the iptv-org catalog has them — never renders one, so on an audio-only
 * entry the only positive signal the player will ever emit is reaching `STATE_READY`.
 * Treating the first frame as the sole proof meant the slow-load watchdog fired on a
 * stream that was playing perfectly, reported a playback failure worth two strikes,
 * and eliminated the stream on its second viewing.
 *
 * Media3 does not promise an order between `onTracksChanged` and the move to
 * `STATE_READY`, so both are accepted in either order and the attempt is confirmed as
 * soon as the pair of them says a video frame is not coming.
 *
 * Not thread-safe: every caller is a `Player.Listener` callback, and Media3 delivers
 * those on the application thread.
 */
class PlaybackConfirmation {

    private var confirmed = false
    private var ready = false

    /**
     * Whether a video frame is still expected.
     *
     * Starts true so that, until the tracks are known, only an actual frame confirms —
     * assuming otherwise would let a stalled video stream pass on `STATE_READY` alone.
     */
    private var expectVideo = true

    /** Discards the previous attempt's state. Call before preparing a stream. */
    fun reset() {
        confirmed = false
        ready = false
        expectVideo = true
    }

    /** The player rendered a frame — the strongest proof there is. */
    fun onRenderedFirstFrame(): Boolean = confirm()

    /** @param hasVideoTrack whether the prepared stream offers any video track at all. */
    fun onTracksKnown(hasVideoTrack: Boolean): Boolean {
        expectVideo = hasVideoTrack
        return if (!hasVideoTrack && ready) confirm() else false
    }

    fun onReady(): Boolean {
        ready = true
        return if (!expectVideo) confirm() else false
    }

    /**
     * @return true when this event is the one that confirms the attempt, so the caller
     *   reports the stream healthy exactly once. A video stream reaches READY and then
     *   renders a frame; the health store should hear about that once, not twice.
     */
    private fun confirm(): Boolean {
        if (confirmed) return false
        confirmed = true
        return true
    }
}
