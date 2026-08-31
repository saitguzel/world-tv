package com.worldtv.feature.radio.mobile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.worldtv.core.designsystem.component.HealthDot
import com.worldtv.core.model.RadioStation
import com.worldtv.feature.radio.R
import com.worldtv.feature.radio.badge
import com.worldtv.feature.radio.describe
import com.worldtv.feature.radio.RadioViewModel

/**
 * Radio, for touch.
 *
 * The 280dp country rail becomes a sheet, as on browse.
 *
 * The more consequential difference is that this screen has transport controls at all.
 * The TV screen deliberately has none: a remote has dedicated media keys and the
 * MediaSession notification covers the rest, so a play button would be a target nobody
 * needs to reach. Neither is true under a thumb, so a now-playing bar sits above the
 * list whenever something is playing. `RadioViewModel.togglePlayPause` already existed
 * and had no caller.
 *
 * The bar lives in this screen rather than above the navigation host, which means it
 * disappears when the user switches tabs even though playback continues. A
 * process-lifetime bar is the better answer and is what the plan calls for, but it
 * needs a view model of its own outside any screen; this is the smaller, honest step.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileRadioScreen(
    modifier: Modifier = Modifier,
    viewModel: RadioViewModel = hiltViewModel(),
) {
    val stations = viewModel.stations.collectAsLazyPagingItems()
    val countries by viewModel.availableCountries.collectAsStateWithLifecycle()
    val nowPlaying by viewModel.nowPlaying.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()

    val sheetState = rememberModalBottomSheetState()
    var sheetOpen by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val favoriteUuids = remember(favorites) { favorites.map { it.uuid }.toSet() }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.radio_title)) },
                actions = {
                    IconButton(onClick = { sheetOpen = true }) {
                        Icon(
                            Icons.Filled.List,
                            contentDescription = stringResource(R.string.radio_filter),
                        )
                    }
                },
            )
        },
        bottomBar = { nowPlaying?.let { NowPlayingBar(it, isPlaying, viewModel::togglePlayPause) } },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            items(
                count = stations.itemCount,
                key = stations.itemKey { it.uuid },
            ) { index ->
                val station = stations[index] ?: return@items
                val isFavorite = station.uuid in favoriteUuids
                StationRow(
                    station = station,
                    isFavorite = isFavorite,
                    isCurrent = station.uuid == nowPlaying?.uuid,
                    onClick = { viewModel.play(station) },
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.toggleFavorite(station.uuid, isFavorite)
                    },
                )
            }
        }

        if (sheetOpen) {
            ModalBottomSheet(onDismissRequest = { sheetOpen = false }, sheetState = sheetState) {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    item {
                        ListItem(
                            modifier = Modifier.clickable {
                                viewModel.setCountry(null); sheetOpen = false
                            },
                            headlineContent = { Text(stringResource(R.string.radio_all_countries)) },
                        )
                    }
                    items(countries.size, key = { countries[it].code }) { index ->
                        val country = countries[index]
                        ListItem(
                            modifier = Modifier.clickable {
                                viewModel.setCountry(country.code); sheetOpen = false
                            },
                            headlineContent = { Text("${country.flag} ${country.name}") },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StationRow(
    station: RadioStation,
    isFavorite: Boolean,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    ListItem(
        // Material 3's ListItem is a layout container with no click handling of its
        // own, unlike the TV one — without this wrapper the row would be inert.
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
        headlineContent = {
            Text(
                text = if (isFavorite) {
                    stringResource(R.string.radio_favorite_marker, station.name)
                } else {
                    station.name
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isCurrent) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        },
        supportingContent = { Text(station.describe(), maxLines = 1) },
        leadingContent = { HealthDot(station.badge()) },
    )
}

@Composable
private fun NowPlayingBar(station: RadioStation, isPlaying: Boolean, onToggle: () -> Unit) {
    Surface(tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth()) {
            HorizontalDivider()
            ListItem(
                headlineContent = {
                    Text(station.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                supportingContent = { Text(stringResource(R.string.radio_now_playing), maxLines = 1) },
                trailingContent = {
                    IconButton(onClick = onToggle) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = stringResource(
                                if (isPlaying) R.string.radio_pause else R.string.radio_play,
                            ),
                        )
                    }
                },
            )
        }
    }
}
