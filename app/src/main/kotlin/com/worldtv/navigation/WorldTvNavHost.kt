package com.worldtv.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.worldtv.feature.catalog.BrowseScreen
import com.worldtv.feature.catalog.HomeScreen
import com.worldtv.feature.catalog.SearchScreen
import com.worldtv.feature.favorites.FavoritesScreen
import com.worldtv.feature.player.PlayerScreen
import com.worldtv.feature.radio.RadioScreen
import com.worldtv.feature.settings.SettingsScreen

@Composable
fun WorldTvNavHost(
    onExit: () -> Unit = {},
    voiceQuery: StateFlow<String?> = MutableStateFlow(null),
    navController: NavHostController = rememberNavController(),
) {
    // A query from Assistant jumps straight to search rather than waiting for the
    // user to navigate there — that is the whole point of speaking to the TV.
    val pendingVoiceQuery by voiceQuery.collectAsStateWithLifecycle()
    LaunchedEffect(pendingVoiceQuery) {
        if (!pendingVoiceQuery.isNullOrBlank()) navController.navigate(Routes.SEARCH)
    }

    val toPlayer: (String) -> Unit = { channelId ->
        navController.navigate(Routes.player(channelId))
    }

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onExit = onExit,
                onChannelSelected = toPlayer,
                onBrowse = { navController.navigate(Routes.browse()) },
                onSearch = { navController.navigate(Routes.SEARCH) },
                onRadio = { navController.navigate(Routes.radio()) },
                onFavorites = { navController.navigate(Routes.FAVORITES) },
                onSettings = { navController.navigate(Routes.SETTINGS) },
                onCountrySelected = { code -> navController.navigate(Routes.browse(code)) },
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
            BrowseScreen(
                initialCountry = entry.arguments?.getString("country"),
                onChannelSelected = toPlayer,
            )
        }

        composable(Routes.SEARCH) {
            SearchScreen(
                onChannelSelected = toPlayer,
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
            RadioScreen()
        }

        composable(Routes.FAVORITES) {
            FavoritesScreen(onChannelSelected = toPlayer)
        }

        composable(Routes.SETTINGS) {
            SettingsScreen()
        }


        composable(
            route = Routes.PLAYER,
            arguments = listOf(navArgument("channelId") { type = NavType.StringType }),
        ) { entry ->
            PlayerScreen(
                channelId = entry.arguments?.getString("channelId").orEmpty(),
                // BACK from the player returns to the list, never out of the app.
                onBack = { navController.popBackStack() },
            )
        }
    }
}
