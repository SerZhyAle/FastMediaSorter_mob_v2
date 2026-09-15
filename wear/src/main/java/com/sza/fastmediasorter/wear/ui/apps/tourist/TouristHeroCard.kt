package com.sza.fastmediasorter.wear.ui.apps.tourist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
private val ACCENT_COLOR = Color(0xFF00BFA5.toInt())
private val HR_COLOR = Color(0xFFFF5252.toInt())
private val GPS_FIX_COLOR = Color(0xFF00E676.toInt())
private val SUNSET_COLOR = Color(0xFFFF8A80.toInt())

private const val KMH_TO_MPH = 0.621371f
private const val METERS_TO_FEET = 3.28084
private const val METERS_TO_MILES = 0.000621371
private const val METERS_PER_KM = 1000.0

/**
 * S3007 / S3015: Hero card displaying the primary active telemetry metric in large focal typography.
 */
@Composable
fun TouristHeroCard(
    state: WearTouristState,
    isMetric: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colors.surface)
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when (state.focusedMetric) {
                TouristMetricType.SPEED -> HeroSpeedView(state, isMetric)
                TouristMetricType.ALTITUDE -> HeroAltitudeView(state, isMetric)
                TouristMetricType.COMPASS -> HeroCompassView(state)
                TouristMetricType.COORDINATES -> HeroCoordinatesView(state)
                TouristMetricType.SATELLITES -> HeroSatellitesView(state)
                TouristMetricType.STEPS -> HeroStepsView(state)
                TouristMetricType.TRIP_DISTANCE -> HeroTripDistanceView(state, isMetric)
                TouristMetricType.SUN_TIME -> HeroSunTimeView(state)
                TouristMetricType.HEART_RATE -> HeroHeartRateView(state)
            }
        }
    }
}

@Composable
private fun HeroSpeedView(state: WearTouristState, isMetric: Boolean) {
    Text(
        text = stringResource(R.string.wear_tourist_metric_speed),
        style = MaterialTheme.typography.caption2,
        color = ACCENT_COLOR,
    )
    val rawSpeed = state.speedKmh
    val (speedVal, unit) = if (rawSpeed != null) {
        if (isMetric) {
            Pair(String.format(Locale.US, "%.1f", rawSpeed), stringResource(R.string.wear_tourist_unit_kmh))
        } else {
            Pair(
                String.format(Locale.US, "%.1f", rawSpeed * KMH_TO_MPH),
                stringResource(R.string.wear_tourist_unit_mph),
            )
        }
    } else {
        val fallbackUnit = if (isMetric) {
            stringResource(R.string.wear_tourist_unit_kmh)
        } else {
            stringResource(R.string.wear_tourist_unit_mph)
        }
        Pair("--", fallbackUnit)
    }
    Text(
        text = speedVal,
        fontSize = 32.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
    )
    Text(
        text = unit,
        style = MaterialTheme.typography.caption2,
        color = Color.Gray,
    )
    val maxVal = if (isMetric) state.maxSpeedKmh else state.maxSpeedKmh * KMH_TO_MPH
    Text(
        text = "Max: ${String.format(Locale.US, "%.1f", maxVal)} $unit",
        style = MaterialTheme.typography.caption3,
        color = Color.LightGray,
    )
}

@Composable
private fun HeroAltitudeView(state: WearTouristState, isMetric: Boolean) {
    Text(
        text = stringResource(R.string.wear_tourist_metric_altitude),
        style = MaterialTheme.typography.caption2,
        color = ACCENT_COLOR,
    )
    val rawAlt = state.altitudeMeters
    val (altVal, unit) = if (rawAlt != null) {
        if (isMetric) {
            Pair(rawAlt.toInt().toString(), stringResource(R.string.wear_tourist_unit_meters))
        } else {
            Pair((rawAlt * METERS_TO_FEET).toInt().toString(), stringResource(R.string.wear_tourist_unit_feet))
        }
    } else {
        val fallbackUnit = if (isMetric) {
            stringResource(R.string.wear_tourist_unit_meters)
        } else {
            stringResource(R.string.wear_tourist_unit_feet)
        }
        Pair("--", fallbackUnit)
    }
    Text(
        text = altVal,
        fontSize = 32.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
    )
    Text(
        text = unit,
        style = MaterialTheme.typography.caption2,
        color = Color.Gray,
    )
}

@Composable
private fun HeroCompassView(state: WearTouristState) {
    Text(
        text = stringResource(R.string.wear_tourist_metric_compass),
        style = MaterialTheme.typography.caption2,
        color = ACCENT_COLOR,
    )
    val azimuth = state.azimuthDegrees
    val heading = azimuth?.toInt()?.toString() ?: "--"
    val cardinal = state.cardinalDirection ?: ""
    Text(
        text = "$heading° $cardinal",
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
    )
    Text(
        text = if (state.hasCompassSensor) "Heading" else stringResource(R.string.wear_tourist_sensor_unavailable),
        style = MaterialTheme.typography.caption2,
        color = Color.Gray,
    )
}

