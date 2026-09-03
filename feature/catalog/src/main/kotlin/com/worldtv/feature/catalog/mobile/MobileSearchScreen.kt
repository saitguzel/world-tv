package com.worldtv.feature.catalog.mobile

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.worldtv.core.designsystem.component.HealthDot
import com.worldtv.core.designsystem.component.toCardState
import com.worldtv.core.designsystem.mobile.component.MobileChannelCard
import com.worldtv.core.designsystem.mobile.component.MobileChannelGrid
import com.worldtv.feature.catalog.R
import com.worldtv.feature.catalog.SearchKind
import com.worldtv.feature.catalog.SearchViewModel
import com.worldtv.core.model.badge
import com.worldtv.core.model.describe
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Place
import com.worldtv.core.designsystem.component.RadioIcon
import com.worldtv.core.designsystem.component.TvIcon
import com.worldtv.core.designsystem.mobile.component.SelectionCheck

/**
 * Search, for touch.
 *
 * The TV screen draws its own 6x7 alphabetical keyboard, because the system IME's focus
 * behaviour on a television is inconsistent between launchers. None of that applies
 * here, and the grid keyboard is roughly 448dp wide — wider than a portrait phone — so
 * this screen does not render it at all. A real TextField replaces it, and the Turkish
 * letters the TV keyboard has to spell out (ÇĞİÖŞÜ) come free with the IME.
 *
 * [SearchViewModel] needs no change: `setQuery` already exists for the Assistant path,
 * and a text field is just another caller. `append`, `backspace` and `clear` stay for
 * the TV keyboard.
 *
 * Voice search is the TV launcher reused verbatim — it is one of the few parts of that
 * screen that was already a phone idiom.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileSearchScreen(
    onChannelSelected: (String) -> Unit,
    onRadioSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    initialQuery: String? = null,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val kind by viewModel.kind.collectAsStateWithLifecycle()
    val country by viewModel.country.collectAsStateWithLifecycle()
    val countries by viewModel.countries.collectAsStateWithLifecycle()
    val channelResults by viewModel.channelResults.collectAsStateWithLifecycle()
    val radioResults by viewModel.radioResults.collectAsStateWithLifecycle()
    val recentSearches by viewModel.recentSearches.collectAsStateWithLifecycle()
    val popularChannels by viewModel.popularChannels.collectAsStateWithLifecycle()

    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    val sheetState = rememberModalBottomSheetState()
    var countrySheetOpen by remember { mutableStateOf(false) }

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

    LaunchedEffect(initialQuery) {
        if (!initialQuery.isNullOrBlank()) viewModel.setQuery(initialQuery)
    }

    // The phone analogue of the TV keyboard grabbing its first key: arriving on a
    // search screen with no keyboard costs a tap before anything can be typed.
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    Scaffold(modifier = modifier) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .focusRequester(focusRequester),
                placeholder = { Text(stringResource(R.string.search_placeholder)) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(onClick = viewModel::clear) {
                            Icon(Icons.Filled.Clear, contentDescription = null)
                        }
                    } else {
                        IconButton(
                            onClick = {
                                voiceLauncher.launch(
                                    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(
                                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                                    ),
                                )
                            },
                        ) {
                            Icon(
                                Icons.Filled.Search,
                                contentDescription = stringResource(R.string.search_voice),
                            )
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(
                    // Channel names are proper nouns, and autocorrect fights them.
                    capitalization = KeyboardCapitalization.Words,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Search,
                ),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            )

            // Kind and country, as chips: the same two axes search supports on TV.
            Row(
                Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SearchKind.entries.forEach { entry ->
                    FilterChip(
                        selected = kind == entry,
                        onClick = { viewModel.setKind(entry) },
                        leadingIcon = {
                            Icon(
                                imageVector = when (entry) {
                                    SearchKind.TV -> TvIcon
                                    SearchKind.RADIO -> RadioIcon
                                },
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        label = {
                            Text(
                                stringResource(
                                    when (entry) {
                                        SearchKind.TV -> R.string.search_kind_tv
                                        SearchKind.RADIO -> R.string.search_kind_radio
                                    },
                                ),
                            )
                        },
                    )
                }
                AssistChip(
                    onClick = { countrySheetOpen = true },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Place,
                            contentDescription = stringResource(R.string.search_country_filter),
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    label = {
                        Text(
                            countries.firstOrNull { it.code == country }
                                ?.let { "${it.flag} ${it.name}" }
                                ?: stringResource(R.string.search_all_countries),
                        )
                    },
                )
            }

            if (query.isBlank() && recentSearches.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(recentSearches, key = { it }) { recent ->
                        AssistChip(
                            onClick = { viewModel.setQuery(recent) },
                            label = { Text(recent) },
                        )
                    }
                }
            }

            when (kind) {
                SearchKind.TV -> {
                    val shown = if (query.isBlank()) popularChannels else channelResults
                    if (shown.isEmpty()) {
                        EmptyHint(query, modifier = Modifier.weight(1f))
                        return@Column
                    }
                    MobileChannelGrid(modifier = Modifier.imePadding()) {
                        items(shown, key = { it.channel.id }) { summary ->
                            MobileChannelCard(
                                state = summary.toCardState(),
                                onClick = {
                                    viewModel.onChannelOpened(summary.channel.id)
                                    onChannelSelected(summary.channel.id)
                                },
                            )
                        }
                    }
                }

                SearchKind.RADIO -> {
                    if (radioResults.isEmpty()) {
                        EmptyHint(query, modifier = Modifier.weight(1f))
                        return@Column
                    }
                    LazyColumn(Modifier.imePadding()) {
                        items(radioResults, key = { it.uuid }) { station ->
                            ListItem(
                                modifier = Modifier.clickable {
                                    viewModel.recordRadioOpened()
                                    onRadioSelected(station.uuid)
                                },
                                headlineContent = {
                                    Text(station.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                                supportingContent = { Text(station.describe(), maxLines = 1) },
                                leadingContent = { HealthDot(station.badge()) },
                            )
                        }
                    }
                }
            }
        }

        if (countrySheetOpen) {
            ModalBottomSheet(onDismissRequest = { countrySheetOpen = false }, sheetState = sheetState) {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    item {
                        ListItem(
                            modifier = Modifier.clickable {
                                viewModel.setCountry(null); countrySheetOpen = false
                            },
                            headlineContent = { Text(stringResource(R.string.search_all_countries)) },
                            leadingContent = { SelectionCheck(country == null) },
                        )
                    }
                    items(countries, key = { it.code }) { entry ->
                        ListItem(
                            modifier = Modifier.clickable {
                                viewModel.setCountry(entry.code); countrySheetOpen = false
                            },
                            headlineContent = { Text("${entry.flag} ${entry.name}") },
                            leadingContent = { SelectionCheck(entry.code == country) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyHint(query: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(
                if (query.isBlank()) R.string.search_start else R.string.search_no_results,
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}