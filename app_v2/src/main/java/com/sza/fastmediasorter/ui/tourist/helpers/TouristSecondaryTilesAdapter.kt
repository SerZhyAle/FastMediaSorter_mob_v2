package com.sza.fastmediasorter.ui.tourist.helpers

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ItemTouristTileBinding
import com.sza.fastmediasorter.domain.model.tourist.TouristDashboardState
import com.sza.fastmediasorter.domain.model.tourist.TouristTileType
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

/**
 * S2922/S3000/S2995/S3011: renders the grid of secondary telemetry tiles.
 */
class TouristSecondaryTilesAdapter(
    private val onTileClicked: (TouristTileType) -> Unit,
) : RecyclerView.Adapter<TouristSecondaryTilesAdapter.TileViewHolder>() {

    private var state: TouristDashboardState = TouristDashboardState()
    private val visibleTiles: MutableList<TouristTileType> = mutableListOf()

    fun updateState(newState: TouristDashboardState) {
        this.state = newState
        visibleTiles.clear()
        TouristTileType.entries.forEach { tile ->
            if (tile == TouristTileType.STEPS && !newState.stepsAvailable) {
                return@forEach
            }
            if (tile != newState.focusedTile) {
                visibleTiles.add(tile)
            }
        }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TileViewHolder {
        val binding = ItemTouristTileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TileViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TileViewHolder, position: Int) {
        val tile = visibleTiles[position]
        holder.bind(tile, state, onTileClicked)
    }

    override fun getItemCount(): Int = visibleTiles.size

    class TileViewHolder(
        private val binding: ItemTouristTileBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(
            tile: TouristTileType,
            state: TouristDashboardState,
            onTileClicked: (TouristTileType) -> Unit,
        ) {
            val context = binding.root.context
            binding.cardTileRoot.setOnClickListener { onTileClicked(tile) }

            val bgColor = ContextCompat.getColor(context, getTileBackgroundColorRes(tile))
            binding.cardTileRoot.setCardBackgroundColor(bgColor)

            bindTileContent(tile, state)
        }

        private fun bindTileContent(tile: TouristTileType, state: TouristDashboardState) {
            when (tile) {
                TouristTileType.SPEED -> bindSpeed(state)
                TouristTileType.ALTITUDE -> bindAltitude(state)
                TouristTileType.COMPASS -> bindCompass(state)
                TouristTileType.COORDINATES -> bindCoordinates(state)
                TouristTileType.SATELLITES -> bindSatellites(state)
                TouristTileType.STEPS -> bindSteps(state)
                TouristTileType.TRIP_DISTANCE -> bindTripDistance(state)
                TouristTileType.SUN_TIME -> bindSunTime(state)
                TouristTileType.WEATHER -> bindWeather(state)
                TouristTileType.DEW_POINT -> bindDewPoint(state)
            }
        }

        private fun bindSpeed(state: TouristDashboardState) {
            binding.ivTileIcon.setImageResource(R.drawable.ic_speed)
            binding.tvTileLabel.setText(R.string.tourist_tile_speed)
            binding.tvTileValue.text = state.speedKmh?.let { String.format(Locale.US, "%.1f", it) } ?: "--"
            binding.tvTileUnit.setText(R.string.tourist_unit_kmh)
        }

        private fun bindAltitude(state: TouristDashboardState) {
            binding.ivTileIcon.setImageResource(R.drawable.ic_altitude)
            binding.tvTileLabel.setText(R.string.tourist_tile_altitude)
            binding.tvTileValue.text = state.altitudeMeters?.let { String.format(Locale.US, "%.0f", it) } ?: "--"
            binding.tvTileUnit.setText(R.string.tourist_unit_meters)
        }

        private fun bindCompass(state: TouristDashboardState) {
            binding.ivTileIcon.setImageResource(R.drawable.ic_compass)
            binding.tvTileLabel.setText(R.string.tourist_tile_compass)
            binding.tvTileValue.text = state.azimuthDegrees?.let { String.format(Locale.US, "%.0f°", it) } ?: "--"
            binding.tvTileUnit.text = state.azimuthDegrees?.let { cardinal(it) } ?: ""
        }

        private fun bindCoordinates(state: TouristDashboardState) {
            binding.ivTileIcon.setImageResource(R.drawable.ic_map)
            binding.tvTileLabel.setText(R.string.tourist_tile_coordinates)
            val lat = state.latitude
            val lon = state.longitude
            if (lat != null && lon != null) {
                binding.tvTileValue.text = String.format(Locale.US, "%.2f, %.2f", lat, lon)
                binding.tvTileUnit.text = ""
            } else {
                binding.tvTileValue.text = "--"
                binding.tvTileUnit.text = ""
            }
        }

        private fun bindSatellites(state: TouristDashboardState) {
            binding.ivTileIcon.setImageResource(R.drawable.ic_satellites)
            binding.tvTileLabel.setText(R.string.tourist_tile_satellites)
            val used = state.satellitesUsed ?: 0
            val total = state.satellitesTotal ?: 0
            binding.tvTileValue.text = "$used / $total"
            binding.tvTileUnit.text = ""
        }

        private fun bindSteps(state: TouristDashboardState) {
            binding.ivTileIcon.setImageResource(R.drawable.ic_steps)
            binding.tvTileLabel.setText(R.string.tourist_tile_steps)
            binding.tvTileValue.text = state.stepsCount.toString()
            binding.tvTileUnit.setText(R.string.tourist_unit_steps)
        }

        private fun bindTripDistance(state: TouristDashboardState) {
            binding.ivTileIcon.setImageResource(R.drawable.ic_route_distance)
            binding.tvTileLabel.setText(R.string.tourist_tile_trip_distance)
            if (state.tripDistanceMeters >= METERS_PER_KM) {
                val km = state.tripDistanceMeters / METERS_PER_KM
                binding.tvTileValue.text = String.format(Locale.US, "%.2f", km)
                binding.tvTileUnit.text = "km"
            } else {
                binding.tvTileValue.text = String.format(Locale.US, "%.0f", state.tripDistanceMeters)
                binding.tvTileUnit.setText(R.string.tourist_unit_meters)
            }
        }

        private fun bindSunTime(state: TouristDashboardState) {
            val iconRes = if (state.isDaylight) R.drawable.ic_sunset else R.drawable.ic_sunrise
            binding.ivTileIcon.setImageResource(iconRes)
            binding.tvTileLabel.setText(R.string.tourist_tile_sun_time)
            val targetMillis = if (state.isDaylight) state.sunsetMillis else state.sunriseMillis
            binding.tvTileValue.text = formatTime(targetMillis)
            val unitRes = if (state.isDaylight) R.string.tourist_sunset else R.string.tourist_sunrise
            binding.tvTileUnit.setText(unitRes)
        }

        private fun bindWeather(state: TouristDashboardState) {
            binding.ivTileIcon.setImageResource(R.drawable.ic_info)
            binding.tvTileLabel.setText(R.string.tourist_tile_weather)
            binding.tvTileValue.text = state.temperatureCelsius?.let { String.format(Locale.US, "%.1f", it) } ?: "--"
            binding.tvTileUnit.text = "°C"
        }

        private fun bindDewPoint(state: TouristDashboardState) {
            binding.ivTileIcon.setImageResource(R.drawable.ic_info)
            binding.tvTileLabel.setText(R.string.tourist_tile_dew_point)
            binding.tvTileValue.text = state.dewPointCelsius?.let { String.format(Locale.US, "%.1f", it) } ?: "--"
            binding.tvTileUnit.text = "°C"
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

        private fun cardinal(azimuth: Float): String {
            val directions = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
            val index = ((azimuth + HALF_SECTOR) % FULL_CIRCLE / SECTOR).toInt().coerceIn(0, MAX_CARDINAL_INDEX)
            return directions[index]
        }

        private fun formatTime(millis: Long?): String {
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
}
