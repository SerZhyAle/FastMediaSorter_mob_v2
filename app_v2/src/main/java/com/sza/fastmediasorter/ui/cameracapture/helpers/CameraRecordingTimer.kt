package com.sza.fastmediasorter.ui.cameracapture.helpers

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.util.Locale

/**
 * S0566: elapsed-time ticker for the recording overlay. Counts wall-clock time while a recording is
 * active, freezing the accumulated total across pause/resume so the displayed `mm:ss` matches the
 * file duration. Main-thread Handler based; the host calls [stop] on pause/destroy so no work leaks.
 */
class CameraRecordingTimer(
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
        accumulatedMs = 0L
        lastResumeAt = SystemClock.elapsedRealtime()
        running = true
        paused = false
        onTick(format(0L))
        handler.removeCallbacks(ticker)
        handler.postDelayed(ticker, TICK_INTERVAL_MS)
    }

    fun pause() {
        if (!running || paused) return
        accumulatedMs += SystemClock.elapsedRealtime() - lastResumeAt
        paused = true
        handler.removeCallbacks(ticker)
        onTick(format(accumulatedMs))
    }

    fun resume() {
        if (!running || !paused) return
        lastResumeAt = SystemClock.elapsedRealtime()
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
        if (paused) accumulatedMs else accumulatedMs + (SystemClock.elapsedRealtime() - lastResumeAt)

    private fun format(ms: Long): String {
        val totalSeconds = ms / 1000
        val seconds = totalSeconds % 60
        val minutes = (totalSeconds / 60) % 60
        val hours = totalSeconds / 3600
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    private companion object {
        const val TICK_INTERVAL_MS = 250L
    }
}
