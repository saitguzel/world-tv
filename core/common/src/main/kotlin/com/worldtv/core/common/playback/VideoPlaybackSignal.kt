package com.worldtv.core.common.playback

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether a video player currently exists, and is therefore holding audio focus.
 *
 * The radio needs to know when video goes away so it can resume, but `:feature:player`
 * and `:feature:radio` are siblings that do not depend on each other. Both already
 * depend on `:core:common`, so this costs no new module edge — and unlike watching the
 * navigation route from the app shell, it exists on both form factors and is reachable
 * from a JVM test.
 *
 * A count rather than a flag: today there is one player at a time, but a preview or a
 * picture-in-picture surface would make a flag drop to false on the first release while
 * a second player still held focus.
 */
@Singleton
class VideoPlaybackSignal @Inject constructor() {

    private var holders = 0

    private val _videoActive = MutableStateFlow(false)
    val videoActive: StateFlow<Boolean> = _videoActive.asStateFlow()

    @Synchronized
    fun acquire() {
        holders += 1
        _videoActive.value = holders > 0
    }

    /** Idempotent below zero: a double release must not make the count negative. */
    @Synchronized
    fun release() {
        holders = (holders - 1).coerceAtLeast(0)
        _videoActive.value = holders > 0
    }
}
