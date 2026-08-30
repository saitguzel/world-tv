package com.worldtv.feature.catalog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/**
 * Decides when a focused card should start previewing.
 *
 * The dwell delay is the entire design. Without it, walking across a row of channels
 * opens a connection per card — a dozen streams started and abandoned in two seconds,
 * which is both useless to the user and the fastest way to get an IP banned by a
 * restreamer. With it, a preview only starts once the user has actually stopped on
 * something.
 *
 * @param focusedChannelId the card under focus, or null when nothing is focused
 * @param enabled false while the user has previews turned off, or on a low-RAM box
 *   where a second decoder alongside the grid is not affordable
 * @return the channel to preview, or null
 */
@Composable
fun rememberPreviewTarget(
    focusedChannelId: String?,
    enabled: Boolean = true,
    dwellMillis: Long = PREVIEW_DWELL_MS,
): String? {
    var target by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(focusedChannelId, enabled) {
        if (!enabled || focusedChannelId == null) {
            target = null
            return@LaunchedEffect
        }
        // Cancelled and restarted on every focus change, so a fast scroll never
        // reaches the delay at all.
        target = null
        delay(dwellMillis)
        target = focusedChannelId
    }

    return target
}

/**
 * Long enough that scrolling never triggers it, short enough that stopping on a
 * channel feels like it responded.
 */
const val PREVIEW_DWELL_MS = 1_200L
