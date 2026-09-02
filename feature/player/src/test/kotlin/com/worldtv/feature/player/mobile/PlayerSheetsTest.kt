package com.worldtv.feature.player.mobile

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.worldtv.core.model.Channel
import com.worldtv.core.model.ChannelSummary
import com.worldtv.core.model.MediaTrack
import com.worldtv.core.model.TrackType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The player's two bottom sheets, rendered on the JVM.
 *
 * Both shipped once as lists that selected nothing: Material 3's ListItem has no
 * onClick, and nothing in the build noticed. These tests exist so that mistake fails
 * here instead of on a phone.
 */
@RunWith(RobolectricTestRunner::class)
class PlayerSheetsTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `tapping a channel in the drawer selects it`() {
        var selected: String? = null
        compose.setContent {
            ChannelDrawerList(
                channels = listOf(summary("trt1", "TRT 1"), summary("show", "Show TV")),
                currentId = "trt1",
                listState = rememberLazyListState(),
                onSelect = { selected = it },
            )
        }

        compose.onNodeWithText("Show TV").assertIsDisplayed().performClick()

        assertEquals("show", selected)
    }

    @Test
    fun `the current channel is still selectable`() {
        // Re-tapping the playing channel is how a user retries it from the drawer.
        var selected: String? = null
        compose.setContent {
            ChannelDrawerList(
                channels = listOf(summary("trt1", "TRT 1")),
                currentId = "trt1",
                listState = rememberLazyListState(),
                onSelect = { selected = it },
            )
        }

        compose.onNodeWithText("TRT 1").performClick()

        assertEquals("trt1", selected)
    }

    @Test
    fun `tapping a track selects it`() {
        var selected: MediaTrack? = null
        val tracks = listOf(
            track("off", "Kapalı", isSelected = true, isOff = true),
            track("tr", "Türkçe"),
        )
        compose.setContent { TrackList(tracks = tracks, onSelect = { selected = it }) }

        compose.onNodeWithText("Türkçe").assertIsDisplayed().performClick()

        assertEquals(tracks[1], selected)
    }

    private fun summary(id: String, name: String) = ChannelSummary(
        channel = Channel(
            id = id,
            name = name,
            country = "TR",
            categories = listOf("general"),
            logoUrl = null,
            isNsfw = false,
            isClosed = false,
        ),
        availableStreams = 1,
        verifiedStreams = 1,
        bestLatencyMs = null,
        isFavorite = false,
        geoBlockedOnly = false,
    )

    private fun track(id: String, label: String, isSelected: Boolean = false, isOff: Boolean = false) =
        MediaTrack(id = id, type = TrackType.TEXT, language = null, label = label, isSelected = isSelected, isOff = isOff)
}
