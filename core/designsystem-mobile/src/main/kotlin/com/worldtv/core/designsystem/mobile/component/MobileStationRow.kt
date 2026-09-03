package com.worldtv.core.designsystem.mobile.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.worldtv.core.designsystem.R
import com.worldtv.core.designsystem.component.FavoriteIcon
import com.worldtv.core.designsystem.component.HealthDot
import com.worldtv.core.designsystem.component.PauseIcon
import com.worldtv.core.designsystem.component.StationPlayback
import com.worldtv.core.designsystem.component.StationRowState
import com.worldtv.core.designsystem.component.VolumeUpIcon

/**
 * One radio station, on a phone.
 *
 * Shared by the radio list, favourites, search and home rather than copied into each:
 * the copies had already drifted apart on what "playing" meant and on whether a long
 * press did anything.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MobileStationRow(
    state: StationRowState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    // Material 3's ListItem is a layout container and handles no clicks of its own,
    // unlike the TV one — without this the row would be inert.
    val clicks = if (onLongClick == null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
    }

    ListItem(
        modifier = modifier.then(clicks),
        headlineContent = {
            Text(
                text = state.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (state.playback == StationPlayback.IDLE) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
        },
        supportingContent = { Text(state.subtitle, maxLines = 1) },
        leadingContent = { HealthDot(state.badge) },
        trailingContent = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.isFavorite) {
                    Icon(
                        imageVector = FavoriteIcon,
                        contentDescription = stringResource(R.string.a11y_favorite),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
                StationPlaybackIcon(state.playback)
            }
        },
    )
}

@Composable
private fun StationPlaybackIcon(playback: StationPlayback) {
    val icon = when (playback) {
        StationPlayback.PLAYING, StationPlayback.BUFFERING -> VolumeUpIcon
        StationPlayback.PAUSED -> PauseIcon
        StationPlayback.IDLE -> return
    }
    val description = stringResource(
        when (playback) {
            StationPlayback.PLAYING -> R.string.a11y_playing
            StationPlayback.BUFFERING -> R.string.a11y_buffering
            else -> R.string.a11y_paused
        },
    )
    Icon(
        imageVector = icon,
        contentDescription = description,
        // A buffering station is dimmed rather than given a spinner: it is one row in a
        // long list, and a spinner there reads as the list itself loading.
        tint = if (playback == StationPlayback.PLAYING) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier.size(20.dp),
    )
}
