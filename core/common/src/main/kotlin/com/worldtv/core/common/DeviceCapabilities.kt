package com.worldtv.core.common

import android.app.ActivityManager
import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Runtime device facts used to size the health engine's concurrency. */
@Singleton
class DeviceCapabilities @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Total physical RAM in MB, or a conservative guess when it cannot be read. */
    val totalRamMb: Long by lazy {
        val activityManager = context.getSystemService<ActivityManager>()
            ?: return@lazy CONSERVATIVE_RAM_MB
        val info = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(info)
        (info.totalMem / (1024 * 1024)).coerceAtLeast(CONSERVATIVE_RAM_MB)
    }

    val isLowRamDevice: Boolean by lazy {
        context.getSystemService<ActivityManager>()?.isLowRamDevice ?: true
    }

    /**
     * Whether to present the TV or the phone experience.
     *
     * Read once at the top of the UI and pushed down as a CompositionLocal — the two
     * trees use incompatible Material libraries, so this cannot be decided per screen.
     * The rule itself lives in [FormFactorDetector] so it can be tested without a
     * device; this only gathers the platform facts it needs.
     */
    val formFactor: FormFactor by lazy {
        val uiMode = context.getSystemService<UiModeManager>()?.currentModeType
        FormFactorDetector.detect(
            isTelevisionUiMode = uiMode == Configuration.UI_MODE_TYPE_TELEVISION,
            isNormalUiMode = uiMode == Configuration.UI_MODE_TYPE_NORMAL,
            hasTouchscreen = context.packageManager
                .hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN),
            hasLeanback = context.packageManager
                .hasSystemFeature(PackageManager.FEATURE_LEANBACK),
            hasTelevisionFeature = context.packageManager
                .hasSystemFeature(FEATURE_TYPE_TELEVISION),
        )
    }

    val isTelevision: Boolean get() = formFactor == FormFactor.TV

    private companion object {
        // A Chromecast with Google TV has 2 GB; assuming less is always safe.
        const val CONSERVATIVE_RAM_MB = 1_024L

        /**
         * Superseded by `FEATURE_LEANBACK`, but still the only marker some bare AOSP
         * boxes set, and this app supports back to API 23.
         */
        const val FEATURE_TYPE_TELEVISION = "android.hardware.type.television"
    }
}
