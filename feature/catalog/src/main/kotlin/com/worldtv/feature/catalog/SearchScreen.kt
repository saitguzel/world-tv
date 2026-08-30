package com.worldtv.feature.catalog

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
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
import com.worldtv.core.designsystem.theme.WorldTvColors
import com.worldtv.core.designsystem.theme.WorldTvDimens

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
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()

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
                text = query.ifBlank { "Kanal ara" },
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
                Text("Sesle ara")
            }

            GridKeyboard(
                onCharacter = viewModel::append,
                onBackspace = viewModel::backspace,
                onClear = viewModel::clear,
            )
        }

        Column(Modifier.weight(0.6f)) {
            if (query.isNotBlank() && results.isEmpty()) {
                Text(
                    text = "Sonuç yok",
                    style = MaterialTheme.typography.titleLarge,
                    color = WorldTvColors.OnSurfaceMuted,
                    modifier = Modifier.padding(24.dp),
                )
            }
            TvChannelGrid(columns = 3, modifier = Modifier.fillMaxWidth()) {
                items(results, key = { it.channel.id }) { summary ->
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
    }
}
