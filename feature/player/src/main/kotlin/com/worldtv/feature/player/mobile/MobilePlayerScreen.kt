package com.worldtv.feature.player.mobile

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.PlayerSurface
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.worldtv.core.designsystem.component.HealthDot
import com.worldtv.core.model.HealthBadge
import com.worldtv.feature.player.PlayerGesture
import com.worldtv.feature.player.PlayerGestures
import com.worldtv.feature.player.PlayerViewModel
import com.worldtv.feature.player.R

/**
 * The player, for touch.
 *
 * Every action the TV screen binds to a key is bound to a gesture or a button here, and
 * [PlayerViewModel] is unchanged apart from two additions it never needed on a
 * television: an aspect ratio, and a play/pause action.
 *
 * | remote            | phone                                    |
 * |-------------------|------------------------------------------|
 * | UP / CHANNEL_UP   | drag up, or the ⌃ button                 |
 * | DOWN             | drag down, or the ⌄ button               |
 * | LEFT / RIGHT     | drag sideways, or "channels"             |
 * | SELECT           | tap the video                            |
 * | LONG SELECT      | the heart button — a long press on video |
 * |                  | is invisible and undiscoverable          |
 * | BACK             | system back, or the ← button             |
 *
 * Orientation is left alone deliberately. Forcing landscape is hostile to the 4:3 SD
 * streams this catalog is full of, and to anyone holding the phone in portrait on
 * purpose; instead the surface takes the stream's own ratio in portrait and fills the
 * screen in landscape.
 */
