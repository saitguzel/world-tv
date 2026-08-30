package com.worldtv.feature.catalog

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
 * The persistent country list.
 *
 * `focusRestorer()` here as well as on the grid: moving right into the grid and back
 * left must land on the country the user came from, not the top of the list.
 */
@Composable
fun CountryDrawer(
    countries: List<Country>,
    selectedCountry: String?,
    onCountrySelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .width(WorldTvDimens.DrawerWidthExpanded)
            .fillMaxHeight()
            .padding(
                start = WorldTvDimens.ScreenPadding,
                top = WorldTvDimens.ScreenPadding,
                bottom = WorldTvDimens.ScreenPadding,
                end = 8.dp,
            ),
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
            items(countries, key = { it.code }) { country ->
                ListItem(
                    selected = country.code == selectedCountry,
                    onClick = { onCountrySelected(country.code) },
                    headlineContent = {
                        Text(
                            text = "${country.flag} ${country.name}",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    trailingContent = {
                        Text(
                            text = country.channelCount.toString(),
                            color = WorldTvColors.OnSurfaceMuted,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
