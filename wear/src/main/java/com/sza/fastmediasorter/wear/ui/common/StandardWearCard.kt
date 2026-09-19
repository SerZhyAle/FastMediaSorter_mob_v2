package com.sza.fastmediasorter.wear.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Card
import androidx.wear.compose.material.CardDefaults
import androidx.wear.compose.material.MaterialTheme

/** Secondary card radius of the watch module (`WEAR_UI_COMPONENT_PATTERNS.md` section 1.4). */
internal val WearCardCorner: Dp = 12.dp

/** Hero card radius: the primary telemetry and sensor summary panels. */
internal val WearHeroCorner: Dp = 16.dp

internal val WearCardShape: Shape = RoundedCornerShape(WearCardCorner)

internal val WearHeroShape: Shape = RoundedCornerShape(WearHeroCorner)

private val CARD_CONTENT_PADDING = PaddingValues(horizontal = 10.dp, vertical = 8.dp)

private val HEADER_BOTTOM_PADDING = 4.dp

private val BADGE_START_PADDING = 6.dp

/**
 * The standard card container of the watch module, drawn with the geometry fixed by
 * `docs/ui/WEAR_UI_COMPONENT_PATTERNS.md` section 2.2.
 *
 * Built on the library [Card] so focus scaling, touch geometry and click semantics come from Wear
 * Material instead of a hand-rolled `Box + clip + background`, which is what every telemetry panel
 * used before S3261. The gradient the library defaults to is flattened to a single
 * [MaterialTheme.colors.surface] tone, because the project's panels are read as flat surfaces and a
 * diagonal gradient under a focal digit reduces its contrast at the far corner.
 *
 * A card with no [onClick] is drawn disabled rather than clickable, so a purely informational panel
 * carries no click role for TalkBack and no ripple.
 */
@Composable
fun StandardWearCard(
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    title: (@Composable () -> Unit)? = null,
    timeOrBadge: (@Composable () -> Unit)? = null,
    cornerShape: Shape = WearCardShape,
    content: @Composable ColumnScope.() -> Unit
) {
    val surface = MaterialTheme.colors.surface
    Card(
        onClick = onClick ?: {},
        enabled = onClick != null,
        // The library defaults content to onSurfaceVariant, which is the caption tone; a panel drawn
        // on surface reads its values in onSurface, so a caller that sets no colour gets the reading
        // tone rather than the caption one.
        contentColor = MaterialTheme.colors.onSurface,
        modifier = modifier.fillMaxWidth(),
        shape = cornerShape,
        backgroundPainter = CardDefaults.cardBackgroundPainter(
            startBackgroundColor = surface,
            endBackgroundColor = surface
        ),
        contentPadding = CARD_CONTENT_PADDING
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (title != null || timeOrBadge != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = HEADER_BOTTOM_PADDING),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f, fill = false)) {
                        title?.invoke()
                    }
                    if (timeOrBadge != null) {
                        Box(modifier = Modifier.padding(start = BADGE_START_PADDING)) {
                            timeOrBadge()
                        }
                    }
                }
            }
            content()
        }
    }
}
