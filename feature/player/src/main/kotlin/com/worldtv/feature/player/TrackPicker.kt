package com.worldtv.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusGroup
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.worldtv.core.designsystem.theme.WorldTvColors
import com.worldtv.core.model.MediaTrack
import androidx.compose.ui.res.stringResource
import com.worldtv.feature.player.R

/**
 * Subtitle and audio track picker.
 *
 * Both lists in one panel rather than two screens: on a remote, every extra level of
 * navigation is several presses, and a viewer changing the subtitle language is
 * usually the same viewer who wants a different audio track.
 */
@Composable
fun TrackPicker(
    subtitleTracks: List<MediaTrack>,
    audioTracks: List<MediaTrack>,
    onSelect: (MediaTrack) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(
        modifier
            .width(420.dp)
            .fillMaxHeight()
            .background(WorldTvColors.Scrim)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LazyColumn(
            Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .focusGroup()
                // Same wall as the channel drawer: without it, RIGHT walks focus onto
                // the video surface behind the panel and the user cannot get back.
                .focusProperties { right = FocusRequester.Cancel },
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (subtitleTracks.isNotEmpty()) {
                item { SectionLabel(stringResource(R.string.tracks_subtitles)) }
                items(subtitleTracks, key = { "text-" + it.id }) { track ->
                    TrackRow(track, onSelect)
                }
            }
            // Only shown when there is a genuine choice; a single audio track is not
            // a decision worth putting in front of anyone.
            if (audioTracks.size > 1) {
                item { SectionLabel(stringResource(R.string.tracks_audio)) }
                items(audioTracks, key = { "audio-" + it.id }) { track ->
                    TrackRow(track, onSelect)
                }
            }
            if (subtitleTracks.isEmpty() && audioTracks.size <= 1) {
                item {
                    Text(
                        text = stringResource(R.string.tracks_none),
                        style = MaterialTheme.typography.bodyLarge,
                        color = WorldTvColors.OnSurfaceMuted,
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackRow(track: MediaTrack, onSelect: (MediaTrack) -> Unit) {
    ListItem(
        selected = track.isSelected,
        onClick = { onSelect(track) },
        headlineContent = {
            Text(track.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = track.language
            ?.takeIf { !track.isOff && !it.equals(track.label, ignoreCase = true) }
            ?.let { language -> { Text(language, color = WorldTvColors.OnSurfaceMuted) } },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = WorldTvColors.OnSurfaceMuted,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}
