package com.sza.fastmediasorter.domain.model

/**
 * S2795: what each [UnitSystem] means for a [Quantity], and the conversions that follow from it.
 *
 * The conversions live here rather than in the formatter because they are the only arithmetic in the
 * seam and are the part a wrong coefficient would break silently - a plausible number is still a wrong
 * one, so they are pinned by unit tests on their own.
 *
 * Date and time are literal patterns rather than skeletons handed to
 * `DateFormat.getBestDateTimePattern`. That call looks like the locale-aware way to do this, but it
 * takes the FIELD ORDER from the locale and only the field SET from the skeleton, so a metric
 * skeleton renders "05.03.2026" in Russian - the exact divergence this ticket removes. The order is
 * the system's decision here (strategic ADR-2), and these patterns are numeric, so nothing in them
 * needs translating; the locale still selects the digits and the AM/PM marker text.
 */
object UnitScale {

    private const val SECONDS_PER_HOUR = 3600.0
    private const val METRES_PER_KILOMETRE = 1000.0
    private const val METRES_PER_MILE = 1609.344
    private const val METRES_PER_FOOT = 0.3048
    private const val KILOMETRES_PER_MILE = 1.609344
    private const val FAHRENHEIT_PER_CELSIUS = 1.8
    private const val FAHRENHEIT_AT_ZERO_CELSIUS = 32.0

    /** Year first, 24-hour clock. */
    const val PATTERN_DATE_METRIC: String = "yyyy-MM-dd"
    const val PATTERN_TIME_METRIC: String = "HH:mm"

    /** Month first, 12-hour clock with a marker. */
    const val PATTERN_DATE_IMPERIAL: String = "MM/dd/yyyy"
    const val PATTERN_TIME_IMPERIAL: String = "h:mm a"

    fun datePattern(system: UnitSystem): String = when (system) {
        UnitSystem.METRIC -> PATTERN_DATE_METRIC
        UnitSystem.IMPERIAL -> PATTERN_DATE_IMPERIAL
    }

    fun timePattern(system: UnitSystem): String = when (system) {
        UnitSystem.METRIC -> PATTERN_TIME_METRIC
        UnitSystem.IMPERIAL -> PATTERN_TIME_IMPERIAL
    }

    /**
     * Seconds go after the minutes rather than at the end: the imperial pattern ends in the AM/PM
     * marker, so appending would read "2:07 PM:09".
     */
    fun timePattern(system: UnitSystem, withSeconds: Boolean): String {
        val base = timePattern(system)
        return if (withSeconds) base.replace(":mm", ":mm:ss") else base
    }

    /**
     * Day and month without a year, for a surface too narrow to carry one - the desktop clock's
     * second line. Still ordered by the system rather than by the locale, for the reason above.
     */
    fun shortDatePattern(system: UnitSystem): String = when (system) {
        UnitSystem.METRIC -> "MM-dd"
        UnitSystem.IMPERIAL -> "MM/dd"
    }

    /** The two joined, in the order every surface shows them. */
    fun dateTimePattern(system: UnitSystem): String =
        datePattern(system) + ' ' + timePattern(system)

    fun metresPerSecondToKmh(metresPerSecond: Double): Double =
        metresPerSecond * SECONDS_PER_HOUR / METRES_PER_KILOMETRE

    fun metresPerSecondToMph(metresPerSecond: Double): Double =
        metresPerSecond * SECONDS_PER_HOUR / METRES_PER_MILE

    /**
     * S3101: a surface whose state already carries km/h converts from there rather than from metres
     * per second - routing it back through the base unit would round the same reading twice.
     */
    fun kmhToMph(kmh: Double): Double = kmh / KILOMETRES_PER_MILE

    fun celsiusToFahrenheit(celsius: Double): Double =
        celsius * FAHRENHEIT_PER_CELSIUS + FAHRENHEIT_AT_ZERO_CELSIUS

    fun metresToFeet(metres: Double): Double = metres / METRES_PER_FOOT

    fun metresToKilometres(metres: Double): Double = metres / METRES_PER_KILOMETRE

    fun metresToMiles(metres: Double): Double = metres / METRES_PER_MILE
}
