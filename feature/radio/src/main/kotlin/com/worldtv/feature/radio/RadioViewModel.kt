package com.worldtv.feature.radio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.worldtv.core.model.Category
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
import androidx.lifecycle.SavedStateHandle

@HiltViewModel
class RadioViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val radioRepository: RadioRepository,
    private val favoritesRepository: FavoritesRepository,
    private val controller: RadioController,
    private val channelRepository: ChannelRepository,
) : ViewModel() {

    val nowPlaying: StateFlow<RadioStation?> = controller.nowPlaying
    val isPlaying: StateFlow<Boolean> = controller.isPlaying

    /**
     * The session's own account of itself, for the rows.
     *
     * The lists used to mark the current station as playing and never read anything
     * else, so a station that was paused, ducked by a notification or dead on arrival
     * still showed as on air.
     */
    val playback: StateFlow<RadioUiState> = controller.state

    private val _country = MutableStateFlow<String?>(null)
    val country: StateFlow<String?> = _country.asStateFlow()

    private val _category = MutableStateFlow<String?>(null)
    val category: StateFlow<String?> = _category.asStateFlow()

    init {
        // A search result or deep link that names a station starts it — once. The
        // flag lives in saved state, so coming back to the Radio tab (its entry is
        // restored, not recreated) or returning after process death does not restart
        // a station the user has since paused or changed. The filters stay put.
        val station = savedStateHandle.get<String>(STATION_ARG)
        if (!station.isNullOrBlank() && savedStateHandle.get<Boolean>(STATION_CONSUMED) != true) {
            savedStateHandle[STATION_CONSUMED] = true
            playStation(station)
        }
    }

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

    /** The station tags in the table, aggregated into a short category list. */
    val availableCategories: StateFlow<List<Category>> = radioRepository.categories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val stations: Flow<PagingData<RadioStation>> = combine(_country, _category) { country, tag ->
        country to tag
    }
        .flatMapLatest { (code, tag) -> radioRepository.stations(code, tag) }
        .cachedIn(viewModelScope)

    val favorites: StateFlow<List<RadioStation>> = radioRepository.favorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setCountry(code: String?) {
        _country.value = code
    }

    fun setCategory(tag: String?) {
        _category.value = tag
    }

    /** Picks a random station from the current country/category filter and plays it. */
    fun playRandom() {
        viewModelScope.launch {
            radioRepository.randomStation(_country.value, _category.value)
                ?.let(::play)
        }
    }

    /**
     * Plays a station by id, for a search result or deep link that names a station.
     * Nothing selected when the id is absent or unknown — a stale deep link must not
     * start an old favourite by accident.
     */
    fun playStation(uuid: String?) {
        if (uuid == null) return
        viewModelScope.launch {
            radioRepository.station(uuid)?.let(::play)
        }
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

    // Deliberately no onCleared override. RadioController is a @Singleton whose
    // connection is meant to outlive any one screen: releasing it here dropped the
    // MediaController every time the user left the radio screen, forcing a reconnect
    // on the way back and — once a now-playing bar exists outside this screen —
    // breaking it outright. Playback itself was never stopped here either, so nothing
    // about the "leaving radio must not silence radio" contract changes.

    private companion object {
        /** The nav argument name; see the radio route in the navigation graphs. */
        const val STATION_ARG = "station"
        const val STATION_CONSUMED = "stationConsumed"
    }
}
