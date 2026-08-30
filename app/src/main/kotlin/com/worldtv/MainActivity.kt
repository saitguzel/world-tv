package com.worldtv

import android.os.Bundle
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var preferences: UserPreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val reduceMotionFlow = preferences.preferences
            .map { it.reduceMotion }
            .stateIn(lifecycleScope, SharingStarted.Eagerly, false)

        setContent {
            val reduceMotion by reduceMotionFlow.collectAsStateWithLifecycle()
            WorldTvTheme(reduceMotion = reduceMotion) {
                WorldTvNavHost()
            }
        }
    }
}
