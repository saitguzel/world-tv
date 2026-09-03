package com.worldtv.feature.favorites.mobile

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.worldtv.core.designsystem.component.HealthDot
import com.worldtv.core.designsystem.component.toCardState
import com.worldtv.core.designsystem.mobile.component.MobileChannelCard
import com.worldtv.core.designsystem.mobile.component.MobileChannelGrid
import com.worldtv.core.model.RadioStation
import com.worldtv.feature.favorites.FavoritesViewModel
import com.worldtv.feature.favorites.R
import kotlinx.coroutines.launch
import com.worldtv.core.model.badge
import com.worldtv.core.model.describe
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import com.worldtv.feature.radio.mobile.rememberNotificationPermissionRequest

/**
 * Favourites, for touch.
 *
 * Shares [FavoritesViewModel] with the TV screen — what differs is the input model, not
 * the data — and extends it with a radio section. Radio favourites are rows rather than
 * cards (there is no logo grid to hang a card on), so they sit in the same grid as
 * full-width spans below the channel cards: one scroll container, two kinds of entry.
 *
 * Removal is the interesting difference. On TV a long press is the only way, and it is
 * discoverable there because it is the only thing a long press does. On a phone that is
 * not true, so the gesture is kept as the accelerator but paired with haptic feedback
 * and an undo snackbar: the snackbar is what teaches the gesture to someone who
 * triggered it by accident, and what makes the accident cheap.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileFavoritesScreen(
    onChannelSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FavoritesViewModel = hiltViewModel(),
) {
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val radioFavorites by viewModel.radioFavorites.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    // Same request the radio screen makes: without it the phone shows no transport
    // notification once the app is in the background.
    val requestNotifications = rememberNotificationPermissionRequest()
    val removedLabel = stringResource(R.string.favorites_removed)
    val undoLabel = stringResource(R.string.favorites_undo)

    // Identical to the TV screen: the health of a list the user cares about is worth
    // refreshing whenever they look at it.
    LaunchedEffect(Unit) { viewModel.refreshFavoriteHealth() }

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.favorites_title)) }) },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        if (favorites.isEmpty() && radioFavorites.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.favorites_empty_touch),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            return@Scaffold
        }

        MobileChannelGrid(modifier = Modifier.padding(padding)) {
            if (favorites.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SectionTitle(stringResource(R.string.favorites_channels))
                }
                items(favorites, key = { it.channel.id }) { summary ->
                    MobileChannelCard(
                        state = summary.toCardState(),
                        onClick = {
                            viewModel.onChannelOpened(summary.channel.id)
                            onChannelSelected(summary.channel.id)
                        },
                        onLongClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.toggleFavorite(summary.channel.id, currentlyFavorite = true)
                            scope.launch {
                                val result = snackbarHost.showSnackbar(
                                    message = removedLabel,
                                    actionLabel = undoLabel,
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    viewModel.toggleFavorite(
                                        summary.channel.id,
                                        currentlyFavorite = false,
                                    )
                                }
                            }
                        },
                    )
                }
            }

            if (radioFavorites.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SectionTitle(stringResource(R.string.favorites_radio))
                }
                items(
                    radioFavorites,
                    key = { it.uuid },
                    // Rows, not cells: without the span each station would be squeezed
                    // into one third of the width like a card.
                    span = { GridItemSpan(maxLineSpan) },
                ) { station ->
                    RadioFavoriteRow(
                        station = station,
                        onPlay = {
                            requestNotifications()
                            viewModel.playRadio(station)
                        },
                        onRemove = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.toggleRadioFavorite(station.uuid, currentlyFavorite = true)
                            scope.launch {
                                val result = snackbarHost.showSnackbar(
                                    message = removedLabel,
                                    actionLabel = undoLabel,
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    viewModel.toggleRadioFavorite(
                                        station.uuid,
                                        currentlyFavorite = false,
                                    )
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

@Composable
private fun RadioFavoriteRow(
    station: RadioStation,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
) {
    ListItem(
        // Material 3's ListItem handles no clicks on its own — same wrapper the radio
        // list uses.
        modifier = Modifier.combinedClickable(onClick = onPlay, onLongClick = onRemove),
        headlineContent = {
            Text(station.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = { Text(station.describe(), maxLines = 1) },
        leadingContent = { HealthDot(station.badge()) },
        // Cards make their purpose obvious; a row does not, so say that a tap plays.
        trailingContent = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
    )
}