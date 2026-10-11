package com.sza.fastmediasorter.ui.tourist.helpers

import android.content.Context
import com.sza.fastmediasorter.domain.model.tourist.TouristDashboardState
import com.sza.fastmediasorter.domain.model.tourist.TouristTileType

/**
 * S4145: the one place a [TouristTileType] becomes what the card and the grid draw.
 *
 * The `when` below has no `else` on purpose: a tile added to the enum without content breaks the build
 * here rather than showing a placeholder (strategic §5.3).
 */
class TouristTileContentManager(
    context: Context,
    formatter: TouristTileValueFormatter,
) {

    private val motion = TouristMotionTileContentBuilder(context, formatter)
    private val navigation = TouristNavigationTileContentBuilder(context, formatter)
    private val weather = TouristWeatherTileContentBuilder(context, formatter)
    private val watch = TouristWatchTileContentBuilder(context, formatter)

    @Suppress("CyclomaticComplexMethod") // S4145: one when-branch per tile; complexity tracks tile count
    fun content(tile: TouristTileType, state: TouristDashboardState): TouristTileContent = when (tile) {
        TouristTileType.SPEED -> motion.speed(state)
        TouristTileType.ALTITUDE -> motion.altitude(state)
        TouristTileType.COMPASS -> navigation.compass(state)
        TouristTileType.COORDINATES -> navigation.coordinates(state)
        TouristTileType.SATELLITES -> navigation.satellites(state)
        TouristTileType.STEPS -> motion.steps(state)
        TouristTileType.TRIP_DISTANCE -> motion.tripDistance(state)
        TouristTileType.SUN_TIME -> weather.sunTime(state)
        TouristTileType.WEATHER -> weather.weather(state)
        TouristTileType.DEW_POINT -> weather.dewPoint(state)
        TouristTileType.PRESSURE -> motion.pressure(state)
        TouristTileType.BAROMETRIC_ALTITUDE -> motion.barometricAltitude(state)
        TouristTileType.ELEVATION_GAIN_LOSS -> motion.elevationGainLoss(state)
        TouristTileType.SLOPE -> motion.slope(state)
        TouristTileType.ELAPSED_TIME -> motion.elapsedTime(state)
        TouristTileType.MOVING_TIME -> motion.movingTime(state)
        TouristTileType.AVERAGE_PACE -> motion.averagePace(state)
        TouristTileType.CADENCE -> motion.cadence(state)
        TouristTileType.GPS_ACCURACY -> navigation.gpsAccuracy(state)
        TouristTileType.COURSE -> navigation.course(state)
        TouristTileType.LEVEL -> navigation.level(state)
        TouristTileType.LIGHT -> navigation.light(state)
        TouristTileType.MAGNETIC_FIELD -> navigation.magneticField(state)
        TouristTileType.WIND -> weather.wind(state)
        TouristTileType.FEELS_LIKE -> weather.feelsLike(state)
        TouristTileType.HUMIDITY -> weather.humidity(state)
        TouristTileType.UV_INDEX -> weather.uvIndex(state)
        TouristTileType.PRECIPITATION_PROBABILITY -> weather.precipitation(state)
        TouristTileType.CLOUD_COVER -> weather.cloudCover(state)
        TouristTileType.VISIBILITY -> weather.visibility(state)
        TouristTileType.WATCH_HEART_RATE -> watch.heartRate(state)
        TouristTileType.WATCH_BODY_TEMPERATURE -> watch.bodyTemperature(state)
    }

    /** Every tile of the trip resets the whole trip; steps keep their own counter. */
    fun supportsReset(tile: TouristTileType): Boolean = tile in RESETTABLE

    private companion object {
        val RESETTABLE = setOf(
            TouristTileType.SPEED,
            TouristTileType.STEPS,
            TouristTileType.TRIP_DISTANCE,
            TouristTileType.ELEVATION_GAIN_LOSS,
            TouristTileType.ELAPSED_TIME,
            TouristTileType.MOVING_TIME,
            TouristTileType.AVERAGE_PACE,
        )
    }
}
