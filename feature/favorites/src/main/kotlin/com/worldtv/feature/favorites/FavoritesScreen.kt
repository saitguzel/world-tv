package com.worldtv.feature.favorites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.worldtv.core.designsystem.tv.component.ChannelCard
import com.worldtv.core.designsystem.tv.component.EmptyState
import com.worldtv.core.designsystem.tv.component.TvChannelGrid
import com.worldtv.core.designsystem.theme.WorldTvColors
import com.worldtv.core.designsystem.theme.WorldTvDimens
import com.worldtv.core.designsystem.component.toCardState
import androidx.compose.ui.res.stringResource
import com.worldtv.feature.favorites.R

@Composable
fun FavoritesScreen(
    onChannelSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FavoritesViewModel = hiltViewModel(),
) {
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()

    if (favorites.isEmpty()) {
        EmptyState(
            message = stringResource(R.string.favorites_empty),
            actionLabel = stringResource(R.string.favorites_check),
            onAction = viewModel::refreshFavoriteHealth,
            modifier = modifier,
        )
        return
    }

    Column(modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.favorites_title),
            style = MaterialTheme.typography.headlineLarge,
            color = WorldTvColors.OnSurface,
            modifier = Modifier.padding(
                start = WorldTvDimens.ScreenPadding,
                top = WorldTvDimens.ScreenPadding,
                bottom = 8.dp,
            ),
        )
        TvChannelGrid(columns = 5) {
            items(favorites, key = { it.channel.id }) { summary ->
                ChannelCard(
                    state = summary.toCardState(),
                    onClick = {
                        viewModel.onChannelOpened(summary.channel.id)
                        onChannelSelected(summary.channel.id)
                    },
                    onLongClick = {
                        viewModel.toggleFavorite(summary.channel.id, currentlyFavorite = true)
                    },
                )
            }
        }
    }
}

