package com.sza.fastmediasorter.core.xr.input

import android.view.KeyEvent
import android.view.MotionEvent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import timber.log.Timber

/**
 * S0249 Phase 05: Android-side input handler that converts any user input event into an exit
 * request for the diagnostic OpenXR session.
 *
 * The handler is shared between three input surfaces:
 * - Hardware key events (`KeyEvent`) — controller buttons routed through Android input, plus
 *   external keyboards on Android XR.
 * - Touch and pointer events (`MotionEvent`) — mouse clicks, taps on hosting Activity.
 * - Native OpenXR action set polling (see `diagnostic_xr_runtime.cpp`) — controller triggers
 *   and buttons not exposed as Android `KeyEvent`s.
 *
 * Any of the three triggers writes to [exitRequested]; callers observe it and tear down the
 * session. A short grace period after first frame present (see [GRACE_PERIOD_MS]) lets the
 * user actually see the image before a stray input dismisses it.
 *
 * The handler is `@Singleton` to align with the native runtime instance lifetime.
 */
@Singleton
class DiagnosticXrInputExitHandler @Inject constructor() {

    private val _exitRequested = MutableSharedFlow<ExitReason>(replay = 0, extraBufferCapacity = 4)

    /** Hot flow that emits whenever an exit is requested. Collectors close the XR session. */
    val exitRequested: SharedFlow<ExitReason> = _exitRequested.asSharedFlow()

    @Volatile private var firstFrameAtElapsedMs: Long = 0L

    /** Called once the OpenXR session presents its first composition layer. */
    fun markFirstFramePresented(nowMs: Long) {
        firstFrameAtElapsedMs = nowMs
    }

    /** Returns true when enough time has passed since [markFirstFramePresented] to treat input as exit. */
    fun isGracePeriodOver(nowMs: Long): Boolean {
        val firstFrame = firstFrameAtElapsedMs
        if (firstFrame == 0L) return false
        return nowMs - firstFrame >= GRACE_PERIOD_MS
    }

    /**
     * Forwards a [KeyEvent] from the hosting Activity. Returns true if the event was consumed
     * as an exit gesture, false otherwise (caller can pass to super-class).
     */
    fun onKeyEvent(event: KeyEvent, nowMs: Long): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false
        if (!isGracePeriodOver(nowMs)) {
            Timber.d("DiagnosticXrInputExitHandler: KeyEvent ${event.keyCode} ignored - grace period")
            return false
        }
        requestExit(ExitReason.KeyEvent(event.keyCode))
        return true
    }

    /**
     * Forwards a [MotionEvent] from the hosting Activity. Returns true if consumed.
     */
    fun onMotionEvent(event: MotionEvent, nowMs: Long): Boolean {
        if (event.action != MotionEvent.ACTION_DOWN) return false
        if (!isGracePeriodOver(nowMs)) {
            Timber.d("DiagnosticXrInputExitHandler: MotionEvent ${event.action} ignored - grace period")
            return false
        }
        requestExit(ExitReason.MotionEvent(event.action))
        return true
    }

    /**
     * Called from JNI when the native OpenXR action set polling detects controller input.
     * The native side already enforces the grace period before raising this call.
     */
    fun onNativeAction(actionPath: String) {
        requestExit(ExitReason.NativeAction(actionPath))
    }

    private fun requestExit(reason: ExitReason) {
        Timber.d("DiagnosticXrInputExitHandler: exit requested by $reason")
        _exitRequested.tryEmit(reason)
    }

    sealed class ExitReason {
        data class KeyEvent(val keyCode: Int) : ExitReason()
        data class MotionEvent(val action: Int) : ExitReason()
        data class NativeAction(val path: String) : ExitReason()
    }

    private companion object {
        const val GRACE_PERIOD_MS = 400L
    }
}
