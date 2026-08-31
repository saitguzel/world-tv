package com.worldtv.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChannelQueueTest {

    private val queue = ChannelQueue.of(listOf("a", "b", "c", "d"), startId = "b")

    @Test
    fun `starts at the channel the user actually picked`() {
        assertEquals(1, queue.index)
        assertEquals("b", queue.currentId)
        assertEquals(2, queue.position)
    }

    @Test
    fun `an unknown start id falls back to the top of the list`() {
        val fallback = ChannelQueue.of(listOf("a", "b"), startId = "missing")
        assertEquals(0, fallback.index)
        assertEquals("a", fallback.currentId)
    }

    @Test
    fun `zapping wraps at both ends instead of stopping dead`() {
        val atEnd = queue.jumpTo("d")
        assertEquals("a", atEnd.shift(1).currentId)

        val atStart = queue.jumpTo("a")
        assertEquals("d", atStart.shift(-1).currentId)
    }

    @Test
    fun `large and negative shifts wrap correctly`() {
        // Starts at index 1 ("b").
        assertEquals("b", queue.shift(4).currentId, "a full lap returns to the start")
        assertEquals("a", queue.shift(-5).currentId, "a full lap backwards, then one more")
        // 1 - 99 = -98; -98 mod 4 == 2 once normalised into range.
        assertEquals("c", queue.shift(-99).currentId)
    }

    @Test
    fun `an empty queue is inert rather than throwing`() {
        val empty = ChannelQueue()
        assertTrue(empty.isEmpty)
        assertNull(empty.currentId)
        assertEquals(0, empty.position)
        assertEquals(empty, empty.shift(3))
        assertEquals(emptyList<String>(), empty.neighbourIds())
    }

    @Test
    fun `neighbours are the channels either side, for prefetching`() {
        assertEquals(listOf("c", "a"), queue.neighbourIds())
    }

    @Test
    fun `a single-channel queue has no neighbours to prefetch`() {
        assertEquals(emptyList<String>(), ChannelQueue(listOf("only"), 0).neighbourIds())
    }

    @Test
    fun `a two-channel queue lists the other channel once, not twice`() {
        // shift(+1) and shift(-1) both land on the same channel here.
        assertEquals(listOf("b"), ChannelQueue(listOf("a", "b"), 0).neighbourIds())
    }

    @Test
    fun `jumping to a channel outside the queue leaves it untouched`() {
        assertEquals(queue, queue.jumpTo("zzz"))
    }
}
