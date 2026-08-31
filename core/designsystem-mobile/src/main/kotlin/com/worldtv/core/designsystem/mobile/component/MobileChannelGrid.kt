package com.worldtv.core.designsystem.mobile.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.worldtv.core.designsystem.mobile.theme.MobileDimens

/**
 * The phone channel grid.
 *
 * Two things are deliberately absent compared with `TvChannelGrid`, and both matter:
 *
 * `GridCells.Adaptive` rather than a fixed column count — the TV grid hardcodes five
 * because a television is one size, while this has to work at 360dp and at 1200dp.
 *
 * No `focusRestorer`, no `focusGroup`, and above all no pivot `BringIntoViewSpec`. That
 * last one is not merely useless here: it overrides `LocalBringIntoViewSpec` for
 * everything inside, which changes touch scrolling and the way the IME and TalkBack
 * bring content into view. It lives in the TV module so it cannot reach this one.
 */
@Composable
fun MobileChannelGrid(
    modifier: Modifier = Modifier,
    state: LazyGridState = rememberLazyGridState(),
    minCellWidth: androidx.compose.ui.unit.Dp = MobileDimens.CardMinWidth,
    content: LazyGridScope.() -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minCellWidth),
        modifier = modifier,
        state = state,
        contentPadding = PaddingValues(MobileDimens.ScreenPadding),
        horizontalArrangement = Arrangement.spacedBy(MobileDimens.CardSpacing),
        verticalArrangement = Arrangement.spacedBy(MobileDimens.CardSpacing),
        content = content,
    )
}
