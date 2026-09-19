package com.sza.fastmediasorter.wear.domain.tourist

/**
 * S3007: unified immutable state representing real-time navigation and sensor telemetry for Wear OS.
 */
data class WearTouristState(
    val focusedMetric: TouristMetricType = TouristMetricType.SPEED,
    val speedKmh: Float? = null,
    val maxSpeedKmh: Float = 0f,
    val avgSpeedKmh: Float = 0f,
    val altitudeMeters: Double? = null,
    val azimuthDegrees: Float? = null,
    val cardinalDirection: String? = null,
    val compassAccuracy: Int = 0,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val satelliteCount: Int? = null,
    val usedSatellites: Int? = null,
    val hasGpsFix: Boolean = false,
    val stepCount: Long = 0L,
    val tripDistanceMeters: Double = 0.0,
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
)
