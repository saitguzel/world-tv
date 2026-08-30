package com.worldtv.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the baseline profile.
 *
 * Run against a rooted emulator or a userdebug device:
 * `./gradlew :baselineprofile:generateBaselineProfile`
 *
 * The journey below is deliberately the cold-start path plus the first grid scroll:
 * those are where the JIT cost actually lands, and the architecture doc budgets under
 * two seconds from cold start to first screen. Profiling paths the user rarely takes
 * only makes the profile bigger and the win smaller.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startupAndBrowse() = rule.collect(packageName = PACKAGE) {
        pressHome()
        startActivityAndWait()

        // Wait for the home screen to settle before touching anything, or the profile
        // captures the splash and not the work that follows it.
        device.wait(Until.hasObject(By.focused(true)), TIMEOUT)

        // Into Browse: the paging query, the Room read and the first grid layout are
        // the most expensive things the app does on a cold start.
        device.pressDPadDown()
        device.pressDPadCenter()
        device.wait(Until.hasObject(By.focused(true)), TIMEOUT)

        // A real scroll, so the LazyVerticalGrid item composition path is profiled.
        repeat(SCROLL_STEPS) { device.pressDPadDown() }
        device.waitForIdle()

        repeat(SCROLL_STEPS) { device.pressDPadUp() }
        device.waitForIdle()
    }

    private companion object {
        const val PACKAGE = "com.worldtv"
        const val TIMEOUT = 10_000L
        const val SCROLL_STEPS = 12
    }
}
