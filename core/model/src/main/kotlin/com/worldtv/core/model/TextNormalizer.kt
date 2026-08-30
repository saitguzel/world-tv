package com.worldtv.core.model

import java.text.Normalizer
import java.util.Locale

/**
 * Normalises channel names for search.
 *
 * Everything here uses [Locale.ROOT] on purpose. Under a Turkish locale
 * `"I".lowercase()` yields `"ı"` and `"i".uppercase()` yields `"İ"`, so a
 * locale-sensitive fold would make "TRT 1" and "trt 1" fail to match on exactly the
 * devices this app targets most.
 */
object TextNormalizer {

    private val COMBINING_MARKS = Regex("\\p{Mn}+")
    private val NON_ALNUM = Regex("[^a-z0-9]+")

    /**
     * Folds a display name to a lowercase, accent-free, single-spaced token string
     * suitable for `LIKE '%…%'` matching.
     *
     * Turkish letters that Unicode decomposition does not strip (ı, ş, ğ) are mapped
     * explicitly before decomposition.
     */
    fun normalize(input: String): String {
        if (input.isEmpty()) return ""
        val preFolded = buildString(input.length) {
            for (ch in input) {
                when (ch) {
                    'ı', 'İ' -> append('i')
                    'ş', 'Ş' -> append('s')
                    'ğ', 'Ğ' -> append('g')
                    'ç', 'Ç' -> append('c')
                    'ö', 'Ö' -> append('o')
                    'ü', 'Ü' -> append('u')
                    'ß' -> append("ss")
                    'ø', 'Ø' -> append('o')
                    'æ', 'Æ' -> append("ae")
                    'đ', 'Đ', 'ð', 'Ð' -> append('d')
                    'ł', 'Ł' -> append('l')
                    else -> append(ch)
                }
            }
        }
        val decomposed = Normalizer.normalize(preFolded, Normalizer.Form.NFD)
        return COMBINING_MARKS.replace(decomposed, "")
            .lowercase(Locale.ROOT)
            .replace(NON_ALNUM, " ")
            .trim()
    }

    /**
     * Builds the value stored in `channels.searchText`: the channel name, its alt
     * names and its id folded into one haystack.
     */
    fun searchText(name: String, altNames: List<String>, channelId: String): String =
        buildList {
            add(normalize(name))
            altNames.forEach { add(normalize(it)) }
            add(normalize(channelId.substringBefore('.')))
        }.filter { it.isNotEmpty() }.distinct().joinToString(" ")
}
