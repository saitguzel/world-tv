package com.worldtv.navigation

import androidx.compose.runtime.Composable
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

/**
 * Navigation graph.
 *
 * Flat by design: every destination is one hop from Home, so nothing is ever more
 * than two BACK presses from the start. Backing out of four levels with a remote is
 * punishing, and the architecture doc caps depth at three for that reason.
 */
object Routes {
    const val HOME = "home"
    const val BROWSE = "browse?country={country}"
    const val SEARCH = "search"
    const val RADIO = "radio"
    const val FAVORITES = "favorites"
    const val SETTINGS = "settings"
    const val PLAYER = "player/{channelId}"

    fun browse(country: String? = null): String =
        if (country == null) "browse" else "browse?country=$country"

    fun player(channelId: String) = "player/$channelId"
}

@Composable
fun WorldTvNavHost(navController: NavHostController = rememberNavController()) {
    val toPlayer: (String) -> Unit = { channelId ->
        navController.navigate(Routes.player(channelId))
    }

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onChannelSelected = toPlayer,
                onBrowse = { navController.navigate(Routes.browse()) },
                onSearch = { navController.navigate(Routes.SEARCH) },
                onRadio = { navController.navigate(Routes.RADIO) },
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
            SearchScreen(onChannelSelected = toPlayer)
        }

        composable(Routes.RADIO) {
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
