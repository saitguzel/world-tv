package com.worldtv.core.database

import java.security.MessageDigest

/**
 * Stable primary keys for streams.
 *
 * Derived from the URL **and** the owning channel, not the URL alone: the iptv-org
 * catalog lists the same URL under several channels, and hashing only the URL would
 * silently collapse those into one row and drop every channel but the first.
 */
object StreamIdFactory {

    fun idFor(url: String, channelId: String?): String =
        sha1("${channelId.orEmpty()}|$url")

    private fun sha1(input: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(input.toByteArray())
        return buildString(digest.size * 2) {
            for (byte in digest) {
                val value = byte.toInt() and 0xFF
                append(HEX[value ushr 4])
                append(HEX[value and 0x0F])
            }
        }
    }

    private val HEX = "0123456789abcdef".toCharArray()
}
