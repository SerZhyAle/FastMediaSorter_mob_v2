package com.sza.fastmediasorter.ui.tourist.helpers

import android.content.Context
import com.sza.fastmediasorter.domain.model.tourist.TouristDashboardState
import com.sza.fastmediasorter.domain.model.tourist.TouristTileType
import java.util.Locale

/**
 * S4145: the one place a [TouristTileType] becomes what the card and the grid draw.
 *
 * The `when` below has no `else` on purpose: a tile added to the enum without content breaks the build
 * here rather than showing a placeholder (strategic §5.3).
 */
class TouristTileContentManager(
    private val context: Context,
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
        TouristTileType.STOPWATCH -> stopwatch(state)
        TouristTileType.COUNTER -> counter(state)
    }

    private fun counter(state: TouristDashboardState): TouristTileContent = tileContent(
        context = context,
        iconRes = com.sza.fastmediasorter.R.drawable.ic_add,
        labelRes = com.sza.fastmediasorter.R.string.tourist_tile_counter,
        reading = TouristTileValueFormatter.TileValue(state.counterValue.toString(), ""),
        detail = context.getString(com.sza.fastmediasorter.R.string.tourist_detail_counter),
        backgroundRes = com.sza.fastmediasorter.R.color.tourist_tile_bg_group_trip,
    )

    /**
     * S4202: the tile is itself the primitive stopwatch, so its reading is the live elapsed time rather
     * than a launch hint. Seconds and minutes are fixed width, so the digits do not slide sideways
     * between ticks; the width changes only at the hour edge, which a reader sees at most once per run.
     */
    private fun stopwatch(state: TouristDashboardState): TouristTileContent = tileContent(
        context = context,
        iconRes = com.sza.fastmediasorter.R.drawable.ic_stopwatch,
        labelRes = com.sza.fastmediasorter.R.string.tourist_tile_stopwatch,
        reading = TouristTileValueFormatter.TileValue(
            formatStopwatchElapsed(state.stopwatchElapsedMillis),
            "",
        ),
        detail = context.getString(com.sza.fastmediasorter.R.string.tourist_detail_stopwatch),
        backgroundRes = com.sza.fastmediasorter.R.color.tourist_tile_bg_group_trip,
    )

    /** `M:SS` below an hour, `H:MM:SS` from an hour up; hours are never wrapped into days. */
    private fun formatStopwatchElapsed(elapsedMillis: Long): String {
        val safeMillis = elapsedMillis.coerceAtLeast(0L)
        val totalSeconds = safeMillis / MILLIS_PER_SECOND
        val seconds = totalSeconds % SECONDS_PER_MINUTE
        val totalMinutes = totalSeconds / SECONDS_PER_MINUTE
        val hours = totalMinutes / MINUTES_PER_HOUR
        return if (hours > 0L) {
            String.format(Locale.US, FORMAT_WITH_HOURS, hours, totalMinutes % MINUTES_PER_HOUR, seconds)
        } else {
            String.format(Locale.US, FORMAT_MINUTES, totalMinutes, seconds)
        }
    }

    /** Every tile of the trip resets the whole trip; steps keep their own counter. */
    fun supportsReset(tile: TouristTileType): Boolean = tile in RESETTABLE

    private companion object {
        const val MILLIS_PER_SECOND = 1000L
        const val SECONDS_PER_MINUTE = 60L
        const val MINUTES_PER_HOUR = 60L

        const val FORMAT_MINUTES = "%d:%02d"
        const val FORMAT_WITH_HOURS = "%d:%02d:%02d"

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
