package com.worldtv.data.repository

import com.worldtv.core.database.dao.RadioTagRow
import com.worldtv.core.model.Category

/**
 * Turns Radio Browser's free-form station tags into a short category list.
 *
 * Radio Browser has no category hierarchy; a station simply tags itself ("music,pop"
 * or "Rock Music"). The catalog therefore has to synthesise categories from the tags
 * in the table, and the rule for doing that is pure so it can be tested without a
 * device.
 *
 * The tag shown is the normalised one — lowercase, comma/semicolon-split — because the
 * list filter matches with `(',' || tags || ',') LIKE '%,' || :tag || ',%'`, and SQLite's
 * LIKE is ASCII-case-insensitive, so a lowercased category still matches the stored
 * mixed-case tag.
 */
object RadioCategories {

    /**
     * @param rows one entry per distinct stored `tags` cell, with the station count
     *   behind each; the DAO sums per identical cell.
     * @param max how many categories to keep. Everything beyond this has almost no
     *   stations behind it and would just be scrolling noise.
     */
    fun aggregate(rows: List<RadioTagRow>, max: Int = MAX_CATEGORIES): List<Category> {
        val counts = HashMap<String, Int>()
        for (row in rows) {
            for (tag in row.tags.split(TAG_SEPARATORS)) {
                val normalized = tag.trim().lowercase()
                if (!KEEP(normalized)) continue
                counts[normalized] = (counts[normalized] ?: 0) + row.stationCount
            }
        }
        return counts.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(max)
            .map { (tag, count) -> Category(id = tag, name = tag, channelCount = count) }
    }

    /** Stations tag themselves with arbitrary text; only short, word-like tags are kept. */
    private val KEEP: (String) -> Boolean = { tag ->
        // Digits are fine ("90s", "top 40"); the characters that mark a junk tag are
        // the punctuation that appears in none of the real categories. Bounds are
        // compared explicitly rather than with `in` — this Kotlin's stdlib cannot
        // disambiguate ClosedRange and OpenEndRange contains on a plain IntRange.
        tag.length >= 2 &&
            tag.length <= 24 &&
            tag.all { it.isLetterOrDigit() || it.isWhitespace() || it in ALLOWED_PUNCTUATION }
    }

    private val ALLOWED_PUNCTUATION = setOf('&', '-', '.', '\'')

    private val TAG_SEPARATORS = Regex("[,;]")

    private const val MAX_CATEGORIES = 24
}