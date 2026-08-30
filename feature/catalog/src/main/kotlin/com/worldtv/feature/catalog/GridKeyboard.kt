package com.worldtv.feature.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusGroup
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/**
 * An on-screen keyboard laid out as an alphabetical grid.
 *
 * Not QWERTY, and not the system IME. QWERTY is a muscle-memory layout for ten
 * fingers; scanning it visually with a D-pad is slow, whereas an alphabetical grid can
 * be searched the way you search an index. The system IME is skipped because its focus
 * behaviour on TV is inconsistent between launchers.
 *
 * The Turkish letters are included as their own keys — a user hunting for "Show TV"
 * types Latin letters, but one hunting for "Kanal Ş" needs Ş to exist.
 */
private val KEY_ROWS: List<String> = listOf(
    "ABCDEFG",
    "HIJKLMN",
    "OPQRSTU",
    "VWXYZÇĞ",
    "İÖŞÜ012",
    "3456789",
)

@Composable
fun GridKeyboard(
    onCharacter: (Char) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val firstKey = remember { FocusRequester() }

    // A search screen that opens with nothing focused looks broken; the first key is
    // both a sensible landing spot and a guarantee the remote does something.
    LaunchedEffect(Unit) { firstKey.requestFocus() }

    Column(
        modifier.focusGroup(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        KEY_ROWS.forEachIndexed { rowIndex, row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                row.forEachIndexed { columnIndex, character ->
                    Button(
                        onClick = { onCharacter(character) },
                        modifier = Modifier
                            .size(56.dp)
                            .then(
                                if (rowIndex == 0 && columnIndex == 0) {
                                    Modifier.focusRequester(firstKey)
                                } else {
                                    Modifier
                                },
                            ),
                    ) {
                        Text(character.toString(), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onCharacter(' ') }, modifier = Modifier.width(120.dp)) {
                Text("Boşluk")
            }
            Button(onClick = onBackspace, modifier = Modifier.width(100.dp)) {
                Text("Sil")
            }
            Button(onClick = onClear, modifier = Modifier.width(100.dp)) {
                Text("Temizle")
            }
        }
    }
}
