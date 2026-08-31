package com.worldtv.feature.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class HomeUiState(
    val recents: List<ChannelSummary> = emptyList(),
    val favorites: List<ChannelSummary> = emptyList(),
    val countries: List<Country> = emptyList(),
    val isEmpty: Boolean = true,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val channelRepository: ChannelRepository,
    private val healthRepository: HealthRepository,
    private val playbackQueue: PlaybackQueueHolder,
    private val syncTrigger: SyncTrigger,
) : ViewModel() {

    val isSyncing: StateFlow<Boolean> = syncTrigger.isSyncing
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val uiState: StateFlow<HomeUiState> = combine(
        channelRepository.recents(limit = 12),
        channelRepository.favorites(),
        channelRepository.countries().map { it.take(FEATURED_COUNTRIES) },
    ) { recents, favorites, countries ->
        HomeUiState(
            recents = recents,
            favorites = favorites,
            countries = countries,
            // Nothing anywhere means the first catalog sync has not landed yet, and
            // the screen should say so rather than showing three empty rows.
            isEmpty = recents.isEmpty() && favorites.isEmpty() && countries.isEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /**
     * Retry for a first run whose catalog sync failed.
     *
     * Without this the user is stuck on an empty home screen until the periodic
     * worker comes round again, with nothing to press.
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
