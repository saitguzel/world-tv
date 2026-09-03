package com.worldtv.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.worldtv.feature.catalog.mobile.MobileBrowseScreen
import com.worldtv.feature.catalog.mobile.MobileHomeScreen
import com.worldtv.feature.catalog.mobile.MobileSearchScreen
import com.worldtv.feature.favorites.mobile.MobileFavoritesScreen
import com.worldtv.feature.player.mobile.MobilePlayerScreen
import com.worldtv.feature.radio.mobile.MobileRadioScreen
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
    modifier: Modifier = Modifier,
) {
    val pendingVoiceQuery by voiceQuery.collectAsStateWithLifecycle()
    LaunchedEffect(pendingVoiceQuery) {
        if (!pendingVoiceQuery.isNullOrBlank()) navController.navigate(Routes.SEARCH)
    }

    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = modifier,
    ) {
        composable(Routes.HOME) {
            MobileHomeScreen(
                onChannelSelected = { navController.navigate(Routes.player(it)) },
                onCountrySelected = { navController.navigate(Routes.browse(it)) },
                onSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }

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

        composable(Routes.SEARCH) {
            MobileSearchScreen(
                onChannelSelected = { navController.navigate(Routes.player(it)) },
                onRadioSelected = { station -> navController.navigate(Routes.radio(station)) },
                initialQuery = pendingVoiceQuery,
            )
        }
        composable(
            route = Routes.RADIO,
            arguments = listOf(
                navArgument("station") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) {
            // The optional station argument reaches RadioViewModel through its
            // SavedStateHandle, which is also what lets it be consumed exactly once.
            MobileRadioScreen()
        }
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
            MobilePlayerScreen(
                channelId = entry.arguments?.getString("channelId").orEmpty(),
                onBack = { navController.popBackStack() },
            )
        }
    }
}

