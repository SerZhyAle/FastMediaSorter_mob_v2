package com.sza.fastmediasorter.wear.domain.tourist

import com.sza.fastmediasorter.wear.domain.model.WearTouristWeatherPayload

/**
 * S3007: unified immutable state representing real-time navigation and sensor telemetry for Wear OS.
 *
 * S4146: [altitudeMeters] is the GPS altitude only - the barometric one is [baroAltitudeMeters], and the
 * two never overwrite each other. The trip fields mirror `WearTouristTripTracker`'s snapshot.
 *
 * S4152: [weather] is the phone's answer while it is young enough to show, and null otherwise.
 */
data class WearTouristState(
    val focusedMetric: TouristMetricType = TouristMetricType.SPEED,
    val speedKmh: Float? = null,
    val maxSpeedKmh: Float = 0f,
    val avgSpeedKmh: Float = 0f,
    val altitudeMeters: Double? = null,
    val gpsAltitudeIsEllipsoid: Boolean = false,
    val azimuthDegrees: Float? = null,
    val cardinalDirection: String? = null,
    val compassAccuracy: Int = 0,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val satelliteCount: Int? = null,
    val usedSatellites: Int? = null,
    val hasGpsFix: Boolean = false,
    val gpsAccuracyMeters: Float? = null,
    val courseDegrees: Float? = null,
    val declinationDegrees: Float? = null,
    val stepCount: Long = 0L,
    val cadenceStepsPerMinute: Int? = null,
    val tripDistanceMeters: Double = 0.0,
    val tripStartedAtMillis: Long? = null,
    val movingMillis: Long = 0L,
    val gainMeters: Double = 0.0,
    val lossMeters: Double = 0.0,
    val elevationSource: WearElevationSource? = null,
    val gradePercent: Float? = null,
    val pressureHpa: Float? = null,
    val pressureTrend: WearPressureTrendResult? = null,
    val baroAltitudeMeters: Double? = null,
    val baroAltitudeApproximate: Boolean = true,
    val pitchDegrees: Float? = null,
    val rollDegrees: Float? = null,
    val lightLux: Float? = null,
    val magneticMicrotesla: Float? = null,
    val magneticInterference: Boolean = false,
    val heartRateBpm: Int? = null,
    val bodyTemperatureCelsius: Float? = null,
    val sunriseMillis: Long? = null,
    val sunsetMillis: Long? = null,
    val isDaylight: Boolean = true,
    val hasLocationPermission: Boolean = false,
    val hasCompassSensor: Boolean = true,
    val hasPressureSensor: Boolean = true,
    val hasStepSensor: Boolean = true,
    val hasHeartRateSensor: Boolean = true,
    val hasBodyTemperatureSensor: Boolean = false,
    val hasLightSensor: Boolean = false,
    val hasMagneticSensor: Boolean = false,
    val hasLevelSensor: Boolean = false,
    val weather: WearTouristWeatherPayload? = null,
)
