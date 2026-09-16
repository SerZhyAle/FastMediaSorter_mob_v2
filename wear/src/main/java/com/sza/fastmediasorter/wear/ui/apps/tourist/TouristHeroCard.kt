package com.sza.fastmediasorter.wear.ui.apps.tourist

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.core.util.WearUnitScale
import com.sza.fastmediasorter.wear.domain.tourist.TouristMetricType
import com.sza.fastmediasorter.wear.domain.tourist.WearTouristState
import com.sza.fastmediasorter.wear.ui.common.LocalWearDateTimeFormatter
import com.sza.fastmediasorter.wear.ui.common.LocalWearUnitSystem
import timber.log.Timber
import java.util.Locale

private val ACCENT_COLOR = Color(0xFF00BFA5.toInt())
private val HR_COLOR = Color(0xFFFF5252.toInt())
private val GPS_FIX_COLOR = Color(0xFF00E676.toInt())
private val SUNSET_COLOR = Color(0xFFFF8A80.toInt())
private val LOCK_ICON_SIZE_SP = 14.sp
private val COMPASS_HEADING_SIZE_SP = 20.sp
private val NEEDLE_SIZE = 64.dp
private val NEEDLE_NORTH_COLOR = Color(0xFFE53935.toInt())
private val NEEDLE_SOUTH_COLOR = Color(0xFF9E9E9E.toInt())
private const val NEEDLE_BASE_FRACTION = 0.22f
private const val NEEDLE_PIVOT_FRACTION = 0.10f

/**
 * S3007 / S3015: Hero card displaying the primary active telemetry metric in large focal typography.
 */
@Composable
fun TouristHeroCard(
    state: WearTouristState,
    isMetric: Boolean,
    isScreenLocked: Boolean,
    onLockScreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lockDescription = stringResource(R.string.wear_tourist_lock_screen)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colors.surface)
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        // The lock rides on the reading itself, so the panel the owner is watching carries its own
        // control; while locked it disappears, since only a hardware key ends the lock.
        if (!isScreenLocked) {
            Text(
                text = "🔒",
                fontSize = LOCK_ICON_SIZE_SP,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clickable(onClick = onLockScreen)
                    .semantics { contentDescription = lockDescription },
            )
        }
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
                TouristMetricType.BODY_TEMPERATURE -> HeroBodyTemperatureView(state)
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
                String.format(Locale.US, "%.1f", WearUnitScale.kmhToMph(rawSpeed.toDouble())),
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
    val maxVal = if (isMetric) {
        state.maxSpeedKmh.toDouble()
    } else {
        WearUnitScale.kmhToMph(state.maxSpeedKmh.toDouble())
    }
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
            Pair(
                WearUnitScale.metresToFeet(rawAlt).toInt().toString(),
                stringResource(R.string.wear_tourist_unit_feet),
            )
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
    CompassNeedle(azimuthDegrees = azimuth ?: 0f)
    Text(
        text = "$heading° $cardinal",
        fontSize = COMPASS_HEADING_SIZE_SP,
        fontWeight = FontWeight.Bold,
        color = Color.White,
    )
    if (!state.hasCompassSensor) {
        Text(
            text = stringResource(R.string.wear_tourist_sensor_unavailable),
            style = MaterialTheme.typography.caption2,
            color = Color.Gray,
        )
    }
}

/**
 * The needle is rotated against the azimuth, the way the phone's launcher widget rotates its compass
 * rose: a bare heading number carries no direction at the glance this screen is read in.
 */
@Composable
private fun CompassNeedle(azimuthDegrees: Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(NEEDLE_SIZE)) {
        val centre = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 2f
        val halfBase = radius * NEEDLE_BASE_FRACTION
        rotate(degrees = -azimuthDegrees, pivot = centre) {
            drawPath(
                path = Path().apply {
                    moveTo(centre.x, centre.y - radius)
                    lineTo(centre.x - halfBase, centre.y)
                    lineTo(centre.x + halfBase, centre.y)
                    close()
                },
                color = NEEDLE_NORTH_COLOR,
            )
            drawPath(
                path = Path().apply {
                    moveTo(centre.x, centre.y + radius)
                    lineTo(centre.x - halfBase, centre.y)
                    lineTo(centre.x + halfBase, centre.y)
                    close()
                },
                color = NEEDLE_SOUTH_COLOR,
            )
        }
        drawCircle(color = Color.White, radius = radius * NEEDLE_PIVOT_FRACTION, center = centre)
    }
}

@Composable
private fun HeroBodyTemperatureView(state: WearTouristState) {
    Text(
        text = stringResource(R.string.wear_tourist_metric_body_temperature),
        style = MaterialTheme.typography.caption2,
        color = ACCENT_COLOR,
    )
    Text(
        text = state.bodyTemperatureCelsius?.let { String.format(Locale.US, "%.1f", it) } ?: "--",
        fontSize = 32.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
    )
    Text(
        text = stringResource(R.string.wear_tourist_unit_celsius),
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
    // Below the large unit the reading switches to the small one, or a short walk reads as a zero.
    val (distVal, unit) = if (isMetric) {
        val kilometres = WearUnitScale.metresToKilometres(distMeters)
        if (kilometres >= 1.0) {
            Pair(
                String.format(Locale.US, "%.2f", kilometres),
                stringResource(R.string.wear_tourist_unit_km),
            )
        } else {
            Pair(distMeters.toInt().toString(), stringResource(R.string.wear_tourist_unit_meters))
        }
    } else {
        val miles = WearUnitScale.metresToMiles(distMeters)
        if (miles >= 1.0) {
            Pair(String.format(Locale.US, "%.2f", miles), stringResource(R.string.wear_tourist_unit_miles))
        } else {
            Pair(
                WearUnitScale.metresToFeet(distMeters).toInt().toString(),
                stringResource(R.string.wear_tourist_unit_feet),
            )
        }
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
    // The clock length and the AM/PM marker are the watch seam's decision, so this delegates to it.
    val formatter = LocalWearDateTimeFormatter.current
    val system = LocalWearUnitSystem.current
    Timber.d("S3101: watch sun time rendering under $system")
    val sunriseStr = state.sunriseMillis?.let { formatter.formatTime(it, system) } ?: "--:--"
    val sunsetStr = state.sunsetMillis?.let { formatter.formatTime(it, system) } ?: "--:--"
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
