package com.worldtv.feature.radio

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.worldtv.core.designsystem.component.stationPlayback
import com.worldtv.core.designsystem.component.toRowState
import com.worldtv.core.designsystem.tv.component.TvStationRow
import com.worldtv.core.designsystem.tv.component.LoadingState
import com.worldtv.core.designsystem.theme.WorldTvColors
import com.worldtv.core.designsystem.theme.WorldTvDimens
import androidx.compose.ui.res.stringResource
import com.worldtv.feature.radio.R
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.tv.material3.Icon
import com.worldtv.core.designsystem.component.ShuffleIcon

@Composable
fun RadioScreen(
    modifier: Modifier = Modifier,
    viewModel: RadioViewModel = hiltViewModel(),
) {
    val stations = viewModel.stations.collectAsLazyPagingItems()
    val nowPlaying by viewModel.nowPlaying.collectAsStateWithLifecycle()
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    val countries by viewModel.availableCountries.collectAsStateWithLifecycle()
    val categories by viewModel.availableCategories.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val selectedCountry by viewModel.country.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.category.collectAsStateWithLifecycle()

    if (stations.itemCount == 0 && countries.isEmpty()) {
        LoadingState(message = stringResource(R.string.radio_loading), modifier = modifier)
        return
    }

    Row(modifier.fillMaxSize().padding(WorldTvDimens.ScreenPadding)) {
        RadioFilterDrawer(
            countries = countries,
            categories = categories,
            selectedCountry = selectedCountry,
            selectedCategory = selectedCategory,
            onCountrySelected = viewModel::setCountry,
            onCategorySelected = viewModel::setCategory,
            onRandom = viewModel::playRandom,
        )

        Column(Modifier.weight(1f)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Text(
                // "Now playing" only while it actually is: the heading used to keep
                // announcing a station that had been paused or had died.
                text = nowPlaying
                    ?.takeIf { playback.playing || playback.buffering }
                    ?.let { stringResource(R.string.radio_now_playing, it.name) }
                    ?: stringResource(R.string.radio_title),
                style = MaterialTheme.typography.headlineLarge,
                color = WorldTvColors.OnSurface,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = viewModel::playRandom) {
                Icon(ShuffleIcon, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.radio_random))
            }
        }

        LazyColumn(
            Modifier
                .fillMaxWidth()
                .focusRestorer()
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Favourites first, as their own labelled block: a radio list is
            // thousands of rows deep and scrolling to a favourite is not viable.
            if (favorites.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.radio_favorites),
                        style = MaterialTheme.typography.titleMedium,
                        color = WorldTvColors.OnSurfaceMuted,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                items(favorites, key = { "fav-" + it.uuid }) { station ->
                    TvStationRow(
                        state = station.toRowState(
                            isFavorite = true,
                            playback = stationPlayback(
                                isCurrent = station.uuid == nowPlaying?.uuid,
                                isPlaying = playback.playing,
                                isBuffering = playback.buffering,
                            ),
                        ),
                        onClick = { viewModel.play(station) },
                        onLongClick = {
                            viewModel.toggleFavorite(station.uuid, currentlyFavorite = true)
                        },
                    )
                }
                item {
                    Text(
                        text = stringResource(R.string.radio_all_stations),
                        style = MaterialTheme.typography.titleMedium,
                        color = WorldTvColors.OnSurfaceMuted,
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                    )
                }
            }

            items(
                count = stations.itemCount,
                key = stations.itemKey { it.uuid },
            ) { index ->
                val station = stations[index] ?: return@items
                val isFavorite = favorites.any { it.uuid == station.uuid }
                TvStationRow(
                    state = station.toRowState(
                        isFavorite = isFavorite,
                        playback = stationPlayback(
                            isCurrent = station.uuid == nowPlaying?.uuid,
                            isPlaying = playback.playing,
                            isBuffering = playback.buffering,
                        ),
                    ),
                    onClick = { viewModel.play(station) },
                    onLongClick = { viewModel.toggleFavorite(station.uuid, isFavorite) },
                )
            }
        }
        }
    }
}


