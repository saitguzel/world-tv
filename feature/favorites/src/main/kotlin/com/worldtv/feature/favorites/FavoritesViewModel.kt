package com.worldtv.feature.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.worldtv.core.model.ChannelSummary
import com.worldtv.core.model.RadioStation
import com.worldtv.data.repository.ChannelRepository
import com.worldtv.data.repository.FavoritesRepository
import com.worldtv.data.repository.HealthRepository
import com.worldtv.data.repository.PlaybackQueueHolder
import com.worldtv.data.repository.RadioRepository
import com.worldtv.feature.radio.RadioController
import com.worldtv.feature.radio.RadioUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val channelRepository: ChannelRepository,
    private val favoritesRepository: FavoritesRepository,
    private val healthRepository: HealthRepository,
    private val playbackQueue: PlaybackQueueHolder,
    private val radioRepository: RadioRepository,
    private val radioController: RadioController,
) : ViewModel() {

    val favorites: StateFlow<List<ChannelSummary>> = channelRepository.favorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Radio favourites, newest first — shown below the channel grid on a phone. */
    val radioFavorites: StateFlow<List<RadioStation>> = radioRepository.favorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** What the radio session is doing, so a favourite row can say so honestly. */
    val nowPlaying: StateFlow<RadioStation?> = radioController.nowPlaying
    val radioPlayback: StateFlow<RadioUiState> = radioController.state

    val recents: StateFlow<List<ChannelSummary>> = channelRepository.recents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun toggleFavorite(channelId: String, currentlyFavorite: Boolean) {
        viewModelScope.launch {
            favoritesRepository.toggle(
                channelId,
                FavoritesRepository.Kind.CHANNEL,
                currentlyFavorite,
            )
        }
    }

    fun toggleRadioFavorite(uuid: String, currentlyFavorite: Boolean) {
        viewModelScope.launch {
            favoritesRepository.toggle(
                uuid,
                FavoritesRepository.Kind.RADIO,
                currentlyFavorite,
            )
        }
    }

    /** Plays a favourite radio right from this tab; the session outlives the screen. */
    fun playRadio(station: RadioStation) {
        radioController.play(station)
        viewModelScope.launch {
            favoritesRepository.recordWatch(station.uuid, FavoritesRepository.Kind.RADIO)
        }
    }

    /** Zapping from a favourite walks the favourites list, not the whole catalog. */
    fun onChannelOpened(startId: String) {
        playbackQueue.setQueue(favorites.value.map { it.channel.id }, startId)
    }

    /** Explicit refresh: favourites get the full two-tier check, on demand. */
    fun refreshFavoriteHealth() {
        viewModelScope.launch { healthRepository.refreshFavorites() }
    }
}