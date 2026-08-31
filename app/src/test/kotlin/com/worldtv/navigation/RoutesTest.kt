package com.worldtv.navigation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Route construction and matching, tested on the JVM.
 *
 * These are string-manipulation bugs that otherwise only surface as a navigation bar
 * highlighting the wrong tab, or chrome refusing to hide over the player — symptoms
 * that are slow to spot on a device and instant to catch here.
 */
class RoutesTest {

    @Test
    fun `browse without a country omits the argument entirely`() {
        // Not "browse?country=null": the argument is declared nullable with a null
        // default, and passing the literal string would defeat that.
        assertEquals("browse", Routes.browse())
        assertEquals("browse", Routes.browse(null))
    }

    @Test
    fun `browse with a country carries it`() {
        assertEquals("browse?country=TR", Routes.browse("TR"))
    }

    @Test
    fun `the player route embeds the channel id`() {
        assertEquals("player/trt1", Routes.player("trt1"))
        assertTrue(Routes.isPlayer(Routes.player("trt1")))
    }

    @Test
    fun `nothing but the player counts as the player`() {
        // The phone hides its navigation chrome on this test alone, so a false positive
        // would strand the user on a screen with no visible way back to the tabs.
        assertFalse(Routes.isPlayer(Routes.HOME))
        assertFalse(Routes.isPlayer(Routes.browse("TR")))
        assertFalse(Routes.isPlayer(null))
    }

    @Test
    fun `the browse tab stays selected once a country is chosen`() {
        // The regression this guards: the back stack reports "browse?country=TR" while
        // the nav bar only knows "browse", so a plain equality check would drop the
        // highlight exactly when the user is browsing.
        assertTrue(Routes.isTopLevel("browse", Routes.BROWSE_BASE))
        assertTrue(Routes.isTopLevel("browse?country=TR", Routes.BROWSE_BASE))
        assertTrue(Routes.isTopLevel("browse?country={country}", Routes.BROWSE_BASE))
    }

    @Test
    fun `a route is not confused with one that merely starts the same way`() {
        assertFalse(Routes.isTopLevel("browsers", Routes.BROWSE_BASE))
        assertFalse(Routes.isTopLevel(null, Routes.BROWSE_BASE))
    }

    @Test
    fun `every navigation bar entry is a real destination`() {
        // TOP_LEVEL is hand-written; this stops it drifting from the graph.
        val declared = setOf(
            Routes.HOME, Routes.BROWSE_BASE, Routes.SEARCH,
            Routes.RADIO, Routes.FAVORITES, Routes.SETTINGS,
        )
        assertTrue(declared.containsAll(Routes.TOP_LEVEL))
    }

    @Test
    fun `settings is deliberately not a navigation bar entry`() {
        // Six items crowd a bottom bar. Settings lives in Home's app bar instead, and
        // this pins that decision so it is not quietly undone.
        assertFalse(Routes.TOP_LEVEL.contains(Routes.SETTINGS))
        assertEquals(5, Routes.TOP_LEVEL.size)
    }
}
