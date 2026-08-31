package com.worldtv.core.designsystem.component

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.worldtv.core.designsystem.theme.WorldTvColors

/**
 * Text that belongs to neither Material.
 *
 * The shared components below are rendered under the TV theme on a television and the
 * Material 3 theme on a phone, and those are different CompositionLocal trees — a
 * `Text` from either one would read the wrong typography, or none at all, in the other.
 * Building on [BasicText] with an explicit style sidesteps the question entirely.
 *
 * Colour is a required-in-practice parameter rather than something inherited from
 * `LocalContentColor`, which matches what this codebase already does: every call site
 * passes its colour explicitly.
 */
@Composable
fun WorldTvText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = WorldTvColors.OnSurface,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    textAlign: TextAlign? = null,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = style.merge(color = color, textAlign = textAlign ?: TextAlign.Unspecified),
        maxLines = maxLines,
        overflow = overflow,
    )
}
