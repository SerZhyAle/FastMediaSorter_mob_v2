package com.sza.fastmediasorter.wear.ui.player.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.TimeText

/**
 * Share of the shorter screen edge kept clear of controls on a round display. A chord near the top
 * or bottom of a circle is far shorter than the diameter, so an overlay that reaches an edge loses
 * its ends unless it is inset in proportion to the screen instead of by a fixed dp value.
 */
private const val ROUND_OVERLAY_INSET_FRACTION = 0.10f

/** A square screen clips nothing, so the inset only has to keep content off the bezel. */
private val SQUARE_OVERLAY_INSET = 4.dp

/**
 * Common root for the three player screens: a Wear [Scaffold] that always draws [TimeText].
 *
 * Every player screen composes through here so the clock survives every player state, and so no
 * screen paints its own root background any more.
 *
 * @param positionIndicator scroll indicator for a scrolling player screen, omitted by the ones
 * whose content is full-bleed and does not scroll.
 */
@Composable
fun PlayerScaffold(
    modifier: Modifier = Modifier,
    positionIndicator: (@Composable () -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        positionIndicator = positionIndicator,
        timeText = { TimeText() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background),
            contentAlignment = Alignment.Center,
            content = content
        )
    }
}

/**
 * Padding that keeps a full-bleed overlay inside the visible area of the current screen.
 *
 * Derived from the shape and size the platform reports, never from the one watch this project
 * happens to own - a padding tuned to a single device breaks silently on the next one.
 */
@Composable
fun playerOverlayInsets(): PaddingValues {
    val configuration = LocalConfiguration.current
    return if (configuration.isScreenRound) {
        val shorterEdge = minOf(configuration.screenWidthDp, configuration.screenHeightDp).dp
        PaddingValues(shorterEdge * ROUND_OVERLAY_INSET_FRACTION)
    } else {
        PaddingValues(SQUARE_OVERLAY_INSET)
    }
}
