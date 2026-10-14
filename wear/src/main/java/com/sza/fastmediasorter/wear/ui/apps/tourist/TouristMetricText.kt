package com.sza.fastmediasorter.wear.ui.apps.tourist

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.core.util.WearUnitScale
import com.sza.fastmediasorter.wear.domain.model.WearTouristWeatherPayload
import com.sza.fastmediasorter.wear.domain.tourist.TouristMetricType
import com.sza.fastmediasorter.wear.domain.tourist.WearGpsAccuracyGrade
import com.sza.fastmediasorter.wear.domain.tourist.WearPressureTrend
import com.sza.fastmediasorter.wear.domain.tourist.WearTouristState
import com.sza.fastmediasorter.wear.domain.tourist.WearTripMotionPolicy
import com.sza.fastmediasorter.wear.ui.common.LocalWearDateTimeFormatter
import com.sza.fastmediasorter.wear.ui.common.LocalWearUnitSystem
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

private const val MINUTES_PER_HOUR = 60.0
private const val SECONDS_PER_MINUTE = 60

/**
 * S4146: the one label-and-value text of every Tourist metric, shared by the secondary tiles, the
 * generic promoted card and athlete mode. Every `when` lists every metric, so a new one cannot fall
 * through to a placeholder; a reading that has not arrived is a word, never a zero.
 */
@Suppress("CyclomaticComplexMethod") // S4146: one when-branch per metric; complexity tracks metric count
@StringRes
fun TouristMetricType.labelRes(): Int = when (this) {
    TouristMetricType.SPEED -> R.string.wear_tourist_metric_speed
    TouristMetricType.ALTITUDE -> R.string.wear_tourist_metric_altitude
    TouristMetricType.COMPASS -> R.string.wear_tourist_metric_compass
    TouristMetricType.COORDINATES -> R.string.wear_tourist_metric_coordinates
    TouristMetricType.SATELLITES -> R.string.wear_tourist_metric_satellites
    TouristMetricType.STEPS -> R.string.wear_tourist_metric_steps
    TouristMetricType.TRIP_DISTANCE -> R.string.wear_tourist_metric_trip
    TouristMetricType.SUN_TIME -> R.string.wear_tourist_metric_sun
    TouristMetricType.HEART_RATE -> R.string.wear_tourist_metric_heart_rate
    TouristMetricType.BODY_TEMPERATURE -> R.string.wear_tourist_metric_body_temperature
    TouristMetricType.PRESSURE -> R.string.wear_tourist_metric_pressure
    TouristMetricType.BAROMETRIC_ALTITUDE -> R.string.wear_tourist_metric_baro_altitude
    TouristMetricType.ELEVATION_GAIN_LOSS -> R.string.wear_tourist_metric_elevation_gain_loss
    TouristMetricType.SLOPE -> R.string.wear_tourist_metric_slope
    TouristMetricType.ELAPSED_TIME -> R.string.wear_tourist_metric_elapsed_time
    TouristMetricType.MOVING_TIME -> R.string.wear_tourist_metric_moving_time
    TouristMetricType.AVERAGE_PACE -> R.string.wear_tourist_metric_average_pace
    TouristMetricType.CADENCE -> R.string.wear_tourist_metric_cadence
    TouristMetricType.GPS_ACCURACY -> R.string.wear_tourist_metric_gps_accuracy
    TouristMetricType.COURSE -> R.string.wear_tourist_metric_course
    TouristMetricType.DECLINATION -> R.string.wear_tourist_metric_declination
    TouristMetricType.LEVEL -> R.string.wear_tourist_metric_level
    TouristMetricType.LIGHT -> R.string.wear_tourist_metric_light
    TouristMetricType.MAGNETIC_FIELD -> R.string.wear_tourist_metric_magnetic_field
    TouristMetricType.WIND -> R.string.wear_tourist_metric_wind
    TouristMetricType.FEELS_LIKE -> R.string.wear_tourist_metric_feels_like
    TouristMetricType.HUMIDITY -> R.string.wear_tourist_metric_humidity
    TouristMetricType.UV_INDEX -> R.string.wear_tourist_metric_uv_index
    TouristMetricType.PRECIPITATION_PROBABILITY -> R.string.wear_tourist_metric_precipitation
    TouristMetricType.CLOUD_COVER -> R.string.wear_tourist_metric_cloud_cover
    TouristMetricType.VISIBILITY -> R.string.wear_tourist_metric_visibility
}

@Composable
fun touristMetricLabel(metric: TouristMetricType): String = stringResource(metric.labelRes())

