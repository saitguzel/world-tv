package com.worldtv.feature.catalog.mobile

import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.worldtv.core.model.HealthInfo
import com.worldtv.core.model.RadioStation
import com.worldtv.core.model.StreamState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The radio shelves on Home.
 *
 * Home used to be television-only; these rows are the thing that changed, so this
 * pins what a shelf renders and what a tap reports. The card carries the station's
 * name in its content description, which is also the node a tap lands on.
 */
@RunWith(RobolectricTestRunner::class)
// compileSdk 37 is ahead of what Robolectric ships an image for; 35 is the newest it
// can run, and nothing under test here is API-specific.
@Config(sdk = [35])
class MobileHomeStationShelfTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `a shelf lists its stations and reports the uuid of the one tapped`() {
        var opened: String? = null
        compose.setContent {
            LazyColumn {
                stationShelf(
                    title = "Favori radyolar",
                    stations = listOf(station("a", "Radyo A"), station("b", "Radyo B")),
                    onOpen = { opened = it },
                )
            }
        }

        compose.onNodeWithContentDescription("Radyo B", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Radyo A", substring = true).performClick()

        // The uuid, not the name: the radio route names a station by uuid.
        assertEquals("a", opened)
    }

    @Test
    fun `an empty shelf draws nothing, not a heading over a gap`() {
        compose.setContent {
            LazyColumn { stationShelf("Favori radyolar", emptyList(), onOpen = {}) }
        }

        compose.onNodeWithContentDescription("Radyo", substring = true).assertDoesNotExist()
    }

    private fun station(uuid: String, name: String) = RadioStation(
        uuid = uuid,
        name = name,
        url = "https://example/$uuid",
        faviconUrl = null,
        tags = listOf("pop"),
        countryCode = "TR",
        language = "tr",
        codec = "MP3",
        bitrate = 128,
        serverSideOk = false,
        clickCount = 0,
        votes = 0,
        health = HealthInfo(state = StreamState.OK),
    )
}
