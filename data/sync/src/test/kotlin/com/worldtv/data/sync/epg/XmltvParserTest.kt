package com.worldtv.data.sync.epg

import com.worldtv.core.model.Programme
import com.worldtv.core.model.XmltvTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class XmltvParserTest {

    private val parser = XmltvParser()

    private fun parse(
        xml: String,
        filter: (String) -> Boolean = { true },
    ): Pair<List<Programme>, XmltvParser.Stats> {
        val collected = mutableListOf<Programme>()
        val stats = parser.parse(xml.byteInputStream(), filter) { collected += it }
        return collected to stats
    }

    @Test
    fun `reads a well-formed programme`() {
        val (programmes, stats) = parse(
            """
            <tv>
              <programme start="20240315203000 +0300" stop="20240315213000 +0300" channel="TRT1.tr">
                <title lang="tr">Haber</title>
                <desc lang="tr">Akşam haberleri</desc>
                <category lang="tr">news</category>
                <episode-num system="onscreen">S2E14</episode-num>
              </programme>
            </tv>
            """.trimIndent(),
        )

        assertEquals(1, stats.parsed)
        assertEquals(0, stats.skipped)
        val programme = programmes.single()
        assertEquals("TRT1.tr", programme.channelId)
        assertEquals("Haber", programme.title)
        assertEquals("Akşam haberleri", programme.description)
        assertEquals("news", programme.category)
        assertEquals("S2E14", programme.episode)
        assertEquals(XmltvTime.parse("20240315173000 +0000"), programme.startAt)
        assertEquals(60 * 60 * 1000L, programme.durationMillis)
    }

    @Test
    fun `keeps only the first title when a guide repeats it per language`() {
        val (programmes, _) = parse(
            """
            <tv>
              <programme start="20240315203000 +0000" stop="20240315213000 +0000" channel="a">
                <title lang="tr">Birinci</title>
                <title lang="en">Second</title>
              </programme>
            </tv>
            """.trimIndent(),
        )
        assertEquals("Birinci", programmes.single().title)
    }

    @Test
    fun `a single bad entry does not abort the rest of the document`() {
        val (programmes, stats) = parse(
            """
            <tv>
              <programme start="NOT-A-TIME" stop="20240315213000 +0000" channel="a">
                <title>Broken</title>
              </programme>
              <programme start="20240315210000 +0000" stop="20240315220000 +0000" channel="a">
                <title>Good</title>
              </programme>
            </tv>
            """.trimIndent(),
        )
        // Losing a whole country's schedule to one bad timestamp is the failure mode
        // this guards against.
        assertEquals(listOf("Good"), programmes.map { it.title })
        assertEquals(1, stats.parsed)
        assertEquals(1, stats.skipped)
    }

    @Test
    fun `entries with no usable duration are skipped`() {
        val (programmes, stats) = parse(
            """
            <tv>
              <programme start="20240315210000 +0000" stop="20240315210000 +0000" channel="a">
                <title>Zero length</title>
              </programme>
              <programme start="20240315220000 +0000" stop="20240315210000 +0000" channel="a">
                <title>Ends before it starts</title>
              </programme>
            </tv>
            """.trimIndent(),
        )
        assertTrue(programmes.isEmpty())
        assertEquals(2, stats.skipped)
    }

    @Test
    fun `an entry with no title is skipped`() {
        val (programmes, stats) = parse(
            """
            <tv>
              <programme start="20240315210000 +0000" stop="20240315220000 +0000" channel="a">
                <desc>No title here</desc>
              </programme>
            </tv>
            """.trimIndent(),
        )
        assertTrue(programmes.isEmpty())
        assertEquals(1, stats.skipped)
    }

    @Test
    fun `the channel filter keeps guides for channels the catalog does not carry out`() {
        val (programmes, _) = parse(
            """
            <tv>
              <programme start="20240315210000 +0000" stop="20240315220000 +0000" channel="wanted">
                <title>Keep</title>
              </programme>
              <programme start="20240315210000 +0000" stop="20240315220000 +0000" channel="other">
                <title>Drop</title>
              </programme>
            </tv>
            """.trimIndent(),
            filter = { it == "wanted" },
        )
        assertEquals(listOf("Keep"), programmes.map { it.title })
    }

    @Test
    fun `filtered-out entries do not leak their text into the next programme`() {
        // The handler must reset its capture state between entries, or a skipped
        // programme's title reappears on the following one.
        val (programmes, _) = parse(
            """
            <tv>
              <programme start="20240315210000 +0000" stop="20240315220000 +0000" channel="other">
                <title>Leaked</title>
                <desc>Leaked description</desc>
              </programme>
              <programme start="20240315220000 +0000" stop="20240315230000 +0000" channel="wanted">
                <title>Correct</title>
              </programme>
            </tv>
            """.trimIndent(),
            filter = { it == "wanted" },
        )
        val programme = programmes.single()
        assertEquals("Correct", programme.title)
        assertNull(programme.description)
    }

    @Test
    fun `text split across buffer boundaries is reassembled`() {
        // SAX may deliver character data in several callbacks; entities force that.
        val (programmes, _) = parse(
            """
            <tv>
              <programme start="20240315210000 +0000" stop="20240315220000 +0000" channel="a">
                <title>Haber &amp; Yorum</title>
              </programme>
            </tv>
            """.trimIndent(),
        )
        assertEquals("Haber & Yorum", programmes.single().title)
    }

    @Test
    fun `a truncated document keeps everything read so far`() {
        val (programmes, _) = parse(
            """
            <tv>
              <programme start="20240315210000 +0000" stop="20240315220000 +0000" channel="a">
                <title>Complete</title>
              </programme>
              <programme start="20240315220000 +0000" stop="20240315230000 +0000" chann
            """.trimIndent(),
        )
        // A download cut short must not throw away the megabytes that did arrive.
        assertEquals(listOf("Complete"), programmes.map { it.title })
    }

    @Test
    fun `an empty document yields nothing rather than failing`() {
        val (programmes, stats) = parse("<tv></tv>")
        assertTrue(programmes.isEmpty())
        assertEquals(0, stats.parsed)
        assertEquals(0, stats.skipped)
    }

    @Test
    fun `optional fields are null rather than blank`() {
        val (programmes, _) = parse(
            """
            <tv>
              <programme start="20240315210000 +0000" stop="20240315220000 +0000" channel="a">
                <title>Only a title</title>
                <desc>   </desc>
              </programme>
            </tv>
            """.trimIndent(),
        )
        val programme = programmes.single()
        assertNull(programme.description)
        assertNull(programme.category)
        assertNull(programme.episode)
    }
}
