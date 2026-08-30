package com.worldtv.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.worldtv.feature.catalog.BrowseScreen
import com.worldtv.feature.player.PlayerScreen
import com.worldtv.feature.settings.SettingsScreen

/**
 * Navigation graph.
 *
 * Kept deliberately flat — three destinations, no nesting. Backing out of four levels
 * with a remote is punishing, and the architecture doc caps depth at three for that
 * reason.
 */
object Routes {
    const val BROWSE = "browse"
    const val PLAYER = "player/{channelId}"
    const val SETTINGS = "settings"

    fun player(channelId: String) = "player/$channelId"
}

@Composable
fun WorldTvNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.BROWSE) {
        composable(Routes.BROWSE) {
            BrowseScreen(
                onChannelSelected = { channelId ->
                    navController.navigate(Routes.player(channelId))
                },
                onToggleFavorite = { /* handled by the card's long-press in the VM */ },
            )
        }

        composable(Routes.PLAYER) { entry ->
            val channelId = entry.arguments?.getString("channelId").orEmpty()
            PlayerScreen(
                channelId = channelId,
                onBack = { navController.popBackStack() },
                onOpenChannelList = { navController.popBackStack() },
                onZap = { /* wired to the channel list in a later phase */ },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen()
        }
    }
}
