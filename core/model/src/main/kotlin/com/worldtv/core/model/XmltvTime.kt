package com.worldtv.core.model

/**
 * Parses XMLTV timestamps.
 *
 * The format is `YYYYMMDDHHMMSS` optionally followed by a `+HHMM` / `-HHMM` offset,
 * and the offset is the whole difficulty: a Turkish guide emits `+0300`, a British one
 * emits `+0100` in summer and `+0000` in winter, and some emit nothing at all. Getting
 * this wrong shifts an entire schedule by hours, which is worse than having no guide.
 *
 * Implemented by hand rather than with `java.time` because `:core:model` targets
 * minSdk 23, where `java.time` needs desugaring, and because the arithmetic here is
 * simple enough to test exhaustively.
 */
object XmltvTime {

    /**
     * @param value an XMLTV timestamp, e.g. `20240315203000 +0300`
     * @param defaultOffsetMinutes offset assumed when the timestamp carries none.
     *   Defaults to UTC: guessing the device's zone would silently shift a guide that
     *   simply forgot its offset, and UTC at least fails consistently.
     * @return epoch millis, or null when the value is not a usable timestamp
     */
    fun parse(value: String, defaultOffsetMinutes: Int = 0): Long? {
        val trimmed = value.trim()
        if (trimmed.length < 14) return null

        val digits = trimmed.take(14)
        if (!digits.all { it.isDigit() }) return null

        val year = digits.substring(0, 4).toInt()
        val month = digits.substring(4, 6).toInt()
        val day = digits.substring(6, 8).toInt()
        val hour = digits.substring(8, 10).toInt()
        val minute = digits.substring(10, 12).toInt()
        val second = digits.substring(12, 14).toInt()

        if (month !in 1..12 || day !in 1..31) return null
        if (hour !in 0..23 || minute !in 0..59 || second !in 0..60) return null

        val offsetMinutes = parseOffset(trimmed.substring(14)) ?: defaultOffsetMinutes

        val days = daysFromCivil(year, month, day)
        val utcSeconds = days * 86_400L +
            hour * 3_600L + minute * 60L + second -
            offsetMinutes * 60L
        return utcSeconds * 1_000L
    }

    /** `+0300`, `-0430`, ` +0000`, or empty. Returns null when absent or malformed. */
    internal fun parseOffset(suffix: String): Int? {
        val text = suffix.trim()
        if (text.length < 5) return null
        val sign = when (text[0]) {
            '+' -> 1
            '-' -> -1
            else -> return null
        }
        val body = text.substring(1, 5)
        if (!body.all { it.isDigit() }) return null
        val hours = body.substring(0, 2).toInt()
        val minutes = body.substring(2, 4).toInt()
        if (minutes > 59) return null
        return sign * (hours * 60 + minutes)
    }

    /**
     * Days since 1970-01-01 for a proleptic Gregorian date.
     *
     * Howard Hinnant's `days_from_civil`: shifts the year to start in March so leap
     * days land at the end of the cycle, which removes every special case except the
     * 400-year rule.
     */
    internal fun daysFromCivil(year: Int, month: Int, day: Int): Long {
        val y = (if (month <= 2) year - 1 else year).toLong()
        val era = (if (y >= 0) y else y - 399) / 400
        val yearOfEra = y - era * 400
        val monthShifted = if (month > 2) month - 3 else month + 9
        val dayOfYear = (153L * monthShifted + 2) / 5 + day - 1
        val dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
        return era * 146_097 + dayOfEra - 719_468
    }
}
