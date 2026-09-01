package com.worldtv.feature.radio.mobile

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Asks for notification permission the first time the user actually plays something.
 *
 * The permission is declared in the manifest but was never requested anywhere, which on
 * API 33+ means the radio service's foreground notification is silently suppressed —
 * and that notification is the phone's primary transport control once the app is in the
 * background. On a television nobody noticed, because nobody looks there.
 *
 * Asked at first playback rather than at launch, deliberately. A permission prompt with
 * no context in front of it is refused far more often, and two refusals make it
 * permanent. Playback is never blocked on the answer: the station starts either way and
 * the prompt runs alongside it.
 *
 * @return a function to call when playback starts; it is a no-op below API 33, when the
 *   permission is already granted, or after it has been asked once this session.
 */
@Composable
fun rememberNotificationPermissionRequest(): () -> Unit {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return remember { {} }

    val context = LocalContext.current
    val asked = remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* Nothing to do either way — the notification is a convenience, not a gate. */ }

    return remember(context) {
        {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

            if (!granted && !asked.value) {
                asked.value = true
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