@Suppress("CyclomaticComplexMethod") // S4146: one when-branch per metric; complexity tracks metric count
@Composable
fun touristMetricValue(metric: TouristMetricType, state: WearTouristState, isMetric: Boolean): String =
    when (metric) {
        TouristMetricType.SPEED -> formatSpeedText(state.speedKmh, isMetric)
        TouristMetricType.ALTITUDE -> formatAltitudeText(state.altitudeMeters, isMetric, R.string.wear_tourist_no_fix)
        TouristMetricType.COMPASS -> formatCompassText(state.azimuthDegrees, state.cardinalDirection)
        TouristMetricType.COORDINATES -> formatCoordinatesText(state.latitude, state.longitude)
        TouristMetricType.SATELLITES -> "${state.usedSatellites ?: 0}/${state.satelliteCount ?: 0}"
        TouristMetricType.STEPS -> "${state.stepCount}"
        TouristMetricType.TRIP_DISTANCE -> formatDistanceText(state.tripDistanceMeters, isMetric)
        TouristMetricType.SUN_TIME -> formatSunTimeText(state.sunriseMillis, state.sunsetMillis)
        TouristMetricType.HEART_RATE -> state.heartRateBpm?.let {
            "$it ${stringResource(R.string.wear_tourist_unit_bpm)}"
        } ?: "--"
        TouristMetricType.BODY_TEMPERATURE -> formatTemperatureText(state.bodyTemperatureCelsius)
        TouristMetricType.PRESSURE -> formatPressureText(state.pressureHpa, isMetric)
        TouristMetricType.BAROMETRIC_ALTITUDE ->
            formatAltitudeText(state.baroAltitudeMeters, isMetric, R.string.wear_tourist_status_sensor_waiting)
        TouristMetricType.ELEVATION_GAIN_LOSS -> formatGainLossText(state, isMetric)
        TouristMetricType.SLOPE -> state.gradePercent?.let {
            "${String.format(Locale.US, "%.0f", it)} ${stringResource(R.string.wear_tourist_unit_percent)}"
        } ?: stringResource(R.string.wear_tourist_status_accumulating)
        TouristMetricType.ELAPSED_TIME -> state.tripStartedAtMillis?.let {
            formatDuration((System.currentTimeMillis() - it).coerceAtLeast(0L))
        } ?: stringResource(R.string.wear_tourist_status_trip_empty)
        TouristMetricType.MOVING_TIME -> if (state.tripStartedAtMillis == null) {
            stringResource(R.string.wear_tourist_status_trip_empty)
        } else {
            formatDuration(state.movingMillis)
        }
        TouristMetricType.AVERAGE_PACE -> if (state.movingMillis > 0L) {
            formatSpeedText(state.avgSpeedKmh, isMetric)
        } else {
            stringResource(R.string.wear_tourist_status_trip_empty)
        }
        TouristMetricType.CADENCE -> state.cadenceStepsPerMinute?.let {
            "$it ${stringResource(R.string.wear_tourist_unit_steps_per_minute)}"
        } ?: stringResource(R.string.wear_tourist_status_accumulating)
        TouristMetricType.GPS_ACCURACY -> formatGpsAccuracyText(state.gpsAccuracyMeters, isMetric)
        TouristMetricType.COURSE -> state.courseDegrees?.let { "${it.toInt()}°" }
            ?: stringResource(R.string.wear_tourist_status_course_undetermined)
        TouristMetricType.DECLINATION -> state.declinationDegrees?.let { String.format(Locale.US, "%+.1f°", it) }
            ?: stringResource(R.string.wear_tourist_no_fix)
        TouristMetricType.LEVEL -> formatLevelText(state.pitchDegrees, state.rollDegrees)
        TouristMetricType.LIGHT -> state.lightLux?.let {
            "${String.format(Locale.US, "%.0f", it)} ${stringResource(R.string.wear_tourist_unit_lux)}"
        } ?: stringResource(R.string.wear_tourist_status_sensor_waiting)
        TouristMetricType.MAGNETIC_FIELD -> formatMagneticText(state.magneticMicrotesla, state.magneticInterference)
        TouristMetricType.WIND,
        TouristMetricType.FEELS_LIKE,
        TouristMetricType.HUMIDITY,
        TouristMetricType.UV_INDEX,
        TouristMetricType.PRECIPITATION_PROBABILITY,
        TouristMetricType.CLOUD_COVER,
        TouristMetricType.VISIBILITY,
        -> state.weather?.let { formatWeatherText(metric, it, isMetric) } ?: "--"
    }

@Composable
fun pressureTrendWord(trend: WearPressureTrend): String = when (trend) {
    WearPressureTrend.RISING -> stringResource(R.string.wear_tourist_trend_rising)
    WearPressureTrend.STEADY -> stringResource(R.string.wear_tourist_trend_steady)
    WearPressureTrend.FALLING -> stringResource(R.string.wear_tourist_trend_falling)
}

