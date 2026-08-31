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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.worldtv.core.designsystem.component.HealthDot
import com.worldtv.core.designsystem.tv.component.LoadingState
import com.worldtv.core.designsystem.theme.WorldTvColors
import com.worldtv.core.designsystem.theme.WorldTvDimens
import com.worldtv.core.model.HealthBadge
import com.worldtv.core.model.RadioStation
import com.worldtv.core.model.StreamState
import androidx.compose.ui.res.stringResource
import com.worldtv.feature.radio.R

@Composable
fun RadioScreen(
    modifier: Modifier = Modifier,
    viewModel: RadioViewModel = hiltViewModel(),
) {
    val stations = viewModel.stations.collectAsLazyPagingItems()
    val nowPlaying by viewModel.nowPlaying.collectAsStateWithLifecycle()
    val countries by viewModel.availableCountries.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val selectedCountry by viewModel.country.collectAsStateWithLifecycle()

    if (stations.itemCount == 0 && countries.isEmpty()) {
        LoadingState(message = stringResource(R.string.radio_loading), modifier = modifier)
        return
    }

    Row(modifier.fillMaxSize().padding(WorldTvDimens.ScreenPadding)) {
        RadioCountryDrawer(
            countries = countries,
            selectedCountry = selectedCountry,
            onCountrySelected = viewModel::setCountry,
        )

        Column(Modifier.weight(1f)) {
        Text(
            text = nowPlaying?.let { stringResource(R.string.radio_now_playing, it.name) }
                ?: stringResource(R.string.radio_title),
            style = MaterialTheme.typography.headlineLarge,
            color = WorldTvColors.OnSurface,
            modifier = Modifier.padding(bottom = 16.dp),
        )

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
                    StationRow(
                        station = station,
                        isPlaying = station.uuid == nowPlaying?.uuid,
                        isFavorite = true,
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
                StationRow(
                    station = station,
                    isPlaying = station.uuid == nowPlaying?.uuid,
                    isFavorite = isFavorite,
                    onClick = { viewModel.play(station) },
                    onLongClick = { viewModel.toggleFavorite(station.uuid, isFavorite) },
                )
            }
        }
        }
    }
}

@Composable
private fun StationRow(
    station: RadioStation,
    isPlaying: Boolean,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    ListItem(
        selected = isPlaying,
        onClick = onClick,
        onLongClick = onLongClick,
        headlineContent = {
            Text(
                text = if (isFavorite) {
                    stringResource(R.string.radio_favorite_marker, station.name)
                } else {
                    station.name
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(station.describe(), color = WorldTvColors.OnSurfaceMuted)
        },
        trailingContent = { HealthDot(station.badge()) },
        modifier = Modifier.fillMaxWidth(),
    )
}

