package com.worldtv.feature.radio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.worldtv.core.model.RadioStation
import com.worldtv.data.repository.FavoritesRepository
import com.worldtv.data.repository.RadioRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class RadioViewModel @Inject constructor(
    private val radioRepository: RadioRepository,
    private val favoritesRepository: FavoritesRepository,
) : ViewModel() {

    private val _country = MutableStateFlow<String?>(null)
    val country: StateFlow<String?> = _country.asStateFlow()

    val availableCountries: StateFlow<List<String>> = radioRepository.availableCountries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

    fun recordListen(uuid: String) {
        viewModelScope.launch {
            favoritesRepository.recordWatch(uuid, FavoritesRepository.Kind.RADIO)
        }
    }
}
