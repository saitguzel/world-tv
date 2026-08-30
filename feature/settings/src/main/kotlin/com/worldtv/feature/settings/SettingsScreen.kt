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
                text = "Ayarlar",
                style = MaterialTheme.typography.headlineLarge,
                color = WorldTvColors.OnSurface,
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }

        item {
            SwitchRow(
                title = "Yetişkin kanallarını göster",
                checked = state.preferences.showNsfw,
                onCheckedChange = viewModel::setShowNsfw,
            )
        }
        item {
            SwitchRow(
                title = "Kontrol edilmemiş yayınları göster",
                subtitle = "Kapatırsanız yalnızca doğrulanmış kanallar listelenir",
                checked = state.preferences.showUnchecked,
                onCheckedChange = viewModel::setShowUnchecked,
            )
        }
        item {
            SwitchRow(
                title = "Bölgesel kısıtlı yayınları göster",
                subtitle = "Bölgenizde çalışıyor olabilirler",
                checked = state.preferences.showGeoBlocked,
                onCheckedChange = viewModel::setShowGeoBlocked,
            )
        }
        item {
            SwitchRow(
                title = "Animasyonları azalt",
                checked = state.preferences.reduceMotion,
                onCheckedChange = viewModel::setReduceMotion,
            )
        }

        item {
            ListItem(
                selected = false,
                onClick = viewModel::resyncCatalog,
                headlineContent = { Text("Katalogu şimdi yenile") },
                supportingContent = {
                    Text(
                        if (state.isSyncing) {
                            "Yenileniyor…"
                        } else {
                            "Değişmemiş dosyalar tekrar indirilmez"
                        },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            ListItem(
                selected = false,
                onClick = viewModel::recheckEverything,
                headlineContent = { Text("Tüm yayınları yeniden kontrol et") },
                supportingContent = {
                    Text("Gizlenen yayınlar da dahil, arka planda kademeli olarak")
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            Text(
                text = "Sağlık kontrolü yoğunluğu",
                style = MaterialTheme.typography.titleMedium,
                color = WorldTvColors.OnSurfaceMuted,
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
            )
        }
        items(HealthAggressiveness.entries) { level ->
            ListItem(
                selected = state.preferences.healthAggressiveness == level,
                onClick = { viewModel.setAggressiveness(level) },
                headlineContent = { Text(level.label()) },
                supportingContent = { Text(level.description()) },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            Column(Modifier.padding(top = 32.dp)) {
                Text(
                    text = "${state.verifiedStreams} yayın doğrulandı, " +
                        "${state.deadStreams} yayın gizlendi",
                    style = MaterialTheme.typography.bodyLarge,
                    color = WorldTvColors.OnSurfaceMuted,
                )
                Text(
                    text = "WorldTV hiçbir içerik barındırmaz veya yayınlamaz. " +
                        "Tüm yayın adresleri herkese açık iptv-org ve Radio Browser " +
                        "dizinlerinden gelir.",
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

private fun HealthAggressiveness.label(): String = when (this) {
    HealthAggressiveness.LIGHT -> "Hafif"
    HealthAggressiveness.BALANCED -> "Dengeli"
    HealthAggressiveness.THOROUGH -> "Kapsamlı"
}

private fun HealthAggressiveness.description(): String = when (this) {
    HealthAggressiveness.LIGHT -> "Yalnızca hızlı kontrol, düşük veri kullanımı"
    HealthAggressiveness.BALANCED -> "Manifest ve segment kontrolü — önerilen"
    HealthAggressiveness.THOROUGH -> "Daha fazla eşzamanlı kontrol, güçlü cihazlar için"
}
