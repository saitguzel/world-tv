package com.worldtv.data.repository

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The zap queue.
 *
 * Every screen that opens a channel seeds this — browse, home, search, favourites,
 * radio — and the player reads nothing else when the user presses up or down. It had no
 * tests at all, which is a poor match for how much depends on it.
 */
class PlaybackQueueHolderTest {

    @Test
    fun `opening a channel starts the queue on it, not at the top`() {
        val holder = PlaybackQueueHolder()
        holder.setQueue(listOf("a", "b", "c"), startId = "b")

        // The user pressed OK on the third card, so zapping down must go to the second,
        // not to whatever happened to be first in the list.
        assertEquals("b", holder.queue.value.currentId)
    }

    @Test
    fun `zapping wraps at both ends`() {
        val holder = PlaybackQueueHolder()
        holder.setQueue(listOf("a", "b", "c"), startId = "a")

        // Nothing happening at the end of the list reads as the app having frozen.
        assertEquals("c", holder.shift(-1))
        assertEquals("a", holder.shift(+1))
        assertEquals("b", holder.shift(+1))
    }

    @Test
    fun `zapping past the end of the list keeps landing on real channels`() {
        val holder = PlaybackQueueHolder()
        holder.setQueue(listOf("a", "b", "c"), startId = "a")

        // A held D-pad, or an impatient thumb, walks far further than the list is long.
        repeat(10) { holder.shift(+1) }
        assertEquals("b", holder.queue.value.currentId)
    }

    @Test
    fun `jumping from the channel drawer moves the queue with it`() {
        val holder = PlaybackQueueHolder()
        holder.setQueue(listOf("a", "b", "c"), startId = "a")

        assertEquals("c", holder.jumpTo("c"))
        // And zapping continues from where the jump landed, not from where it started.
        assertEquals("a", holder.shift(+1))
    }

    @Test
    fun `a deep link with no list behind it still zaps rather than doing nothing`() {
        // Reaching the player without browsing first — a deep link, an Assistant query,
        // or process death — used to leave the queue empty, and up or down then did
        // nothing at all with no explanation.
        val holder = PlaybackQueueHolder()
        holder.ensureContains("solo")

        assertEquals("solo", holder.queue.value.currentId)
        assertEquals("solo", holder.shift(+1))
    }

    @Test
    fun `ensureContains keeps the browsed list when the channel is already in it`() {
        val holder = PlaybackQueueHolder()
        holder.setQueue(listOf("a", "b", "c"), startId = "a")

        // The regression this guards: replacing the queue here would silently shrink a
        // country the user was browsing down to the single channel they opened.
        holder.ensureContains("c")

        assertEquals(3, holder.queue.value.size)
        assertEquals("c", holder.queue.value.currentId)
    }

    @Test
    fun `an empty queue reports nothing rather than throwing`() {
        val holder = PlaybackQueueHolder()

        assertTrue(holder.queue.value.isEmpty)
        assertNull(holder.shift(+1))
        assertNull(holder.jumpTo("nope"))
    }

    @Test
    fun `jumping to a channel that is not in the queue leaves the queue alone`() {
        val holder = PlaybackQueueHolder()
        holder.setQueue(listOf("a", "b", "c"), startId = "b")

        holder.jumpTo("elsewhere")

        assertEquals("b", holder.queue.value.currentId)
        assertEquals(3, holder.queue.value.size)
    }
}
