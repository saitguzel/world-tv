package com.worldtv.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class XmltvTimeTest {

    @Test
    fun `parses a UTC timestamp`() {
        // 2024-03-15T20:30:00Z
        assertEquals(1_710_534_600_000L, XmltvTime.parse("20240315203000 +0000"))
    }

    @Test
    fun `applies a positive offset`() {
        // 20:30 in +0300 is 17:30 UTC.
        assertEquals(
            XmltvTime.parse("20240315173000 +0000"),
            XmltvTime.parse("20240315203000 +0300"),
        )
    }

    @Test
    fun `applies a negative offset`() {
        // 15:30 in -0500 is 20:30 UTC.
        assertEquals(
            XmltvTime.parse("20240315203000 +0000"),
            XmltvTime.parse("20240315153000 -0500"),
        )
    }

    @Test
    fun `handles a half-hour offset`() {
        // India is +0530.
        assertEquals(
            XmltvTime.parse("20240315150000 +0000"),
            XmltvTime.parse("20240315203000 +0530"),
        )
    }

    @Test
    fun `an offset with no separating space still parses`() {
        assertEquals(
            XmltvTime.parse("20240315203000 +0300"),
            XmltvTime.parse("20240315203000+0300"),
        )
    }

    @Test
    fun `a timestamp with no offset is treated as UTC, not local time`() {
        // Guessing the device zone would silently shift a whole guide.
        assertEquals(XmltvTime.parse("20240315203000 +0000"), XmltvTime.parse("20240315203000"))
    }

    @Test
    fun `a caller-supplied default offset is used when none is present`() {
        assertEquals(
            XmltvTime.parse("20240315203000 +0300"),
            XmltvTime.parse("20240315203000", defaultOffsetMinutes = 180),
        )
    }

    @Test
    fun `rejects values that are not timestamps`() {
        assertNull(XmltvTime.parse(""))
        assertNull(XmltvTime.parse("2024031520"))
        assertNull(XmltvTime.parse("not-a-timestamp!"))
        assertNull(XmltvTime.parse("2024031A203000"))
    }

    @Test
    fun `rejects impossible field values`() {
        assertNull(XmltvTime.parse("20241315203000"), "month 13")
        assertNull(XmltvTime.parse("20240300203000"), "day 0")
        assertNull(XmltvTime.parse("20240315253000"), "hour 25")
        assertNull(XmltvTime.parse("20240315206000"), "minute 60")
    }

    @Test
    fun `a leap second is accepted rather than discarding the programme`() {
        // Guides do emit :60 occasionally; dropping the row loses a whole programme.
        // 20:30:60 rolls to 20:31:00, i.e. one minute past 20:30:00.
        assertEquals(
            XmltvTime.parse("20240315203000 +0000")!! + 60_000L,
            XmltvTime.parse("20240315203060 +0000"),
        )
    }

    @Test
    fun `a malformed offset falls back to the default rather than failing`() {
        assertEquals(XmltvTime.parse("20240315203000 +0000"), XmltvTime.parse("20240315203000 +03"))
        assertEquals(XmltvTime.parse("20240315203000 +0000"), XmltvTime.parse("20240315203000 abcd"))
        assertNull(XmltvTime.parseOffset("+0370"), "minutes above 59 are not an offset")
    }

    @Test
    fun `handles the epoch, leap years and century boundaries`() {
        assertEquals(0L, XmltvTime.parse("19700101000000 +0000"))
        // 2000 is a leap year (divisible by 400), 1900 is not (divisible by 100).
        assertEquals(0L, XmltvTime.daysFromCivil(1970, 1, 1))
        assertEquals(11_016L, XmltvTime.daysFromCivil(2000, 2, 29))
        assertEquals(-25_567L, XmltvTime.daysFromCivil(1900, 1, 1))
    }

    @Test
    fun `parses dates far past the 2038 boundary`() {
        // The result is millis in a Long, so nothing overflows.
        val parsed = XmltvTime.parse("20991231235959 +0000")
        assertEquals(4_102_444_799_000L, parsed)
    }
}
