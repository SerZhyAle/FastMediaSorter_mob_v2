package com.sza.fastmediasorter.wear.ui.apps.tourist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

/**
 * S3007: Hero card displaying the primary active telemetry metric in large focal typography.
 */
@Composable
fun TouristHeroCard(
    state: WearTouristState,
    isMetric: Boolean,
    modifier: Modifier = Modifier,
) {
    val accentColor = Color(0xFF00BFA5) // Emerald/teal accent

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
                TouristMetricType.SPEED -> {
                    Text(
                        text = stringResource(R.string.wear_tourist_metric_speed),
                        style = MaterialTheme.typography.caption2,
                        color = accentColor,
                    )
                    val rawSpeed = state.speedKmh
                    val (speedVal, unit) = if (rawSpeed != null) {
                        if (isMetric) {
                            Pair(String.format(Locale.US, "%.1f", rawSpeed), stringResource(R.string.wear_tourist_unit_kmh))
                        } else {
                            Pair(String.format(Locale.US, "%.1f", rawSpeed * 0.621371f), stringResource(R.string.wear_tourist_unit_mph))
                        }
                    } else {
                        Pair("--", if (isMetric) stringResource(R.string.wear_tourist_unit_kmh) else stringResource(R.string.wear_tourist_unit_mph))
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
                    val maxVal = if (isMetric) state.maxSpeedKmh else state.maxSpeedKmh * 0.621371f
                    Text(
                        text = "Max: ${String.format(Locale.US, "%.1f", maxVal)} $unit",
                        style = MaterialTheme.typography.caption3,
                        color = Color.LightGray,
                    )
                }
                TouristMetricType.ALTITUDE -> {
                    Text(
                        text = stringResource(R.string.wear_tourist_metric_altitude),
                        style = MaterialTheme.typography.caption2,
                        color = accentColor,
                    )
                    val rawAlt = state.altitudeMeters
                    val (altVal, unit) = if (rawAlt != null) {
                        if (isMetric) {
                            Pair(rawAlt.toInt().toString(), stringResource(R.string.wear_tourist_unit_meters))
                        } else {
                            Pair((rawAlt * 3.28084).toInt().toString(), stringResource(R.string.wear_tourist_unit_feet))
                        }
                    } else {
                        Pair("--", if (isMetric) stringResource(R.string.wear_tourist_unit_meters) else stringResource(R.string.wear_tourist_unit_feet))
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
                TouristMetricType.COMPASS -> {
                    Text(
                        text = stringResource(R.string.wear_tourist_metric_compass),
                        style = MaterialTheme.typography.caption2,
                        color = accentColor,
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
                TouristMetricType.COORDINATES -> {
                    Text(
                        text = stringResource(R.string.wear_tourist_metric_coordinates),
                        style = MaterialTheme.typography.caption2,
                        color = accentColor,
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
                TouristMetricType.SATELLITES -> {
                    Text(
                        text = stringResource(R.string.wear_tourist_metric_satellites),
                        style = MaterialTheme.typography.caption2,
                        color = accentColor,
                    )
                    val used = state.usedSatellites ?: 0
                    val total = state.satelliteCount ?: 0
                    Text(
                        text = "$used / $total",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (state.hasGpsFix) Color(0xFF00E676) else Color.White,
                    )
                    Text(
                        text = if (state.hasGpsFix) "GNSS 3D Fix" else stringResource(R.string.wear_tourist_no_fix),
                        style = MaterialTheme.typography.caption2,
                        color = Color.Gray,
                    )
                }
                TouristMetricType.STEPS -> {
                    Text(
                        text = stringResource(R.string.wear_tourist_metric_steps),
                        style = MaterialTheme.typography.caption2,
                        color = accentColor,
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
                TouristMetricType.TRIP_DISTANCE -> {
                    Text(
                        text = stringResource(R.string.wear_tourist_metric_trip),
                        style = MaterialTheme.typography.caption2,
                        color = accentColor,
                    )
                    val distMeters = state.tripDistanceMeters
                    val (distVal, unit) = if (isMetric) {
                        if (distMeters >= 1000.0) {
                            Pair(String.format(Locale.US, "%.2f", distMeters / 1000.0), stringResource(R.string.wear_tourist_unit_km))
                        } else {
                            Pair(distMeters.toInt().toString(), stringResource(R.string.wear_tourist_unit_meters))
                        }
                    } else {
                        val miles = distMeters * 0.000621371
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
                TouristMetricType.SUN_TIME -> {
                    Text(
                        text = stringResource(R.string.wear_tourist_metric_sun),
                        style = MaterialTheme.typography.caption2,
                        color = accentColor,
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
                            Text("☀️ " + stringResource(R.string.wear_tourist_sunrise), style = MaterialTheme.typography.caption3, color = Color.Yellow)
                            Text(sunriseStr, style = MaterialTheme.typography.body1, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🌙 " + stringResource(R.string.wear_tourist_sunset), style = MaterialTheme.typography.caption3, color = Color(0xFFFF8A80))
                            Text(sunsetStr, style = MaterialTheme.typography.body1, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                TouristMetricType.HEART_RATE -> {
                    Text(
                        text = stringResource(R.string.wear_tourist_metric_heart_rate),
                        style = MaterialTheme.typography.caption2,
                        color = Color(0xFFFF5252),
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
            }
        }
    }
}

