package com.sza.fastmediasorter.ui.tourist.helpers

import android.content.Context
import android.view.View
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ActivityTouristInfoBinding
import com.sza.fastmediasorter.domain.model.sensors.SensorAccuracy
import com.sza.fastmediasorter.domain.model.tourist.TouristDashboardState
import com.sza.fastmediasorter.domain.model.tourist.TouristTileType
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

/**
 * S2922: binds the prominent Hero focus card on the Tourist dashboard.
 */
class TouristHeroTileManager(
    private val binding: ActivityTouristInfoBinding,
) {

    fun bind(state: TouristDashboardState, context: Context) {
        val card = binding.cardHeroTile
        val icon = binding.ivHeroIcon
        val title = binding.tvHeroTitle
        val textGroup = binding.layoutHeroTextMetrics
        val primaryVal = binding.tvHeroPrimaryValue
        val primaryUnit = binding.tvHeroPrimaryUnit
        val compassRose = binding.ivHeroCompassRose
        val detail = binding.tvHeroSecondaryDetail

        when (state.focusedTile) {
            TouristTileType.SPEED -> {
                icon.setImageResource(R.drawable.ic_speed)
                title.setText(R.string.tourist_tile_speed)
                textGroup.visibility = View.VISIBLE
                compassRose.visibility = View.GONE
                primaryVal.text = state.speedKmh?.let { String.format(Locale.US, "%.1f", it) } ?: "--"
                primaryUnit.setText(R.string.tourist_unit_kmh)
                val tripKm = state.tripDistanceMeters / 1000.0
                detail.text = String.format(Locale.US, "Max: %.1f km/h  •  Trip: %.2f km", state.maxSpeedKmh, tripKm)
            }
            TouristTileType.ALTITUDE -> {
                icon.setImageResource(R.drawable.ic_altitude)
                title.setText(R.string.tourist_tile_altitude)
                textGroup.visibility = View.VISIBLE
                compassRose.visibility = View.GONE
                primaryVal.text = state.altitudeMeters?.let { String.format(Locale.US, "%.0f", it) } ?: "--"
                primaryUnit.setText(R.string.tourist_unit_meters)
                detail.text = if (state.altitudeMeters != null) "MSL / GPS Altitude" else context.getString(R.string.tourist_status_no_fix)
            }
            TouristTileType.COMPASS -> {
                icon.setImageResource(R.drawable.ic_compass)
                title.setText(R.string.tourist_tile_compass)
                val azimuth = state.azimuthDegrees
                if (azimuth != null) {
                    textGroup.visibility = View.VISIBLE
                    compassRose.visibility = View.VISIBLE
                    compassRose.rotation = -azimuth
                    val cardinal = cardinalDirection(azimuth)
                    primaryVal.text = String.format(Locale.US, "%.0f°", azimuth)
                    primaryUnit.text = cardinal
                    val accuracyLabel = when (state.compassAccuracy) {
                        SensorAccuracy.HIGH -> "High accuracy"
                        SensorAccuracy.MEDIUM -> "Medium accuracy"
                        SensorAccuracy.LOW -> "Low accuracy"
                        SensorAccuracy.UNRELIABLE -> "Calibrate compass"
                    }
                    detail.text = accuracyLabel
                } else {
                    textGroup.visibility = View.VISIBLE
                    compassRose.visibility = View.GONE
                    primaryVal.text = "--"
                    primaryUnit.text = "°"
                    detail.setText(R.string.tourist_status_sensor_unavailable)
                }
            }
            TouristTileType.COORDINATES -> {
                icon.setImageResource(R.drawable.ic_map)
                title.setText(R.string.tourist_tile_coordinates)
                textGroup.visibility = View.VISIBLE
                compassRose.visibility = View.GONE
                val lat = state.latitude
                val lon = state.longitude
                if (lat != null && lon != null) {
                    primaryVal.text = String.format(Locale.US, "%.4f", lat)
                    primaryUnit.text = String.format(Locale.US, "%.4f", lon)
                    detail.text = "WGS84 • Lat, Lon"
                } else {
                    primaryVal.text = "--"
                    primaryUnit.text = "--"
                    detail.setText(R.string.tourist_status_no_fix)
                }
            }
            TouristTileType.SATELLITES -> {
                icon.setImageResource(R.drawable.ic_satellites)
                title.setText(R.string.tourist_tile_satellites)
                textGroup.visibility = View.VISIBLE
                compassRose.visibility = View.GONE
                val used = state.satellitesUsed ?: 0
                val total = state.satellitesTotal ?: 0
                primaryVal.text = used.toString()
                primaryUnit.text = "/ "
                detail.text = "Satellites:  used in fix ( in view)"
            }
            TouristTileType.STEPS -> {
                icon.setImageResource(R.drawable.ic_steps)
                title.setText(R.string.tourist_tile_steps)
                textGroup.visibility = View.VISIBLE
                compassRose.visibility = View.GONE
                primaryVal.text = state.stepsCount.toString()
                primaryUnit.setText(R.string.tourist_unit_steps)
                detail.text = "Session step counter"
            }
            TouristTileType.TRIP_DISTANCE -> {
                icon.setImageResource(R.drawable.ic_route_distance)
                title.setText(R.string.tourist_tile_trip_distance)
                textGroup.visibility = View.VISIBLE
                compassRose.visibility = View.GONE
                if (state.tripDistanceMeters >= 1000.0) {
                    val km = state.tripDistanceMeters / 1000.0
                    primaryVal.text = String.format(Locale.US, "%.2f", km)
                    primaryUnit.text = "km"
                } else {
                    primaryVal.text = String.format(Locale.US, "%.0f", state.tripDistanceMeters)
                    primaryUnit.setText(R.string.tourist_unit_meters)
                }
                detail.text = "Distance traveled during session"
            }
            TouristTileType.SUN_TIME -> {
                icon.setImageResource(if (state.isDaylight) R.drawable.ic_sunrise else R.drawable.ic_sunset)
                title.setText(R.string.tourist_tile_sun_time)
                textGroup.visibility = View.VISIBLE
                compassRose.visibility = View.GONE

                val sunriseStr = formatTimeMillis(state.sunriseMillis)
                val sunsetStr = formatTimeMillis(state.sunsetMillis)

                if (state.isDaylight) {
                    primaryVal.text = sunsetStr
                    primaryUnit.setText(R.string.tourist_sunset)
                } else {
                    primaryVal.text = sunriseStr
                    primaryUnit.setText(R.string.tourist_sunrise)
                }
                detail.text = "Sunrise:   •  Sunset: "
            }
        }
    }

    private fun cardinalDirection(azimuth: Float): String {
        val directions = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        val index = ((azimuth + 22.5f) % 360 / 45).toInt().coerceIn(0, 7)
        return directions[index]
    }

    private fun formatTimeMillis(millis: Long?): String {
        if (millis == null) return "--:--"
        val zdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())
        return String.format(Locale.US, "%02d:%02d", zdt.hour, zdt.minute)
    }
}
