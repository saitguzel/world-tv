package com.worldtv.feature.youtube

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
import com.worldtv.core.designsystem.component.ChannelCard
import com.worldtv.core.designsystem.component.ChannelCardState
import com.worldtv.core.designsystem.component.EmptyState
import com.worldtv.core.designsystem.component.LoadingState
import com.worldtv.core.designsystem.component.TvChannelGrid
import com.worldtv.core.designsystem.theme.WorldTvColors
import com.worldtv.core.designsystem.theme.WorldTvDimens
import com.worldtv.core.model.HealthBadge
import com.worldtv.core.model.YouTubeLive

@Composable
fun YouTubeBrowseScreen(
    onVideoSelected: (String) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: YouTubeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    when {
        state.isLoading -> LoadingState("Canlı yayınlar yükleniyor…", modifier)

        // No key is a configuration state, not a failure — say what to do about it.
        !state.hasApiKey -> EmptyState(
            message = "YouTube modu için kendi YouTube Data API anahtarınız gerekiyor.\n" +
                "Anahtar ücretsizdir ve kotanız yalnızca size ait olur.",
            actionLabel = "Ayarları aç",
            onAction = onOpenSettings,
            modifier = modifier,
        )

        state.live.isEmpty() -> EmptyState(
            message = "Kürate edilmiş kanallarda şu an canlı yayın yok.",
            actionLabel = "Ayarları aç",
            onAction = onOpenSettings,
            modifier = modifier,
        )

        else -> Column(modifier.fillMaxSize()) {
            Text(
                text = "YouTube canlı",
                style = MaterialTheme.typography.headlineLarge,
                color = WorldTvColors.OnSurface,
                modifier = Modifier.padding(
                    start = WorldTvDimens.ScreenPadding,
                    top = WorldTvDimens.ScreenPadding,
                    bottom = 8.dp,
                ),
            )
            TvChannelGrid(columns = 4) {
                items(state.live, key = { it.videoId }) { live ->
                    ChannelCard(
                        state = live.toCardState(),
                        onClick = { onVideoSelected(live.videoId) },
                    )
                }
            }
        }
    }
}

private fun YouTubeLive.toCardState(): ChannelCardState = ChannelCardState(
    id = videoId,
    name = channelTitle,
    logoUrl = thumbnailUrl,
    // These come straight from YouTube's own live index, so they are as verified as
    // anything in the app gets — the health engine never probes them.
    badge = HealthBadge.VERIFIED,
    isFavorite = false,
    subtitle = title,
)
