package com.sza.fastmediasorter.domain.model.tourist

import com.sza.fastmediasorter.domain.model.sensors.SensorAccuracy

/**
 * S2922: state snapshot for the Tourist dashboard screen.
 */
data class TouristDashboardState(
    val focusedTile: TouristTileType = TouristTileType.SPEED,
    val speedKmh: Float? = null,
    val maxSpeedKmh: Float = 0f,
    val altitudeMeters: Double? = null,
    val azimuthDegrees: Float? = null,
    val compassAccuracy: SensorAccuracy = SensorAccuracy.UNRELIABLE,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val satellitesTotal: Int? = null,
    val satellitesUsed: Int? = null,
    val stepsCount: Long = 0L,
    val tripDistanceMeters: Double = 0.0,
    val sunriseMillis: Long? = null,
    val sunsetMillis: Long? = null,
    val isDaylight: Boolean = true,
    val hasLocationPermission: Boolean = true,
    val hasActivityRecognitionPermission: Boolean = true,
    val temperatureCelsius: Float? = null,
    val humidityPercent: Float? = null,
    val dewPointCelsius: Float? = null,
    val weatherCondition: String? = null,
)
