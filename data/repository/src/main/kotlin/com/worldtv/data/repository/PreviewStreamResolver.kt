package com.worldtv.data.repository

import com.worldtv.core.model.StreamState
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Picks the stream to use for a grid preview.
 *
 * Stricter than the player's own selection on purpose: a preview is a nicety, so it
 * only ever uses a stream already known to work. Trying an unverified one would spend
 * a connection and several seconds to show the user nothing, right while they are
 * scrolling.
 */
@Singleton
class PreviewStreamResolver @Inject constructor(
    private val channelRepository: ChannelRepository,
) {
    suspend fun bestStreamUrl(channelId: String): String? =
        channelRepository.streamsFor(channelId)
            .firstOrNull { it.health.state == StreamState.OK }
            ?.url
}
