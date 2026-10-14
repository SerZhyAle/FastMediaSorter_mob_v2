package com.sza.fastmediasorter.wear.ui.apps.tourist

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.domain.tourist.TouristMetricType
import com.sza.fastmediasorter.wear.domain.tourist.WearTouristState

private val CARD_CORNER = 12.dp
private val CARD_V_PADDING = 4.dp
private val CARD_H_PADDING = 8.dp
private val LABEL_VALUE_GAP = 4.dp
private val LAUNCH_ICON_SIZE = 14.dp

/**
 * S3007 / S3015: Secondary telemetry card. Tapping promotes it to the primary Hero card.
 */
@Composable
fun TouristSecondaryCard(
    metricType: TouristMetricType,
    state: WearTouristState,
    isMetric: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = touristMetricLabel(metricType)
    val value = touristMetricValue(metricType, state, isMetric)
    // Sized to its own content, label beside value: the tiles wrap across the screen as a cloud, and a
    // reading whose caption sits at the far edge of a full-width row has to be followed with the eye.
    // Drawn as a plain clipped Row rather than a wear.compose.material Card: that Card stretches to the
    // full width of its parent whatever width modifier it is given, so inside a FlowRow every tile took
    // a row of its own and nothing ever wrapped (measured on Galaxy Watch 7, 2026-09-17 - all five tiles
    // at x=57..423).
    // S4146: one merged node, so TalkBack reads the name, the value and its unit as one phrase.
    Row(
        modifier = modifier
            .wrapContentWidth()
            .clip(RoundedCornerShape(CARD_CORNER))
            .background(MaterialTheme.colors.surface)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "$label, $value" }
            .padding(vertical = CARD_V_PADDING, horizontal = CARD_H_PADDING),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LABEL_VALUE_GAP),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.caption2,
            color = MaterialTheme.colors.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.body2,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colors.onSurface,
        )
    }
}

/**
 * S4186: a tile that opens another program instead of promoting a metric, so it carries no value.
 * Shaped like [TouristSecondaryCard] to wrap in the same cloud.
 *
 * S4185: an optional [value] follows the label, so the counter tile shows its count before it is opened.
 */
@Composable
fun TouristLaunchCard(
    label: String,
    @DrawableRes iconRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    value: String? = null,
) {
    Row(
        modifier = modifier
            .wrapContentWidth()
            .clip(RoundedCornerShape(CARD_CORNER))
            .background(MaterialTheme.colors.surface)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = if (value == null) label else "$label, $value"
            }
            .padding(vertical = CARD_V_PADDING, horizontal = CARD_H_PADDING),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LABEL_VALUE_GAP),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(LAUNCH_ICON_SIZE),
            tint = MaterialTheme.colors.onSurface,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.caption2,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colors.onSurface,
        )
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.body2,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colors.onSurface,
            )
        }
    }
}
