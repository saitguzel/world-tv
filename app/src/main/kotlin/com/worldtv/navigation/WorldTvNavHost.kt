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
import com.worldtv.feature.youtube.YouTubeBrowseScreen
import com.worldtv.feature.youtube.YouTubePlayerScreen

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
    const val YOUTUBE = "youtube"
    const val YOUTUBE_PLAYER = "youtube/{videoId}"

    fun browse(country: String? = null): String =
        if (country == null) "browse" else "browse?country=$country"

    fun player(channelId: String) = "player/$channelId"

    fun youTubePlayer(videoId: String) = "youtube/$videoId"
}

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
                onRadio = { navController.navigate(Routes.RADIO) },
                onFavorites = { navController.navigate(Routes.FAVORITES) },
                onYouTube = { navController.navigate(Routes.YOUTUBE) },
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
                initialQuery = pendingVoiceQuery,
            )
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

        composable(Routes.YOUTUBE) {
            YouTubeBrowseScreen(
                onVideoSelected = { videoId ->
                    navController.navigate(Routes.youTubePlayer(videoId))
                },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }

        composable(
            route = Routes.YOUTUBE_PLAYER,
            arguments = listOf(navArgument("videoId") { type = NavType.StringType }),
        ) { entry ->
            YouTubePlayerScreen(
                videoId = entry.arguments?.getString("videoId").orEmpty(),
                onBack = { navController.popBackStack() },
            )
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
