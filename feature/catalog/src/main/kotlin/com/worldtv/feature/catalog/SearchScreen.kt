package com.worldtv.feature.catalog

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.FilterChip
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.worldtv.core.designsystem.component.HealthDot
import com.worldtv.core.designsystem.tv.component.ChannelCard
import com.worldtv.core.designsystem.tv.component.TvChannelGrid
import com.worldtv.core.designsystem.component.toCardState
import com.worldtv.core.designsystem.tv.component.TvShelf
import com.worldtv.core.model.ChannelSummary
import com.worldtv.core.model.Country
import com.worldtv.core.model.RadioStation
import com.worldtv.core.designsystem.theme.WorldTvColors
import com.worldtv.core.designsystem.theme.WorldTvDimens
import androidx.compose.ui.res.stringResource
import com.worldtv.feature.catalog.R
import com.worldtv.core.model.badge
import com.worldtv.core.model.describe
import androidx.compose.foundation.layout.size
import androidx.tv.material3.Icon
import com.worldtv.core.designsystem.component.RadioIcon
import com.worldtv.core.designsystem.component.TvIcon

/**
 * Search.
 *
 * Ordered by how painful each input method is on a remote: voice first, incremental
 * filtering second, the on-screen grid keyboard as the last resort. Typing a channel
 * name one D-pad press per letter is the slowest thing a TV app can ask for.
 *
 * Two toggles sit above the results. The TV/radio switch chooses which catalog is being
 * searched — there is no point typing the same query into two lists and staring at both
 * — and the country chips narrow to the device's own country by default, matching how
 * browse behaves.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SearchScreen(
    onChannelSelected: (String) -> Unit,
    onRadioSelected: (String) -> Unit,
    initialQuery: String? = null,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    // Voice search delivers a whole phrase; typing it out again would defeat the point.
    LaunchedEffect(initialQuery) {
        if (!initialQuery.isNullOrBlank()) viewModel.setQuery(initialQuery)
    }

    val query by viewModel.query.collectAsStateWithLifecycle()
    val kind by viewModel.kind.collectAsStateWithLifecycle()
    val country by viewModel.country.collectAsStateWithLifecycle()
    val countries by viewModel.countries.collectAsStateWithLifecycle()
    val channelResults by viewModel.channelResults.collectAsStateWithLifecycle()
    val radioResults by viewModel.radioResults.collectAsStateWithLifecycle()
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

            Row(
                Modifier.focusGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = kind == SearchKind.TV,
                    onClick = { viewModel.setKind(SearchKind.TV) },
                    leadingIcon = { Icon(TvIcon, contentDescription = null, modifier = Modifier.size(20.dp)) },
                    content = { Text(stringResource(R.string.search_kind_tv)) },
                )
                FilterChip(
                    selected = kind == SearchKind.RADIO,
                    onClick = { viewModel.setKind(SearchKind.RADIO) },
                    leadingIcon = { Icon(RadioIcon, contentDescription = null, modifier = Modifier.size(20.dp)) },
                    content = { Text(stringResource(R.string.search_kind_radio)) },
                )
            }

            CountryChips(
                countries = countries,
                selectedCountry = country,
                onSelect = viewModel::setCountry,
            )

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
                when (kind) {
                    SearchKind.TV -> if (popularChannels.isEmpty()) {
                        Text(
                            text = stringResource(R.string.search_start),
                            style = MaterialTheme.typography.titleLarge,
                            color = WorldTvColors.OnSurfaceMuted,
                            modifier = Modifier.padding(24.dp),
                        )
                    } else {
                        SectionHeading(stringResource(R.string.search_popular))
                        ResultGrid(popularChannels, viewModel, onChannelSelected)
                    }

                    SearchKind.RADIO -> if (radioResults.isEmpty()) {
                        Text(
                            text = stringResource(R.string.search_start),
                            style = MaterialTheme.typography.titleLarge,
                            color = WorldTvColors.OnSurfaceMuted,
                            modifier = Modifier.padding(24.dp),
                        )
                    } else {
                        SectionHeading(stringResource(R.string.search_popular))
                        RadioResultList(radioResults, viewModel, onRadioSelected)
                    }
                }
                return@Column
            }

            when (kind) {
                SearchKind.TV -> {
                    if (channelResults.isEmpty()) {
                        Text(
                            text = stringResource(R.string.search_no_results),
                            style = MaterialTheme.typography.titleLarge,
                            color = WorldTvColors.OnSurfaceMuted,
                            modifier = Modifier.padding(24.dp),
                        )
                        return@Column
                    }
                    ResultGrid(channelResults, viewModel, onChannelSelected)
                }

                SearchKind.RADIO -> {
                    if (radioResults.isEmpty()) {
                        Text(
                            text = stringResource(R.string.search_no_results),
                            style = MaterialTheme.typography.titleLarge,
                            color = WorldTvColors.OnSurfaceMuted,
                            modifier = Modifier.padding(24.dp),
                        )
                        return@Column
                    }
                    RadioResultList(radioResults, viewModel, onRadioSelected)
                }
            }
        }
    }
}

/**
 * The country filter as a chip shelf.
 *
 * Top countries only — the full two-hundred-entry list lives in browse, and a search
 * that needed that much scrolling would be faster typed. "Tümü" clears the filter.
 */
@Composable
private fun CountryChips(
    countries: List<Country>,
    selectedCountry: String?,
    onSelect: (String?) -> Unit,
) {
    @OptIn(ExperimentalTvMaterial3Api::class)
    TvShelf(Modifier.height(64.dp)) {
        item {
            FilterChip(
                selected = selectedCountry == null,
                onClick = { onSelect(null) },
                content = { Text(stringResource(R.string.search_all_countries)) },
            )
        }
        items(countries.take(COUNTRY_CHIP_LIMIT), key = { it.code }) { country ->
            FilterChip(
                selected = country.code == selectedCountry,
                onClick = { onSelect(country.code) },
                content = { Text("${country.flag} ${country.name}") },
            )
        }
    }
}

/** Radio hits are rows, not cards — there is no logo grid to hang a card on. */
@Composable
private fun RadioResultList(
    stations: List<RadioStation>,
    viewModel: SearchViewModel,
    onRadioSelected: (String) -> Unit,
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(stations.size, key = { stations[it].uuid }) { index ->
            val station = stations[index]
            ListItem(
                selected = false,
                onClick = {
                    viewModel.recordRadioOpened()
                    onRadioSelected(station.uuid)
                },
                headlineContent = { Text(station.name) },
                supportingContent = { Text(station.describe(), color = WorldTvColors.OnSurfaceMuted) },
                trailingContent = { HealthDot(station.badge()) },
                modifier = Modifier.fillMaxWidth(),
            )
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

private const val COUNTRY_CHIP_LIMIT = 12