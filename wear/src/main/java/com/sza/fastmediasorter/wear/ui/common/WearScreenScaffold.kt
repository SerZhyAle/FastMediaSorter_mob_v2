package com.sza.fastmediasorter.wear.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.scrollAway

/**
 * Share of the shorter screen edge kept clear of controls on a round display. A chord near the top
 * or bottom of a circle is far shorter than the diameter, so an overlay that reaches an edge loses
 * its ends unless it is inset in proportion to the screen instead of by a fixed dp value.
 */
private const val ROUND_INSET_FRACTION = 0.10f

/** A square screen clips nothing, so the inset only has to keep content off the bezel. */
private val SQUARE_INSET = 4.dp

/**
 * Share of the shorter edge a square may take before its corners leave a round display.
 *
 * The largest square inscribed in a circle has a side of the diameter divided by the square root of
 * two, about 0.707; 0.70 keeps a margin under that. [ROUND_INSET_FRACTION] alone is not enough for a
 * square: it leaves a content box of 0.8 of the diameter, whose half-diagonal is 0.566 against a
 * glass radius of 0.5, so every corner is outside the display (S2008).
 */
private const val ROUND_SQUARE_FRACTION = 0.70f

/**
 * Common root for every screen in the module: a Wear [Scaffold] that always draws [TimeText].
 *
 * Every screen composes through here so the clock survives every state, and so no screen paints its
 * own root background.
 *
 * @param positionIndicator scroll indicator for a scrolling screen, omitted by the ones whose
 * content is full-bleed and does not scroll.
 * @param scrollState the scrolling list state when content can move beneath the clock. The clock
 * scrolls away with the list so stationary content is never obscured after a scroll.
 * @param showTimeText false only for the screen-off mode of S1683, where a lit clock would be the one
 * thing still drawn on a screen the user asked to go dark.
 * @param contentPadding defaults to the round-safe inset, so a screen added later is safe without
 * asking for it. Two cases pass `PaddingValues(0.dp)` instead: a full-bleed screen, which wants no
 * inset at all, and a scrolling screen, which hands [wearScreenInsets] to its own list's
 * `contentPadding` so items scroll under the rim rather than being clipped by a padded viewport.
 */
@Composable
fun WearScreenScaffold(
    modifier: Modifier = Modifier,
    positionIndicator: (@Composable () -> Unit)? = null,
    scrollState: ScalingLazyListState? = null,
    showTimeText: Boolean = true,
    contentPadding: PaddingValues = wearScreenInsets(),
    content: @Composable BoxScope.() -> Unit
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        positionIndicator = positionIndicator,
        timeText = if (showTimeText) {
            { TimeText(modifier = if (scrollState == null) Modifier else Modifier.scrollAway(scrollState)) }
        } else {
            null
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Background before padding: the theme colour has to reach the frame edge, while the
                // content stops short of it. The reverse order leaves an unpainted ring at the rim.
                .background(MaterialTheme.colors.background)
                .padding(contentPadding),
            contentAlignment = Alignment.Center,
            content = content
        )
    }
}

/**
 * Padding that keeps content inside the visible area of the current screen.
 *
 * Derived from the shape and size the platform reports, never from the one watch this project
 * happens to own - a padding tuned to a single device breaks silently on the next one.
 */
@Composable
fun wearScreenInsets(): PaddingValues {
    val configuration = LocalConfiguration.current
    return if (configuration.isScreenRound) {
        val shorterEdge = minOf(configuration.screenWidthDp, configuration.screenHeightDp).dp
        PaddingValues(shorterEdge * ROUND_INSET_FRACTION)
    } else {
        PaddingValues(SQUARE_INSET)
    }
}

/**
 * Side of the largest square this display can draw whole.
 *
 * The module's second statement about screen shape, kept beside the first so the two are read
 * together: [wearScreenInsets] insets a rectangle proportionally, which is right for text and rows,
 * and is not enough for a square, whose corners reach further from the centre than its edges do. A
 * screen drawing a square - a game board, a grid, a dial - caps it with this rather than inventing a
 * fraction of its own.
 */
@Composable
fun wearMaxSquareSide(): Dp {
    val configuration = LocalConfiguration.current
    val shorterEdge = minOf(configuration.screenWidthDp, configuration.screenHeightDp).dp
    return if (configuration.isScreenRound) {
        shorterEdge * ROUND_SQUARE_FRACTION
    } else {
        shorterEdge - SQUARE_INSET * 2
    }
}
