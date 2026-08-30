package com.worldtv.core.model

/**
 * The ordered list of channels the user is zapping through, plus where they are in it.
 *
 * Zapping needs the list the user was *looking at* — the Turkish news category, their
 * favourites — not "all channels". Carrying it as a value type keeps the wrap-around
 * and neighbour arithmetic testable without a player or a database.
 */
data class ChannelQueue(
    val channelIds: List<String> = emptyList(),
    val index: Int = 0,
) {
    val isEmpty: Boolean get() = channelIds.isEmpty()

    val currentId: String? get() = channelIds.getOrNull(index)

    val size: Int get() = channelIds.size

    /** 1-based position, for the "12 / 240" line on the channel card. */
    val position: Int get() = if (isEmpty) 0 else index + 1

    /**
     * Moves [delta] channels, wrapping at both ends.
     *
     * Wrapping rather than clamping: on a remote, holding up at the top of the list
     * and having nothing happen reads as a stuck app, and there is no scrollbar to
     * explain that the list ended.
     */
    fun shift(delta: Int): ChannelQueue {
        if (isEmpty) return this
        val next = ((index + delta) % size + size) % size
        return copy(index = next)
    }

    /** Jumps to a channel by id, leaving the queue unchanged if it is not a member. */
    fun jumpTo(channelId: String): ChannelQueue {
        val target = channelIds.indexOf(channelId)
        return if (target < 0) this else copy(index = target)
    }

    /**
     * The channels immediately either side of the current one.
     *
     * Their manifests are prefetched so an up/down press starts from a warm
     * connection — worth roughly a second of zap latency, which is the single most
     * felt number in a TV IPTV app.
     */
    fun neighbourIds(): List<String> {
        if (size <= 1) return emptyList()
        return listOf(shift(1).currentId, shift(-1).currentId)
            .filterNotNull()
            .filter { it != currentId }
            .distinct()
    }

    companion object {
        fun of(channelIds: List<String>, startId: String?): ChannelQueue {
            val start = startId?.let { channelIds.indexOf(it) }?.takeIf { it >= 0 } ?: 0
            return ChannelQueue(channelIds, start)
        }
    }
}
