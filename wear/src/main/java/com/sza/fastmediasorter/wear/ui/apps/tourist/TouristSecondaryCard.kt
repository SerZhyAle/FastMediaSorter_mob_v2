package com.sza.fastmediasorter.wear.ui.apps.tourist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Card
import androidx.wear.compose.material.CardDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.tourist.TouristMetricType
import com.sza.fastmediasorter.wear.domain.tourist.WearTouristState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
private const val KMH_TO_MPH = 0.621371f
private const val METERS_TO_FEET = 3.28084
private const val METERS_TO_MILES = 0.000621371
private const val METERS_PER_KM = 1000.0

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
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        backgroundPainter = CardDefaults.cardBackgroundPainter(
            startBackgroundColor = MaterialTheme.colors.surface,
            endBackgroundColor = MaterialTheme.colors.surface,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = resolveMetricLabel(metricType),
                    style = MaterialTheme.typography.caption2,
                    color = Color.LightGray,
                )
            }

            Text(
                text = resolveMetricValue(metricType, state, isMetric),
                style = MaterialTheme.typography.body2,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun resolveMetricLabel(metricType: TouristMetricType): String {
    return when (metricType) {
        TouristMetricType.SPEED -> stringResource(R.string.wear_tourist_metric_speed)
        TouristMetricType.ALTITUDE -> stringResource(R.string.wear_tourist_metric_altitude)
        TouristMetricType.COMPASS -> stringResource(R.string.wear_tourist_metric_compass)
        TouristMetricType.COORDINATES -> stringResource(R.string.wear_tourist_metric_coordinates)
        TouristMetricType.SATELLITES -> stringResource(R.string.wear_tourist_metric_satellites)
        TouristMetricType.STEPS -> stringResource(R.string.wear_tourist_metric_steps)
        TouristMetricType.TRIP_DISTANCE -> stringResource(R.string.wear_tourist_metric_trip)
        TouristMetricType.SUN_TIME -> stringResource(R.string.wear_tourist_metric_sun)
        TouristMetricType.HEART_RATE -> stringResource(R.string.wear_tourist_metric_heart_rate)
    }
}

@Composable
private fun resolveMetricValue(
    metricType: TouristMetricType,
    state: WearTouristState,
    isMetric: Boolean,
): String {
    return when (metricType) {
        TouristMetricType.SPEED -> formatSecondarySpeed(state.speedKmh, isMetric)
        TouristMetricType.ALTITUDE -> formatSecondaryAltitude(state.altitudeMeters, isMetric)
        TouristMetricType.COMPASS -> formatSecondaryCompass(state.azimuthDegrees, state.cardinalDirection)
        TouristMetricType.COORDINATES -> formatSecondaryCoordinates(state.latitude, state.longitude)
        TouristMetricType.SATELLITES -> "${state.usedSatellites ?: 0}/${state.satelliteCount ?: 0}"
        TouristMetricType.STEPS -> "${state.stepCount}"
        TouristMetricType.TRIP_DISTANCE -> formatSecondaryDistance(state.tripDistanceMeters, isMetric)
        TouristMetricType.SUN_TIME -> formatSecondarySunTime(state.sunriseMillis, state.sunsetMillis)
        TouristMetricType.HEART_RATE -> state.heartRateBpm?.let { "$it bpm" } ?: "--"
    }
}

@Composable
private fun formatSecondarySpeed(rawSpeed: Float?, isMetric: Boolean): String {
    return if (rawSpeed != null) {
        val v = if (isMetric) rawSpeed else rawSpeed * KMH_TO_MPH
        val u = if (isMetric) {
            stringResource(R.string.wear_tourist_unit_kmh)
        } else {
            stringResource(R.string.wear_tourist_unit_mph)
        }
        "${String.format(Locale.US, "%.1f", v)} $u"
    } else {
        "--"
    }
}

@Composable
private fun formatSecondaryAltitude(rawAlt: Double?, isMetric: Boolean): String {
    return if (rawAlt != null) {
        val v = if (isMetric) rawAlt.toInt() else (rawAlt * METERS_TO_FEET).toInt()
        val u = if (isMetric) {
            stringResource(R.string.wear_tourist_unit_meters)
        } else {
            stringResource(R.string.wear_tourist_unit_feet)
        }
        "$v $u"
    } else {
        "--"
    }
}

private fun formatSecondaryCompass(azimuth: Float?, cardinal: String?): String {
    return if (azimuth != null) "${azimuth.toInt()}° ${cardinal ?: ""}" else "--"
}

@Composable
private fun formatSecondaryCoordinates(lat: Double?, lon: Double?): String {
    return if (lat != null && lon != null) {
        String.format(Locale.US, "%.3f, %.3f", lat, lon)
    } else {
        stringResource(R.string.wear_tourist_no_fix)
    }
}

private fun formatSecondaryDistance(distMeters: Double, isMetric: Boolean): String {
    return if (isMetric) {
        if (distMeters >= METERS_PER_KM) {
            "${String.format(Locale.US, "%.2f", distMeters / METERS_PER_KM)} km"
        } else {
            "${distMeters.toInt()} m"
        }
    } else {
        "${String.format(Locale.US, "%.2f", distMeters * METERS_TO_MILES)} mi"
    }
}

private fun formatSecondarySunTime(sunriseMillis: Long?, sunsetMillis: Long?): String {
    val sr = sunriseMillis?.let {
        TIME_FORMATTER.format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()))
    } ?: "--"
    val ss = sunsetMillis?.let {
        TIME_FORMATTER.format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()))
    } ?: "--"
    return "$sr / $ss"
}
