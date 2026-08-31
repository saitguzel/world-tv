package com.worldtv.feature.settings.mobile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.worldtv.data.repository.HealthAggressiveness
import com.worldtv.feature.settings.R
import com.worldtv.feature.settings.SettingsViewModel

/**
 * Settings, for touch.
 *
 * Shares [SettingsViewModel] unchanged. Two differences from the TV screen are worth
 * naming.
 *
 * Material 3's `ListItem` takes no `onClick` — unlike the TV one, it is a pure layout
 * container — so every row here wraps it in `clickable` or `selectable`. That is not a
 * detail: without it the rows would render correctly and do nothing, since the trailing
 * `Switch` is the only part of a TV list item that responds to a tap at all.
 *
 * And "preview on focus" is not offered. There is no focus on a phone, so the setting
 * would control nothing; the phone browse screen never asks for a preview.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val prefs = state.preferences

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                SwitchRow(
                    title = stringResource(R.string.settings_show_nsfw),
                    checked = prefs.showNsfw,
                    onCheckedChange = viewModel::setShowNsfw,
                )
            }
            item {
                SwitchRow(
                    title = stringResource(R.string.settings_show_unchecked),
                    subtitle = stringResource(R.string.settings_show_unchecked_hint),
                    checked = prefs.showUnchecked,
                    onCheckedChange = viewModel::setShowUnchecked,
                )
            }
            item {
                SwitchRow(
                    title = stringResource(R.string.settings_show_geo_blocked),
                    subtitle = stringResource(R.string.settings_show_geo_blocked_hint),
                    checked = prefs.showGeoBlocked,
                    onCheckedChange = viewModel::setShowGeoBlocked,
                )
            }
            item {
                SwitchRow(
                    title = stringResource(R.string.settings_reduce_motion),
                    checked = prefs.reduceMotion,
                    onCheckedChange = viewModel::setReduceMotion,
                )
            }

            item { HorizontalDivider() }

            item {
                Text(
                    text = stringResource(R.string.settings_health_aggressiveness),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            item {
                Column(Modifier.selectableGroup()) {
                    AggressivenessRow(
                        HealthAggressiveness.LIGHT, prefs.healthAggressiveness,
                        R.string.settings_health_light, R.string.settings_health_light_hint,
                        viewModel::setAggressiveness,
                    )
                    AggressivenessRow(
                        HealthAggressiveness.BALANCED, prefs.healthAggressiveness,
                        R.string.settings_health_balanced, R.string.settings_health_balanced_hint,
                        viewModel::setAggressiveness,
                    )
                    AggressivenessRow(
                        HealthAggressiveness.THOROUGH, prefs.healthAggressiveness,
                        R.string.settings_health_thorough, R.string.settings_health_thorough_hint,
                        viewModel::setAggressiveness,
                    )
                }
            }

            item { HorizontalDivider() }

            item {
                ActionRow(
                    title = stringResource(
                        if (state.isSyncing) R.string.settings_resyncing else R.string.settings_resync,
                    ),
                    subtitle = stringResource(R.string.settings_resync_hint),
                    enabled = !state.isSyncing,
                    onClick = viewModel::resyncCatalog,
                )
            }
            item {
                ActionRow(
                    title = stringResource(R.string.settings_recheck),
                    subtitle = stringResource(R.string.settings_recheck_hint),
                    onClick = viewModel::recheckEverything,
                )
            }

            item { HorizontalDivider() }

            item {
                Text(
                    text = stringResource(
                        R.string.settings_health_summary,
                        state.verifiedStreams,
                        state.deadStreams,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            item {
                Text(
                    text = stringResource(R.string.settings_disclaimer),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
) {
    ListItem(
        // Material 3's ListItem has no onClick of its own; without this the row would
        // be inert and only the switch itself would respond.
        modifier = Modifier.clickable { onCheckedChange(!checked) },
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
    )
}

@Composable
private fun ActionRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    ListItem(
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
    )
}

@Composable
private fun AggressivenessRow(
    value: HealthAggressiveness,
    selected: HealthAggressiveness,
    title: Int,
    hint: Int,
    onSelect: (HealthAggressiveness) -> Unit,
) {
    val isSelected = value == selected
    ListItem(
        // selectable with an explicit role, rather than clickable, so TalkBack
        // announces these as a radio group instead of five unrelated buttons.
        modifier = Modifier.selectable(
            selected = isSelected,
            role = Role.RadioButton,
            onClick = { onSelect(value) },
        ),
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Text(stringResource(hint)) },
        leadingContent = { RadioButton(selected = isSelected, onClick = null) },
    )
}
