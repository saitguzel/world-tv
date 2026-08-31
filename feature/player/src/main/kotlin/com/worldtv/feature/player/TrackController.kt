package com.worldtv.feature.player

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import com.worldtv.core.model.MediaTrack
import com.worldtv.core.model.TrackPreferences
import com.worldtv.core.model.TrackType

/**
 * Bridges Media3's track model to the flat list the UI shows, and back.
 *
 * Media3 exposes tracks as groups of formats with per-format support flags; the
 * overlay needs "these are the subtitle options, this one is on". The translation is
 * mechanical but easy to get subtly wrong, so it lives in one place.
 */
object TrackController {

    /** Synthetic id for the "subtitles off" entry. */
    const val OFF_ID = "__off__"

    /**
     * Whether [tracks] offers any video at all.
     *
     * Group presence, not selection: a video group that has not been selected yet is
     * still a frame on its way. Only a stream with no video group whatsoever will
     * never render one, and that is the case [PlaybackConfirmation] needs to know
     * about.
     */
    fun hasVideo(tracks: Tracks): Boolean =
        tracks.groups.any { it.type == C.TRACK_TYPE_VIDEO }

    /**
     * Flattens [tracks] into the selectable options of one type.
     *
     * Unsupported formats are dropped: offering a track the device cannot decode
     * produces a failure the user reads as the channel being broken.
     */
    fun optionsOf(
        tracks: Tracks,
        type: TrackType,
        /** Wording for a track whose language tag says nothing. Supplied by the UI. */
        unknownLabel: String,
        /** Wording for the synthetic "subtitles off" entry. Supplied by the UI. */
        offLabel: String,
    ): List<MediaTrack> {
        val c3Type = type.toC3()
        val options = mutableListOf<MediaTrack>()

        tracks.groups.forEachIndexed { groupIndex, group ->
            if (group.type != c3Type) return@forEachIndexed
            for (formatIndex in 0 until group.length) {
                if (!group.isTrackSupported(formatIndex)) continue
                val format = group.getTrackFormat(formatIndex)
                options += MediaTrack(
                    id = "$groupIndex:$formatIndex",
                    type = type,
                    language = format.language,
                    label = format.label
                        ?: TrackPreferences.labelFor(format.language)
                        ?: unknownLabel,
                    isSelected = group.isTrackSelected(formatIndex),
                )
            }
        }

        // Subtitles need an explicit off switch; audio always has to be something.
        if (type == TrackType.TEXT && options.isNotEmpty()) {
            options.add(
                0,
                MediaTrack(
                    id = OFF_ID,
                    type = TrackType.TEXT,
                    language = null,
                    label = offLabel,
                    isSelected = options.none { it.isSelected },
                    isOff = true,
                ),
            )
        }
        return options
    }

    /** Applies a selection made in the overlay. */
    fun select(player: Player, tracks: Tracks, track: MediaTrack) {
        if (track.isOff) {
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
            return
        }

        val (groupIndex, formatIndex) = track.id.split(':')
            .mapNotNull(String::toIntOrNull)
            .takeIf { it.size == 2 }
            ?: return
        val group = tracks.groups.getOrNull(groupIndex) ?: return

        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(track.type.toC3(), false)
            .setOverrideForType(
                TrackSelectionOverride(group.mediaTrackGroup, listOf(formatIndex)),
            )
            .build()
    }

    /**
     * Sets the initial preference before anything is loaded.
     *
     * Done through `setPreferredTextLanguage` rather than an override because the
     * tracks are not known yet — this is a standing instruction the selector applies
     * to whatever the stream turns out to carry.
     */
    fun applyInitialPreferences(
        player: Player,
        captionsEnabled: Boolean,
        captionLanguage: String?,
        deviceLanguage: String?,
    ) {
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setPreferredTextLanguage(captionLanguage ?: deviceLanguage)
            .setPreferredAudioLanguage(deviceLanguage)
            // Off unless the user asked for captions system-wide. Forcing subtitles on
            // for everyone is worse than leaving them off for the few who want them.
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !captionsEnabled)
            .build()
    }

    private fun TrackType.toC3(): Int = when (this) {
        TrackType.AUDIO -> C.TRACK_TYPE_AUDIO
        TrackType.TEXT -> C.TRACK_TYPE_TEXT
    }
}
