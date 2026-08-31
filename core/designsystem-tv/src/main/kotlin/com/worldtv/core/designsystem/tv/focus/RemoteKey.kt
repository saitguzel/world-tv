package com.worldtv.core.designsystem.tv.focus

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type

/** Logical remote button, decoupled from the wildly variable physical key codes. */
sealed interface RemoteKey {
    data object Up : RemoteKey
    data object Down : RemoteKey
    data object Left : RemoteKey
    data object Right : RemoteKey
    data object Select : RemoteKey
    data object LongSelect : RemoteKey
    data object Back : RemoteKey
    data object PlayPause : RemoteKey
    data object ChannelUp : RemoteKey
    data object ChannelDown : RemoteKey
    data object Info : RemoteKey
    data object Search : RemoteKey
    data class Digit(val value: Int) : RemoteKey
}

/**
 * Maps a key event to a [RemoteKey].
 *
 * Modern Google TV remotes are minimal: no number row, no colour keys, usually no
 * channel up/down. Everything below the D-pad cluster is treated as a shortcut only —
 * every action in the app must also be reachable with D-pad plus centre.
 */
fun KeyEvent.toRemoteKey(): RemoteKey? {
    if (type != KeyEventType.KeyDown) return null
    val native = nativeKeyEvent
    return when (native.keyCode) {
        AndroidKeyEvent.KEYCODE_DPAD_UP -> RemoteKey.Up
        AndroidKeyEvent.KEYCODE_DPAD_DOWN -> RemoteKey.Down
        AndroidKeyEvent.KEYCODE_DPAD_LEFT -> RemoteKey.Left
        AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> RemoteKey.Right

        // DPAD_CENTER, ENTER and BUTTON_A are distinct codes and different hardware
        // sends different ones. A gamepad sends BUTTON_A and nothing else.
        AndroidKeyEvent.KEYCODE_DPAD_CENTER,
        AndroidKeyEvent.KEYCODE_ENTER,
        AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
        AndroidKeyEvent.KEYCODE_BUTTON_A,
        -> if (native.repeatCount == 1) RemoteKey.LongSelect else RemoteKey.Select

        AndroidKeyEvent.KEYCODE_BACK,
        AndroidKeyEvent.KEYCODE_BUTTON_B,
        -> RemoteKey.Back

        AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        AndroidKeyEvent.KEYCODE_MEDIA_PLAY,
        AndroidKeyEvent.KEYCODE_MEDIA_PAUSE,
        -> RemoteKey.PlayPause

        AndroidKeyEvent.KEYCODE_CHANNEL_UP,
        AndroidKeyEvent.KEYCODE_PAGE_UP,
        -> RemoteKey.ChannelUp

        AndroidKeyEvent.KEYCODE_CHANNEL_DOWN,
        AndroidKeyEvent.KEYCODE_PAGE_DOWN,
        -> RemoteKey.ChannelDown

        AndroidKeyEvent.KEYCODE_INFO -> RemoteKey.Info
        AndroidKeyEvent.KEYCODE_SEARCH -> RemoteKey.Search

        in AndroidKeyEvent.KEYCODE_0..AndroidKeyEvent.KEYCODE_9 ->
            RemoteKey.Digit(native.keyCode - AndroidKeyEvent.KEYCODE_0)

        else -> null
    }
}
