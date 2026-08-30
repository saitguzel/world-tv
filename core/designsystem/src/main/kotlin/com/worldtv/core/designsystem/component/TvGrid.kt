package com.worldtv.core.designsystem.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusGroup
import androidx.compose.ui.focus.focusRestorer
import com.worldtv.core.designsystem.focus.rememberTvPivotSpec
import com.worldtv.core.designsystem.theme.WorldTvDimens

/**
 * The channel grid.
 *
 * `focusRestorer()` before `focusGroup()` is deliberate and load-bearing: it puts
 * focus back on the card the user left from when they return from the player. Without
 * it, coming back from channel 47 drops focus at the top of the list, which is the
 * single most reported annoyance in TV apps.
 *
 * `LazyVerticalGrid`, not `TvLazyVerticalGrid` — the `TvLazy*` containers were removed
 * from `tv-foundation` once `compose-foundation` grew the TV behaviour itself.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TvChannelGrid(
    columns: Int = 5,
    state: LazyGridState = rememberLazyGridState(),
    modifier: Modifier = Modifier,
    content: androidx.compose.foundation.lazy.grid.LazyGridScope.() -> Unit,
) {
    CompositionLocalProvider(LocalBringIntoViewSpec provides rememberTvPivotSpec(0.35f)) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = state,
            modifier = modifier
                .focusRestorer()
                .focusGroup(),
            contentPadding = PaddingValues(WorldTvDimens.ScreenPadding),
            horizontalArrangement = Arrangement.spacedBy(WorldTvDimens.CardSpacing),
            verticalArrangement = Arrangement.spacedBy(WorldTvDimens.CardSpacing),
            content = content,
        )
    }
}

/**
 * A horizontal shelf.
 *
 * The trailing content padding is smaller than the leading one so the next card is
 * always half-visible at the right edge — that sliver is what tells a TV user the row
 * scrolls at all.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TvShelf(
    modifier: Modifier = Modifier,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    CompositionLocalProvider(LocalBringIntoViewSpec provides rememberTvPivotSpec(0.3f)) {
        LazyRow(
            state = rememberLazyListState(),
            modifier = modifier
                .focusRestorer()
                .focusGroup(),
            contentPadding = PaddingValues(
                start = WorldTvDimens.ScreenPadding,
                end = WorldTvDimens.ScreenPadding / 2,
            ),
            horizontalArrangement = Arrangement.spacedBy(WorldTvDimens.CardSpacing),
            content = content,
        )
    }
}
