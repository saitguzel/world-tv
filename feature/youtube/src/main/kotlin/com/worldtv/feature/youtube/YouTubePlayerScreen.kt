package com.worldtv.feature.youtube

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.worldtv.core.designsystem.focus.RemoteKey
import com.worldtv.core.designsystem.focus.toRemoteKey
import com.worldtv.core.designsystem.theme.WorldTvColors
import kotlinx.coroutines.delay

/**
 * Full-screen YouTube playback.
 *
 * The whole screen is one focusable box that intercepts keys in the preview pass and
 * bridges them into the IFrame player as JS commands. The WebView is deliberately not
 * focusable: once the remote reaches a WebView its own key handling takes over and the
 * user has no reliable way back out.
 */
@Composable
fun YouTubePlayerScreen(
    videoId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: YouTubeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val live = remember(videoId, state.live) { state.live.firstOrNull { it.videoId == videoId } }

    val controller = rememberYouTubePlayerController()
    val containerFocus = remember { FocusRequester() }
    val view = LocalView.current

    var playerState by remember { mutableStateOf(YouTubePlayerState.UNSTARTED) }
    var errorCode by remember { mutableStateOf<Int?>(null) }
    var showInfo by remember { mutableStateOf(true) }

    // Nothing else on this screen is focusable, so the container has to take focus or
    // the remote does nothing at all.
    LaunchedEffect(Unit) { containerFocus.requestFocus() }

    LaunchedEffect(showInfo) {
        if (showInfo) {
            delay(INFO_CARD_MS)
            showInfo = false
        }
    }

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
                when (event.toRemoteKey()) {
                    RemoteKey.Select, RemoteKey.PlayPause -> {
                        controller.dispatch(YouTubeCommand.Toggle)
                        showInfo = true
                        true
                    }
                    RemoteKey.Info -> { showInfo = !showInfo; true }
                    RemoteKey.LongSelect -> {
                        controller.dispatch(YouTubeCommand.ToggleMute)
                        true
                    }
                    RemoteKey.Back -> { onBack(); true }
                    // Up/down/left/right have nothing to control here — a live
                    // broadcast cannot be seeked — so they are swallowed rather than
                    // moving focus somewhere invisible behind the video.
                    RemoteKey.Up, RemoteKey.Down, RemoteKey.Left, RemoteKey.Right -> true
                    else -> false
                }
            },
    ) {
        YouTubePlayerView(
            videoId = videoId,
            controller = controller,
            onReady = { errorCode = null },
            onStateChange = { playerState = it },
            onError = { errorCode = it },
            modifier = Modifier.fillMaxSize(),
        )

        val buffering = playerState == YouTubePlayerState.BUFFERING ||
            playerState == YouTubePlayerState.UNSTARTED

        if (buffering && errorCode == null) {
            StatusCard("Yükleniyor…", Modifier.align(Alignment.Center))
        }

        errorCode?.let { code ->
            StatusCard(
                text = code.describeIFrameError(),
                modifier = Modifier.align(Alignment.Center),
            )
        }

        AnimatedVisibility(
            visible = showInfo && live != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomStart).padding(48.dp),
        ) {
            Column(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(WorldTvColors.Scrim)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = live?.channelTitle.orEmpty(),
                    style = MaterialTheme.typography.titleLarge,
                    color = WorldTvColors.OnSurface,
                )
                Text(
                    text = live?.title.orEmpty(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = WorldTvColors.OnSurfaceMuted,
                )
                Text(
                    text = if (playerState == YouTubePlayerState.PAUSED) {
                        "Duraklatıldı · OK ile devam et"
                    } else {
                        "OK: duraklat · OK basılı: sessize al · GERİ: çık"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = WorldTvColors.OnSurfaceMuted,
                )
            }
        }
    }
}

@Composable
private fun StatusCard(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(WorldTvColors.Scrim)
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = WorldTvColors.OnSurface,
        )
    }
}

/**
 * IFrame API error codes.
 *
 * 101 and 150 are the same condition — the owner disabled embedded playback — and
 * both are worth naming plainly, because there is nothing the user can do and a bare
 * number just looks like a crash.
 */
internal fun Int.describeIFrameError(): String = when (this) {
    2 -> "Geçersiz video kimliği"
    5 -> "Bu yayın bu cihazda oynatılamıyor"
    100 -> "Yayın kaldırılmış veya gizli"
    101, 150 -> "Yayıncı gömülü oynatmaya izin vermiyor"
    else -> "Yayın açılamadı ($this)"
}

private const val INFO_CARD_MS = 4_000L
