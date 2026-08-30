package com.worldtv.feature.radio

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusGroup
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.worldtv.core.designsystem.theme.WorldTvColors
import com.worldtv.core.designsystem.theme.WorldTvDimens
import com.worldtv.core.model.Country

/**
 * Country filter for radio.
 *
 * Same shape as the TV browse drawer on purpose: the two modes should not need to be
 * learned separately, and `focusRestorer()` is what makes moving right into the list
 * and back left return to the country the user came from.
 */
@Composable
fun RadioCountryDrawer(
    countries: List<Country>,
    selectedCountry: String?,
    onCountrySelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .width(WorldTvDimens.DrawerWidthExpanded)
            .fillMaxHeight()
            .padding(end = 16.dp),
    ) {
        Text(
            text = "Ülkeler",
            style = MaterialTheme.typography.titleMedium,
            color = WorldTvColors.OnSurfaceMuted,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        LazyColumn(
            Modifier
                .fillMaxWidth()
                .focusRestorer()
                .focusGroup(),
        ) {
            item {
                ListItem(
                    selected = selectedCountry == null,
                    onClick = { onCountrySelected(null) },
                    headlineContent = { Text("Tümü") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            items(countries, key = { it.code }) { country ->
                ListItem(
                    selected = country.code == selectedCountry,
                    onClick = { onCountrySelected(country.code) },
                    headlineContent = {
                        Text(
                            text = listOf(country.flag, country.name)
                                .filter { it.isNotBlank() }
                                .joinToString(" "),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
