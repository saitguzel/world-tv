package com.worldtv.feature.radio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.worldtv.core.model.Country
import com.worldtv.core.model.RadioStation
import com.worldtv.data.repository.FavoritesRepository
import com.worldtv.data.repository.ChannelRepository
import com.worldtv.data.repository.RadioRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class RadioViewModel @Inject constructor(
    private val radioRepository: RadioRepository,
    private val favoritesRepository: FavoritesRepository,
    private val controller: RadioController,
    private val channelRepository: ChannelRepository,
) : ViewModel() {

    val nowPlaying: StateFlow<RadioStation?> = controller.nowPlaying
    val isPlaying: StateFlow<Boolean> = controller.isPlaying

    private val _country = MutableStateFlow<String?>(null)
    val country: StateFlow<String?> = _country.asStateFlow()

    /**
     * Countries that actually have stations, named and flagged.
     *
     * Radio Browser returns bare ISO codes; joining them against the catalog's country
     * table is what turns a list of "TR, US, GB" into something readable from three
     * metres away.
     */
    val availableCountries: StateFlow<List<Country>> = combine(
        radioRepository.availableCountries(),
        channelRepository.countries(),
    ) { codes, countries ->
        val byCode = countries.associateBy { it.code }
        codes.mapNotNull { code ->
            byCode[code] ?: Country(code = code, name = code, flag = "", languages = emptyList())
        }.sortedBy { it.name }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val stations: Flow<PagingData<RadioStation>> = _country
        .flatMapLatest { code -> radioRepository.stations(code) }
        .cachedIn(viewModelScope)

    val favorites: StateFlow<List<RadioStation>> = radioRepository.favorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setCountry(code: String?) {
        _country.value = code
    }

    fun toggleFavorite(uuid: String, currentlyFavorite: Boolean) {
        viewModelScope.launch {
            favoritesRepository.toggle(uuid, FavoritesRepository.Kind.RADIO, currentlyFavorite)
        }
    }

    fun play(station: RadioStation) {
        controller.play(station)
        viewModelScope.launch {
            favoritesRepository.recordWatch(station.uuid, FavoritesRepository.Kind.RADIO)
        }
    }

    fun togglePlayPause() = controller.togglePlayPause()

    override fun onCleared() {
        // The controller is released, but playback is not stopped: leaving the radio
        // screen must not silence the radio — that is the whole point of the mode.
        controller.release()
        super.onCleared()
    }
}
