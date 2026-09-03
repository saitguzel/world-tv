package com.worldtv.feature.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.worldtv.core.common.network.NetworkMonitor
import com.worldtv.core.model.ChannelSummary
import com.worldtv.core.model.Country
import com.worldtv.data.repository.ChannelRepository
import com.worldtv.data.repository.HealthRepository
import com.worldtv.data.repository.PlaybackQueueHolder
import com.worldtv.data.repository.SyncTrigger
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val recents: List<ChannelSummary> = emptyList(),
    val favorites: List<ChannelSummary> = emptyList(),
    val countries: List<Country> = emptyList(),
    /**
     * Whether the catalog itself has been downloaded, whatever else is missing; null
     * until the database has answered, so the download prompt does not flash on every
     * start while the count is still being read.
     */
    val hasCatalog: Boolean? = null,
) {
    /**
     * Driven by catalog presence rather than by the individual rows: the countries
     * step of the sync can fail while channels land, and treating "no countries" as
     * "no catalog" is what made the download prompt come back on every start.
     */
    val isEmpty: Boolean get() = hasCatalog == false
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val channelRepository: ChannelRepository,
    private val healthRepository: HealthRepository,
    private val playbackQueue: PlaybackQueueHolder,
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

    val uiState: StateFlow<HomeUiState> = combine(
        channelRepository.recents(limit = 12),
        channelRepository.favorites(),
        channelRepository.countries().map { it.take(FEATURED_COUNTRIES) },
        channelRepository.channelCount(),
    ) { recents, favorites, countries, channelCount ->
        HomeUiState(
            recents = recents,
            favorites = favorites,
            countries = countries,
            hasCatalog = channelCount > 0,
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
    }
}