package com.worldtv.data.sync

import com.worldtv.data.repository.UserPreferencesRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Supplies the YouTube Data API key.
 *
 * The key is read from settings, not baked into the APK. A shipped key is a shared
 * key: one user's traffic exhausts the daily quota for everyone, and anyone can pull
 * it out of the binary and spend it. Users who want YouTube mode paste their own,
 * which is free to obtain and gives each installation its own budget.
 */
@Singleton
class YouTubeApiKeyProvider @Inject constructor(
    private val preferences: UserPreferencesRepository,
) {
    suspend fun apiKey(): String? =
        preferences.preferences.first().youTubeApiKey?.takeIf { it.isNotBlank() }
}
