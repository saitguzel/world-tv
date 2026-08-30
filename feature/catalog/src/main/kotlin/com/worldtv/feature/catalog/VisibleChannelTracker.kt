package com.worldtv.feature.catalog

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

/**
 * Reports which channels are on screen, so the health engine can verify them.
 *
 * Debounced: holding the D-pad down walks through hundreds of items, and firing a
 * probe batch for each intermediate position would swamp the connection pool with work
 * for rows the user never actually looked at.
 */
@OptIn(FlowPreview::class)
@Composable
fun TrackVisibleChannels(
    gridState: LazyGridState,
    channelIdAt: (Int) -> String?,
    onVisible: (List<String>) -> Unit,
) {
    LaunchedEffect(gridState, channelIdAt) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.map { it.index } }
            .debounce(SETTLE_DELAY_MS)
            .map { indices -> indices.mapNotNull(channelIdAt) }
            .filter { it.isNotEmpty() }
            .distinctUntilChanged()
            .collect(onVisible)
    }
}

/** Long enough that a scroll has stopped, short enough that dots appear while looking. */
private const val SETTLE_DELAY_MS = 400L
