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
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.worldtv.feature.radio.mobile.MiniPlayerViewModel
import com.worldtv.feature.radio.mobile.shouldShowMiniPlayer
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
        Column {
            MobileNavHost(
                navController = navController,
                onExit = onExit,
                voiceQuery = voiceQuery,
                modifier = Modifier.weight(1f),
            )
            // Above the nav host rather than inside the radio screen: playback outlives
            // the screen that started it, so the controls have to as well.
            MiniPlayer(isPlayerRoute = Routes.isPlayer(currentRoute))
        }
    }
}

@Composable
private fun MiniPlayer(
    isPlayerRoute: Boolean,
    viewModel: MiniPlayerViewModel = hiltViewModel(),
) {
    val station by viewModel.nowPlaying.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val current = station ?: return
    if (!shouldShowMiniPlayer(hasStation = true, isPlayerRoute = isPlayerRoute)) return

    Surface(tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth()) {
            HorizontalDivider()
            ListItem(
                headlineContent = {
                    Text(current.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                trailingContent = {
                    IconButton(onClick = viewModel::togglePlayPause) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = stringResource(
                                if (isPlaying) R.string.mini_player_pause
                                else R.string.mini_player_play,
                            ),
                        )
                    }
                },
            )
        }
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
