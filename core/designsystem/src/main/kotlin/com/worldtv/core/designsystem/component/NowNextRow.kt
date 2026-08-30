package com.worldtv.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.worldtv.core.designsystem.R
import com.worldtv.core.designsystem.theme.WorldTvColors
import com.worldtv.core.model.Programme

/**
 * "Now playing" line with a progress bar.
 *
 * A plain bar rather than a Material progress indicator: this is drawn once per card
 * in a grid of sixty, and an indeterminate-capable component brings an animation
 * driver along with it that would run for every one of them.
 */
@Composable
fun NowPlayingLine(
    programme: Programme,
    now: Long,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = programme.title,
            style = MaterialTheme.typography.labelLarge,
            color = WorldTvColors.OnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        ProgressBar(fraction = programme.progressAt(now))
    }
}

@Composable
private fun ProgressBar(fraction: Float, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(WorldTvColors.HealthUnchecked),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(WorldTvColors.Accent),
        )
    }
}

/** Now and next, for the player's info card. */
@Composable
fun NowNextBlock(
    now: Programme?,
    next: Programme?,
    instant: Long,
    modifier: Modifier = Modifier,
) {
    if (now == null && next == null) return

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        now?.let { programme ->
            Text(
                text = programme.title,
                style = MaterialTheme.typography.titleMedium,
                color = WorldTvColors.OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            ProgressBar(fraction = programme.progressAt(instant))
            programme.description?.let { description ->
                Text(
                    text = description,
                    style = MaterialTheme.typography.labelLarge,
                    color = WorldTvColors.OnSurfaceMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        next?.let { programme ->
            Text(
                text = stringResource(R.string.programme_next, programme.title),
                style = MaterialTheme.typography.labelLarge,
                color = WorldTvColors.OnSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
