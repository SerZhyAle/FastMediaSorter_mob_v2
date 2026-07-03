package com.sza.fastmediasorter.core.screencapture

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S0774: process-wide live state of the screen video recording session. The foreground recording
 * service (screenCapture source set) flips it; MainActivity (src/main) observes [isRecording] to show
 * or hide the in-app stop card. The start instant is kept here so the card timer keeps counting across
 * the Activity being backgrounded and re-entered while recording continues in the foreground service.
 */
@Singleton
class ScreenRecordingStateController @Inject constructor() {

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    @Volatile
    var startedAtElapsedRealtimeMs: Long = 0L
        private set

    // Elapsed time is recomputed from these instants (never accumulated in a live in-memory ticker)
    // so it survives MainActivity being recreated while the foreground service keeps recording.
    @Volatile
    private var accumulatedPausedMs: Long = 0L

    @Volatile
    private var pausedAtElapsedRealtimeMs: Long = 0L

    fun markStarted() {
        startedAtElapsedRealtimeMs = SystemClock.elapsedRealtime()
        accumulatedPausedMs = 0L
        _isPaused.value = false
        _isRecording.value = true
    }

    fun markPaused() {
        if (_isPaused.value) return
        pausedAtElapsedRealtimeMs = SystemClock.elapsedRealtime()
        _isPaused.value = true
    }

    fun markResumed() {
        if (!_isPaused.value) return
        accumulatedPausedMs += SystemClock.elapsedRealtime() - pausedAtElapsedRealtimeMs
        _isPaused.value = false
    }

    fun markStopped() {
        _isRecording.value = false
        _isPaused.value = false
        accumulatedPausedMs = 0L
    }

    /** Elapsed recording time excluding any paused span(s), recomputed from stored instants. */
    fun elapsedMs(nowElapsedRealtimeMs: Long = SystemClock.elapsedRealtime()): Long {
        val ongoingPauseMs = if (_isPaused.value) nowElapsedRealtimeMs - pausedAtElapsedRealtimeMs else 0L
        return nowElapsedRealtimeMs - startedAtElapsedRealtimeMs - accumulatedPausedMs - ongoingPauseMs
    }
}