// media3-ui-compose, and PlayerSurface with it, is still @UnstableApi — the same
// opt-in the TV screen carries.
@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobilePlayerScreen(
    channelId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val drawerChannels by viewModel.drawerChannels.collectAsStateWithLifecycle()
    val view = LocalView.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val landscape = LocalConfiguration.current.screenWidthDp > LocalConfiguration.current.screenHeightDp
    val threshold = with(density) { PlayerGestures.DEFAULT_THRESHOLD_DP.dp.toPx() }

    val channelSheet = rememberModalBottomSheetState()
    val trackSheet = rememberModalBottomSheetState()

    LaunchedEffect(channelId) { viewModel.openChannel(channelId) }

    LaunchedEffect(state.showOverlay) {
        if (state.showOverlay) {
            kotlinx.coroutines.delay(OVERLAY_TIMEOUT_MS)
            viewModel.hideOverlay()
        }
    }

    // Immersive while watching, restored on the way out — the same contract the TV
    // screen has for keepScreenOn, extended to the system bars a phone has and a
    // television does not.
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        val window = (context as? Activity)?.window
        val controller = window?.let { WindowInsetsControllerCompat(it, view) }
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose {
            view.keepScreenOn = false
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // A sheet takes back before the screen does; without this, back would leave the
    // player with the sheet still nominally open in the view model.
    BackHandler(enabled = state.showChannelDrawer || state.showTrackPicker) {
        if (state.showTrackPicker) viewModel.closeTrackPicker() else viewModel.closeChannelDrawer()
    }

    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(threshold) {
                detectTapGestures { viewModel.toggleOverlay() }
            }
            .pointerInput(threshold) {
                var dx = 0f
                var dy = 0f
                detectDragGestures(
                    onDragStart = { dx = 0f; dy = 0f },
                    onDragEnd = {
                        when (PlayerGestures.fromDrag(dx, dy, threshold)) {
                            PlayerGesture.ZapUp -> viewModel.zap(+1)
                            PlayerGesture.ZapDown -> viewModel.zap(-1)
                            PlayerGesture.OpenChannels -> viewModel.openChannelDrawer()
                            null -> Unit
                        }
                    },
                ) { _, dragAmount ->
                    dx += dragAmount.x
                    dy += dragAmount.y
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        PlayerSurface(
            player = viewModel.player,
            modifier = if (landscape) {
                Modifier.fillMaxSize()
            } else {
                // The ratio comes from the stream and is guarded against the 0x0 that
                // ExoPlayer reports while buffering — NaN here would crash the layout.
                Modifier.fillMaxWidth().aspectRatio(state.videoAspectRatio)
            },
        )

        AnimatedVisibility(
            visible = state.showOverlay || state.unavailable,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Hud(
                title = state.channel?.name.orEmpty(),
                isPlaying = state.isPlaying,
                isFavorite = state.isFavorite,
                hasTracks = state.subtitleTracks.size + state.audioTracks.size > 1,
                onBack = onBack,
                onPrevious = { viewModel.zap(-1) },
                onNext = { viewModel.zap(+1) },
                onPlayPause = viewModel::togglePlayPause,
                onFavorite = viewModel::toggleFavorite,
                onChannels = viewModel::openChannelDrawer,
                onTracks = viewModel::openTrackPicker,
            )
        }
    }

    if (state.showChannelDrawer) {
        val listState = rememberLazyListState()
        val currentIndex = queue.channelIds.indexOf(state.channel?.id).coerceAtLeast(0)
        LaunchedEffect(currentIndex) { listState.scrollToItem(currentIndex) }

        // onDismissRequest must reach the view model, or the flag stays true and the
        // sheet reopens on the next recomposition.
        ModalBottomSheet(
            onDismissRequest = viewModel::closeChannelDrawer,
            sheetState = channelSheet,
        ) {
            LazyColumn(state = listState) {
                items(drawerChannels.size, key = { drawerChannels[it].channel.id }) { index ->
                    val summary = drawerChannels[index]
                    ListItem(
                        modifier = Modifier.padding(horizontal = 0.dp),
                        headlineContent = {
                            Text(
                                summary.channel.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (summary.channel.id == state.channel?.id) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                        },
                        leadingContent = { HealthDot(summary.healthBadge) },
                    )
                }
            }
        }
    }

    if (state.showTrackPicker) {
        ModalBottomSheet(
            onDismissRequest = viewModel::closeTrackPicker,
            sheetState = trackSheet,
        ) {
            LazyColumn {
                items(state.subtitleTracks.size + state.audioTracks.size) { index ->
                    val track = (state.subtitleTracks + state.audioTracks)[index]
                    ListItem(
                        headlineContent = { Text(track.label) },
                        leadingContent = if (track.isSelected) {
                            { Icon(Icons.Filled.PlayArrow, contentDescription = null) }
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }
}

/**
 * The heads-up display.
 *
 * The TV screen anchors its pieces to three corners with a flat 48dp of overscan
 * padding. In portrait those overlap each other and collide with the status bar and
 * camera cutout, so this is a top bar and a bottom bar over scrims, inset-aware rather
 * than fixed.
 */
@Composable
private fun Hud(
    title: String,
    isPlaying: Boolean,
    isFavorite: Boolean,
    hasTracks: Boolean,
    onBack: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPlayPause: () -> Unit,
    onFavorite: () -> Unit,
    onChannels: () -> Unit,
    onTracks: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.55f))
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = null, tint = Color.White)
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onFavorite) {
                Icon(
                    Icons.Filled.Favorite,
                    contentDescription = stringResource(
                        if (isFavorite) R.string.player_favorite_remove
                        else R.string.player_favorite_add,
                    ),
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else Color.White,
                )
            }
            // A dead button still costs a tap to skip, so it is not drawn when the
            // stream offers no choice — the same rule the TV controls use.
            if (hasTracks) {
                IconButton(onClick = onTracks) {
                    Icon(Icons.Filled.Settings, contentDescription = null, tint = Color.White)
                }
            }
        }

        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.55f))
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPrevious) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = Color.White)
            }
            IconButton(onClick = onPlayPause) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = stringResource(
                        if (isPlaying) R.string.player_pause else R.string.player_play,
                    ),
                    tint = Color.White,
                )
            }
            IconButton(onClick = onNext) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null, tint = Color.White)
            }
            IconButton(onClick = onChannels) {
                Icon(Icons.Filled.List, contentDescription = null, tint = Color.White)
            }
        }
    }
}

private const val OVERLAY_TIMEOUT_MS = 3_000L
