package com.sza.fastmediasorter.wear.domain.stopwatch

import java.util.Locale

/**
 * Renders an elapsed millisecond count for the reading on the watch.
 *
 * The fields that change many times a second - hundredths and seconds - are fixed width, so the string
 * keeps its length between two repaints and the digits do not slide sideways while the measurement runs.
 *
 * S2825: output matches the phone formatter for the same input. The watch cannot reuse that object, so
 * the agreement is held by tests over the same cases rather than by a shared file.
 */
object WearStopwatchTimeFormatter {

    /** `M:SS.hh` below an hour, `H:MM:SS.hh` from an hour up. Hours are never wrapped into days. */
    fun format(elapsedMillis: Long): String {
        val safeMillis = elapsedMillis.coerceAtLeast(0L)
        val hundredths = safeMillis % MILLIS_PER_SECOND / MILLIS_PER_HUNDREDTH
        val totalSeconds = safeMillis / MILLIS_PER_SECOND
        val seconds = totalSeconds % SECONDS_PER_MINUTE
        val totalMinutes = totalSeconds / SECONDS_PER_MINUTE
        val minutes = totalMinutes % MINUTES_PER_HOUR
        val hours = totalMinutes / MINUTES_PER_HOUR
        return if (hours > 0L) {
            String.format(Locale.US, FORMAT_WITH_HOURS, hours, minutes, seconds, hundredths)
        } else {
            String.format(Locale.US, FORMAT_MINUTES, minutes, seconds, hundredths)
        }
    }

    private const val MILLIS_PER_SECOND = 1000L
    private const val MILLIS_PER_HUNDREDTH = 10L
    private const val SECONDS_PER_MINUTE = 60L
    private const val MINUTES_PER_HOUR = 60L

    private const val FORMAT_MINUTES = "%d:%02d.%02d"
    private const val FORMAT_WITH_HOURS = "%d:%02d:%02d.%02d"
}
