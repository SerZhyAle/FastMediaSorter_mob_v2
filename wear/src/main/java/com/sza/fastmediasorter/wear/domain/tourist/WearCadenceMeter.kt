package com.sza.fastmediasorter.wear.domain.tourist

import java.util.concurrent.TimeUnit

/**
 * S4146: steps per minute over a sliding window of step-total samples.
 *
 * Null until the window spans enough time to mean something, so a fresh section does not open on a
 * spike. A total lower than the previous one is a steps reset and starts the window over.
 */
class WearCadenceMeter {

    private val window = ArrayDeque<Pair<Long, Long>>()

    fun onSteps(stepTotal: Long, atMillis: Long): Int? {
        val last = window.lastOrNull()
        if (last != null && stepTotal < last.first) {
            window.clear()
        }
        window.addLast(stepTotal to atMillis)
        while (window.size > 1 && atMillis - window.first().second > WINDOW_MS) {
            window.removeFirst()
        }
        return cadence()
    }

    fun clear() {
        window.clear()
    }

    private fun cadence(): Int? {
        val first = window.firstOrNull()
        val last = window.lastOrNull()
        val span = if (first != null && last != null) last.second - first.second else 0L
        return if (first == null || last == null || span < MIN_SPAN_MS) {
            null
        } else {
            ((last.first - first.first) * MILLIS_PER_MINUTE / span).toInt()
        }
    }

    private companion object {
        val WINDOW_MS = TimeUnit.SECONDS.toMillis(60)
        val MIN_SPAN_MS = TimeUnit.SECONDS.toMillis(10)
        val MILLIS_PER_MINUTE = TimeUnit.MINUTES.toMillis(1)
    }
}
