package com.sza.fastmediasorter.ui.tourist.helpers

import android.content.Context
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.sensors.SensorAccuracy
import com.sza.fastmediasorter.domain.model.tourist.TouristDashboardState
import com.sza.fastmediasorter.ui.tourist.helpers.TouristTileValueFormatter.Companion.NO_VALUE
import com.sza.fastmediasorter.ui.tourist.helpers.TouristTileValueFormatter.TileValue
import java.util.Locale
import kotlin.math.sqrt

/**
 * S4145: content of the compass, position, accuracy, course, level, light and magnetic-field tiles.
 */
class TouristNavigationTileContentBuilder(
    private val context: Context,
    private val formatter: TouristTileValueFormatter,
) {

    /** The compass names the north it shows (goal 9): magnetic, plus the true heading once there is a fix. */
    fun compass(state: TouristDashboardState): TouristTileContent {
        val azimuth = state.azimuthDegrees
        return if (azimuth == null) {
            tileContent(
                context,
                R.drawable.ic_compass,
                R.string.tourist_tile_compass,
                TileValue(NO_VALUE, string(R.string.tourist_unit_degrees)),
                string(R.string.tourist_status_sensor_unavailable),
                R.color.tourist_tile_bg_compass,
            )
        } else {
            tileContent(
                context,
                R.drawable.ic_compass,
                R.string.tourist_tile_compass,
                TileValue(degreesText(azimuth), formatter.cardinal(azimuth)),
                listOf(accuracyWord(state.compassAccuracy), northLine(state)).joinToString(DETAIL_SEPARATOR),
                R.color.tourist_tile_bg_compass,
                HeroVisual.CompassRose(azimuth),
            )
        }
    }

    fun coordinates(state: TouristDashboardState): TouristTileContent {
        val lat = state.latitude
        val lon = state.longitude
        val hasFix = lat != null && lon != null
        val value = if (hasFix) String.format(Locale.US, "%.2f, %.2f", lat, lon) else NO_VALUE
        return tileContent(
            context,
            R.drawable.ic_map,
            R.string.tourist_tile_coordinates,
            TileValue(value, ""),
            string(if (hasFix) R.string.tourist_detail_coordinates else R.string.tourist_status_no_fix),
            R.color.tourist_tile_bg_coordinates,
            HeroVisual.Coordinates(lat, lon),
        )
    }

    fun satellites(state: TouristDashboardState): TouristTileContent {
        val used = state.satellitesUsed ?: 0
        val total = state.satellitesTotal ?: 0
        return tileContent(
            context,
            R.drawable.ic_satellites,
            R.string.tourist_tile_satellites,
            TileValue(used.toString(), "/ $total"),
            context.getString(R.string.tourist_detail_satellites, used, total),
            R.color.tourist_tile_bg_satellites,
        )
    }

    fun gpsAccuracy(state: TouristDashboardState): TouristTileContent = tileContent(
        context,
        R.drawable.ic_location,
        R.string.tourist_tile_gps_accuracy,
        formatter.gpsAccuracy(state.navigation.gpsAccuracyMeters),
        formatter.gpsGrade(state.navigation.gpsGrade) ?: string(R.string.tourist_status_no_fix),
        R.color.tourist_tile_bg_group_navigation,
    )

    fun course(state: TouristDashboardState): TouristTileContent {
        val course = state.navigation.courseDegrees
        return if (course == null) {
            tileContent(
                context,
                R.drawable.ic_arrow_upward,
                R.string.tourist_tile_course,
                TileValue(NO_VALUE, ""),
                string(R.string.tourist_status_course_undetermined),
                R.color.tourist_tile_bg_group_navigation,
            )
        } else {
            tileContent(
                context,
                R.drawable.ic_arrow_upward,
                R.string.tourist_tile_course,
                TileValue(degreesText(course), formatter.cardinal(course)),
                null,
                R.color.tourist_tile_bg_group_navigation,
                HeroVisual.Arrow(course),
            )
        }
    }

    fun level(state: TouristDashboardState): TouristTileContent {
        val pitch = state.navigation.pitchDegrees
        val roll = state.navigation.rollDegrees
        return if (pitch == null || roll == null) {
            tileContent(
                context,
                R.drawable.ic_tune,
                R.string.tourist_tile_level,
                TileValue(NO_VALUE, ""),
                string(R.string.tourist_status_sensor_waiting),
                R.color.tourist_tile_bg_group_navigation,
            )
        } else {
            tileContent(
                context,
                R.drawable.ic_tune,
                R.string.tourist_tile_level,
                formatter.degrees(sqrt(pitch * pitch + roll * roll)),
                context.getString(R.string.tourist_detail_level, wholeDegrees(pitch), wholeDegrees(roll)),
                R.color.tourist_tile_bg_group_navigation,
                HeroVisual.Level(pitch, roll),
            )
        }
    }

    fun light(state: TouristDashboardState): TouristTileContent {
        val lux = state.environment.lux
        return tileContent(
            context,
            R.drawable.ic_weather_clear,
            R.string.tourist_tile_light,
            formatter.lux(lux),
            if (lux == null) string(R.string.tourist_status_sensor_waiting) else null,
            R.color.tourist_tile_bg_group_environment,
        )
    }

    /** The interference warning is a sentence, never only a colour (strategic §3.2 accessibility). */
    fun magneticField(state: TouristDashboardState): TouristTileContent {
        val field = state.environment.magneticMicrotesla
        val detail = when {
            field == null -> R.string.tourist_status_sensor_waiting
            state.environment.magneticInterference -> R.string.tourist_detail_magnetic_interference
            else -> R.string.tourist_detail_magnetic_normal
        }
        return tileContent(
            context,
            R.drawable.ic_compass,
            R.string.tourist_tile_magnetic_field,
            formatter.microtesla(field),
            string(detail),
            R.color.tourist_tile_bg_group_environment,
        )
    }

    private fun northLine(state: TouristDashboardState): String {
        val trueAzimuth = state.navigation.trueAzimuthDegrees
        val declination = state.navigation.declinationDegrees
        return if (trueAzimuth != null && declination != null) {
            context.getString(
                R.string.tourist_detail_true_heading,
                wholeDegrees(trueAzimuth),
                String.format(Locale.getDefault(), "%+.1f", declination),
            )
        } else {
            string(R.string.tourist_detail_magnetic_north)
        }
    }

    private fun accuracyWord(accuracy: SensorAccuracy): String = string(
        when (accuracy) {
            SensorAccuracy.HIGH -> R.string.tourist_accuracy_high
            SensorAccuracy.MEDIUM -> R.string.tourist_accuracy_medium
            SensorAccuracy.LOW -> R.string.tourist_accuracy_low
            SensorAccuracy.UNRELIABLE -> R.string.tourist_accuracy_unreliable
        },
    )

    private fun degreesText(value: Float): String = formatter.degrees(value).let { it.value + it.unit }

    private fun wholeDegrees(value: Float): String = String.format(Locale.getDefault(), "%.0f", value)

    private fun string(res: Int): String = context.getString(res)

    private companion object {
        const val DETAIL_SEPARATOR = " • "
    }
}
