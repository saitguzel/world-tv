package com.worldtv.core.common

import android.app.ActivityManager
import android.content.Context
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

    private companion object {
        // A Chromecast with Google TV has 2 GB; assuming less is always safe.
        const val CONSERVATIVE_RAM_MB = 1_024L
    }
}
