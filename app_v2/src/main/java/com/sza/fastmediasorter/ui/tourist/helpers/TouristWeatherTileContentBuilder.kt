package com.sza.fastmediasorter.ui.tourist.helpers

import android.content.Context
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.tourist.TouristDashboardState
import com.sza.fastmediasorter.ui.tourist.helpers.TouristTileValueFormatter.Companion.NO_VALUE
import com.sza.fastmediasorter.ui.tourist.helpers.TouristTileValueFormatter.TileValue

/**
 * S4145: content of the sun and weather tiles.
 *
 * Every provider-fed tile carries the Open-Meteo credit while it shows a reading (CC BY 4.0, S4068) and
 * says why it is empty when it does not.
 */
class TouristWeatherTileContentBuilder(
    private val context: Context,
    private val formatter: TouristTileValueFormatter,
) {

    fun sunTime(state: TouristDashboardState): TouristTileContent {
        val sunrise = formatter.clockTime(state.sunriseMillis)
        val sunset = formatter.clockTime(state.sunsetMillis)
        return tileContent(
            context,
            if (state.isDaylight) R.drawable.ic_sunset else R.drawable.ic_sunrise,
            R.string.tourist_tile_sun_time,
            TileValue(
                if (state.isDaylight) sunset else sunrise,
                string(if (state.isDaylight) R.string.tourist_sunset else R.string.tourist_sunrise),
            ),
            context.getString(R.string.tourist_detail_sun_time, sunrise, sunset),
            R.color.tourist_tile_bg_sun_time,
        )
    }

    fun weather(state: TouristDashboardState): TouristTileContent = tileContent(
        context,
        R.drawable.ic_info,
        R.string.tourist_tile_weather,
        formatter.temperature(state.temperatureCelsius),
        formatter.weatherCondition(state.weatherCondition) ?: string(R.string.tourist_status_sensor_unavailable),
        R.color.tourist_tile_bg_weather,
    )

    fun dewPoint(state: TouristDashboardState): TouristTileContent = tileContent(
        context,
        R.drawable.ic_info,
        R.string.tourist_tile_dew_point,
        formatter.temperature(state.dewPointCelsius),
        string(R.string.launcher_gadget_weather_attribution),
        R.color.tourist_tile_bg_dew_point,
    )

    /** The arrow points where the wind blows to; the provider reports where it comes from. */
    fun wind(state: TouristDashboardState): TouristTileContent {
        val details = state.weatherDetails
        val direction = details.windDirectionDegrees?.toFloat()
        val speed = formatter.windSpeed(details.windSpeedKmh)
        val detail = if (speed.value == NO_VALUE) {
            string(R.string.tourist_status_no_data)
        } else {
            val gusts = formatter.windSpeed(details.windGustsKmh)
            context.getString(R.string.tourist_detail_wind, gusts.value, gusts.unit, formatter.cardinal(direction))
        }
        val visual = direction?.let { HeroVisual.Arrow((it + HALF_TURN) % FULL_TURN) } ?: HeroVisual.None
        return tileContent(
            context,
            R.drawable.ic_weather_partly_cloudy,
            R.string.tourist_tile_wind,
            speed,
            detail,
            R.color.tourist_tile_bg_group_weather,
            visual,
        )
    }

    fun feelsLike(state: TouristDashboardState): TouristTileContent = providerTile(
        R.drawable.ic_sun_dewpoint,
        R.string.tourist_tile_feels_like,
        formatter.temperature(state.weatherDetails.apparentTemperatureCelsius),
    )

    fun humidity(state: TouristDashboardState): TouristTileContent = providerTile(
        R.drawable.ic_weather_rain,
        R.string.tourist_tile_humidity,
        formatter.percent(state.weatherDetails.humidityPercent),
    )

    fun uvIndex(state: TouristDashboardState): TouristTileContent = providerTile(
        R.drawable.ic_weather_clear,
        R.string.tourist_tile_uv_index,
        formatter.uvIndex(state.weatherDetails.uvIndex),
    )

    fun precipitation(state: TouristDashboardState): TouristTileContent = providerTile(
        R.drawable.ic_weather_rain,
        R.string.tourist_tile_precipitation,
        formatter.percent(state.weatherDetails.precipitationProbabilityPercent),
    )

    fun cloudCover(state: TouristDashboardState): TouristTileContent = providerTile(
        R.drawable.ic_cloud,
        R.string.tourist_tile_cloud_cover,
        formatter.percent(state.weatherDetails.cloudCoverPercent),
    )

    fun visibility(state: TouristDashboardState): TouristTileContent = providerTile(
        R.drawable.ic_weather_fog,
        R.string.tourist_tile_visibility,
        formatter.visibility(state.weatherDetails.visibilityMetres),
    )

    private fun providerTile(iconRes: Int, labelRes: Int, reading: TileValue): TouristTileContent = tileContent(
        context,
        iconRes,
        labelRes,
        reading,
        string(
            if (reading.value == NO_VALUE) {
                R.string.tourist_status_no_data
            } else {
                R.string.launcher_gadget_weather_attribution
            },
        ),
        R.color.tourist_tile_bg_group_weather,
    )

    private fun string(res: Int): String = context.getString(res)

    private companion object {
        const val HALF_TURN = 180f
        const val FULL_TURN = 360f
    }
}
