package com.sza.fastmediasorter.ui.xr.helpers

import android.os.Handler

/**
 * S1239: the immersive HUD's only periodic repaint, and the reason it is allowed to exist.
 *
 * Everything else on the strip is state-driven on purpose - S0290's rule is that native-driven
 * uploads must never run per frame, and S0964 coalesces bursts into one upload per 100 ms. A
 * position bar has no state change to hang off, so it needs a clock. [TICK_MS] is the slowest rate
 * that still keeps a seconds-granularity label honest, and it is two orders of magnitude rarer
 * than the ~12 ms ray cadence that rule was written against.
 *
 * [shouldRun] is re-read on every tick instead of being trusted once at [start]. The ticker then
 * stops itself within one tick when the strip is hidden, playback pauses, or a scrub takes the
 * handle over - even if the host never says so.
 */
class HudSeekProgressTicker(
    private val handler: Handler,
    private val shouldRun: () -> Boolean,
    private val onTick: () -> Unit,
) {

    private var running = false

    private val tickRunnable = object : Runnable {
        override fun run() {
            if (!running) return
            if (!shouldRun()) {
                running = false
                return
            }
            onTick()
            handler.postDelayed(this, TICK_MS)
        }
    }

    /** Idempotent - calling it again while ticking does not stack a second message. */
    fun start() {
        if (running || !shouldRun()) return
        running = true
        handler.postDelayed(tickRunnable, TICK_MS)
    }

    fun stop() {
        running = false
        handler.removeCallbacks(tickRunnable)
    }

    private companion object {
        const val TICK_MS = 1_000L
    }
}
