package com.sza.fastmediasorter.ui.launcher.gadget

import androidx.annotation.DrawableRes
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.weather.WeatherCondition

/** S0426: the condition-to-glyph table, shared by the gadget and the place picker preview. */
@DrawableRes
fun iconFor(condition: WeatherCondition, isDay: Boolean): Int = when (condition) {
    // Night has no dedicated art yet, so a clear night borrows the neutral cloud rather than a sun.
    WeatherCondition.CLEAR -> if (isDay) R.drawable.ic_weather_clear else R.drawable.ic_cloud
    WeatherCondition.PARTLY_CLOUDY -> R.drawable.ic_weather_partly_cloudy
    WeatherCondition.CLOUDY -> R.drawable.ic_cloud
    WeatherCondition.FOG -> R.drawable.ic_weather_fog
    WeatherCondition.RAIN -> R.drawable.ic_weather_rain
    WeatherCondition.SNOW -> R.drawable.ic_weather_snow
    WeatherCondition.THUNDERSTORM -> R.drawable.ic_weather_thunderstorm
    WeatherCondition.UNKNOWN -> R.drawable.ic_cloud
}
