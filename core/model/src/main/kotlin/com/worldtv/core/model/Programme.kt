package com.worldtv.core.model

/** One entry in a channel's schedule. */
data class Programme(
    val channelId: String,
    /** Epoch millis, UTC. XMLTV timestamps carry their own offset; see `XmltvParser`. */
    val startAt: Long,
    val endAt: Long,
    val title: String,
    val description: String? = null,
    val category: String? = null,
    /** Original episode/season marker, when the guide supplies one. */
    val episode: String? = null,
) {
    val durationMillis: Long get() = endAt - startAt

    fun isOnAt(instant: Long): Boolean = instant in startAt until endAt

    /**
     * How far through the programme [instant] is, 0..1.
     *
     * Clamped rather than allowed to run past 1: guides routinely disagree with
     * reality by a few minutes, and a progress bar that overflows looks broken.
     */
    fun progressAt(instant: Long): Float {
        if (durationMillis <= 0) return 0f
        return ((instant - startAt).toFloat() / durationMillis).coerceIn(0f, 1f)
    }
}

/** What a channel is showing now and next. */
data class NowNext(val now: Programme?, val next: Programme?)
