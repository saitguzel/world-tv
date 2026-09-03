package com.worldtv.data.repository

import com.worldtv.core.database.dao.RadioTagRow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The rule that turns Radio Browser's free-form station tags into a category list.
 *
 * Pure by design: the DAO merely hands over distinct `tags` cells, and everything that
 * could disagree with the filter query — normalisation, ranking, cutting — lives here.
 */
class RadioCategoriesTest {

    @Test
    fun `a comma-separated tag cell counts for each of its tags`() {
        val rows = listOf(
            RadioTagRow(tags = "music,pop,rock", stationCount = 10),
            RadioTagRow(tags = "music,jazz", stationCount = 3),
        )

        val categories = RadioCategories.aggregate(rows)

        assertEquals("music", categories.first().id)
        assertEquals(13, categories.first().channelCount)
        assertEquals(setOf("music", "pop", "rock", "jazz"), categories.map { it.id }.toSet())
    }

    @Test
    fun `tags are lowercased and trimmed so filtering matches the stored casing`() {
        val categories = RadioCategories.aggregate(
            listOf(RadioTagRow(tags = "  News ,Pop", stationCount = 5)),
        )

        assertEquals("news", categories[0].id)
        assertEquals("pop", categories[1].id)
    }

    @Test
    fun `semicolon-separated tags count too`() {
        val categories = RadioCategories.aggregate(
            listOf(RadioTagRow(tags = "classical;jazz", stationCount = 4)),
        )

        assertEquals(setOf("classical", "jazz"), categories.map { it.id }.toSet())
    }

    @Test
    fun `junk tags are dropped rather than listed`() {
        val categories = RadioCategories.aggregate(
            listOf(
                RadioTagRow(tags = "music,???,,x,", stationCount = 2),
                RadioTagRow(tags = "music,90s", stationCount = 1),
            ),
        )

        assertEquals(listOf("music", "90s"), categories.map { it.id })
    }

    @Test
    fun `categories are ranked by station count then name`() {
        val rows = listOf(
            RadioTagRow(tags = "rock", stationCount = 1),
            RadioTagRow(tags = "jazz", stationCount = 9),
            RadioTagRow(tags = "pop", stationCount = 9),
        )

        val categories = RadioCategories.aggregate(rows)

        assertEquals(listOf("jazz", "pop", "rock"), categories.map { it.id })
        assertEquals(listOf(9, 9, 1), categories.map { it.channelCount })
    }

    @Test
    fun `the list is capped so the drawer stays scannable`() {
        val rows = (0 until 50).map { RadioTagRow(tags = "tag$it", stationCount = 1) }

        val categories = RadioCategories.aggregate(rows)

        assertEquals(24, categories.size)
    }

    @Test
    fun `no tags means no categories`() {
        assertTrue(RadioCategories.aggregate(emptyList()).isEmpty())
    }
}