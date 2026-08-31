package com.worldtv.core.designsystem.component

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.os.SystemClock

/**
 * "Press back again to exit" on the home screen.
 *
 * A single BACK press must never quit: on a TV, an accidental exit costs a full cold
 * start, and cold-starting an app is far more expensive than on a phone.
 */
@Composable
fun DoubleBackToExit(
    windowMillis: Long = 2_000,
    onPrompt: () -> Unit,
    onExit: () -> Unit,
) {
    var lastPressAt by remember { mutableLongStateOf(0L) }

    BackHandler {
        val now = SystemClock.uptimeMillis()
        if (now - lastPressAt < windowMillis) {
            onExit()
        } else {
            lastPressAt = now
            onPrompt()
        }
    }
}
