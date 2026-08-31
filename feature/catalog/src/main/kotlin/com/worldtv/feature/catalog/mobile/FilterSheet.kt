package com.worldtv.feature.catalog.mobile

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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SheetState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.worldtv.core.model.Category
import com.worldtv.core.model.Country
import com.worldtv.core.model.TextNormalizer
import com.worldtv.feature.catalog.R

/**
 * Country and category selection, as a sheet.
 *
 * The TV screen keeps this as a permanent 280dp rail beside the grid, which is right
 * when the whole layout is 1920dp wide and moving into it costs one D-pad press. In
 * portrait that rail would take 78% of the screen, so on a phone it becomes a sheet
 * that dismisses as soon as a choice is made.
 *
 * The search field has no TV counterpart and is not decoration: scrolling two hundred
 * countries is fine with a held D-pad and miserable with a thumb. It matches on
 * `TextNormalizer.searchText`, so accent- and case-insensitive Turkish input works the
 * same way it does in the main search screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSheet(
    countries: List<Country>,
    categories: List<Category>,
    sheetState: SheetState,
    onCountry: (String?) -> Unit,
    onCategory: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        PrimaryTabRow(selectedTabIndex = tab) {
            Tab(
                selected = tab == 0,
                onClick = { tab = 0 },
                text = { Text(stringResource(R.string.browse_countries)) },
            )
            Tab(
                selected = tab == 1,
                onClick = { tab = 1 },
                text = { Text(stringResource(R.string.browse_categories)) },
            )
        }

        Column(Modifier.padding(horizontal = 16.dp)) {
            if (tab == 0) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.browse_country_search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                )
            }

            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                item {
                    ListItem(
                        modifier = Modifier.clickable {
                            if (tab == 0) onCountry(null) else onCategory(null)
                        },
                        headlineContent = { Text(stringResource(R.string.browse_all_channels)) },
                    )
                }
                if (tab == 0) {
                    // normalize, not raw contains: it folds ı/İ/ş/ğ and strips accents,
                    // so typing "istanbul" finds "İSTANBUL" the way the search screen does.
                    val needle = TextNormalizer.normalize(query)
                    val shown = if (needle.isBlank()) countries else {
                        countries.filter { TextNormalizer.normalize(it.name).contains(needle) }
                    }
                    items(shown, key = { it.code }) { country ->
                        ListItem(
                            modifier = Modifier.clickable { onCountry(country.code) },
                            headlineContent = {
                                Text(
                                    stringResource(
                                        R.string.country_with_count,
                                        country.flag,
                                        country.name,
                                        country.channelCount,
                                    ),
                                )
                            },
                        )
                    }
                } else {
                    items(categories, key = { it.id }) { category ->
                        ListItem(
                            modifier = Modifier.clickable { onCategory(category.id) },
                            headlineContent = { Text(category.name) },
                        )
                    }
                }
            }
        }
    }
}
