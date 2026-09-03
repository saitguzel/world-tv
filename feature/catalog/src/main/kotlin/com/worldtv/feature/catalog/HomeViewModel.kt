package com.worldtv.feature.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.worldtv.core.common.network.NetworkMonitor
import com.worldtv.core.model.Category
import com.worldtv.core.model.ChannelSummary
import com.worldtv.core.model.Country
import com.worldtv.core.model.RadioStation
import com.worldtv.data.repository.ChannelRepository
import com.worldtv.data.repository.HealthRepository
import com.worldtv.data.repository.PlaybackQueueHolder
import com.worldtv.data.repository.RadioRepository
import com.worldtv.data.repository.SyncTrigger
import com.worldtv.data.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The television half of the home screen. */
data class HomeChannelRows(
    val recents: List<ChannelSummary> = emptyList(),
    val favorites: List<ChannelSummary> = emptyList(),
    val popular: List<ChannelSummary> = emptyList(),
    val countries: List<Country> = emptyList(),
    val hasCatalog: Boolean? = null,
)

/** The radio half. Stations are cards here, the same as channels. */
data class HomeRadioRows(
    val recents: List<RadioStation> = emptyList(),
    val favorites: List<RadioStation> = emptyList(),
    val popular: List<RadioStation> = emptyList(),
    val hasStations: Boolean? = null,
)

data class HomeUiState(
    val recents: List<ChannelSummary> = emptyList(),
    val favorites: List<ChannelSummary> = emptyList(),
    val popular: List<ChannelSummary> = emptyList(),
    val countries: List<Country> = emptyList(),
    val categories: List<Category> = emptyList(),
    val radioRecents: List<RadioStation> = emptyList(),
    val radioFavorites: List<RadioStation> = emptyList(),
    val radioPopular: List<RadioStation> = emptyList(),
    /**
     * Whether the catalog itself has been downloaded, whatever else is missing; null
     * until the database has answered, so the download prompt does not flash on every
     * start while the count is still being read.
     */
    val hasCatalog: Boolean? = null,
    /** The same question for radio, which syncs separately and can arrive first. */
    val hasRadio: Boolean? = null,
) {
    /**
     * Driven by content presence rather than by the individual rows: the countries
     * step of the sync can fail while channels land, and treating "no countries" as
     * "no catalog" is what made the download prompt come back on every start.
     *
     * Radio counts too. A device with stations but no channels has a home screen worth
     * showing, and pinning emptiness to the channel table alone hid it behind a
     * download prompt.
     */
    val isEmpty: Boolean get() = hasCatalog == false && hasRadio == false
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val channelRepository: ChannelRepository,
    private val healthRepository: HealthRepository,
    private val playbackQueue: PlaybackQueueHolder,
    private val radioRepository: RadioRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val syncTrigger: SyncTrigger,
    private val networkMonitor: NetworkMonitor,
) : ViewModel() {

    val isSyncing: StateFlow<Boolean> = syncTrigger.isSyncing
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Whether the device has any connectivity, for the empty-state copy on Home. */
    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline
        // Optimistic until the first reading lands: "no connection" is the more
        // alarming thing to flash at a user who is, in fact, online.
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    // Combined in two halves and joined, rather than one call: `combine` is typed up
    // to five flows and this needs eight. The split is along the seam that matters
    // anyway — channels sync separately from stations.
    /**
     * The country the two "popular" shelves are about.
     *
     * Ranked lists over the whole catalog would be a wall of channels from wherever the
     * data happens to be densest; the home country is the only place a first-run user
     * has any reason to look.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val homeCountry: Flow<String?> = preferencesRepository.preferences
        .map { it.homeCountry }
        .distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val channelRows: Flow<HomeChannelRows> = combine(
        channelRepository.recents(limit = ROW_LIMIT),
        channelRepository.favorites(),
        homeCountry.flatMapLatest { channelRepository.popular(it, ROW_LIMIT) },
        channelRepository.countries().map { it.take(FEATURED_COUNTRIES) },
        channelRepository.channelCount(),
    ) { recents, favorites, popular, countries, channelCount ->
        HomeChannelRows(recents, favorites, popular, countries, hasCatalog = channelCount > 0)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val radioRows: Flow<HomeRadioRows> = combine(
        radioRepository.recents(limit = ROW_LIMIT),
        radioRepository.favorites(),
        homeCountry.flatMapLatest { radioRepository.popular(it, ROW_LIMIT) },
        radioRepository.stationCount(),
    ) { recents, favorites, popular, stationCount ->
        HomeRadioRows(recents, favorites, popular, hasStations = stationCount > 0)
    }

    val uiState: StateFlow<HomeUiState> = combine(
        channelRows,
        radioRows,
        channelRepository.categories().map { it.take(FEATURED_CATEGORIES) },
    ) { channels, radio, categories ->
        HomeUiState(
            // A channel already in the favourites row is not repeated below it; the
            // same rule the station rows follow.
            recents = channels.recents.filterNot { row ->
                channels.favorites.any { it.channel.id == row.channel.id }
            },
            favorites = channels.favorites,
            popular = channels.popular.filterNot { row ->
                channels.favorites.any { it.channel.id == row.channel.id } ||
                    channels.recents.any { it.channel.id == row.channel.id }
            },
            countries = channels.countries,
            categories = categories,
            // Favourites first among the station rows, but a station already in the
            // favourites row is not repeated in "recent" — on a short list the same
            // three stations three times over reads as a rendering bug.
            radioRecents = radio.recents.filterNot { station ->
                radio.favorites.any { it.uuid == station.uuid }
            },
            radioFavorites = radio.favorites,
            radioPopular = radio.popular,
            hasCatalog = channels.hasCatalog,
            hasRadio = radio.hasStations,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        // The periodic catalog worker's first run is scheduled within its 24-hour
        // window, not immediately, so a fresh install would otherwise sit on an empty
        // home screen — and an empty catalog also means no countries and no
        // categories, which makes the filters look broken rather than unpopulated.
        //
        // Waits for connectivity rather than giving up on an offline start: the copy on
        // the empty Home promises the download starts by itself once the network is
        // back, and enqueueing while offline would only REPLACE the retrying chain with
        // one that fails on arrival — the churn that made "download now" feel permanent.
        viewModelScope.launch {
            networkMonitor.isOnline.first { it }
            if (channelRepository.channelCount().first() == 0) {
                syncTrigger.syncNow()
            }
        }
    }

    /**
     * Retry for a first run whose catalog sync failed.
     *
     * The automatic trigger above covers the empty case, so this is for the run that
     * started and failed — a flaky first network, most often.
     */
    fun retrySync() = syncTrigger.syncNow()

    /** Home rows are short, so everything on them is worth verifying eagerly. */
    fun verifyVisibleRows() {
        val ids = uiState.value.let { it.recents + it.favorites }.map { it.channel.id }
        healthRepository.verifyVisibleChannels(viewModelScope, ids)
    }

    fun onChannelOpened(row: List<ChannelSummary>, startId: String) {
        playbackQueue.setQueue(row.map { it.channel.id }, startId)
    }

    private companion object {
        /** Countries are ranked by live channel count, so the head of the list is the
         *  useful part; the full list lives one press away in Browse. */
        const val FEATURED_COUNTRIES = 12

        /** Same reasoning as the countries: the head of the ranking, not the tail. */
        const val FEATURED_CATEGORIES = 12

        /** A shelf is scrolled sideways; past a dozen nobody reaches the end. */
        const val ROW_LIMIT = 12
    }
}