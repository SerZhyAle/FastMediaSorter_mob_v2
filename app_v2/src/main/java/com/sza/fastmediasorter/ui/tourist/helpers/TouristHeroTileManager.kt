package com.sza.fastmediasorter.ui.tourist.helpers

import android.content.Context
import android.view.View
import androidx.core.content.ContextCompat
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
 * S2922/S3000: binds the prominent Hero focus card on the Tourist dashboard.
 */
class TouristHeroTileManager(
    private val binding: ActivityTouristInfoBinding,
    private val onResetClicked: (TouristTileType) -> Unit = {},
) {

    fun bind(state: TouristDashboardState, context: Context) {
        val card = binding.cardHeroTile
        val btnReset = binding.btnHeroReset
        val textGroup = binding.layoutHeroTextMetrics
        val coordsGroup = binding.layoutHeroCoords
        val compassRose = binding.ivHeroCompassRose

        val bgColor = ContextCompat.getColor(context, getTileBackgroundColorRes(state.focusedTile))
        card.setCardBackgroundColor(bgColor)

        val supportsReset = state.focusedTile == TouristTileType.SPEED ||
            state.focusedTile == TouristTileType.STEPS ||
            state.focusedTile == TouristTileType.TRIP_DISTANCE
        btnReset.visibility = if (supportsReset) View.VISIBLE else View.GONE
        btnReset.setOnClickListener { onResetClicked(state.focusedTile) }

        textGroup.visibility = View.VISIBLE
        coordsGroup.visibility = View.GONE
        compassRose.visibility = View.GONE

        bindTileContent(state, context)
    }

    private fun bindTileContent(state: TouristDashboardState, context: Context) {
        when (state.focusedTile) {
            TouristTileType.SPEED -> bindSpeedTile(state, context)
            TouristTileType.ALTITUDE -> bindAltitudeTile(state, context)
            TouristTileType.COMPASS -> bindCompassTile(state)
            TouristTileType.COORDINATES -> bindCoordinatesTile(state)
            TouristTileType.SATELLITES -> bindSatellitesTile(state, context)
            TouristTileType.STEPS -> bindStepsTile(state)
            TouristTileType.TRIP_DISTANCE -> bindTripDistanceTile(state)
            TouristTileType.SUN_TIME -> bindSunTimeTile(state, context)
            TouristTileType.WEATHER -> bindWeatherTile(state, context)
            TouristTileType.DEW_POINT -> bindDewPointTile(state)
        }
    }

    private fun bindSpeedTile(state: TouristDashboardState, context: Context) {
        binding.ivHeroIcon.setImageResource(R.drawable.ic_speed)
        binding.tvHeroTitle.setText(R.string.tourist_tile_speed)
        binding.tvHeroPrimaryValue.text = state.speedKmh?.let { String.format(Locale.US, "%.1f", it) } ?: "--"
        binding.tvHeroPrimaryUnit.setText(R.string.tourist_unit_kmh)
        val tripKm = state.tripDistanceMeters / METERS_PER_KM
        binding.tvHeroSecondaryDetail.text =
            context.getString(R.string.tourist_detail_speed, state.maxSpeedKmh, tripKm)
    }

    private fun bindAltitudeTile(state: TouristDashboardState, context: Context) {
        binding.ivHeroIcon.setImageResource(R.drawable.ic_altitude)
        binding.tvHeroTitle.setText(R.string.tourist_tile_altitude)
        binding.tvHeroPrimaryValue.text = state.altitudeMeters?.let { String.format(Locale.US, "%.0f", it) } ?: "--"
        binding.tvHeroPrimaryUnit.setText(R.string.tourist_unit_meters)
        val detailText = if (state.altitudeMeters != null) {
            context.getString(R.string.tourist_detail_altitude)
        } else {
            context.getString(R.string.tourist_status_no_fix)
        }
        binding.tvHeroSecondaryDetail.text = detailText
    }

    private fun bindCompassTile(state: TouristDashboardState) {
        binding.ivHeroIcon.setImageResource(R.drawable.ic_compass)
        binding.tvHeroTitle.setText(R.string.tourist_tile_compass)
        val azimuth = state.azimuthDegrees
        if (azimuth != null) {
            binding.ivHeroCompassRose.visibility = View.VISIBLE
            binding.ivHeroCompassRose.rotation = -azimuth
            binding.tvHeroPrimaryValue.text = String.format(Locale.US, "%.0f°", azimuth)
            binding.tvHeroPrimaryUnit.text = cardinalDirection(azimuth)
            val accuracyRes = when (state.compassAccuracy) {
                SensorAccuracy.HIGH -> R.string.tourist_accuracy_high
                SensorAccuracy.MEDIUM -> R.string.tourist_accuracy_medium
                SensorAccuracy.LOW -> R.string.tourist_accuracy_low
                SensorAccuracy.UNRELIABLE -> R.string.tourist_accuracy_unreliable
            }
            binding.tvHeroSecondaryDetail.setText(accuracyRes)
        } else {
            binding.tvHeroPrimaryValue.text = "--"
            binding.tvHeroPrimaryUnit.text = "°"
            binding.tvHeroSecondaryDetail.setText(R.string.tourist_status_sensor_unavailable)
        }
    }

    private fun bindCoordinatesTile(state: TouristDashboardState) {
        binding.ivHeroIcon.setImageResource(R.drawable.ic_map)
        binding.tvHeroTitle.setText(R.string.tourist_tile_coordinates)
        binding.layoutHeroTextMetrics.visibility = View.GONE
        binding.layoutHeroCoords.visibility = View.VISIBLE
        val lat = state.latitude
        val lon = state.longitude
        if (lat != null && lon != null) {
            binding.tvHeroLat.text = String.format(Locale.US, "Lat: %.4f°", lat)
            binding.tvHeroLon.text = String.format(Locale.US, "Lon: %.4f°", lon)
            binding.tvHeroSecondaryDetail.setText(R.string.tourist_detail_coordinates)
        } else {
            binding.tvHeroLat.text = "Lat: --"
            binding.tvHeroLon.text = "Lon: --"
            binding.tvHeroSecondaryDetail.setText(R.string.tourist_status_no_fix)
        }
    }

    private fun bindSatellitesTile(state: TouristDashboardState, context: Context) {
        binding.ivHeroIcon.setImageResource(R.drawable.ic_satellites)
        binding.tvHeroTitle.setText(R.string.tourist_tile_satellites)
        val used = state.satellitesUsed ?: 0
        val total = state.satellitesTotal ?: 0
        binding.tvHeroPrimaryValue.text = used.toString()
        binding.tvHeroPrimaryUnit.text = "/ $total"
        binding.tvHeroSecondaryDetail.text =
            context.getString(R.string.tourist_detail_satellites, used, total)
    }

    private fun bindStepsTile(state: TouristDashboardState) {
        binding.ivHeroIcon.setImageResource(R.drawable.ic_steps)
        binding.tvHeroTitle.setText(R.string.tourist_tile_steps)
        binding.tvHeroPrimaryValue.text = state.stepsCount.toString()
        binding.tvHeroPrimaryUnit.setText(R.string.tourist_unit_steps)
        binding.tvHeroSecondaryDetail.setText(R.string.tourist_detail_steps)
    }

    private fun bindTripDistanceTile(state: TouristDashboardState) {
        binding.ivHeroIcon.setImageResource(R.drawable.ic_route_distance)
        binding.tvHeroTitle.setText(R.string.tourist_tile_trip_distance)
        if (state.tripDistanceMeters >= METERS_PER_KM) {
            val km = state.tripDistanceMeters / METERS_PER_KM
            binding.tvHeroPrimaryValue.text = String.format(Locale.US, "%.2f", km)
            binding.tvHeroPrimaryUnit.text = "km"
        } else {
            binding.tvHeroPrimaryValue.text = String.format(Locale.US, "%.0f", state.tripDistanceMeters)
            binding.tvHeroPrimaryUnit.setText(R.string.tourist_unit_meters)
        }
        binding.tvHeroSecondaryDetail.setText(R.string.tourist_detail_trip_distance)
    }

    private fun bindSunTimeTile(state: TouristDashboardState, context: Context) {
        binding.ivHeroIcon.setImageResource(if (state.isDaylight) R.drawable.ic_sunrise else R.drawable.ic_sunset)
        binding.tvHeroTitle.setText(R.string.tourist_tile_sun_time)

        val sunriseStr = formatTimeMillis(state.sunriseMillis)
        val sunsetStr = formatTimeMillis(state.sunsetMillis)

        if (state.isDaylight) {
            binding.tvHeroPrimaryValue.text = sunsetStr
            binding.tvHeroPrimaryUnit.setText(R.string.tourist_sunset)
        } else {
            binding.tvHeroPrimaryValue.text = sunriseStr
            binding.tvHeroPrimaryUnit.setText(R.string.tourist_sunrise)
        }
        binding.tvHeroSecondaryDetail.text =
            context.getString(R.string.tourist_detail_sun_time, sunriseStr, sunsetStr)
    }

    private fun bindWeatherTile(state: TouristDashboardState, context: Context) {
        binding.ivHeroIcon.setImageResource(R.drawable.ic_info)
        binding.tvHeroTitle.setText(R.string.tourist_tile_weather)
        binding.tvHeroPrimaryValue.text = state.temperatureCelsius?.let { String.format(Locale.US, "%.1f", it) } ?: "--"
        binding.tvHeroPrimaryUnit.text = "°C"
        binding.tvHeroSecondaryDetail.text =
            state.weatherCondition ?: context.getString(R.string.tourist_status_sensor_unavailable)
    }

    private fun bindDewPointTile(state: TouristDashboardState) {
        binding.ivHeroIcon.setImageResource(R.drawable.ic_info)
        binding.tvHeroTitle.setText(R.string.tourist_tile_dew_point)
        binding.tvHeroPrimaryValue.text = state.dewPointCelsius?.let { String.format(Locale.US, "%.1f", it) } ?: "--"
        binding.tvHeroPrimaryUnit.text = "°C"
        binding.tvHeroSecondaryDetail.setText(R.string.tourist_tile_dew_point)
    }

    private fun getTileBackgroundColorRes(tileType: TouristTileType): Int = when (tileType) {
        TouristTileType.SPEED -> R.color.tourist_tile_bg_speed
        TouristTileType.ALTITUDE -> R.color.tourist_tile_bg_altitude
        TouristTileType.COMPASS -> R.color.tourist_tile_bg_compass
        TouristTileType.COORDINATES -> R.color.tourist_tile_bg_coordinates
        TouristTileType.SATELLITES -> R.color.tourist_tile_bg_satellites
        TouristTileType.STEPS -> R.color.tourist_tile_bg_steps
        TouristTileType.TRIP_DISTANCE -> R.color.tourist_tile_bg_trip_distance
        TouristTileType.SUN_TIME -> R.color.tourist_tile_bg_sun_time
        TouristTileType.WEATHER -> R.color.tourist_tile_bg_weather
        TouristTileType.DEW_POINT -> R.color.tourist_tile_bg_dew_point
    }

    private fun cardinalDirection(azimuth: Float): String {
        val directions = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        val index = ((azimuth + HALF_SECTOR) % FULL_CIRCLE / SECTOR).toInt().coerceIn(0, MAX_CARDINAL_INDEX)
        return directions[index]
    }

    private fun formatTimeMillis(millis: Long?): String {
        if (millis == null) return "--:--"
        val zdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())
        return String.format(Locale.US, "%02d:%02d", zdt.hour, zdt.minute)
    }

    private companion object {
        private const val METERS_PER_KM = 1000.0
        private const val HALF_SECTOR = 22.5f
        private const val SECTOR = 45f
        private const val FULL_CIRCLE = 360
        private const val MAX_CARDINAL_INDEX = 7
    }
}
