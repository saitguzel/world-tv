package com.worldtv.feature.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.worldtv.core.model.ChannelSummary
import com.worldtv.core.model.Country
import com.worldtv.data.repository.ChannelRepository
import com.worldtv.data.repository.HealthRepository
import com.worldtv.data.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CatalogFilter(val country: String? = null, val category: String? = null)

@HiltViewModel
class CatalogViewModel @Inject constructor(
    private val channelRepository: ChannelRepository,
    private val healthRepository: HealthRepository,
    private val preferences: UserPreferencesRepository,
) : ViewModel() {

    private val _filter = MutableStateFlow(CatalogFilter())
    val filter: StateFlow<CatalogFilter> = _filter.asStateFlow()

    val countries: StateFlow<List<Country>> = channelRepository.countries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val channels: Flow<PagingData<ChannelSummary>> = _filter
        .flatMapLatest { filter -> channelRepository.channels(filter.country, filter.category) }
        // cachedIn keeps the loaded pages across configuration changes and, more
        // importantly here, across the drawer opening and closing.
        .cachedIn(viewModelScope)

    fun setCountry(code: String?) {
        _filter.value = CatalogFilter(country = code, category = null)
    }

    fun setCategory(id: String?) {
        _filter.value = CatalogFilter(country = null, category = id)
    }

    /**
     * Lazy verification: probe exactly what the user just scrolled into view.
     *
     * This is the primary health mechanism. Sweeping 10,000 streams up front would
     * take hours and spend most of that budget on countries nobody opened.
     */
    fun onChannelsVisible(channelIds: List<String>) {
        healthRepository.verifyVisibleChannels(viewModelScope, channelIds)
    }

    fun rememberHomeCountry(code: String) {
        viewModelScope.launch { preferences.setHomeCountry(code) }
    }
}
