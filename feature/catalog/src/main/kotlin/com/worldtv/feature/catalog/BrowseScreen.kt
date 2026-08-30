package com.worldtv.feature.catalog

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.worldtv.core.designsystem.component.ChannelCard
import com.worldtv.core.designsystem.component.ChannelCardState
import com.worldtv.core.designsystem.component.EmptyState
import com.worldtv.core.designsystem.component.LoadingState
import com.worldtv.core.designsystem.component.TvChannelGrid
import com.worldtv.core.model.ChannelSummary
import com.worldtv.core.model.Programme

/**
 * Browse: a persistent country/category drawer on the left, the channel grid on the
 * right. Navigation depth is capped at three screens throughout the app — backing out
 * of four levels with a remote is punishing.
 */
@Composable
fun BrowseScreen(
    onChannelSelected: (String) -> Unit,
    initialCountry: String? = null,
    modifier: Modifier = Modifier,
    viewModel: CatalogViewModel = hiltViewModel(),
) {
    val countries by viewModel.countries.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val channels = viewModel.channels.collectAsLazyPagingItems()
    val nowPlaying by viewModel.nowPlaying.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()

    // Arriving from a country tile on Home preselects that country.
    LaunchedEffect(initialCountry) {
        if (initialCountry != null) viewModel.setCountry(initialCountry)
    }

    // Snapshot the visible ids and hand them to the health engine once scrolling
    // settles, so probes follow the user rather than the catalog.
    TrackVisibleChannels(
        gridState = gridState,
        channelIdAt = { index -> channels.peek(index)?.channel?.id },
        onVisible = viewModel::onChannelsVisible,
    )

    Row(modifier.fillMaxSize()) {
        CountryDrawer(
            countries = countries,
            selectedCountry = filter.country,
            onCountrySelected = { code ->
                viewModel.setCountry(code)
                viewModel.rememberHomeCountry(code)
            },
        )

        val isInitialLoad = channels.loadState.refresh is androidx.paging.LoadState.Loading &&
            channels.itemCount == 0

        when {
            isInitialLoad -> LoadingState(
                message = "Kanallar yükleniyor…",
                modifier = Modifier.weight(1f),
            )

            channels.itemCount == 0 -> EmptyState(
                message = "Bu listede kanal yok",
                actionLabel = "Yeniden dene",
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
                    // A stable key is what keeps focus pinned to the same card when
                    // the health engine changes the list underneath. Without it,
                    // Compose matches by index and focus jumps to a different channel.
                    key = channels.itemKey { it.channel.id },
                ) { index ->
                    val summary = channels[index] ?: return@items
                    ChannelCard(
                        state = summary.toCardState(nowPlaying[summary.channel.id]),
                        onClick = {
                            // Hand the player the ids currently loaded in this grid,
                            // so up/down zaps through what the user was browsing.
                            viewModel.onChannelOpened(
                                channelIds = (0 until channels.itemCount)
                                    .mapNotNull { channels.peek(it)?.channel?.id },
                                startId = summary.channel.id,
                            )
                            onChannelSelected(summary.channel.id)
                        },
                        onLongClick = {
                            viewModel.toggleFavorite(summary.channel.id, summary.isFavorite)
                        },
                    )
                }
            }
        }
    }
}

internal fun ChannelSummary.toCardState(
    nowPlaying: Programme? = null,
): ChannelCardState = ChannelCardState(
    id = channel.id,
    name = channel.name,
    logoUrl = channel.logoUrl,
    badge = healthBadge,
    isFavorite = isFavorite,
    // Falls back to the measured latency only when there is no guide for this
    // channel — a viewer wants to know what is on, not how fast the socket was.
    subtitle = bestLatencyMs?.let { "$it ms" },
    nowPlaying = nowPlaying,
)
