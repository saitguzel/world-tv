package com.worldtv.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.worldtv.feature.catalog.mobile.MobileBrowseScreen
import com.worldtv.feature.favorites.mobile.MobileFavoritesScreen
import com.worldtv.feature.settings.mobile.MobileSettingsScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The phone navigation graph.
 *
 * Registers exactly the same seven routes as the TV graph, from the same [Routes]
 * object, so a deep link or an Assistant query resolves identically on both. What
 * differs is only which composable each route renders, and that the tabs are reachable
 * from a navigation bar rather than from buttons on Home.
 *
 * The destinations are placeholders for now. They are deliberately not the TV screens:
 * `androidx.tv.material3`'s click path is D-pad key events only, so rendering those
 * here would produce an app that silently ignores every tap — which reads as broken
 * rather than unfinished.
 */
@Composable
fun MobileNavHost(
    navController: NavHostController,
    onExit: () -> Unit = {},
    voiceQuery: StateFlow<String?> = MutableStateFlow(null),
) {
    val pendingVoiceQuery by voiceQuery.collectAsStateWithLifecycle()
    LaunchedEffect(pendingVoiceQuery) {
        if (!pendingVoiceQuery.isNullOrBlank()) navController.navigate(Routes.SEARCH)
    }

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) { ComingSoon("Ana ekran") }

        composable(
            route = Routes.BROWSE,
            arguments = listOf(
                navArgument("country") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { entry ->
            MobileBrowseScreen(
                initialCountry = entry.arguments?.getString("country"),
                onChannelSelected = { navController.navigate(Routes.player(it)) },
            )
        }

        composable(Routes.SEARCH) { ComingSoon("Ara") }
        composable(Routes.RADIO) { ComingSoon("Radyo") }
        composable(Routes.FAVORITES) {
            MobileFavoritesScreen(
                onChannelSelected = { navController.navigate(Routes.player(it)) },
            )
        }
        composable(Routes.SETTINGS) {
            MobileSettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Routes.PLAYER,
            arguments = listOf(navArgument("channelId") { type = NavType.StringType }),
        ) { entry ->
            ComingSoon("Oynatıcı: " + entry.arguments?.getString("channelId").orEmpty())
        }
    }
}

/** Names the destination so the shell can be walked and verified before it has content. */
@Composable
private fun ComingSoon(destination: String, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = destination,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
    }
}
