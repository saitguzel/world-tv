package com.worldtv.feature.catalog.mobile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.worldtv.core.designsystem.component.ChannelCardState
import com.worldtv.core.designsystem.component.toCardState
import com.worldtv.core.designsystem.mobile.component.MobileChannelCard
import com.worldtv.core.model.ChannelSummary
import com.worldtv.feature.catalog.HomeViewModel
import com.worldtv.feature.catalog.R

/** Cards in a shelf need a width; the grid gets its own from GridCells.Adaptive. */
private val ShelfCardWidth = 160.dp

/**
 * Home, for touch.
 *
 * Two things the TV screen has are gone rather than adapted.
 *
 * The row of five mode buttons — TV, Radio, Search, Favourites, Settings — was the
 * app's only navigation surface on a remote. On a phone those are the navigation bar,
 * and duplicating them here would be five buttons that scroll off the side of a 360dp
 * screen. Settings is the exception, since it is not a tab; it lives in the app bar.
 *
 * And there is no double-back-to-exit. It earns its place on a television, where an
 * accidental exit costs a full cold start, but on a phone it is an anti-pattern that
 * actively fights the predictive back gesture: the peek shows the launcher and then the
 * app refuses to leave.
 *
 * [HomeViewModel] is used unchanged, including verifyVisibleRows — the health engine
 * should refresh the rows the user is looking at regardless of what is driving them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileHomeScreen(
    onChannelSelected: (String) -> Unit,
    onCountrySelected: (String) -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()

    LaunchedEffect(state.recents.size, state.favorites.size) { viewModel.verifyVisibleRows() }

    // Resolved here rather than inside the LazyColumn: its content lambda is a
    // LazyListScope, not a composition, so stringResource cannot be called there.
    val continueTitle = stringResource(R.string.home_continue)
    val favoritesTitle = stringResource(R.string.home_favorites)
    val countriesTitle = stringResource(R.string.home_countries)

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.mode_tv)) },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.action_settings),
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (state.isEmpty) {
            EmptyCatalog(
                isSyncing = isSyncing,
                onRetry = viewModel::retrySync,
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }

        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (state.countries.isNotEmpty()) {
                item { SectionTitle(countriesTitle) }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.countries, key = { it.code }) { country ->
                            FilterChip(
                                selected = false,
                                onClick = { onCountrySelected(country.code) },
                                label = { Text("${country.flag} ${country.name}") },
                            )
                        }
                    }
                }
            }

            shelf(
                title = continueTitle,
                rows = state.recents,
                onOpen = { summary ->
                    viewModel.onChannelOpened(state.recents, summary.channel.id)
                    onChannelSelected(summary.channel.id)
                },
            )
            shelf(
                title = favoritesTitle,
                rows = state.favorites,
                onOpen = { summary ->
                    viewModel.onChannelOpened(state.favorites, summary.channel.id)
                    onChannelSelected(summary.channel.id)
                },
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.shelf(
    title: String,
    rows: List<ChannelSummary>,
    onOpen: (ChannelSummary) -> Unit,
) {
    if (rows.isEmpty()) return
    item { SectionTitle(title) }
    item {
        LazyRow(
            // The asymmetric trailing padding is kept from the TV shelf: leaving the
            // next card half-visible reads as "there is more" under a thumb just as
            // well as it does under a D-pad.
            contentPadding = PaddingValues(start = 16.dp, end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(rows, key = { it.channel.id }) { summary ->
                CardOf(summary.toCardState(), Modifier.width(ShelfCardWidth)) { onOpen(summary) }
            }
        }
    }
}

@Composable
private fun CardOf(state: ChannelCardState, modifier: Modifier, onClick: () -> Unit) {
    MobileChannelCard(state = state, onClick = onClick, modifier = modifier)
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun EmptyCatalog(isSyncing: Boolean, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        if (isSyncing) {
            CircularProgressIndicator()
        } else {
            androidx.compose.foundation.layout.Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.home_catalog_missing),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Button(onClick = onRetry) {
                    Text(stringResource(R.string.home_download_now))
                }
            }
        }
    }
}
