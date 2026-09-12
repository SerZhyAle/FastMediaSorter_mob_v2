package com.sza.fastmediasorter.domain.model

/**
 * S2795: a value the user reads whose appearance depends on the measurement system.
 *
 * Each case carries the value in the scale the app stores or receives it in, never a pre-formatted
 * string and never a target unit: choosing the target is the formatter's job, so a new surface cannot
 * accidentally pick a scale of its own. A quantity with only one scale in use - a byte count, a pixel
 * resolution, a transfer rate - deliberately has no case here.
 */
sealed interface Quantity {

    /** A moment shown as clock time only. */
    data class Instant(val epochMillis: Long) : Quantity

    /** A moment shown as a calendar date only. */
    data class Date(val epochMillis: Long) : Quantity

    /** A moment shown as a date followed by clock time. */
    data class DateTime(val epochMillis: Long) : Quantity

    /** Ground speed as the location stack reports it, in metres per second. */
    data class Speed(val metresPerSecond: Double) : Quantity

    /** Altitude as the location stack reports it, in metres. */
    data class Altitude(val metres: Double) : Quantity

    /** A travelled distance, in metres. Shown on the altitude chart's second line. */
    data class Distance(val metres: Double) : Quantity
}
