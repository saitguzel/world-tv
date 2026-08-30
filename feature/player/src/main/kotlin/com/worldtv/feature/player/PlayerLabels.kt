package com.worldtv.feature.player

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The handful of strings the player builds outside a composable.
 *
 * Track options are assembled in a `Player.Listener` callback, where there is no
 * composition to call `stringResource` from. Rather than let UI copy leak into the
 * ViewModel as literals, the wording is read from resources here.
 */
@Singleton
class PlayerLabels @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Shown for a track whose language tag says nothing useful. */
    val unknownTrack: String get() = context.getString(R.string.tracks_unknown)

    /** The synthetic "no subtitles" option. */
    val subtitlesOff: String get() = context.getString(R.string.tracks_off)
}
