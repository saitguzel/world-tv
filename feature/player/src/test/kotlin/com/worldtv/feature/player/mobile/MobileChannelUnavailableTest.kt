package com.worldtv.feature.player.mobile

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.worldtv.feature.player.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The phone's failure screen, rendered on the JVM.
 *
 * This state did not exist on the phone until recently: every stream could die and the
 * user was left looking at a black surface with no way to retry. A screen test is the
 * only thing short of a device that proves the message and the button are actually
 * there — and that the button does something, which is the bug the sheets had.
 */
@RunWith(RobolectricTestRunner::class)
class MobileChannelUnavailableTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `the failure is explained in words`() {
        compose.setContent { MobileChannelUnavailable(onRetry = {}, onBack = {}) }

        compose.onNodeWithText(string(R.string.player_unavailable)).assertIsDisplayed()
    }

    @Test
    fun `retry is shown and reaches its callback`() {
        var retries = 0
        compose.setContent { MobileChannelUnavailable(onRetry = { retries++ }, onBack = {}) }

        compose.onNodeWithText(string(R.string.player_retry)).assertIsDisplayed().performClick()

        assertEquals(1, retries)
    }

    @Test
    fun `back to the list reaches its callback`() {
        var backs = 0
        compose.setContent { MobileChannelUnavailable(onRetry = {}, onBack = { backs++ }) }

        compose.onNodeWithText(string(R.string.player_back_to_list)).performClick()

        assertEquals(1, backs)
    }

    private fun string(id: Int) = compose.activity.getString(id)
}
