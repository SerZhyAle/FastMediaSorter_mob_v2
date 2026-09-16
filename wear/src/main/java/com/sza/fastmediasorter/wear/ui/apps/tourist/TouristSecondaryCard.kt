package com.sza.fastmediasorter.wear.ui.apps.tourist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
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
import com.sza.fastmediasorter.wear.core.util.WearUnitScale
import com.sza.fastmediasorter.wear.domain.tourist.TouristMetricType
import com.sza.fastmediasorter.wear.domain.tourist.WearTouristState
import com.sza.fastmediasorter.wear.ui.common.LocalWearDateTimeFormatter
import com.sza.fastmediasorter.wear.ui.common.LocalWearUnitSystem
import java.util.Locale

private val CARD_CORNER = 12.dp
private val CARD_V_PADDING = 4.dp
private val CARD_H_PADDING = 8.dp
private val LABEL_VALUE_GAP = 4.dp

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
    // Sized to its own content, label beside value: the tiles wrap across the screen as a cloud, and a
    // reading whose caption sits at the far edge of a full-width row has to be followed with the eye.
    Card(
        onClick = onClick,
        modifier = modifier.wrapContentWidth(),
        shape = RoundedCornerShape(CARD_CORNER),
        backgroundPainter = CardDefaults.cardBackgroundPainter(
            startBackgroundColor = MaterialTheme.colors.surface,
            endBackgroundColor = MaterialTheme.colors.surface,
        ),
    ) {
        Row(
            modifier = Modifier
                .wrapContentWidth()
                .padding(vertical = CARD_V_PADDING, horizontal = CARD_H_PADDING),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LABEL_VALUE_GAP),
        ) {
            Text(
                text = resolveMetricLabel(metricType),
                style = MaterialTheme.typography.caption2,
                color = Color.LightGray,
            )
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
        TouristMetricType.BODY_TEMPERATURE -> stringResource(R.string.wear_tourist_metric_body_temperature)
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
        TouristMetricType.BODY_TEMPERATURE -> formatSecondaryTemperature(state.bodyTemperatureCelsius)
    }
}

@Composable
private fun formatSecondaryTemperature(celsius: Float?): String {
    return if (celsius != null) {
        "${String.format(Locale.US, "%.1f", celsius)} ${stringResource(R.string.wear_tourist_unit_celsius)}"
    } else {
        "--"
    }
}

@Composable
private fun formatSecondarySpeed(rawSpeed: Float?, isMetric: Boolean): String {
    return if (rawSpeed != null) {
        val v = if (isMetric) rawSpeed.toDouble() else WearUnitScale.kmhToMph(rawSpeed.toDouble())
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
        val v = if (isMetric) rawAlt.toInt() else WearUnitScale.metresToFeet(rawAlt).toInt()
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

/** Below the large unit the reading switches to the small one, or a short walk reads as a zero. */
@Composable
private fun formatSecondaryDistance(distMeters: Double, isMetric: Boolean): String {
    return if (isMetric) {
        val kilometres = WearUnitScale.metresToKilometres(distMeters)
        if (kilometres >= 1.0) {
            "${String.format(Locale.US, "%.2f", kilometres)} ${stringResource(R.string.wear_tourist_unit_km)}"
        } else {
            "${distMeters.toInt()} ${stringResource(R.string.wear_tourist_unit_meters)}"
        }
    } else {
        val miles = WearUnitScale.metresToMiles(distMeters)
        if (miles >= 1.0) {
            "${String.format(Locale.US, "%.2f", miles)} ${stringResource(R.string.wear_tourist_unit_miles)}"
        } else {
            val feet = WearUnitScale.metresToFeet(distMeters).toInt()
            "$feet ${stringResource(R.string.wear_tourist_unit_feet)}"
        }
    }
}

/** The clock length and the AM/PM marker are the watch seam's decision, so this delegates to it. */
@Composable
private fun formatSecondarySunTime(sunriseMillis: Long?, sunsetMillis: Long?): String {
    val formatter = LocalWearDateTimeFormatter.current
    val system = LocalWearUnitSystem.current
    val sr = sunriseMillis?.let { formatter.formatTime(it, system) } ?: "--"
    val ss = sunsetMillis?.let { formatter.formatTime(it, system) } ?: "--"
    return "$sr / $ss"
}
