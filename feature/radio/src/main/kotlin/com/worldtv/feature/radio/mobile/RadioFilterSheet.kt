package com.worldtv.feature.radio.mobile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SheetState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.worldtv.core.model.Category
import com.worldtv.core.model.Country
import com.worldtv.feature.radio.R
import com.worldtv.core.designsystem.mobile.component.SelectionCheck

/**
 * Country and category selection for radio, as a sheet.
 *
 * The mirror of [com.worldtv.feature.catalog.mobile.FilterSheet] from the TV browse
 * screen, living here because radio cannot see that screen's module. Same shape, same
 * dismiss-on-select behaviour — a phone sheet that stayed open after a choice would
 * force a second swipe to see the result.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadioFilterSheet(
    countries: List<Country>,
    categories: List<Category>,
    selectedCountry: String?,
    selectedCategory: String?,
    sheetState: SheetState,
    onCountry: (String?) -> Unit,
    onCategory: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        PrimaryTabRow(selectedTabIndex = tab) {
            Tab(
                selected = tab == 0,
                onClick = { tab = 0 },
                text = { Text(stringResource(R.string.radio_countries)) },
            )
            Tab(
                selected = tab == 1,
                onClick = { tab = 1 },
                text = { Text(stringResource(R.string.radio_categories)) },
            )
        }

        Column(Modifier.padding(horizontal = 16.dp)) {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                item {
                    ListItem(
                        modifier = Modifier.clickable {
                            if (tab == 0) onCountry(null) else onCategory(null)
                        },
                        headlineContent = { Text(stringResource(R.string.radio_all)) },
                        leadingContent = {
                            SelectionCheck(
                                selected = if (tab == 0) selectedCountry == null else selectedCategory == null,
                            )
                        },
                    )
                }
                if (tab == 0) {
                    items(countries, key = { it.code }) { country ->
                        ListItem(
                            modifier = Modifier.clickable { onCountry(country.code) },
                            headlineContent = {
                                Text(
                                    text = listOf(country.flag, country.name)
                                        .filter { it.isNotBlank() }
                                        .joinToString(" "),
                                )
                            },
                            leadingContent = { SelectionCheck(country.code == selectedCountry) },
                        )
                    }
                } else {
                    items(categories, key = { it.id }) { category ->
                        ListItem(
                            modifier = Modifier.clickable { onCategory(category.id) },
                            headlineContent = {
                                Text(
                                    text = stringResource(
                                        R.string.category_with_count,
                                        category.name,
                                        category.channelCount,
                                    ),
                                )
                            },
                            leadingContent = { SelectionCheck(category.id == selectedCategory) },
                        )
                    }
                }
            }
        }
    }
}