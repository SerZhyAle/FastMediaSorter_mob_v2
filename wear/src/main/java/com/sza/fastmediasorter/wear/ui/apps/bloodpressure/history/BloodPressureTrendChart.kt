package com.sza.fastmediasorter.wear.ui.apps.bloodpressure.history

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
import com.sza.fastmediasorter.wear.domain.model.BloodPressureCategory
import com.sza.fastmediasorter.wear.domain.model.BloodPressureHistoryEntry

private const val MAX_CHART_POINTS = 8
private const val BASELINE_SYS = 120f
private const val BASELINE_DIA = 80f
private const val MIN_CHART_Y = 50f
private const val MAX_CHART_Y = 200f
private const val DEFAULT_MIN_DIA = 60f
private const val DEFAULT_MAX_SYS = 140f
private const val BASELINE_PAD_SYS = 10f
private const val BASELINE_PAD_DIA = 10f
private const val CANVAS_PAD_Y = 8f
private const val DASH_ON = 6f
private const val DASH_OFF = 6f
private const val BASELINE_STROKE_WIDTH = 1.5f
private const val BAR_STROKE_WIDTH = 6f
private const val SYS_DOT_RADIUS = 4.5f
private const val DIA_DOT_RADIUS = 3.5f
private const val BAR_ALPHA = 0.7f
private const val BASELINE_ALPHA = 0.5f
private const val COLOR_NORMAL_GREEN = 0xFF4CAF50
private const val COLOR_DIA_BLUE = 0xFF42A5F5

/**
 * S3012: Compact visual trend chart for recent blood pressure measurements on Wear OS.
 *
 * Renders vertical systolic-to-diastolic measurement spans for the last [MAX_CHART_POINTS] entries,
 * with standard reference baselines at 120 mmHg and 80 mmHg.
 */
@Composable
fun BloodPressureTrendChart(
    entries: List<BloodPressureHistoryEntry>,
    modifier: Modifier = Modifier
) {
    val chartDesc = stringResource(R.string.blood_pressure_trend_title)
    val recentEntries = entries.take(MAX_CHART_POINTS).reversed()
    val surfaceColor = MaterialTheme.colors.surface

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(surfaceColor)
            .padding(8.dp)
            .semantics { contentDescription = chartDesc }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(90.dp)
        ) {
            val width = size.width
            val height = size.height
            val usableHeight = height - CANVAS_PAD_Y * 2

            val minVal = (recentEntries.minOfOrNull { it.diastolic }?.toFloat() ?: DEFAULT_MIN_DIA)
                .coerceAtMost(BASELINE_DIA - BASELINE_PAD_DIA)
                .coerceAtLeast(MIN_CHART_Y)
            val maxVal = (recentEntries.maxOfOrNull { it.systolic }?.toFloat() ?: DEFAULT_MAX_SYS)
                .coerceAtLeast(BASELINE_SYS + BASELINE_PAD_SYS)
                .coerceAtMost(MAX_CHART_Y)

            val range = (maxVal - minVal).coerceAtLeast(1f)
            fun yFor(value: Float): Float {
                val ratio = (value - minVal) / range
                return height - CANVAS_PAD_Y - (ratio * usableHeight)
            }

            // Reference baseline: 120 mmHg (Systolic limit)
            val y120 = yFor(BASELINE_SYS)
            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(DASH_ON, DASH_OFF), 0f)
            drawLine(
                color = Color(COLOR_NORMAL_GREEN).copy(alpha = BASELINE_ALPHA),
                start = Offset(0f, y120),
                end = Offset(width, y120),
                strokeWidth = BASELINE_STROKE_WIDTH,
                pathEffect = dashEffect
            )

            // Reference baseline: 80 mmHg (Diastolic limit)
            val y80 = yFor(BASELINE_DIA)
            drawLine(
                color = Color(COLOR_DIA_BLUE).copy(alpha = BASELINE_ALPHA),
                start = Offset(0f, y80),
                end = Offset(width, y80),
                strokeWidth = BASELINE_STROKE_WIDTH,
                pathEffect = dashEffect
            )

            if (recentEntries.isNotEmpty()) {
                val stepX = width / (recentEntries.size + 1)
                recentEntries.forEachIndexed { index, entry ->
                    val x = stepX * (index + 1)
                    val sysY = yFor(entry.systolic.toFloat())
                    val diaY = yFor(entry.diastolic.toFloat())

                    val cat = BloodPressureCategory.classify(entry.systolic, entry.diastolic)

                    // Vertical span between diastolic and systolic
                    drawLine(
                        color = cat.color.copy(alpha = BAR_ALPHA),
                        start = Offset(x, diaY),
                        end = Offset(x, sysY),
                        strokeWidth = BAR_STROKE_WIDTH,
                        cap = StrokeCap.Round
                    )

                    // Systolic top dot
                    drawCircle(
                        color = cat.color,
                        radius = SYS_DOT_RADIUS,
                        center = Offset(x, sysY)
                    )

                    // Diastolic bottom dot
                    drawCircle(
                        color = Color(COLOR_DIA_BLUE),
                        radius = DIA_DOT_RADIUS,
                        center = Offset(x, diaY)
                    )
                }
            }
        }
    }
}
