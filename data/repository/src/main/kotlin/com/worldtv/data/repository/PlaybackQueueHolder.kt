package com.worldtv.data.repository

import com.worldtv.core.model.ChannelQueue
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The channel list the player zaps through.
 *
 * Application-scoped rather than passed through navigation: the queue is the list the
 * user was browsing (a country, a category, their favourites), which can be thousands
 * of ids. Serialising that into a nav argument is not viable, and re-deriving it in
 * the player would lose the ordering the user actually saw.
 */
@Singleton
class PlaybackQueueHolder @Inject constructor() {

    private val _queue = MutableStateFlow(ChannelQueue())
    val queue: StateFlow<ChannelQueue> = _queue.asStateFlow()

    /** Called when the user opens a channel from a list. */
    fun setQueue(channelIds: List<String>, startId: String) {
        _queue.value = ChannelQueue.of(channelIds, startId)
    }

    /** Zaps by [delta], wrapping. Returns the channel now current, or null if empty. */
    fun shift(delta: Int): String? {
        _queue.update { it.shift(delta) }
        return _queue.value.currentId
    }

    fun jumpTo(channelId: String): String? {
        _queue.update { it.jumpTo(channelId) }
        return _queue.value.currentId
    }

    /**
     * Ensures the queue at least contains the channel being played.
     *
     * Covers deep links and process death, where the player is reached without a list
     * having been browsed first — zapping then does nothing rather than crashing.
     */
    fun ensureContains(channelId: String) {
        _queue.update { current ->
            if (current.channelIds.contains(channelId)) {
                current.jumpTo(channelId)
            } else {
                ChannelQueue.of(listOf(channelId), channelId)
            }
        }
    }
}
