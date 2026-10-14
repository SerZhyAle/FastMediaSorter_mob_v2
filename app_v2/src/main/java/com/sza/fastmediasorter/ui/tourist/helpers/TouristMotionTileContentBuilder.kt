package com.sza.fastmediasorter.ui.tourist.helpers

import android.content.Context
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.tourist.PressureTrend
import com.sza.fastmediasorter.domain.model.tourist.PressureTrendResult
import com.sza.fastmediasorter.domain.model.tourist.TouristDashboardState
import com.sza.fastmediasorter.ui.tourist.helpers.TouristTileValueFormatter.Companion.NO_VALUE
import com.sza.fastmediasorter.ui.tourist.helpers.TouristTileValueFormatter.TileValue

/**
 * S4145: content of the speed, altitude, steps, trip, pressure and elevation tiles.
 *
 * The original four keep the lines they had before S4145; the newer ones say in words when a reading
 * is still missing, never a bare zero (strategic §3.1.2).
 */
class TouristMotionTileContentBuilder(
    private val context: Context,
    private val formatter: TouristTileValueFormatter,
) {

    fun speed(state: TouristDashboardState): TouristTileContent = tileContent(
        context,
        R.drawable.ic_speed,
        R.string.tourist_tile_speed,
        formatter.speed(state.speedKmh),
        formatter.speedDetail(state.maxSpeedKmh, state.tripDistanceMeters),
        R.color.tourist_tile_bg_speed,
    )

    fun altitude(state: TouristDashboardState): TouristTileContent = tileContent(
        context,
        R.drawable.ic_altitude,
        R.string.tourist_tile_altitude,
        formatter.altitude(state.altitudeMeters),
        string(if (state.altitudeMeters != null) R.string.tourist_detail_altitude else R.string.tourist_status_no_fix),
        R.color.tourist_tile_bg_altitude,
    )

    fun steps(state: TouristDashboardState): TouristTileContent = tileContent(
        context,
        R.drawable.ic_steps,
        R.string.tourist_tile_steps,
        TileValue(state.stepsCount.toString(), string(R.string.tourist_unit_steps)),
        string(R.string.tourist_detail_steps),
        R.color.tourist_tile_bg_steps,
    )

    fun tripDistance(state: TouristDashboardState): TouristTileContent = tileContent(
        context,
        R.drawable.ic_route_distance,
        R.string.tourist_tile_trip_distance,
        formatter.tripDistance(state.tripDistanceMeters),
        string(R.string.tourist_detail_trip_distance),
        R.color.tourist_tile_bg_trip_distance,
    )

    fun pressure(state: TouristDashboardState): TouristTileContent {
        val trend = state.pressure.trend
        val detail = when {
            state.pressure.hectopascals == null -> string(R.string.tourist_status_sensor_waiting)
            trend is PressureTrendResult.Ready -> formatter.trend(trend.trend)
            else -> string(R.string.tourist_status_accumulating)
        }
        val visual = if (trend is PressureTrendResult.Ready) {
            HeroVisual.Arrow(trendRotation(trend.trend))
        } else {
            HeroVisual.None
        }
        return tileContent(
            context,
            R.drawable.ic_tune,
            R.string.tourist_tile_pressure,
            formatter.pressure(state.pressure.hectopascals),
            detail,
            R.color.tourist_tile_bg_group_pressure,
            visual,
        )
    }

    fun barometricAltitude(state: TouristDashboardState): TouristTileContent {
        val detail = when {
            state.pressure.baroAltitudeMeters == null -> string(R.string.tourist_status_sensor_waiting)
            state.pressure.baroApproximate -> string(R.string.tourist_status_approximate)
            else -> null
        }
        return tileContent(
            context,
            R.drawable.ic_altitude,
            R.string.tourist_tile_baro_altitude,
            formatter.elevation(state.pressure.baroAltitudeMeters),
            detail,
            R.color.tourist_tile_bg_group_pressure,
        )
    }

    fun elevationGainLoss(state: TouristDashboardState): TouristTileContent {
        val trip = state.trip
        val source = trip.elevationSource
        val detail = if (source == null) {
            string(R.string.tourist_status_accumulating)
        } else {
            val loss = formatter.elevation(trip.lossMeters)
            context.getString(
                R.string.tourist_detail_elevation_loss,
                loss.value,
                loss.unit,
                formatter.elevationSource(source),
            )
        }
        return tileContent(
            context,
            R.drawable.ic_altitude,
            R.string.tourist_tile_elevation_gain_loss,
            formatter.elevation(trip.gainMeters),
            detail,
            R.color.tourist_tile_bg_group_pressure,
        )
    }

    fun slope(state: TouristDashboardState): TouristTileContent = tileContent(
        context,
        R.drawable.ic_route_distance,
        R.string.tourist_tile_slope,
        formatter.slope(state.trip.slopePercent),
        null,
        R.color.tourist_tile_bg_group_pressure,
    )

    fun elapsedTime(state: TouristDashboardState): TouristTileContent = tileContent(
        context,
        R.drawable.ic_schedule,
        R.string.tourist_tile_elapsed_time,
        durationValue(state.trip.elapsedMillis),
        null,
        R.color.tourist_tile_bg_group_trip,
    )

    fun movingTime(state: TouristDashboardState): TouristTileContent = tileContent(
        context,
        R.drawable.ic_history,
        R.string.tourist_tile_moving_time,
        durationValue(state.trip.movingMillis.takeIf { state.trip.elapsedMillis != null }),
        null,
        R.color.tourist_tile_bg_group_trip,
    )

    fun averagePace(state: TouristDashboardState): TouristTileContent {
        val average = state.trip.averageSpeedKmh
        val pace = formatter.pace(average)
        val detail = if (pace.value != NO_VALUE) {
            context.getString(R.string.tourist_detail_pace, pace.value, pace.unit)
        } else {
            null
        }
        return tileContent(
            context,
            R.drawable.ic_speed,
            R.string.tourist_tile_average_pace,
            formatter.speed(average),
            detail,
            R.color.tourist_tile_bg_group_trip,
        )
    }

    fun cadence(state: TouristDashboardState): TouristTileContent = tileContent(
        context,
        R.drawable.ic_steps,
        R.string.tourist_tile_cadence,
        formatter.cadence(state.trip.cadenceStepsPerMinute),
        null,
        R.color.tourist_tile_bg_group_trip,
    )

    private fun durationValue(millis: Long?): TileValue =
        TileValue(if (millis == null) NO_VALUE else formatter.duration(millis), "")

    /** Up for rising, flat for steady, down for falling - the arrow drawable points up at rest. */
    private fun trendRotation(trend: PressureTrend): Float = when (trend) {
        PressureTrend.RISING -> 0f
        PressureTrend.STEADY -> QUARTER_TURN
        PressureTrend.FALLING -> HALF_TURN
    }

    private fun string(res: Int): String = context.getString(res)

    private companion object {
        const val QUARTER_TURN = 90f
        const val HALF_TURN = 180f
    }
}
