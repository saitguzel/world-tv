package com.worldtv

import android.app.SearchManager
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.worldtv.core.common.DeviceCapabilities
import com.worldtv.core.common.FormFactor
import com.worldtv.core.designsystem.mobile.theme.WorldTvMobileTheme
import com.worldtv.core.designsystem.theme.LocalFormFactor
import com.worldtv.core.designsystem.tv.theme.WorldTvTheme
import com.worldtv.data.repository.UserPreferencesRepository
import com.worldtv.feature.radio.RadioController
import com.worldtv.navigation.MobileApp
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

    @Inject lateinit var device: DeviceCapabilities

    @Inject lateinit var radioController: RadioController

    /** Latest query handed over by Assistant or global search. */
    private val voiceQuery = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        voiceQuery.value = intent.extractSearchQuery()

        val formFactor = device.formFactor
        when (formFactor) {
            // The manifest no longer pins the orientation, so a phone can rotate. TV
            // still must not: these two changes belong together, because dropping the
            // manifest lock without adding this one lets a TV box with an
            // accelerometer turn the picture sideways.
            FormFactor.TV -> requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            // Only the phone tree handles insets; the TV tree relies on overscan padding.
            FormFactor.MOBILE -> enableEdgeToEdge()
        }

        val reduceMotionFlow = preferences.preferences
            .map { it.reduceMotion }
            .stateIn(lifecycleScope, SharingStarted.Eagerly, false)

        setContent {
            val reduceMotion by reduceMotionFlow.collectAsStateWithLifecycle()
            // The one place the two experiences are chosen between. It cannot be
            // pushed lower: the themes are separate CompositionLocal trees backed by
            // incompatible Material libraries, so the decision has to sit above
            // everything that reads a theme.
            CompositionLocalProvider(LocalFormFactor provides formFactor) {
                when (formFactor) {
                    FormFactor.TV -> WorldTvTheme(reduceMotion = reduceMotion) {
                        WorldTvNavHost(onExit = ::finish, voiceQuery = voiceQuery)
                    }

                    FormFactor.MOBILE -> WorldTvMobileTheme(reduceMotion = reduceMotion) {
                        // Phone screens land here next. Until then this is deliberately
                        // a placeholder rather than the TV graph: tv-material's click
                        // path is D-pad key events only, so rendering it here would
                        // look like an app that simply ignores every tap.
                        MobileApp(onExit = ::finish, voiceQuery = voiceQuery)
                    }
                }
            }
        }
    }

    /**
     * Assistant delivers a query through a new intent when the app is already running,
     * so reading it once in [onCreate] would miss every search after the first.
     */
    /**
     * App closed for real: stop the radio.
     *
     * Video already dies with the activity (its view model releases the player), but
     * the radio controller is a process singleton with no screen to release it, so an
     * exit would leave the session audibly playing in the background. `isFinishing`
     * keeps configuration changes and background kills from stopping it; only a
     * genuine finish (exit button, back-to-exit, swipe-away) does.
     */
    override fun onDestroy() {
        if (isFinishing) {
            radioController.stop()
        }
        super.onDestroy()
    }

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
