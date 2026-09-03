package com.worldtv.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.worldtv.core.model.Channel
import com.worldtv.core.model.ChannelQueue
import com.worldtv.core.common.CaptionSettings
import com.worldtv.core.common.playback.VideoPlaybackSignal
import com.worldtv.core.model.ChannelSummary
import com.worldtv.core.model.MediaTrack
import com.worldtv.core.model.TrackType
import com.worldtv.core.model.NowNext
import com.worldtv.core.model.Stream
import com.worldtv.core.model.StreamState
import com.worldtv.core.model.TimeProvider
import com.worldtv.data.health.PlaybackSignal
import com.worldtv.data.repository.ChannelRepository
import com.worldtv.data.repository.EpgRepository
import com.worldtv.data.repository.FavoritesRepository
import com.worldtv.data.repository.HealthRepository
import com.worldtv.data.repository.PlaybackQueueHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlayerUiState(
    val channel: Channel? = null,
    val stream: Stream? = null,
    val streamIndex: Int = 0,
    val totalStreams: Int = 0,
    val isBuffering: Boolean = true,
    val showChannelCard: Boolean = false,
    val showOverlay: Boolean = false,
    val tryingAlternative: Boolean = false,
    val geoWarning: Boolean = false,
    val unavailable: Boolean = false,
    val isFavorite: Boolean = false,
    val showChannelDrawer: Boolean = false,
    val showTrackPicker: Boolean = false,
    val subtitleTracks: List<MediaTrack> = emptyList(),
    val audioTracks: List<MediaTrack> = emptyList(),
    /**
     * Shape of the video, for the phone player. A television fills the screen and never
     * needs this; a phone in portrait would stretch or crop without it.
     */
    val videoAspectRatio: Float = DEFAULT_ASPECT_RATIO,
    /** Unused on TV, where live streams are never paused. */
    val isPlaying: Boolean = false,
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val channelRepository: ChannelRepository,
    private val healthRepository: HealthRepository,
    private val favoritesRepository: FavoritesRepository,
    private val epgRepository: EpgRepository,
    private val playerFactory: PlayerFactory,
    private val playbackQueue: PlaybackQueueHolder,
    private val captionSettings: CaptionSettings,
    private val labels: PlayerLabels,
    private val time: TimeProvider,
    private val videoSignal: VideoPlaybackSignal,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    /** What is on now and next for the channel being watched. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val nowNext: StateFlow<NowNext> = _uiState
        .map { it.channel?.id }
        .distinctUntilChanged()
        .flatMapLatest { channelId ->
            if (channelId == null) flowOf(NowNext(null, null))
            else epgRepository.nowAndNext(channelId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NowNext(null, null))

    /** The list being zapped through, also rendered by the side channel drawer. */
    val queue: StateFlow<ChannelQueue> = playbackQueue.queue

    /**
     * The drawer's contents.
     *
     * Only collected while the drawer is open — `WhileSubscribed` means a queue of a
     * few thousand channels is not held in memory for the whole watching session.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val drawerChannels: StateFlow<List<ChannelSummary>> = playbackQueue.queue
        .map { it.channelIds }
        .distinctUntilChanged()
        .flatMapLatest { ids -> channelRepository.summaries(ids) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(1_000), emptyList())

    private val playerDelegate = lazy {
        playerFactory.create().also { exoPlayer ->
            // The one place an ExoPlayer comes into existence, on both form factors.
            exoPlayer.addListener(listener)
            // Standing preference applied before anything loads, since the stream's
            // tracks are not known yet.
            TrackController.applyInitialPreferences(
                player = exoPlayer,
                captionsEnabled = captionSettings.isEnabled,
                captionLanguage = captionSettings.preferredLanguage,
                deviceLanguage = captionSettings.deviceLanguage,
            )
            // Claiming focus last is deliberate: `lazy` does not cache a failed
            // initialisation, so a throw above would leave a claim behind that the
            // retry then makes again, and a count that never falls back to zero
            // disables the radio's resume for the life of the process.
            if (isActive) holdSignal()
        }
    }

    /**
     * The player, built on first touch.
     *
     * Reached through the delegate rather than `by lazy` directly so [onCleared] can
     * ask whether one was ever built. Touching the property there would construct a
     * player purely to destroy it, and the acquire/release pair that comes with it
     * reads to the radio as "the video just ended" — a resume nobody asked for.
     */
    val player: ExoPlayer by playerDelegate

    /** Whether this view model currently counts as a video holder. Main thread only. */
    private var holdsSignal = false

    /**
     * Whether the screen showing this player is started.
     *
     * The player used to live until its back-stack entry was popped, so pressing Home —
     * or pushing any destination over the player without popping it, which the voice
     * search intent does — left video decoding, audible, and holding audio focus. The
     * focus was never abandoned either, which is what left the radio unable to resume.
     */
    private var isActive = true

    /**
     * The user's standing wish, in the radio's sense: it survives the screen stopping,
     * and only a pause press clears it. Without it, coming back from the background
     * would restart a stream the user had deliberately paused.
     */
    private var userWantsPlayback = true

    /** Latest track set reported by the player, needed to apply a selection. */
    private var currentTracks: Tracks = Tracks.EMPTY

    private var streams: List<Stream> = emptyList()
    private var streamIndex = 0
    private var playbackStartedAt = 0L

    /** Whether the current attempt has proven itself. See [PlaybackConfirmation]. */
    private val confirmation = PlaybackConfirmation()

    private var slowLoadJob: Job? = null
    private var channelCardJob: Job? = null

    /** Debounces rapid zapping so five presses load one channel, not five. */
    private var zapJob: Job? = null

    private val listener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            val current = streams.getOrNull(streamIndex) ?: return
            val signal = PlaybackErrorMapper.toSignal(error)

            // Report before advancing: this is the strongest health signal the app
            // ever gets, and it must survive the user zapping away immediately.
            healthRepository.reportPlayback(current.id, signal)

            // A failure of the user's own network is not this stream's fault, so
            // walking to the next stream would just fail the same way.
            if (signal is PlaybackSignal.NetworkFailure) {
                _uiState.update { it.copy(isBuffering = false, tryingAlternative = false) }
                return
            }
            advanceToNextStream()
        }

        override fun onRenderedFirstFrame() {
            if (confirmation.onRenderedFirstFrame()) confirmPlaybackWorking()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            _uiState.update { it.copy(isBuffering = playbackState == Player.STATE_BUFFERING) }
            if (playbackState == Player.STATE_READY && confirmation.onReady()) {
                confirmPlaybackWorking()
            }
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            _uiState.update {
                it.copy(
                    videoAspectRatio = videoAspectRatio(
                        width = videoSize.width,
                        height = videoSize.height,
                        pixelWidthHeightRatio = videoSize.pixelWidthHeightRatio,
                    ),
                )
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _uiState.update { it.copy(isPlaying = isPlaying) }
        }

        override fun onTracksChanged(tracks: Tracks) {
            currentTracks = tracks
            if (confirmation.onTracksKnown(TrackController.hasVideo(tracks))) {
                confirmPlaybackWorking()
            }
            _uiState.update {
                it.copy(
                    subtitleTracks = TrackController.optionsOf(
                        tracks = tracks,
                        type = TrackType.TEXT,
                        unknownLabel = labels.unknownTrack,
                        offLabel = labels.subtitlesOff,
                    ),
                    audioTracks = TrackController.optionsOf(
                        tracks = tracks,
                        type = TrackType.AUDIO,
                        unknownLabel = labels.unknownTrack,
                        offLabel = labels.subtitlesOff,
                    ),
                )
            }
        }
    }

    /** Up/down while watching. Wraps at the ends; debounced by [openChannel]. */
    fun zap(delta: Int) {
        val next = playbackQueue.shift(delta) ?: return
        openChannel(next)
    }

    /** Selecting a channel from the side drawer. */
    fun jumpTo(channelId: String) {
        playbackQueue.jumpTo(channelId)
        closeChannelDrawer()
        openChannel(channelId)
    }

    fun openChannel(channelId: String) {
        zapJob?.cancel()
        zapJob = viewModelScope.launch {
            // Zap debounce: holding up/down walks a list, and opening every channel
            // on the way costs a connection each and shows four wasted black frames.
            delay(ZAP_DEBOUNCE_MS)

            // Deep links and process death can reach the player without a list
            // having been browsed, and zapping must still do something sensible.
            playbackQueue.ensureContains(channelId)

            val channel = channelRepository.channel(channelId)
            streams = channelRepository.streamsFor(channelId)
            streamIndex = 0

            _uiState.update {
                it.copy(
                    channel = channel,
                    totalStreams = streams.size,
                    streamIndex = 0,
                    unavailable = streams.isEmpty(),
                    showChannelCard = true,
                )
            }
            showChannelCardBriefly()
            favoritesRepository.recordWatch(channelId, FavoritesRepository.Kind.CHANNEL)

            if (streams.isEmpty()) return@launch
            playCurrentStream()
            prefetchNeighbours()
        }
    }

    /**
     * Warms the neighbouring channels' manifests.
     *
     * The health probe fetches the same manifest a zap is about to need, over the same
     * pooled connection — so the next up/down press skips DNS, TCP and TLS. Worth
     * roughly a second, which is most of what makes zapping feel instant or not.
     */
    private fun prefetchNeighbours() {
        val neighbours = playbackQueue.queue.value.neighbourIds()
        if (neighbours.isEmpty()) return
        healthRepository.verifyVisibleChannels(viewModelScope, neighbours)
    }

    // MediaSource, and so setMediaSource and createMediaSource with it, is
    // @UnstableApi. This is the only place the view model reaches past media3's
    // stable Player surface; everything else here is Player, Tracks and
    // PlaybackException, which are stable.
    @androidx.annotation.OptIn(markerClass = [UnstableApi::class])
    private fun playCurrentStream() {
        val stream = streams.getOrNull(streamIndex) ?: run {
            _uiState.update { it.copy(unavailable = true, isBuffering = false) }
            return
        }

        playbackStartedAt = time.elapsedMillis()
        confirmation.reset()
        _uiState.update {
            it.copy(
                stream = stream,
                streamIndex = streamIndex,
                isBuffering = true,
                // A geo-blocked stream is played as a last resort, but the user is
                // told why it may not work rather than being left with a blank screen.
                geoWarning = stream.health.state == StreamState.GEO_BLOCKED,
            )
        }

        // Per-stream headers mean the media source factory has to be rebuilt for
        // each stream; sharing one across streams would send the wrong Referer.
        player.setMediaSource(
            playerFactory.mediaSourceFactory(stream)
                .createMediaSource(playerFactory.mediaItem(stream)),
        )
        player.prepare()
        // Opening a channel is a request to play, but the screen may have stopped while
        // the stream was still being resolved — starting then would put audio behind a
        // backgrounded app. [setActive] starts it on the way back.
        userWantsPlayback = true
        player.playWhenReady = isActive

        watchForSlowLoad()
    }

    /**
     * If nothing has rendered after four seconds, say so and move on.
     *
     * ExoPlayer will keep retrying a stalled origin well past the point the user has
     * decided the app is broken.
     */
    private fun watchForSlowLoad() {
        slowLoadJob?.cancel()
        slowLoadJob = viewModelScope.launch {
            delay(SLOW_LOAD_MS)
            _uiState.update { it.copy(tryingAlternative = true) }
            delay(SLOW_LOAD_GIVE_UP_MS)
            val current = streams.getOrNull(streamIndex)
            if (current != null) {
                healthRepository.reportPlayback(
                    current.id,
                    PlaybackSignal.Failed(PlaybackException.ERROR_CODE_TIMEOUT),
                )
            }
            advanceToNextStream()
        }
    }

    /**
     * Records that the current attempt is actually playing: stops the watchdog and
     * tells the health engine, which counts this as the strongest signal it gets.
     *
     * Called at most once per attempt — [PlaybackConfirmation] owns that guarantee.
     */
    private fun confirmPlaybackWorking() {
        val current = streams.getOrNull(streamIndex) ?: return
        healthRepository.reportPlayback(
            current.id,
            PlaybackSignal.RenderedFirstFrame((time.elapsedMillis() - playbackStartedAt).toInt()),
        )
        slowLoadJob?.cancel()
        _uiState.update {
            it.copy(isBuffering = false, tryingAlternative = false, unavailable = false)
        }
    }

    /** Walks to the next alternative for the same channel. */
    private fun advanceToNextStream() {
        slowLoadJob?.cancel()
        streamIndex += 1
        if (streamIndex >= streams.size) {
            _uiState.update {
                it.copy(unavailable = true, isBuffering = false, tryingAlternative = false)
            }
            return
        }
        _uiState.update { it.copy(tryingAlternative = true) }
        playCurrentStream()
    }

    fun retry() {
        streamIndex = 0
        _uiState.update { it.copy(unavailable = false) }
        playCurrentStream()
    }

    fun toggleOverlay() = _uiState.update { it.copy(showOverlay = !it.showOverlay) }

    /**
     * Pause and resume.
     *
     * The app had no such action at all: a remote has dedicated media keys and these
     * are live streams, so nothing on TV ever needed one. A phone HUD without a play
     * button is simply incomplete.
     */
    fun togglePlayPause() {
        userWantsPlayback = !player.playWhenReady
        player.playWhenReady = userWantsPlayback
    }

    /**
     * Follows the lifecycle of whatever is showing the player.
     *
     * Stopping abandons audio focus as well as pausing. Pausing alone is not enough:
     * focus is what the radio waits on, so a paused player that still held it would
     * keep the radio silent with nothing at all playing.
     */
    fun setActive(active: Boolean) {
        isActive = active
        if (!playerDelegate.isInitialized()) return
        if (active) {
            holdSignal()
            player.playWhenReady = userWantsPlayback
        } else {
            player.playWhenReady = false
            dropSignal()
        }
    }

    private fun holdSignal() {
        if (holdsSignal) return
        holdsSignal = true
        videoSignal.acquire()
    }

    private fun dropSignal() {
        if (!holdsSignal) return
        holdsSignal = false
        videoSignal.release()
    }

    fun openTrackPicker() =
        _uiState.update { it.copy(showTrackPicker = true, showOverlay = false) }

    fun closeTrackPicker() = _uiState.update { it.copy(showTrackPicker = false) }

    fun selectTrack(track: MediaTrack) {
        TrackController.select(player, currentTracks, track)
        closeTrackPicker()
    }

    fun openChannelDrawer() = _uiState.update { it.copy(showChannelDrawer = true, showOverlay = false) }

    fun closeChannelDrawer() = _uiState.update { it.copy(showChannelDrawer = false) }

    fun hideOverlay() = _uiState.update { it.copy(showOverlay = false) }

    fun toggleFavorite() {
        val channelId = _uiState.value.channel?.id ?: return
        val current = _uiState.value.isFavorite
        viewModelScope.launch {
            favoritesRepository.toggle(channelId, FavoritesRepository.Kind.CHANNEL, current)
            _uiState.update { it.copy(isFavorite = !current) }
        }
    }

    private fun showChannelCardBriefly() {
        channelCardJob?.cancel()
        channelCardJob = viewModelScope.launch {
            _uiState.update { it.copy(showChannelCard = true) }
            delay(CHANNEL_CARD_MS)
            _uiState.update { it.copy(showChannelCard = false) }
        }
    }

    override fun onCleared() {
        // Asked of the delegate, not the property: a view model that never showed a
        // surface — an unavailable channel, an immediate back — would otherwise build a
        // player here just to tear it down.
        if (playerDelegate.isInitialized()) {
            player.removeListener(listener)
            player.release()
            // After release, not before: release is what actually abandons audio focus,
            // and the radio resuming earlier would collide with a player still holding
            // it. A no-op when the screen already stopped and let go.
            dropSignal()
        }
        super.onCleared()
    }

    private companion object {
        /**
         * Holding up/down walks a list. Loading every channel on the way costs a
         * connection each and shows the user four black frames they never asked for,
         * so only the channel they land on is opened.
         */
        const val ZAP_DEBOUNCE_MS = 300L
        const val CHANNEL_CARD_MS = 3_000L
        const val SLOW_LOAD_MS = 4_000L
        const val SLOW_LOAD_GIVE_UP_MS = 4_000L
    }
}
