package com.worldtv.core.designsystem.tv.focus

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Keeps the focused item at a fixed point on screen while content scrolls underneath.
 *
 * This is the behaviour every TV UI has — the focused card sits about 30% in from the
 * leading edge instead of drifting to whichever side the user is heading. It replaces
 * `TvLazyRow(pivotOffsets = …)`, which no longer exists: `tv-foundation`'s `TvLazy*`
 * containers were removed and their behaviour folded into the standard `Lazy*` ones.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun rememberTvPivotSpec(pivotFraction: Float = 0.3f): BringIntoViewSpec =
    remember(pivotFraction) {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(
                offset: Float,
                size: Float,
                containerSize: Float,
            ): Float {
                val target = containerSize * pivotFraction
                return offset - target
            }
        }
    }
