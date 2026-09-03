package com.worldtv.feature.catalog

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.worldtv.core.designsystem.tv.component.ChannelCard
import com.worldtv.core.designsystem.component.toCardState
import com.worldtv.core.designsystem.component.DoubleBackToExit
import com.worldtv.core.designsystem.tv.component.EmptyState
import com.worldtv.core.designsystem.tv.component.LoadingState
import com.worldtv.core.designsystem.tv.component.TvShelf
import com.worldtv.core.designsystem.theme.WorldTvColors
import com.worldtv.core.designsystem.theme.WorldTvDimens
import com.worldtv.core.model.ChannelSummary
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
import com.worldtv.feature.catalog.R

/**
 * The landing screen: a mode row, then "continue watching", favourites, and the
 * countries with the most live channels.
 *
 * At most a handful of actions are on screen at once — every one of them costs D-pad
 * presses to reach, so a dense home screen is a slow one.
 */
@Composable
fun HomeScreen(
    onExit: () -> Unit,
    onChannelSelected: (String) -> Unit,
    onBrowse: () -> Unit,
    onSearch: () -> Unit,
    onRadio: () -> Unit,
    onFavorites: () -> Unit,
    onSettings: () -> Unit,
    onCountrySelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showExitPrompt by remember { mutableStateOf(false) }

    // The only place BACK can leave the app, and only on a second press: an
    // accidental exit costs a full cold start on a TV.
    DoubleBackToExit(
        onPrompt = { showExitPrompt = true },
        onExit = onExit,
    )

    LaunchedEffect(state.recents.size, state.favorites.size) { viewModel.verifyVisibleRows() }

    LaunchedEffect(showExitPrompt) {
        if (showExitPrompt) {
            kotlinx.coroutines.delay(2_000)
            showExitPrompt = false
        }
    }

    if (state.isEmpty) {
        val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
        val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
        if (isSyncing) {
            LoadingState(message = stringResource(R.string.home_catalog_downloading),
                modifier = modifier,)
        } else {
            // A failed first sync must leave something focusable on screen, or the
            // remote does nothing and the app looks broken rather than empty. The
            // offline copy explains *why* the download never lands instead of pointing
            // at an app that looks stuck.
            EmptyState(
                message = stringResource(
                    if (isOnline) R.string.home_catalog_missing else R.string.home_offline,
                ),
                actionLabel = stringResource(R.string.home_download_now),
                onAction = viewModel::retrySync,
                modifier = modifier,
            )
        }
        return
    }

    LazyColumn(
        modifier
            .fillMaxSize()
            .focusRestorer()
            .focusGroup(),
        verticalArrangement = Arrangement.spacedBy(28.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            vertical = WorldTvDimens.ScreenPadding,
        ),
    ) {
        if (showExitPrompt) {
            item {
                Text(
                    text = stringResource(R.string.home_exit_confirm),
                    style = MaterialTheme.typography.bodyLarge,
                    color = WorldTvColors.OnSurfaceMuted,
                    modifier = Modifier.padding(horizontal = WorldTvDimens.ScreenPadding),
                )
            }
        }

        item {
            Row(
                Modifier
                    .padding(horizontal = WorldTvDimens.ScreenPadding)
                    .focusGroup(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(onClick = onBrowse) { Text(stringResource(R.string.mode_tv)) }
                Button(onClick = onRadio) { Text(stringResource(R.string.mode_radio)) }
                Button(onClick = onSearch) { Text(stringResource(R.string.action_search)) }
                Button(onClick = onFavorites) { Text(stringResource(R.string.action_favorites)) }
                Button(onClick = onSettings) { Text(stringResource(R.string.action_settings)) }
            }
        }

        if (state.recents.isNotEmpty()) {
            shelf(R.string.home_continue, state.recents, viewModel, onChannelSelected)
        }
        if (state.favorites.isNotEmpty()) {
            shelf(R.string.home_favorites, state.favorites, viewModel, onChannelSelected)
        }

        if (state.countries.isNotEmpty()) {
            item {
                SectionTitle(stringResource(R.string.home_countries))
                TvShelf(Modifier.height(72.dp)) {
                    items(state.countries, key = { it.code }) { country ->
                        Button(onClick = { onCountrySelected(country.code) }) {
                            Text(
                                stringResource(
                                    R.string.country_with_count,
                                    country.flag,
                                    country.name,
                                    country.channelCount,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.shelf(
    @StringRes titleRes: Int,
    channels: List<ChannelSummary>,
    viewModel: HomeViewModel,
    onChannelSelected: (String) -> Unit,
) {
    item {
        SectionTitle(stringResource(titleRes))
        TvShelf(Modifier.height(WorldTvDimens.CardHeight + 24.dp)) {
            items(channels, key = { it.channel.id }) { summary ->
                ChannelCard(
                    state = summary.toCardState(),
                    onClick = {
                        // The row the user picked from becomes the zap queue.
                        viewModel.onChannelOpened(channels, summary.channel.id)
                        onChannelSelected(summary.channel.id)
                    },
                    modifier = Modifier.size(WorldTvDimens.CardWidth, WorldTvDimens.CardHeight),
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        color = WorldTvColors.OnSurface,
        modifier = Modifier.padding(
            start = WorldTvDimens.ScreenPadding,
            bottom = 12.dp,
        ),
    )
}
