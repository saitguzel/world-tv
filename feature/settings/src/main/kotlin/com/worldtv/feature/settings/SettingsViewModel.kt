package com.worldtv.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.worldtv.data.repository.HealthAggressiveness
import com.worldtv.data.repository.HealthRepository
import com.worldtv.data.repository.UserPreferences
import com.worldtv.data.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val preferences: UserPreferences = UserPreferences(),
    val verifiedStreams: Int = 0,
    val deadStreams: Int = 0,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: UserPreferencesRepository,
    private val healthRepository: HealthRepository,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        preferences.preferences,
        healthRepository.verifiedCount,
        healthRepository.deadCount,
    ) { prefs, verified, dead ->
        SettingsUiState(prefs, verified, dead)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setShowNsfw(value: Boolean) = viewModelScope.launch { preferences.setShowNsfw(value) }

    fun setShowUnchecked(value: Boolean) =
        viewModelScope.launch { preferences.setShowUnchecked(value) }

    fun setShowGeoBlocked(value: Boolean) =
        viewModelScope.launch { preferences.setShowGeoBlocked(value) }

    fun setReduceMotion(value: Boolean) =
        viewModelScope.launch { preferences.setReduceMotion(value) }

    fun setAggressiveness(value: HealthAggressiveness) = viewModelScope.launch {
        preferences.setHealthAggressiveness(value)
        // Apply immediately rather than waiting for the next sweep, so the change is
        // observable right away.
        healthRepository.applyConfig()
    }
}
