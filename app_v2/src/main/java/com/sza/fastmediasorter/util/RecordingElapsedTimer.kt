package com.sza.fastmediasorter.util

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * S0566/S0774: elapsed-time ticker shared by every in-app recording indicator (camera video capture,
 * screen video recording, quick voice capture). Counts wall-clock time while a recording is active,
 * freezing the accumulated total across pause/resume so the displayed `mm:ss` matches the file
 * duration. Hosts whose recordings outlive the current Activity can instead supply
 * [elapsedTimeSourceMs] so the timer re-renders from an external source of truth. Main-thread
 * Handler based; the host calls [stop] on pause/destroy so no work leaks.
 */
class RecordingElapsedTimer(
    private val elapsedTimeSourceMs: (() -> Long)? = null,
    private val onTick: (formatted: String) -> Unit,
) {

    private val handler = Handler(Looper.getMainLooper())
    private var accumulatedMs = 0L
    private var lastResumeAt = 0L
    private var running = false
    private var paused = false

    private val ticker = object : Runnable {
        override fun run() {
            if (!running) return
            onTick(format(currentElapsedMs()))
            handler.postDelayed(this, TICK_INTERVAL_MS)
        }
    }

    fun start() {
        if (elapsedTimeSourceMs == null) {
            accumulatedMs = 0L
            lastResumeAt = SystemClock.elapsedRealtime()
        }
        running = true
        paused = false
        onTick(format(currentElapsedMs()))
        handler.removeCallbacks(ticker)
        handler.postDelayed(ticker, TICK_INTERVAL_MS)
    }

    fun pause() {
        if (!running || paused) return
        if (elapsedTimeSourceMs == null) {
            accumulatedMs += SystemClock.elapsedRealtime() - lastResumeAt
        }
        paused = true
        handler.removeCallbacks(ticker)
        onTick(format(currentElapsedMs()))
    }

    fun resume() {
        if (!running || !paused) return
        if (elapsedTimeSourceMs == null) {
            lastResumeAt = SystemClock.elapsedRealtime()
        }
        paused = false
        handler.removeCallbacks(ticker)
        handler.postDelayed(ticker, TICK_INTERVAL_MS)
    }

    fun stop() {
        running = false
        paused = false
        handler.removeCallbacks(ticker)
    }

    private fun currentElapsedMs(): Long =
        elapsedTimeSourceMs?.invoke() ?: if (paused) {
            accumulatedMs
        } else {
            accumulatedMs + (SystemClock.elapsedRealtime() - lastResumeAt)
        }

    private fun format(ms: Long): String {
        val totalSeconds = ms / MILLIS_PER_SECOND
        val seconds = totalSeconds % SECONDS_PER_MINUTE
        val minutes = (totalSeconds / SECONDS_PER_MINUTE) % MINUTES_PER_HOUR
        val hours = totalSeconds / SECONDS_PER_HOUR
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    private companion object {
        val MILLIS_PER_SECOND = TimeUnit.SECONDS.toMillis(1L)
        val SECONDS_PER_MINUTE = TimeUnit.MINUTES.toSeconds(1L)
        val MINUTES_PER_HOUR = TimeUnit.HOURS.toMinutes(1L)
        val SECONDS_PER_HOUR = TimeUnit.HOURS.toSeconds(1L)
        const val TICK_INTERVAL_MS = 250L
    }
}
