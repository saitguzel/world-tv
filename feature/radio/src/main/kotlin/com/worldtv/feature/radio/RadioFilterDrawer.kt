package com.worldtv.feature.radio

import androidx.compose.foundation.focusGroup
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
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.worldtv.core.designsystem.theme.WorldTvColors
import com.worldtv.core.designsystem.theme.WorldTvDimens
import com.worldtv.core.model.Category
import com.worldtv.core.model.Country
import androidx.compose.ui.res.stringResource
import com.worldtv.feature.radio.R
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.FilterChip
import androidx.tv.material3.Icon
import com.worldtv.core.designsystem.component.ShuffleIcon

/** Which facet the radio filter drawer is currently listing. */
enum class RadioFacet { COUNTRY, CATEGORY }

/**
 * Country and category filter for radio.
 *
 * Same shape as the TV browse drawer on purpose: the two modes should not need to be
 * learned separately, and `focusRestorer()` is what makes moving right into the list
 * and back left return to the entry the user came from. Country and category share one
 * list with a facet switch at the top, exactly like the channel drawer — a facet
 * switch costs one press where a screen change costs a navigation plus a BACK.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun RadioFilterDrawer(
    countries: List<Country>,
    categories: List<Category>,
    selectedCountry: String?,
    selectedCategory: String?,
    onCountrySelected: (String?) -> Unit,
    onCategorySelected: (String?) -> Unit,
    onRandom: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var facet by remember { mutableStateOf(RadioFacet.COUNTRY) }
    Column(
        modifier
            .width(WorldTvDimens.DrawerWidthExpanded)
            .fillMaxHeight()
            .padding(end = 16.dp),
    ) {
        Row(
            Modifier.padding(bottom = 12.dp).focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = facet == RadioFacet.COUNTRY,
                onClick = { facet = RadioFacet.COUNTRY },
                content = { Text(stringResource(R.string.radio_countries)) },
            )
            FilterChip(
                selected = facet == RadioFacet.CATEGORY,
                onClick = { facet = RadioFacet.CATEGORY },
                content = { Text(stringResource(R.string.radio_categories)) },
            )
        }

        LazyColumn(
            Modifier
                .fillMaxWidth()
                .focusRestorer()
                .focusGroup(),
        ) {
            item {
                ListItem(
                    selected = when (facet) {
                        RadioFacet.COUNTRY -> selectedCountry == null
                        RadioFacet.CATEGORY -> selectedCategory == null
                    },
                    onClick = {
                        when (facet) {
                            RadioFacet.COUNTRY -> onCountrySelected(null)
                            RadioFacet.CATEGORY -> onCategorySelected(null)
                        }
                    },
                    headlineContent = { Text(stringResource(R.string.radio_all)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            when (facet) {
                RadioFacet.COUNTRY -> items(countries, key = { it.code }) { country ->
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

                RadioFacet.CATEGORY -> items(categories, key = { it.id }) { category ->
                    ListItem(
                        selected = category.id == selectedCategory,
                        onClick = { onCategorySelected(category.id) },
                        headlineContent = {
                            Text(
                                text = stringResource(
                                    R.string.category_with_count,
                                    category.name,
                                    category.channelCount,
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            item {
                ListItem(
                    selected = false,
                    onClick = onRandom,
                    headlineContent = {
                        Text(
                            text = stringResource(R.string.radio_random),
                            color = WorldTvColors.Accent,
                        )
                    },
                    leadingContent = {
                        Icon(ShuffleIcon, contentDescription = null, tint = WorldTvColors.Accent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}