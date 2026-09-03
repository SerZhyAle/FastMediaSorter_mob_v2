package com.sza.fastmediasorter.wear.util

import timber.log.Timber
import java.util.Locale

private const val MILLIS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L
private const val MINUTES_PER_HOUR = 60L

/**
 * S2278: a media duration or playback position as m:ss, rolling over to h:mm:ss past an hour.
 *
 * The module carried four copies of this, and two of them called `String.format` with no [Locale]
 * at all - so on a locale whose default numbering system is not Latin the two player screens
 * printed foreign digits while the media grid, which passed [Locale.US], printed Latin ones for
 * the same kind of value on the same watch. [Locale.US] is the one policy kept: a clock-style
 * duration is a fixed-width figure, not a localised number.
 *
 * A non-positive input formats as `0:00` rather than an empty string, because a player at the very
 * start of a track has a position of zero and must still show a figure. A caller that wants an
 * absent duration to render as nothing - the media grid's badge - tests for that itself.
 */
internal fun formatWearDuration(durationMs: Long): String {
    Timber.d("S2278: formatWearDuration $durationMs ms")
    val totalSeconds = (durationMs / MILLIS_PER_SECOND).coerceAtLeast(0L)
    val minutes = totalSeconds / SECONDS_PER_MINUTE
    val seconds = totalSeconds % SECONDS_PER_MINUTE
    return if (minutes >= MINUTES_PER_HOUR) {
        String.format(
            Locale.US,
            "%d:%02d:%02d",
            minutes / MINUTES_PER_HOUR,
            minutes % MINUTES_PER_HOUR,
            seconds
        )
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}
