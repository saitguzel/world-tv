package com.worldtv.core.designsystem.mobile.component

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The leading slot of a pickable row in a sheet.
 *
 * A check when selected and an equally wide blank otherwise, so the labels of a list
 * line up whether or not a row is the chosen one — a slot that is sometimes empty and
 * sometimes not shifts every other label sideways.
 */
@Composable
fun SelectionCheck(selected: Boolean, modifier: Modifier = Modifier) {
    if (selected) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = modifier.size(24.dp),
        )
    } else {
        Spacer(modifier.size(24.dp))
    }
}
