package com.worldtv.feature.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.worldtv.core.model.ChannelSummary
import com.worldtv.data.repository.ChannelRepository
import com.worldtv.data.repository.PlaybackQueueHolder
import com.worldtv.data.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val channelRepository: ChannelRepository,
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
     * letter is typed.
     */
    val popularChannels: StateFlow<List<ChannelSummary>> = channelRepository
        .recents(limit = POPULAR_LIMIT)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val results: StateFlow<List<ChannelSummary>> = _query
        // Short enough to feel live as letters are added on a grid keyboard, long
        // enough that a held-down key does not issue a query per repeat.
        .debounce(200)
        .flatMapLatest { text ->
            // Incremental filtering is the whole point: the target should be reachable
            // in about three letters, so one letter already narrowing is expected.
            if (text.isBlank()) flowOf(emptyList()) else channelRepository.search(text)
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

    private companion object {
        /** One shelf's worth; more would push the keyboard off screen. */
        const val POPULAR_LIMIT = 10
    }

    fun onChannelOpened(startId: String) {
        playbackQueue.setQueue(results.value.map { it.channel.id }, startId)
        // Recorded on selection rather than on keystroke: a query the user abandoned
        // is not one worth offering back to them.
        viewModelScope.launch { preferences.recordSearch(_query.value) }
    }

    fun clearRecentSearches() = viewModelScope.launch { preferences.clearRecentSearches() }
}
