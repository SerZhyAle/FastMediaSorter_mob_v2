package com.sza.fastmediasorter.wear.domain.tourist

/**
 * S4185: the watch's Tourist tally counter - the running count, the number a reset returns to, and the
 * finished counts kept as dated history. The rules are the phone's (S4184); the two modules share no code,
 * so the model is a copy rather than a dependency.
 *
 * [sessionStartedAtMillis] is null until the first change after a reset, so a reset with nothing counted
 * archives nothing and the history holds only counts the user actually made.
 */
data class WearTouristCounterState(
    val value: Long = 0L,
    val startValue: Long = 0L,
    val sessionStartedAtMillis: Long? = null,
    val extraStepsVisible: Boolean = false,
    val history: List<WearTouristCounterRecord> = emptyList(),
) {

    /** The count never goes below zero: -1 at zero is a no-op rather than a negative tally. */
    fun plus(delta: Long, nowMillis: Long): WearTouristCounterState {
        val next = (value + delta).coerceIn(0L, MAX_VALUE)
        if (next == value) return this
        return copy(value = next, sessionStartedAtMillis = sessionStartedAtMillis ?: nowMillis)
    }

    fun reset(nowMillis: Long): WearTouristCounterState {
        val started = sessionStartedAtMillis
        val archived = if (started != null) {
            (listOf(WearTouristCounterRecord(value, started, nowMillis)) + history).take(HISTORY_LIMIT)
        } else {
            history
        }
        return copy(value = startValue, sessionStartedAtMillis = null, history = archived)
    }

    /** A new starting number also becomes the current count; the session it interrupts is not archived. */
    fun withStartValue(start: Long): WearTouristCounterState {
        val clamped = start.coerceIn(0L, MAX_VALUE)
        return copy(value = clamped, startValue = clamped, sessionStartedAtMillis = null)
    }

    fun clearHistory(): WearTouristCounterState = copy(history = emptyList())

    companion object {
        const val HISTORY_LIMIT = 100

        /** Nine digits keep the count readable on the tile and safely inside the preferences encoding. */
        const val MAX_VALUE = 999_999_999L

        val EXTRA_STEPS: List<Long> = listOf(3L, 5L, 10L, 50L, 100L, 1000L)
    }
}

data class WearTouristCounterRecord(
    val value: Long,
    val startedAtMillis: Long,
    val endedAtMillis: Long,
)
