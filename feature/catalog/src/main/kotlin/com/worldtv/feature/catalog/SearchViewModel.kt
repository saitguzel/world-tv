package com.worldtv.feature.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.worldtv.core.model.ChannelSummary
import com.worldtv.core.model.Country
import com.worldtv.core.model.RadioStation
import com.worldtv.data.repository.ChannelRepository
import com.worldtv.data.repository.PlaybackQueueHolder
import com.worldtv.data.repository.RadioRepository
import com.worldtv.data.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What a search is searching. */
enum class SearchKind { TV, RADIO }

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val channelRepository: ChannelRepository,
    private val radioRepository: RadioRepository,
    private val playbackQueue: PlaybackQueueHolder,
    private val preferences: UserPreferencesRepository,
) : ViewModel() {

    /**
     * Previous searches, most recent first.
     *
     * Kept next to the keyboard because most searches on a TV are repeats — the same
     * handful of channels, typed painfully the first time and clicked thereafter.
     */
    val recentSearches: StateFlow<List<String>> = preferences.preferences
        .map { it.recentSearches }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * The channels most watched on this device, as a starting point before a single
     * letter is typed — the TV half of the blank-query screen.
     */
    val popularChannels: StateFlow<List<ChannelSummary>> = channelRepository
        .recents(limit = POPULAR_LIMIT)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Countries with live channels, for the search country filter. */
    val countries: StateFlow<List<Country>> = channelRepository.countries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _kind = MutableStateFlow(SearchKind.TV)
    val kind: StateFlow<SearchKind> = _kind.asStateFlow()

    private val _country = MutableStateFlow<String?>(null)
    val country: StateFlow<String?> = _country.asStateFlow()

    init {
        // Search opens on the device's own country rather than the whole world: a
        // Turkish user searching "haber" wants Turkish news channels, not the world's
        // "Haber" entries. The user's explicit choice in settings travels through the
        // same preference, so a device that reports nothing simply falls back to it.
        viewModelScope.launch {
            if (_country.value == null) _country.value = preferences.homeCountry()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    private val countryScopedQuery = _query
        // Short enough to feel live as letters are added on a grid keyboard, long
        // enough that a held-down key does not issue a query per repeat.
        .debounce(200)
        .combine(_country) { text, country -> text to country }

    @OptIn(ExperimentalCoroutinesApi::class)
    val channelResults: StateFlow<List<ChannelSummary>> = countryScopedQuery
        .flatMapLatest { (text, country) ->
            // Incremental filtering is the whole point on TV: the target should be
            // reachable in about three letters, so one letter already narrowing is
            // expected. Blank means the popular row does the talking.
            if (text.isBlank()) flowOf(emptyList()) else channelRepository.search(text, country)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val radioResults: StateFlow<List<RadioStation>> = countryScopedQuery
        .flatMapLatest { (text, country) ->
            // A blank query matches everything, so the ordering clause falls through
            // to click-count — the starting row of most-listened stations.
            radioRepository.search(text, country)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun append(character: Char) {
        _query.value = _query.value + character
    }

    fun backspace() {
        _query.value = _query.value.dropLast(1)
    }

    fun clear() {
        _query.value = ""
    }

    /** Voice search hands over a whole phrase at once. */
    fun setQuery(text: String) {
        _query.value = text
    }

    fun setKind(kind: SearchKind) {
        _kind.value = kind
    }

    fun setCountry(code: String?) {
        _country.value = code
    }

    /** Selected from the TV channel results. */
    fun onChannelOpened(startId: String) {
        playbackQueue.setQueue(channelResults.value.map { it.channel.id }, startId)
        recordQuery()
    }

    /** Selected from the radio results. A station, not a queue, so nothing to set. */
    fun recordRadioOpened() {
        recordQuery()
    }

    private fun recordQuery() {
        // Recorded on selection rather than on keystroke: a query the user abandoned
        // is not one worth offering back to them.
        viewModelScope.launch { preferences.recordSearch(_query.value) }
    }

    fun clearRecentSearches() = viewModelScope.launch { preferences.clearRecentSearches() }

    private companion object {
        /** One shelf's worth; more would push the keyboard off screen. */
        const val POPULAR_LIMIT = 10
    }
}