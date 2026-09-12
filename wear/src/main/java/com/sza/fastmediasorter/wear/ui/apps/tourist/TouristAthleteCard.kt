package com.sza.fastmediasorter.wear.ui.apps.tourist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.tourist.TouristMetricType
import com.sza.fastmediasorter.wear.domain.tourist.WearTouristState
import timber.log.Timber
import java.util.Locale

private val ATHLETE_METRICS = listOf(
    TouristMetricType.STEPS,
    TouristMetricType.TRIP_DISTANCE,
    TouristMetricType.HEART_RATE,
    TouristMetricType.SPEED,
)

/**
 * S3015: High-contrast large-digit athlete card for quick readability while running/moving.
 */
@Composable
fun TouristAthleteCard(
    state: WearTouristState,
    isMetric: Boolean,
    onSelectMetric: (TouristMetricType) -> Unit,
    onLockScreen: () -> Unit,
    onExitAthleteMode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Timber.d("S3015: tourist athlete tile active")

    val accentColor = when (state.focusedMetric) {
        TouristMetricType.HEART_RATE -> Color(0xFFFF5252)
        TouristMetricType.SPEED -> Color(0xFF00BFA5)
        TouristMetricType.STEPS -> Color(0xFFFFAB00)
        TouristMetricType.TRIP_DISTANCE -> Color(0xFF448AFF)
        else -> Color(0xFF00BFA5)
    }

    val (metricTitle, metricValue, metricUnit) = formatAthleteMetric(state, isMetric)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxSize(),
        ) {
            // Top row: metric title & exit button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = metricTitle.uppercase(Locale.getDefault()),
                    style = MaterialTheme.typography.caption2,
                    color = accentColor,
                    fontWeight = FontWeight.Bold,
                )
                CompactChip(
                    onClick = onExitAthleteMode,
                    label = { Text(stringResource(R.string.wear_tourist_dashboard_mode), fontSize = 10.sp) },
                    colors = ChipDefaults.secondaryChipColors(),
                )
            }

            // Center: Huge digits & unit
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = metricValue,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    lineHeight = 52.sp,
                )
                Text(
                    text = metricUnit,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = accentColor,
                    textAlign = TextAlign.Center,
                )
            }

            // Bottom controls: metric quick switcher pills & lock button
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ATHLETE_METRICS.forEach { metric ->
                        val isSelected = metric == state.focusedMetric
                        val shortLabel = when (metric) {
                            TouristMetricType.STEPS -> "STP"
                            TouristMetricType.TRIP_DISTANCE -> "DST"
                            TouristMetricType.HEART_RATE -> "BPM"
                            TouristMetricType.SPEED -> "SPD"
                            else -> "???"
                        }
                        CompactChip(
                            onClick = { onSelectMetric(metric) },
                            label = { Text(shortLabel, fontSize = 9.sp) },
                            colors = if (isSelected) {
                                ChipDefaults.primaryChipColors()
                            } else {
                                ChipDefaults.secondaryChipColors()
                            },
                        )
                    }
                }

                CompactChip(
                    onClick = onLockScreen,
                    label = { Text("🔒 " + stringResource(R.string.wear_tourist_lock_screen), fontSize = 11.sp) },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(0.8f),
                )
            }
        }
    }
}

private fun formatAthleteMetric(
    state: WearTouristState,
    isMetric: Boolean,
): Triple<String, String, String> {
    return when (state.focusedMetric) {
        TouristMetricType.STEPS -> {
            Triple("Steps", "${state.stepCount}", "steps")
        }
        TouristMetricType.TRIP_DISTANCE -> {
            val distMeters = state.tripDistanceMeters
            if (isMetric) {
                if (distMeters >= 1000.0) {
                    Triple("Distance", String.format(Locale.US, "%.2f", distMeters / 1000.0), "km")
                } else {
                    Triple("Distance", "${distMeters.toInt()}", "m")
                }
            } else {
                val miles = distMeters * 0.000621371
                Triple("Distance", String.format(Locale.US, "%.2f", miles), "mi")
            }
        }
        TouristMetricType.HEART_RATE -> {
            val bpm = state.heartRateBpm
            Triple("Heart Rate", bpm?.toString() ?: "--", "bpm")
        }
        TouristMetricType.SPEED -> {
            val rawSpeed = state.speedKmh
            if (rawSpeed != null) {
                if (isMetric) {
                    Triple("Speed", String.format(Locale.US, "%.1f", rawSpeed), "km/h")
                } else {
                    Triple("Speed", String.format(Locale.US, "%.1f", rawSpeed * 0.621371f), "mph")
                }
            } else {
                Triple("Speed", "--", if (isMetric) "km/h" else "mph")
            }
        }
        TouristMetricType.ALTITUDE -> {
            val rawAlt = state.altitudeMeters
            if (rawAlt != null) {
                if (isMetric) {
                    Triple("Altitude", "${rawAlt.toInt()}", "m")
                } else {
                    Triple("Altitude", "${(rawAlt * 3.28084).toInt()}", "ft")
                }
            } else {
                Triple("Altitude", "--", if (isMetric) "m" else "ft")
            }
        }
        else -> {
            Triple("Tourist", "--", "")
        }
    }
}
