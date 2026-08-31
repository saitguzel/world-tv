package com.worldtv.feature.catalog

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.worldtv.core.designsystem.component.ChannelCard
import com.worldtv.core.designsystem.component.TvChannelGrid
import com.worldtv.core.designsystem.component.toCardState
import com.worldtv.core.designsystem.component.TvShelf
import com.worldtv.core.model.ChannelSummary
import com.worldtv.core.designsystem.theme.WorldTvColors
import com.worldtv.core.designsystem.theme.WorldTvDimens
import androidx.compose.ui.res.stringResource
import com.worldtv.feature.catalog.R

/**
 * Search.
 *
 * Ordered by how painful each input method is on a remote: voice first, incremental
 * filtering second, the on-screen grid keyboard as the last resort. Typing a channel
 * name one D-pad press per letter is the slowest thing a TV app can ask for.
 */
@Composable
fun SearchScreen(
    onChannelSelected: (String) -> Unit,
    initialQuery: String? = null,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    // Voice search delivers a whole phrase; typing it out again would defeat the point.
    LaunchedEffect(initialQuery) {
        if (!initialQuery.isNullOrBlank()) viewModel.setQuery(initialQuery)
    }

    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val recentSearches by viewModel.recentSearches.collectAsStateWithLifecycle()
    val popularChannels by viewModel.popularChannels.collectAsStateWithLifecycle()

    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.let(viewModel::setQuery)
        }
    }

    Row(modifier.fillMaxSize().padding(WorldTvDimens.ScreenPadding)) {
        Column(
            Modifier.weight(0.4f),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = query.ifBlank { stringResource(R.string.search_placeholder) },
                style = MaterialTheme.typography.headlineMedium,
                color = if (query.isBlank()) {
                    WorldTvColors.OnSurfaceMuted
                } else {
                    WorldTvColors.OnSurface
                },
            )

            Button(
                onClick = {
                    voiceLauncher.launch(
                        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(
                                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                            )
                        },
                    )
                },
            ) {
                Text(stringResource(R.string.search_voice))
            }

            GridKeyboard(
                onCharacter = viewModel::append,
                onBackspace = viewModel::backspace,
                onClear = viewModel::clear,
            )
        }

        Column(Modifier.weight(0.6f)) {
            // Before a single letter is typed, offer what the user is most likely to
            // want: what they searched for before, and what they actually watch. Most
            // searches on a TV end in a click here rather than in typing.
            if (query.isBlank()) {
                if (recentSearches.isNotEmpty()) {
                    SectionHeading(stringResource(R.string.search_recent))
                    RecentSearchRow(
                        searches = recentSearches,
                        onSelect = viewModel::setQuery,
                        onClear = viewModel::clearRecentSearches,
                    )
                }
                if (popularChannels.isNotEmpty()) {
                    SectionHeading(stringResource(R.string.search_popular))
                    ResultGrid(popularChannels, viewModel, onChannelSelected)
                }
                if (recentSearches.isEmpty() && popularChannels.isEmpty()) {
                    Text(
                        text = stringResource(R.string.search_start),
                        style = MaterialTheme.typography.titleLarge,
                        color = WorldTvColors.OnSurfaceMuted,
                        modifier = Modifier.padding(24.dp),
                    )
                }
                return@Column
            }

            if (results.isEmpty()) {
                Text(
                    text = stringResource(R.string.search_no_results),
                    style = MaterialTheme.typography.titleLarge,
                    color = WorldTvColors.OnSurfaceMuted,
                    modifier = Modifier.padding(24.dp),
                )
                return@Column
            }
            ResultGrid(results, viewModel, onChannelSelected)
        }
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = WorldTvColors.OnSurfaceMuted,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun ResultGrid(
    channels: List<ChannelSummary>,
    viewModel: SearchViewModel,
    onChannelSelected: (String) -> Unit,
) {
    TvChannelGrid(columns = 3, modifier = Modifier.fillMaxWidth()) {
        items(channels, key = { it.channel.id }) { summary ->
            ChannelCard(
                state = summary.toCardState(),
                onClick = {
                    viewModel.onChannelOpened(summary.channel.id)
                    onChannelSelected(summary.channel.id)
                },
            )
        }
    }
}

/**
 * Previous queries as focusable chips.
 *
 * A row rather than a column: it sits above the results grid and must not push it off
 * screen, and eight entries fit across a 1080p panel comfortably.
 */
@Composable
private fun RecentSearchRow(
    searches: List<String>,
    onSelect: (String) -> Unit,
    onClear: () -> Unit,
) {
    TvShelf(Modifier.height(64.dp)) {
        items(searches, key = { it }) { search ->
            Button(onClick = { onSelect(search) }) { Text(search) }
        }
        item {
            Button(onClick = onClear) { Text(stringResource(R.string.keyboard_clear)) }
        }
    }
}
