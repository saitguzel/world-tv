package com.worldtv.feature.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.worldtv.core.designsystem.theme.WorldTvColors
import com.worldtv.core.designsystem.theme.WorldTvDimens
import com.worldtv.core.model.Category
import com.worldtv.core.model.Country
import androidx.compose.ui.res.stringResource
import com.worldtv.feature.catalog.R

/** Which facet the drawer is currently listing. */
enum class BrowseFacet { COUNTRY, CATEGORY }

/**
 * The persistent browse drawer.
 *
 * Country and category share one list with a switch at the top rather than living on
 * separate screens: navigation depth is capped at three, and a facet switch costs one
 * press where a screen change costs a navigation plus a BACK to undo.
 *
 * `focusRestorer()` here as well as on the grid: moving right into the grid and back
 * left must land on the entry the user came from, not the top of the list.
 */
@Composable
fun CountryDrawer(
    countries: List<Country>,
    categories: List<Category>,
    selectedCountry: String?,
    selectedCategory: String?,
    onCountrySelected: (String) -> Unit,
    onCategorySelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var facet by remember { mutableStateOf(BrowseFacet.COUNTRY) }
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
        Row(
            Modifier.padding(bottom = 12.dp).focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = { facet = BrowseFacet.COUNTRY }) {
                Text(stringResource(R.string.browse_countries))
            }
            Button(onClick = { facet = BrowseFacet.CATEGORY }) {
                Text(stringResource(R.string.browse_categories))
            }
        }

        LazyColumn(
            Modifier
                .fillMaxWidth()
                .focusRestorer()
                .focusGroup(),
        ) {
            when (facet) {
                BrowseFacet.COUNTRY -> items(countries, key = { it.code }) { country ->
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

                BrowseFacet.CATEGORY -> items(categories, key = { it.id }) { category ->
                    ListItem(
                        selected = category.id == selectedCategory,
                        onClick = { onCategorySelected(category.id) },
                        headlineContent = {
                            Text(
                                text = category.name,
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
}
