package com.worldtv.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.worldtv.data.repository.HealthAggressiveness
import com.worldtv.data.repository.HealthRepository
import com.worldtv.data.repository.UserPreferences
import com.worldtv.data.repository.SyncTrigger
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
    val isSyncing: Boolean = false,
    val verifiedStreams: Int = 0,
    val deadStreams: Int = 0,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: UserPreferencesRepository,
    private val healthRepository: HealthRepository,
    private val syncTrigger: SyncTrigger,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        preferences.preferences,
        healthRepository.verifiedCount,
        healthRepository.deadCount,
        syncTrigger.isSyncing,
    ) { prefs, verified, dead, syncing ->
        SettingsUiState(
            preferences = prefs,
            isSyncing = syncing,
            verifiedStreams = verified,
            deadStreams = dead,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun resyncCatalog() = syncTrigger.syncNow()

    fun setYouTubeApiKey(key: String) =
        viewModelScope.launch { preferences.setYouTubeApiKey(key) }

    /**
     * Re-checks everything, immediately.
     *
     * Resets `nextCheckAt` rather than probing inline: the sweep worker already knows
     * how to pace this within a budget, and doing it here would block the UI for
     * minutes on a large catalog.
     */
    fun recheckEverything() = viewModelScope.launch { healthRepository.recheckAll() }

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
