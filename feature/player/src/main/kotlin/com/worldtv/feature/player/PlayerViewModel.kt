package com.worldtv.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import com.worldtv.core.model.Channel
import com.worldtv.core.model.ChannelQueue
import com.worldtv.core.common.CaptionSettings
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
    private val time: TimeProvider,
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

    val player: ExoPlayer by lazy {
        playerFactory.create().also { exoPlayer ->
            exoPlayer.addListener(listener)
            // Standing preference applied before anything loads, since the stream's
            // tracks are not known yet.
            TrackController.applyInitialPreferences(
                player = exoPlayer,
                captionsEnabled = captionSettings.isEnabled,
                captionLanguage = captionSettings.preferredLanguage,
                deviceLanguage = captionSettings.deviceLanguage,
            )
        }
    }

    /** Latest track set reported by the player, needed to apply a selection. */
    private var currentTracks: Tracks = Tracks.EMPTY

    private var streams: List<Stream> = emptyList()
    private var streamIndex = 0
    private var playbackStartedAt = 0L
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
            val current = streams.getOrNull(streamIndex) ?: return
            val timeToFirstFrame = (time.elapsedMillis() - playbackStartedAt).toInt()
            healthRepository.reportPlayback(
                current.id,
                PlaybackSignal.RenderedFirstFrame(timeToFirstFrame),
            )
            slowLoadJob?.cancel()
            _uiState.update {
                it.copy(isBuffering = false, tryingAlternative = false, unavailable = false)
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            _uiState.update { it.copy(isBuffering = playbackState == Player.STATE_BUFFERING) }
        }

        override fun onTracksChanged(tracks: Tracks) {
            currentTracks = tracks
            _uiState.update {
                it.copy(
                    subtitleTracks = TrackController.optionsOf(tracks, TrackType.TEXT),
                    audioTracks = TrackController.optionsOf(tracks, TrackType.AUDIO),
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

    private fun playCurrentStream() {
        val stream = streams.getOrNull(streamIndex) ?: run {
            _uiState.update { it.copy(unavailable = true, isBuffering = false) }
            return
        }

        playbackStartedAt = time.elapsedMillis()
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
        player.playWhenReady = true

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
        player.removeListener(listener)
        player.release()
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