@Composable
private fun HeroCoordinatesView(state: WearTouristState) {
    Text(
        text = stringResource(R.string.wear_tourist_metric_coordinates),
        style = MaterialTheme.typography.caption2,
        color = ACCENT_COLOR,
    )
    val lat = state.latitude
    val lon = state.longitude
    if (lat != null && lon != null) {
        Text(
            text = String.format(Locale.US, "%.4f°", lat),
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
        )
        Text(
            text = String.format(Locale.US, "%.4f°", lon),
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
        )
    } else {
        Text(
            text = stringResource(R.string.wear_tourist_no_fix),
            fontSize = 16.sp,
            color = Color.Gray,
        )
    }
}

@Composable
private fun HeroSatellitesView(state: WearTouristState) {
    Text(
        text = stringResource(R.string.wear_tourist_metric_satellites),
        style = MaterialTheme.typography.caption2,
        color = ACCENT_COLOR,
    )
    val used = state.usedSatellites ?: 0
    val total = state.satelliteCount ?: 0
    Text(
        text = "$used / $total",
        fontSize = 32.sp,
        fontWeight = FontWeight.Bold,
        color = if (state.hasGpsFix) GPS_FIX_COLOR else Color.White,
    )
    Text(
        text = if (state.hasGpsFix) "GNSS 3D Fix" else stringResource(R.string.wear_tourist_no_fix),
        style = MaterialTheme.typography.caption2,
        color = Color.Gray,
    )
}

@Composable
private fun HeroStepsView(state: WearTouristState) {
    Text(
        text = stringResource(R.string.wear_tourist_metric_steps),
        style = MaterialTheme.typography.caption2,
        color = ACCENT_COLOR,
    )
    Text(
        text = "${state.stepCount}",
        fontSize = 32.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
    )
    Text(
        text = stringResource(R.string.wear_tourist_metric_steps),
        style = MaterialTheme.typography.caption2,
        color = Color.Gray,
    )
}

@Composable
private fun HeroTripDistanceView(state: WearTouristState, isMetric: Boolean) {
    Text(
        text = stringResource(R.string.wear_tourist_metric_trip),
        style = MaterialTheme.typography.caption2,
        color = ACCENT_COLOR,
    )
    val distMeters = state.tripDistanceMeters
    val (distVal, unit) = if (isMetric) {
        if (distMeters >= METERS_PER_KM) {
            Pair(
                String.format(Locale.US, "%.2f", distMeters / METERS_PER_KM),
                stringResource(R.string.wear_tourist_unit_km),
            )
        } else {
            Pair(distMeters.toInt().toString(), stringResource(R.string.wear_tourist_unit_meters))
        }
    } else {
        val miles = distMeters * METERS_TO_MILES
        Pair(String.format(Locale.US, "%.2f", miles), stringResource(R.string.wear_tourist_unit_miles))
    }
    Text(
        text = distVal,
        fontSize = 32.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
    )
    Text(
        text = unit,
        style = MaterialTheme.typography.caption2,
        color = Color.Gray,
    )
}

@Composable
private fun HeroSunTimeView(state: WearTouristState) {
    Text(
        text = stringResource(R.string.wear_tourist_metric_sun),
        style = MaterialTheme.typography.caption2,
        color = ACCENT_COLOR,
    )
    val sunriseStr = state.sunriseMillis?.let {
        TIME_FORMATTER.format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()))
    } ?: "--:--"
    val sunsetStr = state.sunsetMillis?.let {
        TIME_FORMATTER.format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()))
    } ?: "--:--"
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "☀️ " + stringResource(R.string.wear_tourist_sunrise),
                style = MaterialTheme.typography.caption3,
                color = Color.Yellow,
            )
            Text(sunriseStr, style = MaterialTheme.typography.body1, fontWeight = FontWeight.Bold)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "🌙 " + stringResource(R.string.wear_tourist_sunset),
                style = MaterialTheme.typography.caption3,
                color = SUNSET_COLOR,
            )
            Text(sunsetStr, style = MaterialTheme.typography.body1, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun HeroHeartRateView(state: WearTouristState) {
    Text(
        text = stringResource(R.string.wear_tourist_metric_heart_rate),
        style = MaterialTheme.typography.caption2,
        color = HR_COLOR,
    )
    val bpm = state.heartRateBpm
    Text(
        text = bpm?.toString() ?: "--",
        fontSize = 32.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
    )
    Text(
        text = stringResource(R.string.wear_tourist_unit_bpm),
        style = MaterialTheme.typography.caption2,
        color = Color.Gray,
    )
}
