package com.worldtv.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.worldtv.core.designsystem.component.HealthDot
import com.worldtv.core.designsystem.theme.WorldTvColors
import com.worldtv.core.model.ChannelSummary
import androidx.compose.ui.res.stringResource
import com.worldtv.feature.player.R

/**
 * The channel list that slides in over the video.
 *
 * Two focus details do the heavy lifting:
 *  - It grabs focus on open and blocks focus escaping to the left, so LEFT closes the
 *    drawer instead of quietly walking the remote onto the video surface behind it.
 *  - It scrolls to the channel currently playing, because a list that opens at "AAA
 *    News" when the user is watching channel 300 is useless.
 */
@Composable
fun ChannelDrawer(
    channels: List<ChannelSummary>,
    currentChannelId: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(currentChannelId, channels.size) {
        val index = channels.indexOfFirst { it.channel.id == currentChannelId }
        if (index >= 0) listState.scrollToItem(index)
        focusRequester.requestFocus()
    }

    Column(
        modifier
            .width(420.dp)
            .fillMaxHeight()
            .background(WorldTvColors.Scrim)
            .padding(24.dp),
    ) {
        Text(
            text = stringResource(R.string.player_channels),
            style = MaterialTheme.typography.titleMedium,
            color = WorldTvColors.OnSurfaceMuted,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .focusRestorer()
                .focusGroup()
                // A deliberate wall. Without it, LEFT from the first column moves
                // focus onto whatever is painted behind the drawer and the user
                // cannot get back.
                .focusProperties { left = FocusRequester.Cancel },
        ) {
            items(channels, key = { it.channel.id }) { summary ->
                ListItem(
                    selected = summary.channel.id == currentChannelId,
                    onClick = { onSelect(summary.channel.id) },
                    headlineContent = {
                        Text(
                            text = summary.channel.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    trailingContent = { HealthDot(summary.healthBadge) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
