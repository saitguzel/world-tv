package com.worldtv.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusGroup
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import com.worldtv.core.designsystem.theme.WorldTvColors
import com.worldtv.core.designsystem.theme.WorldTvDimens
import com.worldtv.data.repository.HealthAggressiveness
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
import com.worldtv.feature.settings.R

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LazyColumn(
        modifier
            .fillMaxSize()
            .padding(WorldTvDimens.ScreenPadding)
            .focusRestorer()
            .focusGroup(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineLarge,
                color = WorldTvColors.OnSurface,
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }

        item {
            SwitchRow(
                title = stringResource(R.string.settings_show_nsfw),
                checked = state.preferences.showNsfw,
                onCheckedChange = viewModel::setShowNsfw,
            )
        }
        item {
            SwitchRow(
                title = stringResource(R.string.settings_show_unchecked),
                subtitle = stringResource(R.string.settings_show_unchecked_hint),
                checked = state.preferences.showUnchecked,
                onCheckedChange = viewModel::setShowUnchecked,
            )
        }
        item {
            SwitchRow(
                title = stringResource(R.string.settings_show_geo_blocked),
                subtitle = stringResource(R.string.settings_show_geo_blocked_hint),
                checked = state.preferences.showGeoBlocked,
                onCheckedChange = viewModel::setShowGeoBlocked,
            )
        }
        item {
            SwitchRow(
                title = stringResource(R.string.settings_preview),
                subtitle = stringResource(R.string.settings_preview_hint),
                checked = state.preferences.previewOnFocus,
                onCheckedChange = viewModel::setPreviewOnFocus,
            )
        }
        item {
            SwitchRow(
                title = stringResource(R.string.settings_reduce_motion),
                checked = state.preferences.reduceMotion,
                onCheckedChange = viewModel::setReduceMotion,
            )
        }

        item {
            ListItem(
                selected = false,
                onClick = viewModel::resyncCatalog,
                headlineContent = { Text(stringResource(R.string.settings_resync)) },
                supportingContent = {
                    Text(
                        stringResource(
                            if (state.isSyncing) {
                                R.string.settings_resyncing
                            } else {
                                R.string.settings_resync_hint
                            },
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            ListItem(
                selected = false,
                onClick = viewModel::recheckEverything,
                headlineContent = { Text(stringResource(R.string.settings_recheck)) },
                supportingContent = {
                    Text(stringResource(R.string.settings_recheck_hint))
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            Text(
                text = stringResource(R.string.settings_health_aggressiveness),
                style = MaterialTheme.typography.titleMedium,
                color = WorldTvColors.OnSurfaceMuted,
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
            )
        }
        items(HealthAggressiveness.entries) { level ->
            ListItem(
                selected = state.preferences.healthAggressiveness == level,
                onClick = { viewModel.setAggressiveness(level) },
                headlineContent = { Text(stringResource(level.labelRes())) },
                supportingContent = { Text(stringResource(level.descriptionRes())) },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            Column(Modifier.padding(top = 32.dp)) {
                Text(
                    text = stringResource(
                        R.string.settings_health_summary,
                        state.verifiedStreams,
                        state.deadStreams,
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = WorldTvColors.OnSurfaceMuted,
                )
                Text(
                    text = stringResource(R.string.settings_disclaimer),
                    style = MaterialTheme.typography.labelLarge,
                    color = WorldTvColors.OnSurfaceMuted,
                    modifier = Modifier.padding(top = 12.dp),
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
        selected = false,
        onClick = { onCheckedChange(!checked) },
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@StringRes
private fun HealthAggressiveness.labelRes(): Int = when (this) {
    HealthAggressiveness.LIGHT -> R.string.settings_health_light
    HealthAggressiveness.BALANCED -> R.string.settings_health_balanced
    HealthAggressiveness.THOROUGH -> R.string.settings_health_thorough
}

@StringRes
private fun HealthAggressiveness.descriptionRes(): Int = when (this) {
    HealthAggressiveness.LIGHT -> R.string.settings_health_light_hint
    HealthAggressiveness.BALANCED -> R.string.settings_health_balanced_hint
    HealthAggressiveness.THOROUGH -> R.string.settings_health_thorough_hint
}
