package com.sza.fastmediasorter.core.format

import android.content.Context
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.Quantity
import com.sza.fastmediasorter.domain.model.UnitScale
import com.sza.fastmediasorter.domain.model.UnitSystem
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToLong

/**
 * S2795: the one place a user-facing quantity turns into a string.
 *
 * Two things are deliberately not inputs here. The device's clock-format switch is not consulted at
 * all - the app's [UnitSystem] decides between a 12- and a 24-hour clock, which is what makes the
 * metric system mean 24 hours even on a device set to 12. The locale does not decide field order
 * either - [UnitScale] carries the reason that rules out the platform's own pattern builder - so the
 * year-month-day order does not depend on the interface language.
 *
 * Patterns are cached per system and per locale because this runs on the draw path of file lists and
 * of a ticking clock, where rebuilding a formatter per call is measurable.
 */
@Singleton
class QuantityFormatter @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    private val patternCache = HashMap<PatternKey, SimpleDateFormat>()

    fun format(quantity: Quantity, system: UnitSystem): String = when (quantity) {
        is Quantity.Instant -> formatMoment(quantity.epochMillis, system, Field.TIME)
        is Quantity.Date -> formatMoment(quantity.epochMillis, system, Field.DATE)
        is Quantity.DateTime -> formatMoment(quantity.epochMillis, system, Field.DATE_TIME)
        is Quantity.Speed -> formatSpeed(quantity.metresPerSecond, system, spoken = false)
        is Quantity.Altitude -> formatAltitude(quantity.metres, system, spoken = false)
        is Quantity.Distance -> formatDistance(quantity.metres, system, spoken = false)
    }

    /**
     * The spoken form exists so a screen reader announces the value and its unit as one phrase instead
     * of reading a number and leaving the abbreviation as a silent suffix.
     */
    fun contentDescription(quantity: Quantity, system: UnitSystem): String = when (quantity) {
        is Quantity.Speed -> formatSpeed(quantity.metresPerSecond, system, spoken = true)
        is Quantity.Altitude -> formatAltitude(quantity.metres, system, spoken = true)
        is Quantity.Distance -> formatDistance(quantity.metres, system, spoken = true)
        else -> format(quantity, system)
    }

    private fun formatMoment(epochMillis: Long, system: UnitSystem, field: Field): String {
        val locale = Locale.getDefault()
        val formatter = patternCache.getOrPut(PatternKey(system, field, locale)) {
            SimpleDateFormat(pattern(system, field), locale)
        }
        return formatter.format(Date(epochMillis))
    }

    /** Exposed so a surface taking a pattern rather than a string - a `TextClock` - uses the same one. */
    fun pattern(system: UnitSystem, field: Field): String = when (field) {
        Field.TIME -> UnitScale.timePattern(system)
        Field.DATE -> UnitScale.datePattern(system)
        Field.DATE_TIME -> UnitScale.dateTimePattern(system)
    }

    private fun formatSpeed(metresPerSecond: Double, system: UnitSystem, spoken: Boolean): String {
        val converted = when (system) {
            UnitSystem.METRIC -> UnitScale.metresPerSecondToKmh(metresPerSecond)
            UnitSystem.IMPERIAL -> UnitScale.metresPerSecondToMph(metresPerSecond)
        }
        val unitRes = when {
            system == UnitSystem.METRIC && spoken -> R.string.unit_speed_kmh_spoken
            system == UnitSystem.METRIC -> R.string.unit_speed_kmh
            spoken -> R.string.unit_speed_mph_spoken
            else -> R.string.unit_speed_mph
        }
        return context.getString(unitRes, rounded(converted))
    }

    private fun formatAltitude(metres: Double, system: UnitSystem, spoken: Boolean): String {
        val converted = when (system) {
            UnitSystem.METRIC -> metres
            UnitSystem.IMPERIAL -> UnitScale.metresToFeet(metres)
        }
        val unitRes = when {
            system == UnitSystem.METRIC && spoken -> R.string.unit_altitude_metres_spoken
            system == UnitSystem.METRIC -> R.string.unit_altitude_metres
            spoken -> R.string.unit_altitude_feet_spoken
            else -> R.string.unit_altitude_feet
        }
        return context.getString(unitRes, rounded(converted))
    }

    private fun formatDistance(metres: Double, system: UnitSystem, spoken: Boolean): String {
        val converted = when (system) {
            UnitSystem.METRIC -> UnitScale.metresToKilometres(metres)
            UnitSystem.IMPERIAL -> UnitScale.metresToMiles(metres)
        }
        val unitRes = when {
            system == UnitSystem.METRIC && spoken -> R.string.unit_distance_kilometres_spoken
            system == UnitSystem.METRIC -> R.string.unit_distance_kilometres
            spoken -> R.string.unit_distance_miles_spoken
            else -> R.string.unit_distance_miles
        }
        // A distance keeps one decimal: rounded to a whole kilometre, a walk reads as zero until it
        // is over halfway through the first one.
        return context.getString(unitRes, String.format(Locale.getDefault(), "%.1f", converted))
    }

    /** Both surfaces showing these values today are single-line gadgets with no room for decimals. */
    private fun rounded(value: Double): String =
        String.format(Locale.getDefault(), "%d", value.roundToLong())

    enum class Field { TIME, DATE, DATE_TIME }

    /**
     * S2840: the values themselves, never their names. Composing the key out of `system.name` and
     * `field.name` made every durable-enum scan read this in-memory cache as a storage path, and a keep
     * rule answering that would have pinned a name nothing writes down.
     */
    private data class PatternKey(val system: UnitSystem, val field: Field, val locale: Locale)
}
