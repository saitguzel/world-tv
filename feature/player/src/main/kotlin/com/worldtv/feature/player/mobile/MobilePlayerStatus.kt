package com.worldtv.feature.player.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Size
import com.worldtv.core.designsystem.theme.WorldTvColors
import com.worldtv.feature.player.R

/**
 * The phone versions of the player's status and failure surfaces.
 *
 * Written rather than shared with the TV screen: those are built on
 * `androidx.tv.material3`, whose components are unusable here. What *is* shared is what
 * matters — the same strings and the same conditions, so both form factors tell the user
 * the same thing at the same moment.
 */

/**
 * Shown while buffering and while unavailable.
 *
 * A blurred channel logo rather than black, for the reason the TV screen gives: a black
 * rectangle during a zap is indistinguishable from a crash.
 */
@Composable
fun MobileLoadingBackdrop(logoUrl: String?, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(WorldTvColors.Surface)) {
        if (logoUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(logoUrl)
                    .size(Size(320, 320))
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(48.dp)
                    .background(Color.Black.copy(alpha = 0.5f)),
            )
        }
    }
}

/** One line of status over a scrim — "trying an alternative", "may be geo-blocked". */
@Composable
fun MobileStatusBanner(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(WorldTvColors.Scrim)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = WorldTvColors.OnSurface,
        )
    }
}

/**
 * Every stream for this channel failed.
 *
 * The TV version grabs focus onto the retry button, because a failure screen with
 * nothing focusable makes the remote go dead exactly when the user most wants to act. A
 * thumb has no such problem, so this deliberately does not.
 */
@Composable
fun MobileChannelUnavailable(
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = stringResource(R.string.player_unavailable),
            style = MaterialTheme.typography.titleLarge,
            color = WorldTvColors.OnSurface,
            textAlign = TextAlign.Center,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onRetry) { Text(stringResource(R.string.player_retry)) }
            TextButton(onClick = onBack) { Text(stringResource(R.string.player_back_to_list)) }
        }
    }
}
