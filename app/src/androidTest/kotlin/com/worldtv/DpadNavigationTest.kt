package com.worldtv

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * D-pad focus regression tests.
 *
 * Focus bugs are effectively impossible to find by hand — they show up after twenty
 * presses in one direction, or only when returning to a screen — so the invariant
 * "something is always focused" is asserted mechanically instead.
 */
@RunWith(AndroidJUnit4::class)
class DpadNavigationTest {

    private lateinit var device: UiDevice

    @Before
    fun setUp() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.wait(Until.hasObject(By.focused(true)), LAUNCH_TIMEOUT)
    }

    @Test
    fun screenOpensWithSomethingFocused() {
        assertNotNull(
            "No element had focus on launch — the remote would appear dead",
            device.findObject(By.focused(true)),
        )
    }

    @Test
    fun focusSurvivesThirtyPressesInEachDirection() {
        for (press in listOf(
            device::pressDPadDown,
            device::pressDPadUp,
            device::pressDPadRight,
            device::pressDPadLeft,
        )) {
            repeat(30) { press() }
            assertNotNull(
                "Focus was lost while walking the grid",
                device.findObject(By.focused(true)),
            )
        }
    }

    @Test
    fun focusReturnsToTheSameCardAfterEnteringAndLeavingThePlayer() {
        repeat(7) { device.pressDPadRight() }
        val before = device.findObject(By.focused(true))?.text

        device.pressDPadCenter()
        device.wait(Until.hasObject(By.pkg(PACKAGE)), LAUNCH_TIMEOUT)
        device.pressBack()
        device.wait(Until.hasObject(By.focused(true)), LAUNCH_TIMEOUT)

        val after = device.findObject(By.focused(true))?.text
        assert(before == after) {
            "Focus restoration failed: left from '$before', returned to '$after'"
        }
    }

    @Test
    fun holdingDownDoesNotWedgeTheUi() {
        // A held key fires at ~50/s; the app must stay responsive rather than ANR.
        repeat(120) { device.pressDPadDown() }
        assertNotNull(device.findObject(By.focused(true)))
    }

    private companion object {
        const val LAUNCH_TIMEOUT = 10_000L
        const val PACKAGE = "com.worldtv.debug"
    }
}