@Composable
fun gpsAccuracyWord(grade: WearGpsAccuracyGrade): String = when (grade) {
    WearGpsAccuracyGrade.GOOD -> stringResource(R.string.wear_tourist_gps_accuracy_good)
    WearGpsAccuracyGrade.MEDIUM -> stringResource(R.string.wear_tourist_gps_accuracy_medium)
    WearGpsAccuracyGrade.POOR -> stringResource(R.string.wear_tourist_gps_accuracy_poor)
}

/** Pace in minutes per kilometre or mile, from an average speed; null when standing still. */
@Composable
fun formatPaceText(avgSpeedKmh: Float, isMetric: Boolean): String? {
    if (avgSpeedKmh < WearTripMotionPolicy.MOVING_MIN_SPEED_KMH) return null
    val speed = if (isMetric) avgSpeedKmh.toDouble() else WearUnitScale.kmhToMph(avgSpeedKmh.toDouble())
    val totalSeconds = (MINUTES_PER_HOUR / speed * SECONDS_PER_MINUTE).toInt()
    val unit = if (isMetric) {
        stringResource(R.string.wear_tourist_unit_min_per_km)
    } else {
        stringResource(R.string.wear_tourist_unit_min_per_mi)
    }
    val pace = String.format(Locale.US, "%d:%02d", totalSeconds / SECONDS_PER_MINUTE, totalSeconds % SECONDS_PER_MINUTE)
    return stringResource(R.string.wear_tourist_detail_pace, pace, unit)
}

