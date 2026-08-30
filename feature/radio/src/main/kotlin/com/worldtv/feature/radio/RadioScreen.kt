package com.worldtv.feature.radio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusGroup
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
import com.worldtv.core.designsystem.component.LoadingState
import com.worldtv.core.designsystem.theme.WorldTvColors
import com.worldtv.core.designsystem.theme.WorldTvDimens
import com.worldtv.core.model.HealthBadge
import com.worldtv.core.model.RadioStation
import com.worldtv.core.model.StreamState

@Composable
fun RadioScreen(
    modifier: Modifier = Modifier,
    viewModel: RadioViewModel = hiltViewModel(),
) {
    val stations = viewModel.stations.collectAsLazyPagingItems()
    val nowPlaying by viewModel.nowPlaying.collectAsStateWithLifecycle()

    if (stations.itemCount == 0) {
        LoadingState(message = "Radyo istasyonları yükleniyor…", modifier = modifier)
        return
    }

    Column(modifier.fillMaxSize().padding(WorldTvDimens.ScreenPadding)) {
        Text(
            text = nowPlaying?.let { "Çalıyor: ${it.name}" } ?: "Radyo",
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
            items(
                count = stations.itemCount,
                key = stations.itemKey { it.uuid },
            ) { index ->
                val station = stations[index] ?: return@items
                ListItem(
                    selected = station.uuid == nowPlaying?.uuid,
                    onClick = { viewModel.play(station) },
                    headlineContent = {
                        Text(station.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    supportingContent = {
                        Text(station.describe(), color = WorldTvColors.OnSurfaceMuted)
                    },
                    trailingContent = { HealthDot(station.badge()) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun RadioStation.describe(): String = buildList {
    codec?.let(::add)
    if (bitrate > 0) add("$bitrate kbps")
    if (tags.isNotEmpty()) add(tags.take(2).joinToString(", "))
}.joinToString(" · ")

/**
 * Radio Browser runs its own health checks from a different region, so a station it
 * calls broken is shown as unchecked rather than verified until our own probe agrees.
 */
private fun RadioStation.badge(): HealthBadge = when {
    health.state == StreamState.DEAD -> HealthBadge.UNAVAILABLE
    health.state == StreamState.OK -> HealthBadge.VERIFIED
    health.state == StreamState.GEO_BLOCKED -> HealthBadge.GEO_BLOCKED
    else -> HealthBadge.UNCHECKED
}
