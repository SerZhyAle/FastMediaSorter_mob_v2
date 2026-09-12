package com.sza.fastmediasorter.wear.ui.apps.tourist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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

/**
 * S3007: Secondary telemetry card. Tapping promotes it to the primary Hero card.
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
                val label = when (metricType) {
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
                Text(
                    text = label,
                    style = MaterialTheme.typography.caption2,
                    color = Color.LightGray,
                )
            }

            val valueString = when (metricType) {
                TouristMetricType.SPEED -> {
                    val rawSpeed = state.speedKmh
                    if (rawSpeed != null) {
                        val v = if (isMetric) rawSpeed else rawSpeed * 0.621371f
                        val u = if (isMetric) stringResource(R.string.wear_tourist_unit_kmh) else stringResource(R.string.wear_tourist_unit_mph)
                        "${String.format(Locale.US, "%.1f", v)} $u"
                    } else "--"
                }
                TouristMetricType.ALTITUDE -> {
                    val rawAlt = state.altitudeMeters
                    if (rawAlt != null) {
                        val v = if (isMetric) rawAlt.toInt() else (rawAlt * 3.28084).toInt()
                        val u = if (isMetric) stringResource(R.string.wear_tourist_unit_meters) else stringResource(R.string.wear_tourist_unit_feet)
                        "$v $u"
                    } else "--"
                }
                TouristMetricType.COMPASS -> {
                    val az = state.azimuthDegrees
                    val card = state.cardinalDirection ?: ""
                    if (az != null) "${az.toInt()}° $card" else "--"
                }
                TouristMetricType.COORDINATES -> {
                    val lat = state.latitude
                    val lon = state.longitude
                    if (lat != null && lon != null) {
                        String.format(Locale.US, "%.3f, %.3f", lat, lon)
                    } else stringResource(R.string.wear_tourist_no_fix)
                }
                TouristMetricType.SATELLITES -> {
                    val used = state.usedSatellites ?: 0
                    val total = state.satelliteCount ?: 0
                    "$used/$total"
                }
                TouristMetricType.STEPS -> {
                    "${state.stepCount}"
                }
                TouristMetricType.TRIP_DISTANCE -> {
                    val m = state.tripDistanceMeters
                    if (isMetric) {
                        if (m >= 1000.0) "${String.format(Locale.US, "%.2f", m / 1000.0)} km" else "${m.toInt()} m"
                    } else {
                        "${String.format(Locale.US, "%.2f", m * 0.000621371)} mi"
                    }
                }
                TouristMetricType.SUN_TIME -> {
                    val sr = state.sunriseMillis?.let { TIME_FORMATTER.format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())) } ?: "--"
                    val ss = state.sunsetMillis?.let { TIME_FORMATTER.format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())) } ?: "--"
                    "$sr / $ss"
                }
                TouristMetricType.HEART_RATE -> {
                    state.heartRateBpm?.let { "$it bpm" } ?: "--"
                }
            }

            Text(
                text = valueString,
                style = MaterialTheme.typography.body2,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
    }
}

