package com.worldtv.core.designsystem.tv.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.Text
import com.worldtv.core.designsystem.R
import com.worldtv.core.designsystem.component.FavoriteIcon
import com.worldtv.core.designsystem.component.HealthDot
import com.worldtv.core.designsystem.component.PauseIcon
import com.worldtv.core.designsystem.component.StationPlayback
import com.worldtv.core.designsystem.component.StationRowState
import com.worldtv.core.designsystem.component.VolumeUpIcon
import com.worldtv.core.designsystem.theme.WorldTvColors

/**
 * One radio station, on a television.
 *
 * The phone twin is `MobileStationRow`; the state and the rule that fills it are
 * shared, only the Material tree differs.
 */
@Composable
fun TvStationRow(
    state: StationRowState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: () -> Unit = {},
) {
    ListItem(
        // Selected means focus-and-current, which is what the D-pad list needs; the
        // playing indicator is separate, because a station can be current and silent.
        selected = state.playback != StationPlayback.IDLE,
        onClick = onClick,
        onLongClick = onLongClick,
        headlineContent = {
            Text(text = state.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = { Text(state.subtitle, color = WorldTvColors.OnSurfaceMuted) },
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
                        tint = WorldTvColors.Accent,
                        modifier = Modifier.size(18.dp),
                    )
                }
                StationPlaybackIcon(state.playback)
            }
        },
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun StationPlaybackIcon(playback: StationPlayback) {
    val icon = when (playback) {
        StationPlayback.PLAYING, StationPlayback.BUFFERING -> VolumeUpIcon
        StationPlayback.PAUSED -> PauseIcon
        StationPlayback.IDLE -> return
    }
    Icon(
        imageVector = icon,
        contentDescription = stringResource(
            when (playback) {
                StationPlayback.PLAYING -> R.string.a11y_playing
                StationPlayback.BUFFERING -> R.string.a11y_buffering
                else -> R.string.a11y_paused
            },
        ),
        tint = if (playback == StationPlayback.PLAYING) {
            WorldTvColors.Accent
        } else {
            WorldTvColors.OnSurfaceMuted
        },
        modifier = Modifier.size(20.dp),
    )
}
