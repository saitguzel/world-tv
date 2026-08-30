package com.worldtv.feature.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.compose.PlayerSurface
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.worldtv.core.designsystem.focus.RemoteKey
import com.worldtv.core.designsystem.focus.rememberKeyRepeatLimiter
import com.worldtv.core.designsystem.focus.toRemoteKey
import com.worldtv.core.designsystem.theme.WorldTvColors
import androidx.compose.ui.res.stringResource
import com.worldtv.feature.player.R

/**
 * Full-screen player.
 *
 * Focus works differently here than anywhere else in the app: while watching, nothing
 * is focused and the video owns the screen. Keys are intercepted at the container in
 * the preview pass, because a component that is not focused cannot receive key events
 * in Compose, and the whole point of this screen is that nothing is focused.
 */
@Composable
fun PlayerScreen(
    channelId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val nowNext by viewModel.nowNext.collectAsStateWithLifecycle()
    val overlayFocus = remember { FocusRequester() }
    val containerFocus = remember { FocusRequester() }
    val repeatLimiter = rememberKeyRepeatLimiter()
    val view = LocalView.current

    LaunchedEffect(channelId) { viewModel.openChannel(channelId) }

    // Nothing on this screen is focusable by default, so the container has to take
    // focus itself or the remote does nothing at all.
    LaunchedEffect(Unit) { containerFocus.requestFocus() }

    LaunchedEffect(state.showOverlay) {
        if (state.showOverlay) overlayFocus.requestFocus()
    }

    // Auto-hide the overlay. Restarted whenever the overlay is reopened.
    LaunchedEffect(state.showOverlay) {
        if (state.showOverlay) {
            kotlinx.coroutines.delay(OVERLAY_TIMEOUT_MS)
            viewModel.hideOverlay()
        }
    }

    // Keep the screen awake while video is playing, and — just as importantly —
    // release it as soon as this screen goes away. Radio mode must never hold it.
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    Box(
        modifier
            .fillMaxSize()
            .background(WorldTvColors.Surface)
            .focusRequester(containerFocus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (!repeatLimiter.accept(event.nativeKeyEvent.repeatCount)) {
                    return@onPreviewKeyEvent true
                }
                // While the drawer or overlay is open, its own focusable content
                // owns the D-pad; intercepting here would steal navigation from it.
                if (state.showChannelDrawer || state.showOverlay || state.showTrackPicker) {
                    return@onPreviewKeyEvent when (event.toRemoteKey()) {
                        RemoteKey.Back -> {
                            when {
                                state.showTrackPicker -> viewModel.closeTrackPicker()
                                state.showChannelDrawer -> viewModel.closeChannelDrawer()
                                else -> viewModel.hideOverlay()
                            }
                            true
                        }
                        else -> false
                    }
                }
                when (event.toRemoteKey()) {
                    RemoteKey.Up, RemoteKey.ChannelUp -> { viewModel.zap(+1); true }
                    RemoteKey.Down, RemoteKey.ChannelDown -> { viewModel.zap(-1); true }
                    RemoteKey.Left, RemoteKey.Right -> { viewModel.openChannelDrawer(); true }
                    RemoteKey.Select -> { viewModel.toggleOverlay(); true }
                    RemoteKey.LongSelect -> { viewModel.toggleFavorite(); true }
                    RemoteKey.Back -> {
                        // BACK returns to the list rather than leaving the app.
                        onBack()
                        true
                    }
                    else -> false
                }
            },
    ) {
        PlayerSurface(
            player = viewModel.player,
            modifier = Modifier.fillMaxSize(),
        )

        if (state.isBuffering || state.unavailable) {
            // Never a black screen during a zap: the channel logo blurred behind a
            // scrim reads as "loading", a black rectangle reads as "broken".
            LoadingBackdrop(
                logoUrl = state.channel?.logoUrl,
                modifier = Modifier.fillMaxSize(),
            )
        }

        AnimatedVisibility(
            visible = state.showTrackPicker,
            enter = slideInHorizontally { -it },
            exit = slideOutHorizontally { -it },
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            TrackPicker(
                subtitleTracks = state.subtitleTracks,
                audioTracks = state.audioTracks,
                onSelect = viewModel::selectTrack,
            )
        }

        AnimatedVisibility(
            visible = state.showChannelDrawer,
            enter = slideInHorizontally { it },
            exit = slideOutHorizontally { it },
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            val drawerChannels by viewModel.drawerChannels.collectAsStateWithLifecycle()
            ChannelDrawer(
                channels = drawerChannels,
                currentChannelId = queue.currentId ?: state.channel?.id,
                onSelect = viewModel::jumpTo,
                onDismiss = viewModel::closeChannelDrawer,
            )
        }

        AnimatedVisibility(
            visible = state.showChannelCard,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopStart).padding(48.dp),
        ) {
            ChannelInfoCard(state, nowNext)
        }

        AnimatedVisibility(
            visible = state.showOverlay,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            PlayerControls(
                state = state,
                onToggleFavorite = viewModel::toggleFavorite,
                onOpenChannelList = viewModel::openChannelDrawer,
                onOpenTracks = viewModel::openTrackPicker,
                hasTrackChoices = state.subtitleTracks.isNotEmpty() ||
                    state.audioTracks.size > 1,
                modifier = Modifier.focusRequester(overlayFocus),
            )
        }

        if (state.tryingAlternative && !state.unavailable) {
            StatusBanner(
                text = stringResource(R.string.player_trying_alternative),
                modifier = Modifier.align(Alignment.BottomStart).padding(48.dp),
            )
        }

        if (state.geoWarning && !state.isBuffering) {
            StatusBanner(
                text = stringResource(R.string.player_geo_warning),
                modifier = Modifier.align(Alignment.TopEnd).padding(48.dp),
            )
        }

        if (state.unavailable) {
            ChannelUnavailable(
                onRetry = viewModel::retry,
                onBack = onBack,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@Composable
private fun StatusBanner(text: String, modifier: Modifier = Modifier) {
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

private const val OVERLAY_TIMEOUT_MS = 3_000L
