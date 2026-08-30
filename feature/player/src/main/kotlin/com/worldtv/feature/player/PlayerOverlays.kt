package com.worldtv.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusGroup
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Size
import com.worldtv.core.designsystem.component.NowNextBlock
import com.worldtv.core.designsystem.theme.WorldTvColors
import com.worldtv.core.model.NowNext

/**
 * What is drawn while a channel loads.
 *
 * A blurred channel logo, never black. During a zap the user's eye is on the screen,
 * and a black rectangle is indistinguishable from a crash.
 */
@Composable
fun LoadingBackdrop(logoUrl: String?, modifier: Modifier = Modifier) {
    Box(modifier.background(WorldTvColors.Surface)) {
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

/** The card shown for three seconds after a zap. */
@Composable
fun ChannelInfoCard(
    state: PlayerUiState,
    nowNext: NowNext = NowNext(null, null),
    modifier: Modifier = Modifier,
) {
    val channel = state.channel ?: return
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(WorldTvColors.Scrim)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = channel.name,
            style = MaterialTheme.typography.headlineMedium,
            color = WorldTvColors.OnSurface,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            state.stream?.quality?.let { quality ->
                Text(
                    text = quality,
                    style = MaterialTheme.typography.labelLarge,
                    color = WorldTvColors.OnSurfaceMuted,
                )
            }
            if (state.totalStreams > 1) {
                Text(
                    text = "Kaynak ${state.streamIndex + 1}/${state.totalStreams}",
                    style = MaterialTheme.typography.labelLarge,
                    color = WorldTvColors.OnSurfaceMuted,
                )
            }
        }

        // Only drawn when a guide exists for this channel; the block renders nothing
        // rather than reserving empty space for the many channels that have none.
        NowNextBlock(
            now = nowNext.now,
            next = nowNext.next,
            instant = System.currentTimeMillis(),
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/**
 * Overlay controls.
 *
 * `focusGroup()` plus an explicit focus request keeps the remote inside the overlay
 * while it is open — without it, LEFT/RIGHT would walk into whatever is painted behind.
 */
@Composable
fun PlayerControls(
    state: PlayerUiState,
    onToggleFavorite: () -> Unit,
    onOpenChannelList: () -> Unit,
    onOpenTracks: () -> Unit = {},
    hasTrackChoices: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val firstButton = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstButton.requestFocus() }

    Row(
        modifier
            .fillMaxWidth()
            .background(WorldTvColors.Scrim)
            .padding(48.dp)
            .focusGroup(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = onToggleFavorite,
            modifier = Modifier.focusRequester(firstButton),
        ) {
            Text(if (state.isFavorite) "Favorilerden çıkar" else "Favorilere ekle")
        }
        Button(onClick = onOpenChannelList) {
            Text("Kanal listesi")
        }
        // Hidden entirely when the stream carries nothing to choose between, rather
        // than shown disabled — a dead button still costs a D-pad press to skip.
        if (hasTrackChoices) {
            Button(onClick = onOpenTracks) {
                Text("Altyazı ve ses")
            }
        }
    }
}

/** Shown once every alternative for a channel has failed. */
@Composable
fun ChannelUnavailable(
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val retryFocus = remember { FocusRequester() }
    // Always leave something focusable on a failure screen, or the remote goes dead
    // exactly when the user most wants to do something.
    LaunchedEffect(Unit) { retryFocus.requestFocus() }

    Column(
        modifier.padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = "Bu kanalın çalışan yayını bulunamadı",
            style = MaterialTheme.typography.titleLarge,
            color = WorldTvColors.OnSurface,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(onClick = onRetry, modifier = Modifier.focusRequester(retryFocus)) {
                Text("Yeniden dene")
            }
            Button(onClick = onBack) { Text("Listeye dön") }
        }
    }
}
