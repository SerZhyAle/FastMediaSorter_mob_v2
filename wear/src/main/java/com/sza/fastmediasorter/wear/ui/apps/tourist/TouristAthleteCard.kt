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

private val COLOR_HR = Color(0xFFFF5252.toInt())
private val COLOR_SPEED = Color(0xFF00BFA5.toInt())
private val COLOR_STEPS = Color(0xFFFFAB00.toInt())
private val COLOR_DIST = Color(0xFF448AFF.toInt())

private val FOCAL_DIGITS_SIZE_SP = 48.sp
private val FOCAL_LINE_HEIGHT_SP = 52.sp
private val UNIT_SIZE_SP = 14.sp
private val CHIP_LABEL_SIZE_SP = 9.sp
private val LOCK_CHIP_SIZE_SP = 11.sp
private const val LOCK_BUTTON_WIDTH_FRACTION = 0.8f

private const val KMH_TO_MPH = 0.621371f
private const val METERS_TO_FEET = 3.28084
private const val METERS_TO_MILES = 0.000621371
private const val METERS_PER_KM = 1000.0

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

    val accentColor = when (state.focusedMetric) {
        TouristMetricType.HEART_RATE -> COLOR_HR
        TouristMetricType.SPEED -> COLOR_SPEED
        TouristMetricType.STEPS -> COLOR_STEPS
        TouristMetricType.TRIP_DISTANCE -> COLOR_DIST
        else -> COLOR_SPEED
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
            AthleteTopBar(
                title = metricTitle,
                accentColor = accentColor,
                onExit = onExitAthleteMode,
            )
            AthleteFocalDisplay(
                value = metricValue,
                unit = metricUnit,
                accentColor = accentColor,
            )
            AthleteControls(
                focusedMetric = state.focusedMetric,
                onSelectMetric = onSelectMetric,
                onLock = onLockScreen,
            )
        }
    }
}

@Composable
private fun AthleteTopBar(
    title: String,
    accentColor: Color,
    onExit: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title.uppercase(Locale.getDefault()),
            style = MaterialTheme.typography.caption2,
            color = accentColor,
            fontWeight = FontWeight.Bold,
        )
        CompactChip(
            onClick = onExit,
            label = { Text(stringResource(R.string.wear_tourist_dashboard_mode), fontSize = CHIP_LABEL_SIZE_SP) },
            colors = ChipDefaults.secondaryChipColors(),
        )
    }
}

@Composable
private fun AthleteFocalDisplay(
    value: String,
    unit: String,
    accentColor: Color,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = value,
            fontSize = FOCAL_DIGITS_SIZE_SP,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            textAlign = TextAlign.Center,
            lineHeight = FOCAL_LINE_HEIGHT_SP,
        )
        Text(
            text = unit,
            fontSize = UNIT_SIZE_SP,
            fontWeight = FontWeight.Medium,
            color = accentColor,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AthleteControls(
    focusedMetric: TouristMetricType,
    onSelectMetric: (TouristMetricType) -> Unit,
    onLock: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ATHLETE_METRICS.forEach { metric ->
                val isSelected = metric == focusedMetric
                val shortLabel = when (metric) {
                    TouristMetricType.STEPS -> "STP"
                    TouristMetricType.TRIP_DISTANCE -> "DST"
                    TouristMetricType.HEART_RATE -> "BPM"
                    TouristMetricType.SPEED -> "SPD"
                    else -> "???"
                }
                CompactChip(
                    onClick = { onSelectMetric(metric) },
                    label = { Text(shortLabel, fontSize = CHIP_LABEL_SIZE_SP) },
                    colors = if (isSelected) {
                        ChipDefaults.primaryChipColors()
                    } else {
                        ChipDefaults.secondaryChipColors()
                    },
                )
            }
        }

        CompactChip(
            onClick = onLock,
            label = { Text("🔒 " + stringResource(R.string.wear_tourist_lock_screen), fontSize = LOCK_CHIP_SIZE_SP) },
            colors = ChipDefaults.secondaryChipColors(),
            modifier = Modifier.fillMaxWidth(LOCK_BUTTON_WIDTH_FRACTION),
        )
    }
}

private fun formatAthleteMetric(
    state: WearTouristState,
    isMetric: Boolean,
): Triple<String, String, String> {
    return when (state.focusedMetric) {
        TouristMetricType.STEPS -> Triple("Steps", "${state.stepCount}", "steps")
        TouristMetricType.TRIP_DISTANCE -> formatAthleteDistance(state.tripDistanceMeters, isMetric)
        TouristMetricType.HEART_RATE -> Triple("Heart Rate", state.heartRateBpm?.toString() ?: "--", "bpm")
        TouristMetricType.SPEED -> formatAthleteSpeed(state.speedKmh, isMetric)
        TouristMetricType.ALTITUDE -> formatAthleteAltitude(state.altitudeMeters, isMetric)
        else -> Triple("Tourist", "--", "")
    }
}

private fun formatAthleteDistance(distMeters: Double, isMetric: Boolean): Triple<String, String, String> {
    return if (isMetric) {
        if (distMeters >= METERS_PER_KM) {
            Triple("Distance", String.format(Locale.US, "%.2f", distMeters / METERS_PER_KM), "km")
        } else {
            Triple("Distance", "${distMeters.toInt()}", "m")
        }
    } else {
        val miles = distMeters * METERS_TO_MILES
        Triple("Distance", String.format(Locale.US, "%.2f", miles), "mi")
    }
}

private fun formatAthleteSpeed(rawSpeed: Float?, isMetric: Boolean): Triple<String, String, String> {
    return if (rawSpeed != null) {
        if (isMetric) {
            Triple("Speed", String.format(Locale.US, "%.1f", rawSpeed), "km/h")
        } else {
            Triple("Speed", String.format(Locale.US, "%.1f", rawSpeed * KMH_TO_MPH), "mph")
        }
    } else {
        Triple("Speed", "--", if (isMetric) "km/h" else "mph")
    }
}

private fun formatAthleteAltitude(rawAlt: Double?, isMetric: Boolean): Triple<String, String, String> {
    return if (rawAlt != null) {
        if (isMetric) {
            Triple("Altitude", "${rawAlt.toInt()}", "m")
        } else {
            Triple("Altitude", "${(rawAlt * METERS_TO_FEET).toInt()}", "ft")
        }
    } else {
        Triple("Altitude", "--", if (isMetric) "m" else "ft")
    }
}