fun formatDuration(millis: Long): String {
    val hours = TimeUnit.MILLISECONDS.toHours(millis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % SECONDS_PER_MINUTE
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % SECONDS_PER_MINUTE
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

@Composable
fun formatAltitudeText(rawAlt: Double?, isMetric: Boolean, @StringRes missingRes: Int): String {
    return if (rawAlt != null) {
        val v = if (isMetric) rawAlt.toInt() else WearUnitScale.metresToFeet(rawAlt).toInt()
        "$v ${lengthUnit(isMetric)}"
    } else {
        stringResource(missingRes)
    }
}

@Composable
fun lengthUnit(isMetric: Boolean): String = if (isMetric) {
    stringResource(R.string.wear_tourist_unit_meters)
} else {
    stringResource(R.string.wear_tourist_unit_feet)
}

@Composable
private fun formatPressureText(hpa: Float?, isMetric: Boolean): String {
    return if (hpa == null) {
        stringResource(R.string.wear_tourist_status_sensor_waiting)
    } else if (isMetric) {
        "${String.format(Locale.US, "%.0f", hpa)} ${stringResource(R.string.wear_tourist_unit_hpa)}"
    } else {
        val inHg = WearUnitScale.hpaToInHg(hpa.toDouble())
        "${String.format(Locale.US, "%.2f", inHg)} ${stringResource(R.string.wear_tourist_unit_inhg)}"
    }
}

@Composable
private fun formatGainLossText(state: WearTouristState, isMetric: Boolean): String {
    if (state.elevationSource == null) return stringResource(R.string.wear_tourist_status_accumulating)
    val scale: (Double) -> Int = { if (isMetric) it.toInt() else WearUnitScale.metresToFeet(it).toInt() }
    val unit = lengthUnit(isMetric)
    return "+${scale(state.gainMeters)} / -${scale(state.lossMeters)} $unit"
}

@Composable
private fun formatGpsAccuracyText(accuracyM: Float?, isMetric: Boolean): String {
    val grade = WearTripMotionPolicy.gpsAccuracyGrade(accuracyM)
    return if (accuracyM == null || grade == null) {
        stringResource(R.string.wear_tourist_no_fix)
    } else {
        val value = if (isMetric) accuracyM.toInt() else WearUnitScale.metresToFeet(accuracyM.toDouble()).toInt()
        "$value ${lengthUnit(isMetric)} • ${gpsAccuracyWord(grade)}"
    }
}

@Composable
private fun formatLevelText(pitch: Float?, roll: Float?): String {
    return if (pitch == null || roll == null) {
        stringResource(R.string.wear_tourist_status_sensor_waiting)
    } else {
        "${pitch.toInt()}° / ${roll.toInt()}°"
    }
}

@Composable
private fun formatMagneticText(microtesla: Float?, interference: Boolean): String {
    if (microtesla == null) return stringResource(R.string.wear_tourist_status_sensor_waiting)
    val unit = stringResource(R.string.wear_tourist_unit_microtesla)
    val value = "${String.format(Locale.US, "%.0f", microtesla)} $unit"
    return if (interference) "$value • ${stringResource(R.string.wear_tourist_magnetic_interference)}" else value
}

@Composable
private fun formatTemperatureText(celsius: Float?): String {
    return if (celsius != null) {
        "${String.format(Locale.US, "%.1f", celsius)} ${stringResource(R.string.wear_tourist_unit_celsius)}"
    } else {
        "--"
    }
}

@Composable
private fun formatSpeedText(rawSpeed: Float?, isMetric: Boolean): String {
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

private fun formatCompassText(azimuth: Float?, cardinal: String?): String {
    return if (azimuth != null) "${azimuth.toInt()}° ${cardinal ?: ""}" else "--"
}

@Composable
private fun formatCoordinatesText(lat: Double?, lon: Double?): String {
    return if (lat != null && lon != null) {
        String.format(Locale.US, "%.3f, %.3f", lat, lon)
    } else {
        stringResource(R.string.wear_tourist_no_fix)
    }
}

/** Below the large unit the reading switches to the small one, or a short walk reads as a zero. */
@Composable
private fun formatDistanceText(distMeters: Double, isMetric: Boolean): String {
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
private fun formatSunTimeText(sunriseMillis: Long?, sunsetMillis: Long?): String {
    val formatter = LocalWearDateTimeFormatter.current
    val system = LocalWearUnitSystem.current
    val sr = sunriseMillis?.let { formatter.formatTime(it, system) } ?: "--"
    val ss = sunsetMillis?.let { formatter.formatTime(it, system) } ?: "--"
    return "$sr / $ss"
}

/**
 * S4152: the value of a weather metric. Wind and visibility arrive in km/h and metres and the feels-like
 * temperature in Celsius whatever the phone shows, so the conversion follows this watch's unit system.
 */
@Composable
private fun formatWeatherText(
    metric: TouristMetricType,
    weather: WearTouristWeatherPayload,
    isMetric: Boolean,
): String {
    val percent = stringResource(R.string.wear_tourist_unit_percent)
    return when (metric) {
        TouristMetricType.WIND -> formatWindText(weather, isMetric)
        TouristMetricType.FEELS_LIKE -> weather.apparentTemperatureCelsius?.let { formatAirTemperature(it, isMetric) }
        TouristMetricType.HUMIDITY -> weather.humidityPercent?.let { "$it $percent" }
        TouristMetricType.UV_INDEX -> weather.uvIndex?.let { String.format(Locale.US, "%.1f", it) }
        TouristMetricType.PRECIPITATION_PROBABILITY -> weather.precipitationProbabilityPercent?.let { "$it $percent" }
        TouristMetricType.CLOUD_COVER -> weather.cloudCoverPercent?.let { "$it $percent" }
        TouristMetricType.VISIBILITY -> weather.visibilityMetres?.let { formatVisibilityText(it, isMetric) }
        else -> null
    } ?: "--"
}

@Composable
private fun formatWindText(weather: WearTouristWeatherPayload, isMetric: Boolean): String? {
    val speed = weather.windSpeedKmh ?: return null
    val unit = if (isMetric) {
        stringResource(R.string.wear_tourist_unit_kmh)
    } else {
        stringResource(R.string.wear_tourist_unit_mph)
    }
    val scale: (Double) -> Int = { if (isMetric) it.roundToInt() else WearUnitScale.kmhToMph(it).roundToInt() }
    val direction = weather.windDirectionDegrees?.let { " $it°" }.orEmpty()
    val headline = "${scale(speed)} $unit$direction"
    val gusts = weather.windGustsKmh
    return if (gusts == null) {
        headline
    } else {
        stringResource(R.string.wear_tourist_detail_wind_gusts, headline, "${scale(gusts)} $unit")
    }
}

@Composable
private fun formatAirTemperature(celsius: Double, isMetric: Boolean): String = if (isMetric) {
    "${celsius.roundToInt()} ${stringResource(R.string.wear_tourist_unit_celsius)}"
} else {
    "${WearUnitScale.celsiusToFahrenheit(celsius).roundToInt()} " +
        stringResource(R.string.wear_tourist_unit_fahrenheit)
}

@Composable
private fun formatVisibilityText(metres: Double, isMetric: Boolean): String = if (isMetric) {
    "${String.format(Locale.US, "%.1f", WearUnitScale.metresToKilometres(metres))} " +
        stringResource(R.string.wear_tourist_unit_km)
} else {
    "${String.format(Locale.US, "%.1f", WearUnitScale.metresToMiles(metres))} " +
        stringResource(R.string.wear_tourist_unit_miles)
}
