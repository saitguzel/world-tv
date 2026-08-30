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
    /** User-supplied YouTube Data API key; null disables YouTube mode. */
    val youTubeApiKey: String? = null,
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
            youTubeApiKey = prefs[YOUTUBE_API_KEY],
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

    suspend fun setYouTubeApiKey(value: String) = edit { it[YOUTUBE_API_KEY] = value.trim() }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }

    private companion object {
        val SHOW_NSFW = booleanPreferencesKey("show_nsfw")
        val SHOW_UNCHECKED = booleanPreferencesKey("show_unchecked")
        val SHOW_GEO_BLOCKED = booleanPreferencesKey("show_geo_blocked")
        val HOME_COUNTRY = stringPreferencesKey("home_country")
        val LAST_MODE = stringPreferencesKey("last_mode")
        val HEALTH_AGGRESSIVENESS = stringPreferencesKey("health_aggressiveness")
        val REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
        val YOUTUBE_API_KEY = stringPreferencesKey("youtube_api_key")
    }
}
