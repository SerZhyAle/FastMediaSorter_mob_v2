package com.sza.fastmediasorter.wear.ui.player.common

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme

private val RING_STROKE = 2.dp

/**
 * Draws the playing position as a ring around the control it wraps.
 *
 * S2766: below the compact-screen breakpoint the player has no room for a separate time and seek
 * row - its column asks for about 161.5 dp of a 138 dp budget at 192 dp, and the row is what closes
 * the gap - so the position rides the main control instead, which is where Google's media layout
 * puts it.
 *
 * The ring is drawn at the wrapped control's own size and adds no measurement of its own, so a
 * 48 dp touch target inside it is still measured as 48 dp.
 *
 * The indicator carries no description of its own: the button it encloses already announces the
 * position, and a second announcement on every tick is noise rather than information.
 */
@Composable
fun PlayerProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
    ) {
        CircularProgressIndicator(
            progress = progress.coerceIn(0f, 1f),
            strokeWidth = RING_STROKE,
            indicatorColor = MaterialTheme.colors.primary,
            trackColor = MaterialTheme.colors.onSurfaceVariant,
            modifier = Modifier
                .matchParentSize()
                .clearAndSetSemantics { }
        )
        content()
    }
}
