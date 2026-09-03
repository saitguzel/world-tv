package com.worldtv.feature.player

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.LifecycleStartEffect

/**
 * Binds the player to the lifecycle of whatever is showing it.
 *
 * On both form factors that owner is the player's own `NavBackStackEntry`, so this
 * fires in two cases the app used to miss entirely: the app going to the background,
 * and another destination being pushed over the player without popping it. Both left
 * video decoding, audible, and holding the audio focus the radio waits on.
 *
 * Stopping pauses rather than releases: the surface is coming back, and rebuilding the
 * player would cost the user a reconnect to a live stream every time they glance at
 * another app.
 */
@Composable
internal fun PlayerLifecycleEffect(viewModel: PlayerViewModel) {
    LifecycleStartEffect(viewModel) {
        viewModel.setActive(true)
        onStopOrDispose { viewModel.setActive(false) }
    }
}
