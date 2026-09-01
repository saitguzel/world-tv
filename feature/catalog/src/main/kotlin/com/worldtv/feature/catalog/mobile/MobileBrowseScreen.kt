package com.worldtv.feature.catalog.mobile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.worldtv.core.designsystem.component.toCardState
import com.worldtv.core.designsystem.mobile.component.MobileChannelCard
import com.worldtv.core.designsystem.mobile.component.MobileChannelGrid
import com.worldtv.feature.catalog.CatalogViewModel
import com.worldtv.feature.catalog.R
import com.worldtv.feature.catalog.TrackVisibleChannels
import kotlinx.coroutines.launch

/**
 * Browsing the catalog, for touch.
 *
 * Uses [CatalogViewModel] unchanged. Three differences from the TV screen are
 * deliberate.
 *
 * The permanent side rail becomes a sheet — see [FilterSheet].
 *
 * The grid is adaptive rather than five fixed columns, so the same screen works at
 * 360dp and on a tablet without anyone choosing a number per breakpoint.
 *
 * And there is no preview. The TV screen opens a muted second decoder after the focus
 * has rested on a card for 1.2s; a phone has no focus and no hover, so there is nothing
 * to hang that on. Substituting long-press would collide with the favourite gesture,
 * and auto-previewing whatever is centred after a scroll settles would open a stream at
 * every stop — which is the IP-ban and cellular-data scenario the dwell rule exists to
 * prevent. So this screen never calls `onPreviewTargetChanged`, and the view model's
 * lazy preview player is therefore never constructed. This is an acknowledged gap, not
 * an oversight.
 *
 * What is kept identical is [TrackVisibleChannels]: the health engine follows whatever
 * the user is looking at, and a settle-debounced snapshot of visible ids works exactly
 * as well under a fling as under a held D-pad.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileBrowseScreen(
    onChannelSelected: (String) -> Unit,
    initialCountry: String? = null,
    modifier: Modifier = Modifier,
    viewModel: CatalogViewModel = hiltViewModel(),
) {
    val countries by viewModel.countries.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val nowPlaying by viewModel.nowPlaying.collectAsStateWithLifecycle()
    val channels = viewModel.channels.collectAsLazyPagingItems()

    val gridState = rememberLazyGridState()
    val sheetState = rememberModalBottomSheetState()
    var sheetOpen by remember { mutableStateOf(false) }
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val addedLabel = stringResource(R.string.browse_favorite_added)
    val removedLabel = stringResource(R.string.browse_favorite_removed)
    val undoLabel = stringResource(R.string.browse_undo)

    LaunchedEffect(initialCountry) {
        if (initialCountry != null) viewModel.setCountry(initialCountry)
    }

    TrackVisibleChannels(
        gridState = gridState,
        channelIdAt = { index -> channels.peek(index)?.channel?.id },
        onVisible = viewModel::onChannelsVisible,
    )

    val countryName = countries.firstOrNull { it.code == filter.country }?.name
    val categoryName = categories.firstOrNull { it.id == filter.category }?.name
    val hasActiveFilter = filter.country != null || filter.category != null
    val removeLabel = stringResource(R.string.browse_remove_filter)

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.browse_all_channels)) },
                actions = {
                    IconButton(onClick = { sheetOpen = true }) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = stringResource(R.string.browse_filter),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        val loading = channels.loadState.refresh is LoadState.Loading

        // Active filters as chips rather than only in the title: with two axes the
        // title can no longer say what is on, and the x removes one axis without
        // opening the sheet at all.
        if (hasActiveFilter) {
            Row(
                Modifier
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                countryName?.let { name ->
                    FilterChipWithClear(name, removeLabel) { viewModel.setCountry(null) }
                }
                categoryName?.let { name ->
                    FilterChipWithClear(name, removeLabel) { viewModel.setCategory(null) }
                }
            }
        }

        when {
            loading && channels.itemCount == 0 -> Centered(padding) {
                CircularProgressIndicator()
            }

            channels.itemCount == 0 -> Centered(padding) {
                Text(
                    text = stringResource(R.string.browse_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            else -> MobileChannelGrid(
                modifier = Modifier.padding(top = if (hasActiveFilter) 56.dp else 0.dp)
                    .padding(padding),
                state = gridState,
            ) {
                items(
                    count = channels.itemCount,
                    key = channels.itemKey { it.channel.id },
                ) { index ->
                    val summary = channels[index] ?: return@items
                    val wasFavorite = summary.isFavorite
                    MobileChannelCard(
                        state = summary.toCardState(nowPlaying[summary.channel.id]),
                        onClick = {
                            // Hand the player the ids loaded in this grid, so the phone's
                            // next/previous walks the list the user was actually browsing.
                            viewModel.onChannelOpened(
                                channelIds = (0 until channels.itemCount)
                                    .mapNotNull { channels.peek(it)?.channel?.id },
                                startId = summary.channel.id,
                            )
                            onChannelSelected(summary.channel.id)
                        },
                        onLongClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.toggleFavorite(summary.channel.id, wasFavorite)
                            scope.launch {
                                val result = snackbarHost.showSnackbar(
                                    message = if (wasFavorite) removedLabel else addedLabel,
                                    actionLabel = undoLabel,
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    viewModel.toggleFavorite(summary.channel.id, !wasFavorite)
                                }
                            }
                        },
                    )
                }
            }
        }

        if (sheetOpen) {
            FilterSheet(
                countries = countries,
                categories = categories,
                sheetState = sheetState,
                onCountry = { viewModel.setCountry(it); sheetOpen = false },
                onCategory = { viewModel.setCategory(it); sheetOpen = false },
                onClearAll = { viewModel.clearFilters(); sheetOpen = false },
                hasActiveFilter = hasActiveFilter,
                onDismiss = { sheetOpen = false },
            )
        }
    }
}

/** A filter chip whose trailing x clears just that axis. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterChipWithClear(label: String, removeLabel: String, onClear: () -> Unit) {
    InputChip(
        selected = true,
        onClick = onClear,
        label = { Text(label) },
        trailingIcon = { Icon(Icons.Filled.Clear, contentDescription = removeLabel) },
    )
}

@Composable
private fun Centered(
    padding: androidx.compose.foundation.layout.PaddingValues,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier.fillMaxSize().padding(padding).padding(24.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}
