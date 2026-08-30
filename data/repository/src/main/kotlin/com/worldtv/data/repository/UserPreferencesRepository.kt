package com.worldtv.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.worldtv.data.health.HealthCheckConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("settings")

/** How hard the user wants the health engine to work. */
enum class HealthAggressiveness {
    /** Tier 1 only, low concurrency. For metered links and the weakest boxes. */
    LIGHT,

    /** Tier 1 + tier 2. The default. */
    BALANCED,

    /** Tier 1 + tier 2 at higher concurrency. */
    THOROUGH,
    ;

    fun toConfig(deviceDefault: HealthCheckConfig): HealthCheckConfig = when (this) {
        LIGHT -> deviceDefault.copy(
            maxParallel = (deviceDefault.maxParallel / 2).coerceAtLeast(2),
            deepCheck = false,
        )
        BALANCED -> deviceDefault
        THOROUGH -> deviceDefault.copy(
            maxParallel = deviceDefault.maxParallel * 2,
            deepCheck = true,
        )
    }
}

data class UserPreferences(
    val showNsfw: Boolean = false,
    val showUnchecked: Boolean = true,
    val showGeoBlocked: Boolean = true,
    val homeCountry: String? = null,
    val lastMode: String = "TV",
    val healthAggressiveness: HealthAggressiveness = HealthAggressiveness.BALANCED,
    val reduceMotion: Boolean = false,
    /** Preview the focused channel in the grid after a short dwell. */
    val previewOnFocus: Boolean = true,
    /** Most recent first, capped at [UserPreferencesRepository.MAX_RECENT_SEARCHES]. */
    val recentSearches: List<String> = emptyList(),
)

@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val preferences: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        UserPreferences(
            showNsfw = prefs[SHOW_NSFW] ?: false,
            showUnchecked = prefs[SHOW_UNCHECKED] ?: true,
            showGeoBlocked = prefs[SHOW_GEO_BLOCKED] ?: true,
            homeCountry = prefs[HOME_COUNTRY],
            lastMode = prefs[LAST_MODE] ?: "TV",
            healthAggressiveness = prefs[HEALTH_AGGRESSIVENESS]
                ?.let { runCatching { HealthAggressiveness.valueOf(it) }.getOrNull() }
                ?: HealthAggressiveness.BALANCED,
            reduceMotion = prefs[REDUCE_MOTION] ?: false,
            previewOnFocus = prefs[PREVIEW_ON_FOCUS] ?: true,
            // Stored as one delimited string rather than a string-set: DataStore's
            // set type is unordered, and recency order is the entire point here.
            recentSearches = prefs[RECENT_SEARCHES]
                ?.split(SEARCH_DELIMITER)
                ?.filter { it.isNotBlank() }
                .orEmpty(),
        )
    }

    suspend fun homeCountry(): String? = preferences.first().homeCountry

    suspend fun setShowNsfw(value: Boolean) = edit { it[SHOW_NSFW] = value }

    suspend fun setShowUnchecked(value: Boolean) = edit { it[SHOW_UNCHECKED] = value }

    suspend fun setShowGeoBlocked(value: Boolean) = edit { it[SHOW_GEO_BLOCKED] = value }

    suspend fun setHomeCountry(code: String) = edit { it[HOME_COUNTRY] = code }

    suspend fun setLastMode(mode: String) = edit { it[LAST_MODE] = mode }

    suspend fun setHealthAggressiveness(value: HealthAggressiveness) =
        edit { it[HEALTH_AGGRESSIVENESS] = value.name }

    suspend fun setReduceMotion(value: Boolean) = edit { it[REDUCE_MOTION] = value }

    suspend fun setPreviewOnFocus(value: Boolean) = edit { it[PREVIEW_ON_FOCUS] = value }

    /** Records a search, moving a repeat to the front rather than duplicating it. */
    suspend fun recordSearch(query: String) {
        val cleaned = query.trim()
        if (cleaned.length < MIN_RECORDED_SEARCH_LENGTH) return
        edit { prefs ->
            val existing = prefs[RECENT_SEARCHES]
                ?.split(SEARCH_DELIMITER)
                ?.filter { it.isNotBlank() }
                .orEmpty()
            val updated = (listOf(cleaned) + existing.filterNot { it.equals(cleaned, true) })
                .take(MAX_RECENT_SEARCHES)
            prefs[RECENT_SEARCHES] = updated.joinToString(SEARCH_DELIMITER)
        }
    }

    suspend fun clearRecentSearches() = edit { it.remove(RECENT_SEARCHES) }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }

    companion object {
        /** Short enough to be a full row on screen, long enough to be useful. */
        const val MAX_RECENT_SEARCHES = 8

        /** One or two letters is not a search worth remembering. */
        private const val MIN_RECORDED_SEARCH_LENGTH = 3

        private val SHOW_NSFW = booleanPreferencesKey("show_nsfw")
        private val SHOW_UNCHECKED = booleanPreferencesKey("show_unchecked")
        private val SHOW_GEO_BLOCKED = booleanPreferencesKey("show_geo_blocked")
        private val HOME_COUNTRY = stringPreferencesKey("home_country")
        private val LAST_MODE = stringPreferencesKey("last_mode")
        private val HEALTH_AGGRESSIVENESS = stringPreferencesKey("health_aggressiveness")
        private val REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
        private val PREVIEW_ON_FOCUS = booleanPreferencesKey("preview_on_focus")
        private val RECENT_SEARCHES = stringPreferencesKey("recent_searches")

        /**
         * A control character, so it cannot collide with anything a user could type
         * on the grid keyboard.
         */
        private const val SEARCH_DELIMITER = "\u001F"
    }
}
