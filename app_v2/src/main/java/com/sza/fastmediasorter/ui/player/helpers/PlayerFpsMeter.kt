package com.sza.fastmediasorter.ui.player.helpers

import android.os.SystemClock
import android.view.Choreographer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

/**
 * S0021: Lightweight frames-per-second meter for the flat 2D player overlay.
 *
 * Uses [Choreographer.FrameCallback] (frame-presentation rate, the user-relevant
 * smoothness signal). On every ~500 ms window the average FPS is recomputed and
 * pushed to [fps]. The producer side is allocation-free in the hot path: the
 * frame callback only updates two longs.
 *
 * Thread model: [start]/[stop] must be called on the main thread because they
 * touch [Choreographer.getInstance]. The [fps] flow is safe to collect from any
 * coroutine context.
 */
class PlayerFpsMeter {

    private val _fps = MutableStateFlow(0)
    val fps: StateFlow<Int> = _fps.asStateFlow()

    internal var running: Boolean = false
        private set

    private var frameCount: Int = 0
    private var windowStartElapsedMs: Long = 0L

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            frameCount++
            val nowMs = SystemClock.elapsedRealtime()
            val elapsedMs = nowMs - windowStartElapsedMs
            if (elapsedMs >= WINDOW_MS) {
                _fps.value = (frameCount * 1000f / elapsedMs).toInt()
                frameCount = 0
                windowStartElapsedMs = nowMs
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun start() {
        if (running) return
        running = true
        frameCount = 0
        windowStartElapsedMs = SystemClock.elapsedRealtime()
        Choreographer.getInstance().postFrameCallback(frameCallback)
        Timber.v("PlayerFpsMeter: started")
    }

    fun stop() {
        if (!running) return
        running = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        _fps.value = 0
        Timber.v("PlayerFpsMeter: stopped")
    }

    companion object {
        private const val WINDOW_MS = 500L
    }
}
