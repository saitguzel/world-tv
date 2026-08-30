package com.worldtv.feature.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.worldtv.core.model.ChannelSummary
import com.worldtv.data.repository.ChannelRepository
import com.worldtv.data.repository.PlaybackQueueHolder
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val channelRepository: ChannelRepository,
    private val playbackQueue: PlaybackQueueHolder,
) : ViewModel() {

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

    fun onChannelOpened(startId: String) {
        playbackQueue.setQueue(results.value.map { it.channel.id }, startId)
    }
}
