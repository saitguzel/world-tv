package com.worldtv.navigation

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.worldtv.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The phone shell: a navigation bar wrapped around [MobileNavHost].
 *
 * `NavigationSuiteScaffold` rather than a hand-rolled `NavigationBar` because it
 * becomes a bar at compact width, a rail at medium and a drawer at expanded — and a
 * phone held in landscape is precisely the case that would otherwise need solving
 * twice. Its API has moved between releases, so every call to it is confined to this
 * file: falling back to `Scaffold` + `NavigationBar` stays a one-file change.
 */
@Composable
fun MobileApp(
    onExit: () -> Unit = {},
    voiceQuery: StateFlow<String?> = MutableStateFlow(null),
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            NAV_ITEMS.forEach { item ->
                item(
                    selected = Routes.isTopLevel(currentRoute, item.route),
                    onClick = { navController.navigateToTab(item.route) },
                    icon = { Icon(item.icon, contentDescription = null) },
                    label = { Text(stringResource(item.label)) },
                )
            }
        },
        // The player is full-bleed video; a navigation bar over it would be both ugly
        // and a mis-tap away from leaving playback.
        layoutType = if (Routes.isPlayer(currentRoute)) {
            NavigationSuiteType.None
        } else {
            NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfo())
        },
    ) {
        MobileNavHost(
            navController = navController,
            onExit = onExit,
            voiceQuery = voiceQuery,
        )
    }
}

/**
 * The standard top-level idiom: one entry per tab on the back stack, each tab keeping
 * its own scroll position, and back always leading to the start destination.
 */
private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private data class NavItem(val route: String, val label: Int, val icon: ImageVector)

/**
 * Five entries, matching [Routes.TOP_LEVEL].
 *
 * Radio borrows the play glyph: `material-icons-core` has no radio, and pulling in
 * `material-icons-extended` for one icon would add thousands of unused vectors to the
 * APK. Worth replacing with a hand-drawn vector.
 */
private val NAV_ITEMS = listOf(
    NavItem(Routes.HOME, R.string.tab_home, Icons.Filled.Home),
    NavItem(Routes.BROWSE_BASE, R.string.tab_browse, Icons.Filled.List),
    NavItem(Routes.SEARCH, R.string.tab_search, Icons.Filled.Search),
    NavItem(Routes.RADIO, R.string.tab_radio, Icons.Filled.PlayArrow),
    NavItem(Routes.FAVORITES, R.string.tab_favorites, Icons.Filled.Favorite),
)
