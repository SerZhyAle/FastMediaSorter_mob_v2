package com.sza.fastmediasorter.wear.ui.apps.motionmonitor.history

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
import com.sza.fastmediasorter.wear.domain.model.ActivityIntensity
import com.sza.fastmediasorter.wear.domain.model.MotionHistoryEntry

private const val MAX_CHART_POINTS = 8
private const val BASELINE_GOAL_5K = 5000f
private const val BASELINE_GOAL_10K = 10000f
private const val MIN_CHART_Y = 0f
private const val MAX_CHART_Y = 25000f
private const val DEFAULT_MIN_STEPS = 0f
private const val DEFAULT_MAX_STEPS = 12000f
private const val BASELINE_PAD = 1000f
private const val CANVAS_PAD_Y = 8f
private const val DASH_ON = 6f
private const val DASH_OFF = 6f
private const val BASELINE_STROKE_WIDTH = 1.5f
private const val BAR_STROKE_WIDTH = 8f
private const val DOT_RADIUS = 4.5f
private const val BAR_ALPHA = 0.7f
private const val BASELINE_ALPHA = 0.5f
private const val COLOR_GOAL_5K = 0xFF4CAF50
private const val COLOR_GOAL_10K = 0xFFFF9800

private val CHART_CORNER_RADIUS = 8.dp
private val CHART_HORIZONTAL_PADDING = 8.dp
private val CHART_VERTICAL_PADDING = 4.dp
private val CHART_INNER_PADDING = 8.dp
private val CHART_HEIGHT = 80.dp

/**
 * S3014: Compact visual trend chart for recent step and activity measurements on Wear OS.
 *
 * Renders vertical bars for the last [MAX_CHART_POINTS] recordings colored by activity intensity,
 * with standard reference baselines for daily step targets (5 000 and 10 000 steps).
 */
@Composable
fun MotionTrendChart(
    entries: List<MotionHistoryEntry>,
    modifier: Modifier = Modifier
) {
    val chartDesc = stringResource(R.string.motion_trend_title)
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

            val minVal = (recentEntries.minOfOrNull { it.steps }?.toFloat() ?: DEFAULT_MIN_STEPS)
                .coerceAtLeast(MIN_CHART_Y)
            val maxVal = (recentEntries.maxOfOrNull { it.steps }?.toFloat() ?: DEFAULT_MAX_STEPS)
                .coerceAtLeast(BASELINE_GOAL_10K + BASELINE_PAD)
                .coerceAtMost(MAX_CHART_Y)

            val range = (maxVal - minVal).coerceAtLeast(1f)
            fun yFor(value: Float): Float {
                val ratio = (value - minVal) / range
                return height - CANVAS_PAD_Y - (ratio * usableHeight)
            }

            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(DASH_ON, DASH_OFF), 0f)

            // Reference baseline: 10 000 steps (High activity goal)
            val y10k = yFor(BASELINE_GOAL_10K)
            drawLine(
                color = Color(COLOR_GOAL_10K).copy(alpha = BASELINE_ALPHA),
                start = Offset(0f, y10k),
                end = Offset(width, y10k),
                strokeWidth = BASELINE_STROKE_WIDTH,
                pathEffect = dashEffect
            )

            // Reference baseline: 5 000 steps (Baseline daily goal)
            val y5k = yFor(BASELINE_GOAL_5K)
            drawLine(
                color = Color(COLOR_GOAL_5K).copy(alpha = BASELINE_ALPHA),
                start = Offset(0f, y5k),
                end = Offset(width, y5k),
                strokeWidth = BASELINE_STROKE_WIDTH,
                pathEffect = dashEffect
            )

            if (recentEntries.isNotEmpty()) {
                val stepX = width / (recentEntries.size + 1)
                val bottomY = height - CANVAS_PAD_Y

                recentEntries.forEachIndexed { index, entry ->
                    val x = stepX * (index + 1)
                    val topY = yFor(entry.steps.toFloat())
                    val intensity = ActivityIntensity.classify(entry.steps)

                    // Vertical bar from bottom to value
                    drawLine(
                        color = intensity.color.copy(alpha = BAR_ALPHA),
                        start = Offset(x, bottomY),
                        end = Offset(x, topY),
                        strokeWidth = BAR_STROKE_WIDTH,
                        cap = StrokeCap.Round
                    )

                    // Top indicator dot
                    drawCircle(
                        color = intensity.color,
                        radius = DOT_RADIUS,
                        center = Offset(x, topY)
                    )
                }
            }
        }
    }
}
