package com.sza.fastmediasorter.ui.tourist.helpers

import android.content.Context
import androidx.annotation.StringRes
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.format.QuantityFormatter
import com.sza.fastmediasorter.domain.model.Quantity
import com.sza.fastmediasorter.domain.model.UnitScale
import com.sza.fastmediasorter.domain.model.UnitSystem
import com.sza.fastmediasorter.domain.model.tourist.ElevationSource
import com.sza.fastmediasorter.domain.model.tourist.GpsAccuracyGrade
import com.sza.fastmediasorter.domain.model.tourist.PressureTrend
import com.sza.fastmediasorter.domain.model.weather.WeatherCondition
import java.util.Locale

/**
 * S3101: the one place a Tourist dashboard tile decides which scale its reading is in.
 *
 * It returns the number and its unit as two fields rather than one string: the tile layout holds them
 * in separate views at different type sizes, so the combined form [QuantityFormatter] produces cannot
 * be used here. The scale decision is still made once - both the hero card and the secondary grid read
 * this class instead of each carrying a copy.
 *
 * Every conversion comes from [UnitScale]; this class declares no coefficient of its own.
 */
class TouristTileValueFormatter(
    private val context: Context,
    private val quantityFormatter: QuantityFormatter,
    private val systemProvider: () -> UnitSystem,
) {

    val system: UnitSystem
        get() = systemProvider()

    fun speed(kmh: Float?): TileValue {
        val unit = unitLabel(R.string.tourist_unit_kmh, R.string.tourist_unit_mph)
        val value = kmh ?: return TileValue(NO_VALUE, unit)
        val converted = convertSpeed(value.toDouble())
        return TileValue(decimal(converted, ONE_DECIMAL), unit)
    }

    fun altitude(metres: Double?): TileValue {
        val unit = unitLabel(R.string.tourist_unit_meters, R.string.tourist_unit_feet)
        val value = metres ?: return TileValue(NO_VALUE, unit)
        val converted = when (system) {
            UnitSystem.METRIC -> value
            UnitSystem.IMPERIAL -> UnitScale.metresToFeet(value)
        }
        return TileValue(decimal(converted, NO_DECIMALS), unit)
    }

    /**
     * Below the large unit the reading switches to the small one on both scales - metres under a
     * kilometre, feet under a mile - because a walk reads as a row of zeroes otherwise.
     */
    fun tripDistance(metres: Double): TileValue {
        val reading = tripReading(metres)
        return TileValue(decimal(reading.value, reading.decimals), reading.unit)
    }

    fun temperature(celsius: Float?): TileValue {
        val unit = unitLabel(R.string.tourist_unit_celsius, R.string.tourist_unit_fahrenheit)
        val value = celsius ?: return TileValue(NO_VALUE, unit)
        val converted = when (system) {
            UnitSystem.METRIC -> value.toDouble()
            UnitSystem.IMPERIAL -> UnitScale.celsiusToFahrenheit(value.toDouble())
        }
        return TileValue(decimal(converted, ONE_DECIMAL), unit)
    }

    /** S4068: null for [WeatherCondition.UNKNOWN] too, so the caller shows its "unavailable" line instead. */
    fun weatherCondition(condition: WeatherCondition?): String? {
        val res = when (condition) {
            WeatherCondition.CLEAR -> R.string.tourist_weather_condition_clear
            WeatherCondition.PARTLY_CLOUDY -> R.string.tourist_weather_condition_partly_cloudy
            WeatherCondition.CLOUDY -> R.string.tourist_weather_condition_cloudy
            WeatherCondition.FOG -> R.string.tourist_weather_condition_fog
            WeatherCondition.RAIN -> R.string.tourist_weather_condition_rain
            WeatherCondition.SNOW -> R.string.tourist_weather_condition_snow
            WeatherCondition.THUNDERSTORM -> R.string.tourist_weather_condition_thunderstorm
            WeatherCondition.UNKNOWN, null -> null
        }
        return res?.let(context::getString)
    }

    /** The clock length and the AM/PM marker are the seam's decision, so this delegates to it. */
    fun clockTime(epochMillis: Long?): String {
        val millis = epochMillis ?: return NO_TIME
        return quantityFormatter.format(Quantity.Instant(millis), system)
    }

    /** The speed detail line takes its two units as arguments - see strategic ADR-3. */
    fun speedDetail(maxSpeedKmh: Float, tripMetres: Double): String {
        val trip = tripReading(tripMetres)
        return context.getString(
            R.string.tourist_detail_speed,
            convertSpeed(maxSpeedKmh.toDouble()),
            unitLabel(R.string.tourist_unit_kmh, R.string.tourist_unit_mph),
            trip.value,
            trip.unit,
        )
    }

    /** S4145: hPa on the metric scale, inches of mercury on the imperial one (ADR-4). */
    fun pressure(hectopascals: Float?): TileValue {
        val unit = unitLabel(R.string.tourist_unit_hpa, R.string.tourist_unit_inhg)
        val value = hectopascals ?: return TileValue(NO_VALUE, unit)
        return when (system) {
            UnitSystem.METRIC -> TileValue(decimal(value.toDouble(), NO_DECIMALS), unit)
            UnitSystem.IMPERIAL ->
                TileValue(decimal(UnitScale.hectopascalsToInchesOfMercury(value.toDouble()), TWO_DECIMALS), unit)
        }
    }

    fun elevation(metres: Double?): TileValue = altitude(metres)

    fun gpsAccuracy(metres: Float?): TileValue = altitude(metres?.toDouble())

    fun windSpeed(kmh: Double?): TileValue {
        val unit = unitLabel(R.string.tourist_unit_kmh, R.string.tourist_unit_mph)
        val value = kmh ?: return TileValue(NO_VALUE, unit)
        return TileValue(decimal(convertSpeed(value), NO_DECIMALS), unit)
    }

    fun visibility(metres: Double?): TileValue {
        val unit = unitLabel(R.string.tourist_unit_km, R.string.tourist_unit_miles)
        val value = metres ?: return TileValue(NO_VALUE, unit)
        val converted = when (system) {
            UnitSystem.METRIC -> UnitScale.metresToKilometres(value)
            UnitSystem.IMPERIAL -> UnitScale.metresToMiles(value)
        }
        return TileValue(decimal(converted, ONE_DECIMAL), unit)
    }

    /** Minutes and seconds per kilometre or mile; no value at a standstill, where pace is infinite. */
    fun pace(kmh: Float?): TileValue {
        val unit = unitLabel(R.string.tourist_unit_min_per_km, R.string.tourist_unit_min_per_mi)
        val speed = kmh?.toDouble() ?: 0.0
        val minutes = when (system) {
            UnitSystem.METRIC -> UnitScale.paceMinutesPerKilometre(speed)
            UnitSystem.IMPERIAL -> UnitScale.paceMinutesPerMile(speed)
        } ?: return TileValue(NO_VALUE, unit)
        val totalSeconds = Math.round(minutes * SECONDS_PER_MINUTE)
        val text = String.format(
            Locale.getDefault(),
            PACE_PATTERN,
            totalSeconds / SECONDS_PER_MINUTE,
            totalSeconds % SECONDS_PER_MINUTE,
        )
        return TileValue(text, unit)
    }

    /** `h:mm:ss`; hours are not capped, a multi-day trip simply shows more of them. */
    fun duration(millis: Long?): String {
        val total = millis?.div(MILLIS_PER_SECOND) ?: return NO_TIME
        return String.format(
            Locale.getDefault(),
            DURATION_PATTERN,
            total / SECONDS_PER_HOUR,
            total % SECONDS_PER_HOUR / SECONDS_PER_MINUTE,
            total % SECONDS_PER_MINUTE,
        )
    }

    fun percent(value: Number?): TileValue =
        TileValue(value?.let { decimal(it.toDouble(), NO_DECIMALS) } ?: NO_VALUE, string(R.string.tourist_unit_percent))

    fun slope(percent: Float?): TileValue = percent(percent)

    fun lux(value: Float?): TileValue =
        TileValue(value?.let { decimal(it.toDouble(), NO_DECIMALS) } ?: NO_VALUE, string(R.string.tourist_unit_lux))

    fun microtesla(value: Float?): TileValue = TileValue(
        value?.let { decimal(it.toDouble(), NO_DECIMALS) } ?: NO_VALUE,
        string(R.string.tourist_unit_microtesla),
    )

    fun cadence(stepsPerMinute: Int?): TileValue =
        TileValue(stepsPerMinute?.toString() ?: NO_VALUE, string(R.string.tourist_unit_steps_per_minute))

    fun heartRate(bpm: Int?): TileValue = TileValue(bpm?.toString() ?: NO_VALUE, string(R.string.tourist_unit_bpm))

    fun degrees(value: Float?): TileValue =
        TileValue(value?.let { decimal(it.toDouble(), NO_DECIMALS) } ?: NO_VALUE, string(R.string.tourist_unit_degrees))

    fun uvIndex(value: Double?): TileValue = TileValue(value?.let { decimal(it, ONE_DECIMAL) } ?: NO_VALUE, "")

    /** Compass point from resources, so a locale can use its own letters (strategic §3.2). */
    fun cardinal(azimuth: Float?): String {
        val degrees = azimuth ?: return ""
        val normalized = (degrees % FULL_CIRCLE + FULL_CIRCLE) % FULL_CIRCLE
        val index = ((normalized + HALF_SECTOR) / SECTOR).toInt() % CARDINAL_RES.size
        return string(CARDINAL_RES[index])
    }

    fun gpsGrade(grade: GpsAccuracyGrade?): String? = when (grade) {
        GpsAccuracyGrade.GOOD -> string(R.string.tourist_gps_accuracy_good)
        GpsAccuracyGrade.MEDIUM -> string(R.string.tourist_gps_accuracy_medium)
        GpsAccuracyGrade.POOR -> string(R.string.tourist_gps_accuracy_poor)
        null -> null
    }

    fun trend(trend: PressureTrend): String = when (trend) {
        PressureTrend.RISING -> string(R.string.tourist_trend_rising)
        PressureTrend.STEADY -> string(R.string.tourist_trend_steady)
        PressureTrend.FALLING -> string(R.string.tourist_trend_falling)
    }

    fun elevationSource(source: ElevationSource?): String = when (source) {
        ElevationSource.BAROMETER -> string(R.string.tourist_elevation_source_barometer)
        ElevationSource.GPS -> string(R.string.tourist_elevation_source_gps)
        null -> ""
    }

    private fun string(@StringRes res: Int): String = context.getString(res)

    /**
     * Below the large unit the reading switches to the small one on both scales - metres under a
     * kilometre, feet under a mile - because a walk reads as a row of zeroes otherwise.
     */
    private fun tripReading(metres: Double): TripReading = when (system) {
        UnitSystem.METRIC -> {
            val kilometres = UnitScale.metresToKilometres(metres)
            if (kilometres >= 1.0) {
                TripReading(kilometres, context.getString(R.string.tourist_unit_km), TWO_DECIMALS)
            } else {
                TripReading(metres, context.getString(R.string.tourist_unit_meters), NO_DECIMALS)
            }
        }

        UnitSystem.IMPERIAL -> {
            val miles = UnitScale.metresToMiles(metres)
            if (miles >= 1.0) {
                TripReading(miles, context.getString(R.string.tourist_unit_miles), TWO_DECIMALS)
            } else {
                TripReading(
                    UnitScale.metresToFeet(metres),
                    context.getString(R.string.tourist_unit_feet),
                    NO_DECIMALS,
                )
            }
        }
    }

    private fun convertSpeed(kmh: Double): Double = when (system) {
        UnitSystem.METRIC -> kmh
        UnitSystem.IMPERIAL -> UnitScale.kmhToMph(kmh)
    }

    private fun unitLabel(@StringRes metricRes: Int, @StringRes imperialRes: Int): String = when (system) {
        UnitSystem.METRIC -> context.getString(metricRes)
        UnitSystem.IMPERIAL -> context.getString(imperialRes)
    }

    private fun decimal(value: Double, pattern: String): String =
        String.format(Locale.getDefault(), pattern, value)

    data class TileValue(val value: String, val unit: String)

    private data class TripReading(val value: Double, val unit: String, val decimals: String)

    companion object {
        const val NO_VALUE = "--"
        private const val NO_TIME = "--:--"
        private const val NO_DECIMALS = "%.0f"
        private const val ONE_DECIMAL = "%.1f"
        private const val TWO_DECIMALS = "%.2f"
        private const val PACE_PATTERN = "%d:%02d"
        private const val DURATION_PATTERN = "%d:%02d:%02d"
        private const val MILLIS_PER_SECOND = 1000L
        private const val SECONDS_PER_MINUTE = 60L
        private const val SECONDS_PER_HOUR = 3600L
        private const val FULL_CIRCLE = 360f
        private const val HALF_SECTOR = 22.5f
        private const val SECTOR = 45f
        private val CARDINAL_RES = intArrayOf(
            R.string.tourist_cardinal_n,
            R.string.tourist_cardinal_ne,
            R.string.tourist_cardinal_e,
            R.string.tourist_cardinal_se,
            R.string.tourist_cardinal_s,
            R.string.tourist_cardinal_sw,
            R.string.tourist_cardinal_w,
            R.string.tourist_cardinal_nw,
        )
    }
}
