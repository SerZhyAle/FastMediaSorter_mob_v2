package com.sza.fastmediasorter.wear.domain.tourist

import com.sza.fastmediasorter.wear.domain.model.WearTouristWeatherPayload

/**
 * S3007: telemetry and navigation metric types available in the Wear OS Tourist sub-program.
 *
 * S4146: declaration order is the tile order - the original ten, then the pressure-and-altitude, trip,
 * navigation and environment groups, with the same names as the phone's S4145 tiles. S4152 appends the
 * weather group, fed by the paired phone.
 */
enum class TouristMetricType {
    SPEED,
    ALTITUDE,
    COMPASS,
    COORDINATES,
    SATELLITES,
    STEPS,
    TRIP_DISTANCE,
    SUN_TIME,
    HEART_RATE,
    BODY_TEMPERATURE,
    PRESSURE,
    BAROMETRIC_ALTITUDE,
    ELEVATION_GAIN_LOSS,
    SLOPE,
    ELAPSED_TIME,
    MOVING_TIME,
    AVERAGE_PACE,
    CADENCE,
    GPS_ACCURACY,
    COURSE,
    DECLINATION,
    LEVEL,
    LIGHT,
    MAGNETIC_FIELD,
    WIND,
    FEELS_LIKE,
    HUMIDITY,
    UV_INDEX,
    PRECIPITATION_PROBABILITY,
    CLOUD_COVER,
    VISIBILITY,
}

/**
 * S4146: whether the section offers [this] metric at all. A watch without the sensor drops the tile
 * rather than showing a dash or a zero.
 *
 * S4152: a weather metric follows the same rule - offered only while the phone's answer is fresh and
 * carries that very value, so a missing source leaves no empty tile behind.
 */
fun TouristMetricType.isOffered(state: WearTouristState): Boolean = when (this) {
    TouristMetricType.PRESSURE, TouristMetricType.BAROMETRIC_ALTITUDE -> state.hasPressureSensor
    TouristMetricType.CADENCE -> state.hasStepSensor
    TouristMetricType.LEVEL -> state.hasLevelSensor
    TouristMetricType.LIGHT -> state.hasLightSensor
    TouristMetricType.MAGNETIC_FIELD -> state.hasMagneticSensor
    TouristMetricType.BODY_TEMPERATURE -> state.hasBodyTemperatureSensor
    TouristMetricType.WIND,
    TouristMetricType.FEELS_LIKE,
    TouristMetricType.HUMIDITY,
    TouristMetricType.UV_INDEX,
    TouristMetricType.PRECIPITATION_PROBABILITY,
    TouristMetricType.CLOUD_COVER,
    TouristMetricType.VISIBILITY,
    -> state.weather?.let { weatherValue(it) } != null
    TouristMetricType.SPEED,
    TouristMetricType.ALTITUDE,
    TouristMetricType.COMPASS,
    TouristMetricType.COORDINATES,
    TouristMetricType.SATELLITES,
    TouristMetricType.STEPS,
    TouristMetricType.TRIP_DISTANCE,
    TouristMetricType.SUN_TIME,
    TouristMetricType.HEART_RATE,
    TouristMetricType.ELEVATION_GAIN_LOSS,
    TouristMetricType.SLOPE,
    TouristMetricType.ELAPSED_TIME,
    TouristMetricType.MOVING_TIME,
    TouristMetricType.AVERAGE_PACE,
    TouristMetricType.GPS_ACCURACY,
    TouristMetricType.COURSE,
    TouristMetricType.DECLINATION,
    -> true
}

/** S4152: the headline value of a weather metric in [weather], or null for a non-weather metric or a gap. */
fun TouristMetricType.weatherValue(weather: WearTouristWeatherPayload): Number? = when (this) {
    TouristMetricType.WIND -> weather.windSpeedKmh
    TouristMetricType.FEELS_LIKE -> weather.apparentTemperatureCelsius
    TouristMetricType.HUMIDITY -> weather.humidityPercent
    TouristMetricType.UV_INDEX -> weather.uvIndex
    TouristMetricType.PRECIPITATION_PROBABILITY -> weather.precipitationProbabilityPercent
    TouristMetricType.CLOUD_COVER -> weather.cloudCoverPercent
    TouristMetricType.VISIBILITY -> weather.visibilityMetres
    else -> null
}
