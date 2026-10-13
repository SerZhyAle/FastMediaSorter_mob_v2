package com.sza.fastmediasorter.domain.model.tourist

import com.sza.fastmediasorter.domain.model.sensors.SensorCapability

/**
 * S2922: available telemetry and navigation tiles on the Tourist dashboard.
 *
 * S4145: declaration order is the grid order - the original ten, then the pressure, trip, navigation,
 * environment and weather groups. Names are persisted as the focused tile and kept by R8 (S3076), so a
 * value is never renamed. [requiredCapability] names the hardware without which the tile is not offered.
 *
 * S4150: a [fromWatch] tile reads the paired watch rather than this phone; it is offered by the build's
 * watch-health capability, never by local hardware, and appended last so no persisted name moves.
 */
enum class TouristTileType(
    val requiredCapability: SensorCapability? = null,
    val fromWatch: Boolean = false,
) {
    SPEED,
    ALTITUDE,
    COMPASS,
    COORDINATES,
    SATELLITES,
    STEPS(SensorCapability.STEP_COUNTER),
    TRIP_DISTANCE,
    SUN_TIME,
    WEATHER,
    DEW_POINT,
    PRESSURE(SensorCapability.BAROMETER),
    BAROMETRIC_ALTITUDE(SensorCapability.BAROMETER),
    ELEVATION_GAIN_LOSS,
    SLOPE,
    ELAPSED_TIME,
    MOVING_TIME,
    AVERAGE_PACE,
    CADENCE(SensorCapability.STEP_COUNTER),
    GPS_ACCURACY,
    COURSE,
    LEVEL(SensorCapability.LEVEL),
    LIGHT(SensorCapability.LIGHT),
    MAGNETIC_FIELD(SensorCapability.MAGNETOMETER),
    WIND,
    FEELS_LIKE,
    HUMIDITY,
    UV_INDEX,
    PRECIPITATION_PROBABILITY,
    CLOUD_COVER,
    VISIBILITY,
    WATCH_HEART_RATE(fromWatch = true),
    WATCH_BODY_TEMPERATURE(fromWatch = true),
    STOPWATCH,
    COUNTER,
}

/**
 * S4145: whether the dashboard offers [this] tile at all. The step counter keeps reading `stepsAvailable`,
 * which also folds in the activity-recognition declaration of the flavor.
 */
fun TouristTileType.isOffered(state: TouristDashboardState): Boolean = when {
    fromWatch -> state.watch.offered
    requiredCapability == null -> true
    requiredCapability == SensorCapability.STEP_COUNTER -> state.stepsAvailable
    else -> requiredCapability in state.availableCapabilities
}
