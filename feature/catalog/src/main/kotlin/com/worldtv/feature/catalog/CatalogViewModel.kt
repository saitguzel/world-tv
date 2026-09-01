package com.worldtv.feature.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.worldtv.core.model.ChannelSummary
import com.worldtv.core.model.Category
import com.worldtv.core.model.Country
import com.worldtv.core.model.Programme
import com.worldtv.data.repository.ChannelRepository
import com.worldtv.data.repository.EpgRepository
import com.worldtv.data.repository.FavoritesRepository
import com.worldtv.data.repository.HealthRepository
import com.worldtv.data.repository.PreviewStreamResolver
import com.worldtv.data.repository.PlaybackQueueHolder
import com.worldtv.data.repository.UserPreferences
import com.worldtv.data.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CatalogFilter(val country: String? = null, val category: String? = null)

@HiltViewModel
class CatalogViewModel @Inject constructor(
    private val channelRepository: ChannelRepository,
    private val healthRepository: HealthRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val playbackQueue: PlaybackQueueHolder,
    private val favoritesRepository: FavoritesRepository,
    private val epgRepository: EpgRepository,
    private val previewResolver: PreviewStreamResolver,
    private val previewPlayerFactory: PreviewPlayerFactory,
) : ViewModel() {

    private val _previewUrl = MutableStateFlow<String?>(null)

    /** URL of the muted preview currently playing behind the grid, if any. */
    val previewUrl: StateFlow<String?> = _previewUrl.asStateFlow()

    private var previewJob: Job? = null

    // Held through its delegate so onCleared can tell whether a player was ever
    // created — releasing a `by lazy` value would otherwise construct one just to
    // throw it away, on every screen a user browsed with previews turned off.
    private val previewPlayerDelegate = lazy { previewPlayerFactory.create() }

    /** Created lazily so a user who never enables previews never pays for a decoder. */
    val previewPlayer by previewPlayerDelegate

    val preferences: StateFlow<UserPreferences> = preferencesRepository.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserPreferences())

    private val visibleChannelIds = MutableStateFlow<List<String>>(emptyList())

    /**
     * What is on right now for the channels on screen.
     *
     * Keyed by channel and fetched for the visible set only: a country with two
     * thousand channels has no business loading two thousand guide rows to render
     * fifteen cards.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val nowPlaying: StateFlow<Map<String, Programme>> = visibleChannelIds
        .flatMapLatest { ids -> epgRepository.nowForChannels(ids) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _filter = MutableStateFlow(CatalogFilter())
    val filter: StateFlow<CatalogFilter> = _filter.asStateFlow()

    init {
        // Open on the user's home country rather than the whole world: the full
        // catalog is tens of thousands of channels and almost none of them are the
        // one they want. An explicit country from a deep link still wins, because it
        // arrives through setCountry after this and only an untouched filter is
        // seeded here.
        viewModelScope.launch {
            val home = preferencesRepository.homeCountry()
            if (home != null && _filter.value == CatalogFilter()) {
                _filter.value = CatalogFilter(country = home)
            }
        }
    }

    val countries: StateFlow<List<Country>> = channelRepository.countries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories: StateFlow<List<Category>> = channelRepository.categories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val channels: Flow<PagingData<ChannelSummary>> = _filter
        .flatMapLatest { filter -> channelRepository.channels(filter.country, filter.category) }
        // cachedIn keeps the loaded pages across configuration changes and, more
        // importantly here, across the drawer opening and closing.
        .cachedIn(viewModelScope)

    /**
     * Country and category narrow together rather than replacing each other, so
     * "Turkey" and "News" can both be on. Passing null clears just that one.
     */
    fun setCountry(code: String?) {
        _filter.update { it.copy(country = code) }
    }

    fun setCategory(id: String?) {
        _filter.update { it.copy(category = id) }
    }

    fun clearFilters() {
        _filter.value = CatalogFilter()
    }

    /**
     * Lazy verification: probe exactly what the user just scrolled into view.
     *
     * This is the primary health mechanism. Sweeping 10,000 streams up front would
     * take hours and spend most of that budget on countries nobody opened.
     */
    fun onChannelsVisible(channelIds: List<String>) {
        visibleChannelIds.value = channelIds
        healthRepository.verifyVisibleChannels(viewModelScope, channelIds)
    }

    /**
     * Hands the player the list the user is actually looking at, so up/down zaps
     * through this country or category rather than through the whole catalog.
     */
    fun onChannelOpened(channelIds: List<String>, startId: String) {
        playbackQueue.setQueue(channelIds, startId)
    }

    /** Long-press on a card. */
    fun toggleFavorite(channelId: String, currentlyFavorite: Boolean) {
        viewModelScope.launch {
            favoritesRepository.toggle(
                channelId,
                FavoritesRepository.Kind.CHANNEL,
                currentlyFavorite,
            )
        }
    }

    /**
     * Starts or stops the preview.
     *
     * The dwell delay lives in the composable; by the time this is called the user has
     * genuinely stopped on a channel. Resolving the stream still costs a database
     * read, so the previous lookup is cancelled rather than left to race.
     */
    fun onPreviewTargetChanged(channelId: String?) {
        previewJob?.cancel()
        if (channelId == null) {
            _previewUrl.value = null
            return
        }
        previewJob = viewModelScope.launch {
            _previewUrl.value = previewResolver.bestStreamUrl(channelId)
        }
    }

    override fun onCleared() {
        previewJob?.cancel()
        if (previewPlayerDelegate.isInitialized()) previewPlayer.release()
        super.onCleared()
    }

    fun rememberHomeCountry(code: String) {
        viewModelScope.launch { preferencesRepository.setHomeCountry(code) }
    }
}
