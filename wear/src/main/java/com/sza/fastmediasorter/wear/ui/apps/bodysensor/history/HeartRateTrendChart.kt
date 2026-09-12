package com.sza.fastmediasorter.wear.ui.apps.bodysensor.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.MaterialTheme
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.HeartRateHistoryEntry
import com.sza.fastmediasorter.wear.domain.model.HeartRateZone

private const val MAX_CHART_POINTS = 8
private const val BASELINE_LOW_BPM = 60f
private const val BASELINE_HIGH_BPM = 100f
private const val MIN_CHART_Y = 40f
private const val MAX_CHART_Y = 200f
private const val DEFAULT_MIN_BPM = 50f
private const val DEFAULT_MAX_BPM = 120f
private const val BASELINE_PAD = 10f
private const val CANVAS_PAD_Y = 8f
private const val DASH_ON = 6f
private const val DASH_OFF = 6f
private const val BASELINE_STROKE_WIDTH = 1.5f
private const val BAR_STROKE_WIDTH = 8f
private const val DOT_RADIUS = 4.5f
private const val BAR_ALPHA = 0.7f
private const val BASELINE_ALPHA = 0.5f
private const val COLOR_NORMAL_GREEN = 0xFF4CAF50
private const val COLOR_LOW_BLUE = 0xFF42A5F5

private val CHART_CORNER_RADIUS = 8.dp
private val CHART_HORIZONTAL_PADDING = 8.dp
private val CHART_VERTICAL_PADDING = 4.dp
private val CHART_INNER_PADDING = 8.dp
private val CHART_HEIGHT = 80.dp

/**
 * S3013: Compact visual trend chart for recent heart rate measurements on Wear OS.
 *
 * Renders vertical bars for the last [MAX_CHART_POINTS] readings colored by heart rate zone,
 * with standard reference baselines for normal resting heart rate (60 - 100 BPM).
 */
@Composable
fun HeartRateTrendChart(
    entries: List<HeartRateHistoryEntry>,
    modifier: Modifier = Modifier
) {
    val chartDesc = stringResource(R.string.heart_rate_trend_title)
    val recentEntries = entries.take(MAX_CHART_POINTS).reversed()
    val surfaceColor = MaterialTheme.colors.surface

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = CHART_HORIZONTAL_PADDING, vertical = CHART_VERTICAL_PADDING)
            .clip(RoundedCornerShape(CHART_CORNER_RADIUS))
            .background(surfaceColor)
            .padding(CHART_INNER_PADDING)
            .semantics { contentDescription = chartDesc }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(CHART_HEIGHT)
        ) {
            val width = size.width
            val height = size.height
            val usableHeight = height - CANVAS_PAD_Y * 2

            val minVal = (recentEntries.minOfOrNull { it.bpm }?.toFloat() ?: DEFAULT_MIN_BPM)
                .coerceAtMost(BASELINE_LOW_BPM - BASELINE_PAD)
                .coerceAtLeast(MIN_CHART_Y)
            val maxVal = (recentEntries.maxOfOrNull { it.bpm }?.toFloat() ?: DEFAULT_MAX_BPM)
                .coerceAtLeast(BASELINE_HIGH_BPM + BASELINE_PAD)
                .coerceAtMost(MAX_CHART_Y)

            val range = (maxVal - minVal).coerceAtLeast(1f)
            fun yFor(value: Float): Float {
                val ratio = (value - minVal) / range
                return height - CANVAS_PAD_Y - (ratio * usableHeight)
            }

            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(DASH_ON, DASH_OFF), 0f)

            // Reference baseline: 100 BPM (Upper limit for normal resting heart rate)
            val y100 = yFor(BASELINE_HIGH_BPM)
            drawLine(
                color = Color(COLOR_NORMAL_GREEN).copy(alpha = BASELINE_ALPHA),
                start = Offset(0f, y100),
                end = Offset(width, y100),
                strokeWidth = BASELINE_STROKE_WIDTH,
                pathEffect = dashEffect
            )

            // Reference baseline: 60 BPM (Lower limit for normal resting heart rate)
            val y60 = yFor(BASELINE_LOW_BPM)
            drawLine(
                color = Color(COLOR_LOW_BLUE).copy(alpha = BASELINE_ALPHA),
                start = Offset(0f, y60),
                end = Offset(width, y60),
                strokeWidth = BASELINE_STROKE_WIDTH,
                pathEffect = dashEffect
            )

            if (recentEntries.isNotEmpty()) {
                val stepX = width / (recentEntries.size + 1)
                val bottomY = height - CANVAS_PAD_Y

                recentEntries.forEachIndexed { index, entry ->
                    val x = stepX * (index + 1)
                    val topY = yFor(entry.bpm.toFloat())
                    val zone = HeartRateZone.classify(entry.bpm)

                    // Vertical bar from bottom to value
                    drawLine(
                        color = zone.color.copy(alpha = BAR_ALPHA),
                        start = Offset(x, bottomY),
                        end = Offset(x, topY),
                        strokeWidth = BAR_STROKE_WIDTH,
                        cap = StrokeCap.Round
                    )

                    // Top indicator dot
                    drawCircle(
                        color = zone.color,
                        radius = DOT_RADIUS,
                        center = Offset(x, topY)
                    )
                }
            }
        }
    }
}
