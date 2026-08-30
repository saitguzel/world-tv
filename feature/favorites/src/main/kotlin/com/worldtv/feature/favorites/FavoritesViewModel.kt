package com.worldtv.feature.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.worldtv.core.model.ChannelSummary
import com.worldtv.data.repository.ChannelRepository
import com.worldtv.data.repository.FavoritesRepository
import com.worldtv.data.repository.HealthRepository
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
) : ViewModel() {

    val favorites: StateFlow<List<ChannelSummary>> = channelRepository.favorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

    /** Explicit refresh: favourites get the full two-tier check, on demand. */
    fun refreshFavoriteHealth() {
        viewModelScope.launch { healthRepository.refreshFavorites() }
    }
}
