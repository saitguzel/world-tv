package com.worldtv.feature.radio.mobile

import androidx.lifecycle.ViewModel
import com.worldtv.core.model.RadioStation
import com.worldtv.feature.radio.RadioController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/**
 * Backs the now-playing bar that sits above the navigation host.
 *
 * A view model of its own is not ceremony: the bar is rendered outside every screen, so
 * it cannot borrow `RadioViewModel`, which is scoped to the radio destination and would
 * be cleared the moment the user switched tabs — taking the bar's state with it.
 *
 * It holds nothing itself. [RadioController] is a `@Singleton` whose connection outlives
 * any screen, and this only re-exposes it. Notably it does not release the controller in
 * `onCleared`; doing exactly that from a screen-scoped view model is the bug this bar
 * would otherwise have inherited.
 */
@HiltViewModel
class MiniPlayerViewModel @Inject constructor(
    private val controller: RadioController,
) : ViewModel() {

    val nowPlaying: StateFlow<RadioStation?> = controller.nowPlaying
    val isPlaying: StateFlow<Boolean> = controller.isPlaying

    init {
        // Connect now rather than on first tap: the service may have outlived the
        // process, and the bar is the thing that needs that restored state. This is
        // the bar's view model, constructed on every phone start, so binding here does
        // not start a service at process launch the way doing it in Application would.
        controller.connect()
    }

    fun togglePlayPause() = controller.togglePlayPause()

    fun stop() = controller.stop()
}

/**
 * Whether the bar should be on screen.
 *
 * Pure so it can be tested without a device. Two rules: nothing playing means nothing to
 * show, and the player route is full-bleed video where a bar would both obscure the
 * picture and sit a mis-tap away from stopping the radio.
 */
fun shouldShowMiniPlayer(hasStation: Boolean, isPlayerRoute: Boolean): Boolean =
    hasStation && !isPlayerRoute
