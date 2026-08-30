package com.worldtv.feature.youtube

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.worldtv.core.model.YouTubeLive
import com.worldtv.data.repository.UserPreferencesRepository
import com.worldtv.data.repository.YouTubeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class YouTubeUiState(
    val live: List<YouTubeLive> = emptyList(),
    val hasApiKey: Boolean = false,
    val isLoading: Boolean = true,
)

@HiltViewModel
class YouTubeViewModel @Inject constructor(
    private val repository: YouTubeRepository,
    preferences: UserPreferencesRepository,
) : ViewModel() {

    val uiState: StateFlow<YouTubeUiState> = combine(
        repository.liveNow(),
        preferences.preferences,
    ) { live, prefs ->
        YouTubeUiState(
            live = live,
            // Distinguishes "no key configured" from "key configured, nothing live".
            // Those need very different messages.
            hasApiKey = !prefs.youTubeApiKey.isNullOrBlank(),
            isLoading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), YouTubeUiState())
}
