package com.sza.fastmediasorter.ui.player.helpers

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * S1510: the periodic half of stream diagnostics. Everything else this player logs is an event -
 * first frame, a batch of dropped frames, a decoder swapping in - which answers "what happened" and
 * never "how is it going right now". This samples on a fixed cadence so the rendered frame rate can
 * be read as a difference over elapsed time, the one number the user is actually complaining about.
 *
 * Knows nothing about the arithmetic: it takes a reading, hands the pair to [StreamStatsFormatter]
 * and prints whatever comes back. Knows only about lifetime, which is the part the formatter must not
 * have to think about.
 *
 * Bound to the stream session, not to visibility: it starts where the analytics listener is attached
 * and stops where that listener is removed, so it cannot outlive the player it samples. That is also
 * why it behaves identically below API 24, where the player is not released on stop.
 */
internal class StreamStatsSampler(
    private val scope: CoroutineScope,
    private val intervalMs: Long,
    private val readSample: () -> StreamStatsSample?,
) {

    private var job: Job? = null

    fun start() {
        if (job != null) return
        job = scope.launch {
            var previous: StreamStatsSample? = null
            while (isActive) {
                delay(intervalMs)
                val current = readSample() ?: continue
                StreamStatsFormatter.line(previous, current)?.let { Timber.i(it) }
                previous = current
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    companion object {
        /**
         * The reference implementation this was modelled on samples every 2 s because four resilience
         * rules feed off that one tick and need the reaction time. Here the only consumer is the log,
         * so 5 s carries the same resolution at a fraction of the written volume - and it is the
         * cadence this app's own retired telemetry tick already used (S1148).
         */
        const val SAMPLE_INTERVAL_MS = 5_000L
    }
}
