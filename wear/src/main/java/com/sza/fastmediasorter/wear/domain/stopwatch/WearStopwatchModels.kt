package com.sza.fastmediasorter.wear.domain.stopwatch

import kotlin.math.abs

/**
 * One recorded lap of a participant.
 *
 * Both times are kept because the reader wants both and neither can be recovered from the other alone:
 * [totalMillis] is the measurement from its start, [splitMillis] the distance from the lap before it.
 */
data class WearStopwatchLap(
    val index: Int,
    val totalMillis: Long,
    val splitMillis: Long
)

/**
 * One participant of a measurement.
 *
 * A running participant carries the instant it started; a stopped one carries only what it accumulated.
 */
data class WearStopwatchParticipant(
    val index: Int,
    val startedAtMillis: Long? = null,
    val accumulatedMillis: Long = 0L,
    val laps: List<WearStopwatchLap> = emptyList()
) {

    val isRunning: Boolean get() = startedAtMillis != null

    /** Nothing to reset: no time accumulated, nothing running, no laps recorded. */
    val isPristine: Boolean
        get() = startedAtMillis == null && accumulatedMillis == 0L && laps.isEmpty()

    /**
     * S2825: elapsed time is DERIVED from a monotonic instant and never stored.
     *
     * A watch screen goes dark on wrist-down without warning, so a counter that has to keep ticking to
     * stay right would lose exactly the time the user was not looking. Nothing ticks here - the reading
     * is recomputed from the start instant every time it is drawn.
     */
    fun elapsedAt(nowMillis: Long): Long {
        val running = startedAtMillis?.let { nowMillis - it } ?: 0L
        return (accumulatedMillis + running).coerceAtLeast(0L)
    }
}

/** The whole measurement: one independent participant per region of the screen. */
data class WearStopwatchState(val participants: List<WearStopwatchParticipant>) {

    val anyRunning: Boolean get() = participants.any { it.isRunning }

    val isPristine: Boolean get() = participants.all { it.isPristine }

    companion object {

        /** The phone offers the same three counts, and the watch is meant to be recognisable after it. */
        val ALLOWED_COUNTS = listOf(1, 2, 4)

        const val DEFAULT_COUNT = 1

        /**
         * An unknown count is snapped rather than rejected: it arrives from a store, and a value the app
         * no longer offers is a stale key, not a condition worth failing a screen over.
         */
        fun snapCount(count: Int): Int =
            ALLOWED_COUNTS.minByOrNull { abs(it - count) } ?: DEFAULT_COUNT

        fun initial(count: Int): WearStopwatchState =
            WearStopwatchState(List(snapCount(count)) { WearStopwatchParticipant(index = it) })
    }
}
