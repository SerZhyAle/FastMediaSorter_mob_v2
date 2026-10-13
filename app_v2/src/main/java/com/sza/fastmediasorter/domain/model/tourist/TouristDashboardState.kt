package com.sza.fastmediasorter.domain.model.tourist

import com.sza.fastmediasorter.domain.model.sensors.SensorAccuracy
import com.sza.fastmediasorter.domain.model.sensors.SensorCapability
import com.sza.fastmediasorter.domain.model.weather.WeatherCondition

/**
 * S2922: state snapshot for the Tourist dashboard screen.
 *
 * S4145: the newer readings are grouped by tile family; every value a sensor or the provider may not
 * supply is nullable, so a tile can tell "no reading" from zero.
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
    val stepsAvailable: Boolean = false,
    val tripDistanceMeters: Double = 0.0,
    val sunriseMillis: Long? = null,
    val sunsetMillis: Long? = null,
    val isDaylight: Boolean = true,
    val hasLocationPermission: Boolean = true,
    val hasActivityRecognitionPermission: Boolean = true,
    val temperatureCelsius: Float? = null,
    val dewPointCelsius: Float? = null,
    val weatherCondition: WeatherCondition? = null,
    val trip: TripStats = TripStats(),
    val pressure: PressureStats = PressureStats(),
    val navigation: NavigationStats = NavigationStats(),
    val environment: EnvironmentStats = EnvironmentStats(),
    val weatherDetails: TouristWeatherDetails = TouristWeatherDetails(),
    val availableCapabilities: Set<SensorCapability> = emptySet(),
    val watch: WatchHealthStats = WatchHealthStats(),
    val counterValue: Long = 0L,
)

/**
 * S4150: the paired watch's readings. [offered] is the build's answer, independent of any watch, so the
 * tiles stay in the grid and explain themselves when no fresh value arrived.
 */
data class WatchHealthStats(
    val offered: Boolean = false,
    val heartRateBpm: Int? = null,
    val bodyTemperatureCelsius: Float? = null,
)

data class TripStats(
    val elapsedMillis: Long? = null,
    val movingMillis: Long = 0L,
    val gainMeters: Double = 0.0,
    val lossMeters: Double = 0.0,
    val elevationSource: ElevationSource? = null,
    val averageSpeedKmh: Float? = null,
    val slopePercent: Float? = null,
    val cadenceStepsPerMinute: Int? = null,
)

data class PressureStats(
    val hectopascals: Float? = null,
    val trend: PressureTrendResult? = null,
    val baroAltitudeMeters: Double? = null,
    val baroApproximate: Boolean = true,
)

data class NavigationStats(
    val gpsAccuracyMeters: Float? = null,
    val gpsGrade: GpsAccuracyGrade? = null,
    val courseDegrees: Float? = null,
    val declinationDegrees: Float? = null,
    val trueAzimuthDegrees: Float? = null,
    val pitchDegrees: Float? = null,
    val rollDegrees: Float? = null,
)

data class EnvironmentStats(
    val lux: Float? = null,
    val magneticMicrotesla: Float? = null,
    val magneticInterference: Boolean = false,
)

/** Temperatures in Celsius like the rest of the state; wind in km/h, visibility in metres (ADR-3). */
data class TouristWeatherDetails(
    val apparentTemperatureCelsius: Float? = null,
    val humidityPercent: Int? = null,
    val windSpeedKmh: Double? = null,
    val windGustsKmh: Double? = null,
    val windDirectionDegrees: Int? = null,
    val uvIndex: Double? = null,
    val precipitationProbabilityPercent: Int? = null,
    val cloudCoverPercent: Int? = null,
    val visibilityMetres: Double? = null,
)
