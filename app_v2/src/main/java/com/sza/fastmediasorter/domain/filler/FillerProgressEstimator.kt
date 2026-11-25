package com.sza.fastmediasorter.domain.filler

/**
 * Turns (bytes written, time) samples into the milliseconds a fill still needs. The speed is read over a
 * recent window instead of since the start: a memory card slows down once its cache is full, and an
 * average over the whole run would keep promising the early, faster finish.
 */
class FillerProgressEstimator(private val windowMillis: Long = WINDOW_MILLIS) {
    private class Sample(val writtenBytes: Long, val atMillis: Long)

    private val samples = ArrayDeque<Sample>()

    fun reset() {
        samples.clear()
    }

    fun record(writtenBytes: Long, nowMillis: Long) {
        samples.addLast(Sample(writtenBytes, nowMillis))
        // The second sample decides: while it is already outside the window the first one is redundant,
        // and what stays first is the newest sample at least a window old, so the span covers the window.
        while (samples.size > 2 && nowMillis - samples[1].atMillis >= windowMillis) {
            samples.removeFirst()
        }
    }

    /** Null while the data is too thin to say anything honest; a number there would only mislead. */
    fun remainingMillis(plannedBytes: Long): Long? {
        val first = samples.firstOrNull() ?: return null
        val last = samples.last()
        val span = last.atMillis - first.atMillis
        val bytes = last.writtenBytes - first.writtenBytes
        return if (span < MIN_SPAN_MILLIS || bytes <= 0) {
            null
        } else {
            (plannedBytes - last.writtenBytes).coerceAtLeast(0) * span / bytes
        }
    }

    private companion object {
        const val WINDOW_MILLIS = 15_000L
        const val MIN_SPAN_MILLIS = 2_000L
    }
}
