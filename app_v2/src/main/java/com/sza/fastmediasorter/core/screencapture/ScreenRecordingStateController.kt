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

    @Volatile
    var startedAtElapsedRealtimeMs: Long = 0L
        private set

    fun markStarted() {
        startedAtElapsedRealtimeMs = SystemClock.elapsedRealtime()
        _isRecording.value = true
    }

    fun markStopped() {
        _isRecording.value = false
    }
}
