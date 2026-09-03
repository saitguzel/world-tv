package com.worldtv.navigation

/**
 * The destinations, shared by both navigation graphs.
 *
 * Extracted so neither NavHost owns them. The TV graph is flat by design — every
 * destination is one hop from Home, because backing out of four levels with a remote is
 * punishing — while the phone reaches the same seven places through a navigation bar.
 * The route strings are identical either way, so a deep link or an Assistant query
 * lands in the same place on both.
 */
object Routes {
    const val HOME = "home"
    const val BROWSE = "browse?country={country}&category={category}"
    const val SEARCH = "search"
    const val RADIO = "radio?station={station}"
    const val FAVORITES = "favorites"
    const val SETTINGS = "settings"
    const val PLAYER = "player/{channelId}"

    /** The browse pattern without its optional argument; the nav bar matches on this. */
    const val BROWSE_BASE = "browse"

    /** The radio pattern without its optional argument; the nav bar matches on this. */
    const val RADIO_BASE = "radio"

    /** Prefix the player uses, so chrome can be hidden without parsing arguments. */
    const val PLAYER_PREFIX = "player/"

    /**
     * The browse destination, with either filter, both, or neither.
     *
     * Arguments are appended only when present rather than passed as "null": they are
     * declared nullable with a null default, and a literal "null" in the URI would be
     * read as a country called null.
     */
    fun browse(country: String? = null, category: String? = null): String = buildString {
        append(BROWSE_BASE)
        val args = buildList {
            country?.let { add("country=$it") }
            category?.let { add("category=$it") }
        }
        if (args.isNotEmpty()) append('?').append(args.joinToString("&"))
    }

    /**
     * The radio destination. A station uuid starts it playing — that is how a search
     * result hands a station over — while a plain visit opens the usual list.
     */
    fun radio(station: String? = null): String =
        if (station == null) RADIO_BASE else "$RADIO_BASE?station=$station"

    fun player(channelId: String) = "$PLAYER_PREFIX$channelId"

    /**
     * What the phone's navigation bar shows.
     *
     * Five, deliberately: Settings is reached from Home's app bar instead. Six crowds a
     * bottom bar, and Settings is the one nobody visits twice.
     */
    val TOP_LEVEL: List<String> = listOf(HOME, BROWSE_BASE, SEARCH, RADIO_BASE, FAVORITES)

    /** True while the player is on screen — when the phone hides its chrome. */
    fun isPlayer(route: String?): Boolean = route?.startsWith(PLAYER_PREFIX) == true

    /**
     * Matches a live back-stack route against a top-level entry.
     *
     * Needed because the back stack reports `browse?country={country}` for the pattern
     * and `browse?country=TR` once an argument is supplied, while the nav bar only
     * knows `browse`. A plain equality check would leave the Browse tab unhighlighted
     * exactly when the user is browsing a country.
     */
    fun isTopLevel(route: String?, entry: String): Boolean =
        route == entry || route?.startsWith("$entry?") == true
}
