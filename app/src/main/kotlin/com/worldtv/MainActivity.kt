package com.worldtv

import android.app.SearchManager
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.worldtv.core.designsystem.theme.WorldTvTheme
import com.worldtv.data.repository.UserPreferencesRepository
import com.worldtv.navigation.WorldTvNavHost
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var preferences: UserPreferencesRepository

    /** Latest query handed over by Assistant or global search. */
    private val voiceQuery = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        voiceQuery.value = intent.extractSearchQuery()

        val reduceMotionFlow = preferences.preferences
            .map { it.reduceMotion }
            .stateIn(lifecycleScope, SharingStarted.Eagerly, false)

        setContent {
            val reduceMotion by reduceMotionFlow.collectAsStateWithLifecycle()
            WorldTvTheme(reduceMotion = reduceMotion) {
                WorldTvNavHost(
                    onExit = ::finish,
                    voiceQuery = voiceQuery,
                )
            }
        }
    }

    /**
     * Assistant delivers a query through a new intent when the app is already running,
     * so reading it once in [onCreate] would miss every search after the first.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.extractSearchQuery()?.let { voiceQuery.value = it }
    }
}

/**
 * Pulls a query out of an Assistant or global-search intent.
 *
 * `SearchManager.QUERY` is the extra both carry. `ACTION_MEDIA_PLAY_FROM_SEARCH` is
 * what "play BBC News on WorldTV" produces, and handling only `ACTION_SEARCH` would
 * silently ignore the phrasing users actually say to a TV.
 */
private fun Intent.extractSearchQuery(): String? = when (action) {
    Intent.ACTION_SEARCH,
    MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH,
    -> getStringExtra(SearchManager.QUERY)?.takeIf { it.isNotBlank() }
    else -> null
}
