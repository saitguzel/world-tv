package com.worldtv.core.common

import android.content.Context
import android.view.accessibility.CaptioningManager
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The platform's caption preferences.
 *
 * A TV's accessibility settings are used far more than a phone's — subtitles are a
 * mainstream feature in a shared living room, not only an accessibility one — so the
 * app follows the system choice instead of inventing its own default.
 */
@Singleton
class CaptionSettings @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val manager: CaptioningManager?
        get() = context.getSystemService<CaptioningManager>()

    /** True when the user has captions switched on system-wide. */
    val isEnabled: Boolean get() = manager?.isEnabled == true

    /** The caption language the user chose, or null when they left it on automatic. */
    val preferredLanguage: String?
        get() = manager?.locale?.toLanguageTag()

    /**
     * The system caption font scale.
     *
     * Applied to the rendered subtitles rather than ignored: a viewer who scaled
     * captions up did so because they could not read them at the default size, and a
     * TV is further away than the phone those defaults were chosen for.
     */
    val fontScale: Float get() = manager?.fontScale ?: 1f

    val deviceLanguage: String get() = Locale.getDefault().toLanguageTag()
}
