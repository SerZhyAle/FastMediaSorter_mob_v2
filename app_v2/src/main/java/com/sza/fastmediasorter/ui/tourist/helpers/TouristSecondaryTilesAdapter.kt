package com.sza.fastmediasorter.ui.tourist.helpers

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
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
 * S2922: renders the grid of secondary telemetry tiles.
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

            when (tile) {
                TouristTileType.SPEED -> {
                    binding.ivTileIcon.setImageResource(R.drawable.ic_speed)
                    binding.tvTileLabel.setText(R.string.tourist_tile_speed)
                    binding.tvTileValue.text = state.speedKmh?.let { String.format(Locale.US, "%.1f", it) } ?: "--"
                    binding.tvTileUnit.setText(R.string.tourist_unit_kmh)
                }
                TouristTileType.ALTITUDE -> {
                    binding.ivTileIcon.setImageResource(R.drawable.ic_altitude)
                    binding.tvTileLabel.setText(R.string.tourist_tile_altitude)
                    binding.tvTileValue.text = state.altitudeMeters?.let { String.format(Locale.US, "%.0f", it) } ?: "--"
                    binding.tvTileUnit.setText(R.string.tourist_unit_meters)
                }
                TouristTileType.COMPASS -> {
                    binding.ivTileIcon.setImageResource(R.drawable.ic_compass)
                    binding.tvTileLabel.setText(R.string.tourist_tile_compass)
                    binding.tvTileValue.text = state.azimuthDegrees?.let { String.format(Locale.US, "%.0f°", it) } ?: "--"
                    binding.tvTileUnit.text = state.azimuthDegrees?.let { cardinal(it) } ?: ""
                }
                TouristTileType.COORDINATES -> {
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
                TouristTileType.SATELLITES -> {
                    binding.ivTileIcon.setImageResource(R.drawable.ic_satellites)
                    binding.tvTileLabel.setText(R.string.tourist_tile_satellites)
                    val used = state.satellitesUsed ?: 0
                    val total = state.satellitesTotal ?: 0
                    binding.tvTileValue.text = " / "
                    binding.tvTileUnit.text = ""
                }
                TouristTileType.STEPS -> {
                    binding.ivTileIcon.setImageResource(R.drawable.ic_steps)
                    binding.tvTileLabel.setText(R.string.tourist_tile_steps)
                    binding.tvTileValue.text = state.stepsCount.toString()
                    binding.tvTileUnit.setText(R.string.tourist_unit_steps)
                }
                TouristTileType.TRIP_DISTANCE -> {
                    binding.ivTileIcon.setImageResource(R.drawable.ic_route_distance)
                    binding.tvTileLabel.setText(R.string.tourist_tile_trip_distance)
                    if (state.tripDistanceMeters >= 1000.0) {
                        binding.tvTileValue.text = String.format(Locale.US, "%.2f", state.tripDistanceMeters / 1000.0)
                        binding.tvTileUnit.text = "km"
                    } else {
                        binding.tvTileValue.text = String.format(Locale.US, "%.0f", state.tripDistanceMeters)
                        binding.tvTileUnit.setText(R.string.tourist_unit_meters)
                    }
                }
                TouristTileType.SUN_TIME -> {
                    binding.ivTileIcon.setImageResource(if (state.isDaylight) R.drawable.ic_sunset else R.drawable.ic_sunrise)
                    binding.tvTileLabel.setText(R.string.tourist_tile_sun_time)
                    val targetMillis = if (state.isDaylight) state.sunsetMillis else state.sunriseMillis
                    binding.tvTileValue.text = formatTime(targetMillis)
                    binding.tvTileUnit.setText(if (state.isDaylight) R.string.tourist_sunset else R.string.tourist_sunrise)
                }
            }
        }

        private fun cardinal(azimuth: Float): String {
            val directions = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
            val index = ((azimuth + 22.5f) % 360 / 45).toInt().coerceIn(0, 7)
            return directions[index]
        }

        private fun formatTime(millis: Long?): String {
            if (millis == null) return "--:--"
            val zdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())
            return String.format(Locale.US, "%02d:%02d", zdt.hour, zdt.minute)
        }
    }
}
