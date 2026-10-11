package com.sza.fastmediasorter.ui.tourist.helpers

import android.content.Context
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.tourist.TouristDashboardState
import com.sza.fastmediasorter.ui.tourist.helpers.TouristTileValueFormatter.Companion.NO_VALUE
import com.sza.fastmediasorter.ui.tourist.helpers.TouristTileValueFormatter.TileValue

/**
 * S4150: content of the tiles fed by the paired watch.
 *
 * Without a fresh reading the status line asks for the watch Tourist to be opened, because that is the
 * only thing that makes the watch send - the phone never starts its sensors.
 */
class TouristWatchTileContentBuilder(
    private val context: Context,
    private val formatter: TouristTileValueFormatter,
) {

    fun heartRate(state: TouristDashboardState): TouristTileContent = watchTile(
        R.drawable.ic_favorite,
        R.string.tourist_tile_watch_heart_rate,
        formatter.heartRate(state.watch.heartRateBpm),
        R.color.tourist_tile_bg_trip_distance,
    )

    fun bodyTemperature(state: TouristDashboardState): TouristTileContent = watchTile(
        R.drawable.ic_info,
        R.string.tourist_tile_watch_body_temperature,
        formatter.temperature(state.watch.bodyTemperatureCelsius),
        R.color.tourist_tile_bg_steps,
    )

    private fun watchTile(
        @DrawableRes iconRes: Int,
        @StringRes labelRes: Int,
        reading: TileValue,
        @ColorRes backgroundRes: Int,
    ): TouristTileContent {
        val status = if (reading.value == NO_VALUE) {
            R.string.tourist_status_open_watch_tourist
        } else {
            R.string.tourist_detail_from_watch
        }
        return tileContent(context, iconRes, labelRes, reading, context.getString(status), backgroundRes)
    }
}
