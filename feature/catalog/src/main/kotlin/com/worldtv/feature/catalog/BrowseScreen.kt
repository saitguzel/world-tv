package com.worldtv.feature.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.worldtv.core.designsystem.tv.component.ChannelCard
import com.worldtv.core.designsystem.tv.component.EmptyState
import com.worldtv.core.designsystem.tv.component.LoadingState
import com.worldtv.core.designsystem.tv.component.TvChannelGrid
import com.worldtv.core.designsystem.component.toCardState
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
import com.worldtv.feature.catalog.R

/**
 * Browse: a persistent country/category drawer on the left, the channel grid on the
 * right. Navigation depth is capped at three screens throughout the app — backing out
 * of four levels with a remote is punishing.
 */
@Composable
fun BrowseScreen(
    onChannelSelected: (String) -> Unit,
    initialCountry: String? = null,
    initialCategory: String? = null,
    modifier: Modifier = Modifier,
    viewModel: CatalogViewModel = hiltViewModel(),
) {
    val countries by viewModel.countries.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val channels = viewModel.channels.collectAsLazyPagingItems()
    val nowPlaying by viewModel.nowPlaying.collectAsStateWithLifecycle()
    val previewUrl by viewModel.previewUrl.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    var focusedChannelId by remember { mutableStateOf<String?>(null) }

    // The dwell rule: nothing starts until the user has actually stopped on a card.
    // Without it, walking across a row opens and abandons a connection per channel.
    val previewTarget = rememberPreviewTarget(
        focusedChannelId = focusedChannelId,
        enabled = preferences.previewOnFocus,
    )
    LaunchedEffect(previewTarget) { viewModel.onPreviewTargetChanged(previewTarget) }
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()

    // Arriving from a country or category tile on Home preselects that filter. The
    // two are independent: a category arriving alone must not clear the home country
    // the view model seeded.
    LaunchedEffect(initialCountry) {
        if (initialCountry != null) viewModel.setCountry(initialCountry)
    }
    LaunchedEffect(initialCategory) {
        if (initialCategory != null) viewModel.setCategory(initialCategory)
    }

    // Snapshot the visible ids and hand them to the health engine once scrolling
    // settles, so probes follow the user rather than the catalog.
    TrackVisibleChannels(
        gridState = gridState,
        channelIdAt = { index -> channels.peek(index)?.channel?.id },
        onVisible = viewModel::onChannelsVisible,
    )

    Box(modifier.fillMaxSize()) {
        // Behind everything and dimmed: the preview is context, not the subject. At
        // full brightness it competes with the card the user is trying to read.
        if (previewUrl != null) {
            PreviewSurface(
                player = viewModel.previewPlayer,
                streamUrl = previewUrl,
                modifier = Modifier.fillMaxSize().alpha(0.28f),
            )
        }

        Row(Modifier.fillMaxSize()) {
            CountryDrawer(
                countries = countries,
                categories = categories,
                selectedCountry = filter.country,
                selectedCategory = filter.category,
                onCountrySelected = { code ->
                    viewModel.setCountry(code)
                    viewModel.rememberHomeCountry(code)
                },
                onCategorySelected = viewModel::setCategory,
                onRandom = {
                    scope.launch {
                        viewModel.openRandomChannel(
                            loadedIds = (0 until channels.itemCount)
                                .mapNotNull { channels.peek(it)?.channel?.id },
                        )?.let(onChannelSelected)
                    }
                },
            )

            val isInitialLoad =
                channels.loadState.refresh is LoadState.Loading && channels.itemCount == 0

            when {
                isInitialLoad -> LoadingState(
                    message = stringResource(R.string.browse_loading),
                    modifier = Modifier.weight(1f),
                )

                channels.itemCount == 0 -> EmptyState(
                    message = stringResource(R.string.browse_empty),
                    actionLabel = stringResource(R.string.browse_retry),
                    onAction = { channels.refresh() },
                    modifier = Modifier.weight(1f),
                )

                else -> TvChannelGrid(
                    columns = 5,
                    state = gridState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                ) {
                    items(
                        count = channels.itemCount,
                        // A stable key is what pins focus to the same card when the
                        // health engine changes the list underneath. Without it,
                        // Compose matches by index and focus jumps to another channel.
                        key = channels.itemKey { it.channel.id },
                    ) { index ->
                        val summary = channels[index] ?: return@items
                        ChannelCard(
                            state = summary.toCardState(nowPlaying[summary.channel.id]),
                            onClick = {
                                // Hand the player the ids loaded in this grid, so
                                // up/down zaps through what the user was browsing.
                                viewModel.onChannelOpened(
                                    channelIds = (0 until channels.itemCount)
                                        .mapNotNull { channels.peek(it)?.channel?.id },
                                    startId = summary.channel.id,
                                )
                                onChannelSelected(summary.channel.id)
                            },
                            onLongClick = {
                                viewModel.toggleFavorite(
                                    summary.channel.id,
                                    summary.isFavorite,
                                )
                            },
                            onFocusChanged = { focused ->
                                if (focused) focusedChannelId = summary.channel.id
                            },
                        )
                    }
                }
            }
        }
    }
}

