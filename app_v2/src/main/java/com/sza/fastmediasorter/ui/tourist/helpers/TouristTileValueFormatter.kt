package com.sza.fastmediasorter.ui.tourist.helpers

import android.content.Context
import androidx.annotation.StringRes
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.format.QuantityFormatter
import com.sza.fastmediasorter.domain.model.Quantity
import com.sza.fastmediasorter.domain.model.UnitScale
import com.sza.fastmediasorter.domain.model.UnitSystem
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

    private companion object {
        const val NO_VALUE = "--"
        const val NO_TIME = "--:--"
        const val NO_DECIMALS = "%.0f"
        const val ONE_DECIMAL = "%.1f"
        const val TWO_DECIMALS = "%.2f"
    }
}
