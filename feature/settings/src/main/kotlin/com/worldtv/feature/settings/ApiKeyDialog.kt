package com.worldtv.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusGroup
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.worldtv.core.designsystem.theme.WorldTvColors

/**
 * On-screen entry for the YouTube Data API key.
 *
 * API keys are mixed-case alphanumeric with dashes and underscores, so the grid has to
 * carry both cases — unlike the search keyboard, where folding to one case is fine
 * because the search index is normalised anyway.
 */
private val KEY_ROWS = listOf(
    "ABCDEFGHIJ",
    "KLMNOPQRST",
    "UVWXYZ0123",
    "456789-_ab",
    "cdefghijkl",
    "mnopqrstuv",
    "wxyz",
)

@Composable
fun ApiKeyEntry(
    initialValue: String,
    onSave: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var value by remember { mutableStateOf(initialValue) }
    val firstKey = remember { FocusRequester() }

    LaunchedEffect(Unit) { firstKey.requestFocus() }

    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(WorldTvColors.SurfaceVariant)
            .padding(24.dp)
            .focusGroup()
            // A modal must not leak focus to the list behind it, or the remote walks
            // off into content the user cannot see.
            .focusProperties { canFocus = true },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "YouTube Data API anahtarı",
            style = MaterialTheme.typography.titleLarge,
            color = WorldTvColors.OnSurface,
        )
        Text(
            // Masked: the key is a credential and TVs are watched by more than one
            // person. The length is enough to confirm it was typed correctly.
            text = if (value.isEmpty()) "—" else "${value.take(6)}… (${value.length} karakter)",
            style = MaterialTheme.typography.bodyLarge,
            color = WorldTvColors.OnSurfaceMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        KEY_ROWS.forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEachIndexed { columnIndex, character ->
                    Button(
                        onClick = { value += character },
                        modifier = Modifier
                            .size(48.dp)
                            .then(
                                if (rowIndex == 0 && columnIndex == 0) {
                                    Modifier.focusRequester(firstKey)
                                } else {
                                    Modifier
                                },
                            ),
                    ) {
                        Text(character.toString())
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = { value = value.dropLast(1) }, modifier = Modifier.width(96.dp)) {
                Text("Sil")
            }
            Button(onClick = { value = "" }, modifier = Modifier.width(110.dp)) {
                Text("Temizle")
            }
            Button(onClick = { onSave(value) }, modifier = Modifier.width(110.dp)) {
                Text("Kaydet")
            }
            Button(onClick = onCancel, modifier = Modifier.width(96.dp)) {
                Text("İptal")
            }
        }
    }
}
