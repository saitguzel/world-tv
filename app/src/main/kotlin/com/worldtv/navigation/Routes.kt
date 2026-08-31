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
    const val BROWSE = "browse?country={country}"
    const val SEARCH = "search"
    const val RADIO = "radio"
    const val FAVORITES = "favorites"
    const val SETTINGS = "settings"
    const val PLAYER = "player/{channelId}"

    /** The browse pattern without its optional argument; the nav bar matches on this. */
    const val BROWSE_BASE = "browse"

    /** Prefix the player uses, so chrome can be hidden without parsing arguments. */
    const val PLAYER_PREFIX = "player/"

    fun browse(country: String? = null): String =
        if (country == null) BROWSE_BASE else "$BROWSE_BASE?country=$country"

    fun player(channelId: String) = "$PLAYER_PREFIX$channelId"

    /**
     * What the phone's navigation bar shows.
     *
     * Five, deliberately: Settings is reached from Home's app bar instead. Six crowds a
     * bottom bar, and Settings is the one nobody visits twice.
     */
    val TOP_LEVEL: List<String> = listOf(HOME, BROWSE_BASE, SEARCH, RADIO, FAVORITES)

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
