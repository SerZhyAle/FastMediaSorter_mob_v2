package com.sza.fastmediasorter.wear.ui.apps.motionmonitor

import java.util.Locale

private const val AXIS_SEPARATOR = "  "
private const val AXIS_FORMAT = "%.2f"
private const val HERTZ_FORMAT = "%.1f"
private const val AGE_FORMAT = "%.1f"
private const val MILLIS_PER_SECOND = 1000.0

/**
 * The reading of a motion stream, axis by axis.
 *
 * Two decimals rather than the sensor's full precision: a watch row has no width for it, and S2458 §5.3
 * asks whether events are arriving, not what the seventh decimal of gravity is.
 */
fun formatAxes(values: List<Float>): String =
    values.joinToString(AXIS_SEPARATOR) { String.format(Locale.US, AXIS_FORMAT, it) }

/** The step counter reports a cumulative count as a float; a fractional step would be a lie. */
fun formatStepCount(values: List<Float>): String =
    values.firstOrNull()?.toLong()?.toString().orEmpty()

fun formatHertz(hertz: Double): String = String.format(Locale.US, HERTZ_FORMAT, hertz)

fun formatAgeSeconds(ageMillis: Long): String =
    String.format(Locale.US, AGE_FORMAT, ageMillis / MILLIS_PER_SECOND)
