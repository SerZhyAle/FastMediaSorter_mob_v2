package com.sza.fastmediasorter.ui.launcher.helpers

import android.os.Handler
import android.os.Looper
import timber.log.Timber

/**
 * S1741 / S2388 / S2384: the launcher's inactivity countdown, and nothing else.
 *
 * It draws no surface of its own. When the countdown elapses it asks [onScreenOff] - the launcher's
 * single screen-off decision point - to turn the screen off, exactly as the desktop's double-tap
 * gesture does, so the two paths cannot end in different behaviour (S2384 ADR-1). Where a real device
 * lock is out of reach the decision point raises the desktop's own black screen, which owns its own
 * dismissal; that is why this class consumes no input event and only restarts its timer.
 *
 * The countdown runs regardless of the global keep-screen-awake setting: `preventSleep` tells the
 * SYSTEM not to sleep and does not veto the app's own deliberate screen-off on its own surface. The two
 * device profiles S2384 sets to 30 seconds, `personal_smartphone` and `home_tablet`, both carry
 * `preventSleep` on, so a veto would make the new default inert exactly where it was asked for.
 *
 * S3285 narrows what that elapsed countdown may choose, not whether it runs: while the hold stands, the
 * host asks the decision point for the app's own dark surface and forbids the real device lock, so the
 * desktop dims without the system sleeping behind it.
 *
 * Requirements kept from S1741:
 * 1. The countdown runs only while the launcher activity is started, owns window focus and the
 *    timeout in force - the on-charge one while power is connected (S3284) - is above zero.
 * 2. A dialog, popup or system window taking focus pauses it, and regaining focus starts it over at
 *    full length.
 * 3. It mutates no system bar, no Android system timeout and no DevicePolicyManager.
 *
 * @param onScreenOff the launcher's screen-off decision point.
 */
class LauncherIdleScreenOffManager(
    private val onScreenOff: () -> Unit,
) {
    private var timeoutSeconds: Int = 0

    // S3284: the countdown that replaces [timeoutSeconds] outright while power is connected (owner
    // ruling 2026-09-18) - including its 0, which means the desktop never blacks out on a charger.
    private var timeoutOnChargeSeconds: Int = 0
    private var isCharging: Boolean = false

    private var isStarted: Boolean = false

    // A dialog, a popup or the notification shade lives in its own window, so input there never reaches
    // the activity's dispatch* callbacks and cannot restart the countdown. Without this pause the screen
    // goes off behind that window while the user is reading it.
    private var hasWindowFocus: Boolean = true

    // The screen is already off (locked, or the fallback overlay is up), so the countdown must not fire
    // a second time into a surface that is already dark.
    private var isScreenOff: Boolean = false

    private val handler = Handler(Looper.getMainLooper())
    private val screenOffRunnable = Runnable { fireScreenOff() }

    fun updateTimeouts(defaultSeconds: Int, onChargeSeconds: Int) {
        val coercedDefault = defaultSeconds.coerceAtLeast(0)
        val coercedOnCharge = onChargeSeconds.coerceAtLeast(0)
        if (timeoutSeconds == coercedDefault && timeoutOnChargeSeconds == coercedOnCharge) return
        timeoutSeconds = coercedDefault
        timeoutOnChargeSeconds = coercedOnCharge
        resetTimer()
    }

    /**
     * Restarts the countdown at the length the new power state selects, rather than letting the
     * running one finish: plugging in is a user action on the desktop, and the whole point of the
     * on-charge value is that it takes effect the moment the cable does.
     */
    fun onChargingChanged(charging: Boolean) {
        if (isCharging == charging) return
        isCharging = charging
        Timber.d("S3284: charging=%b, effective timeout=%ds", charging, effectiveTimeoutSeconds())
        resetTimer()
    }

    fun onStart() {
        isStarted = true
        isScreenOff = false
        resetTimer()
    }

    fun onStop() {
        isStarted = false
        stopTimer()
    }

    fun onWindowFocusChanged(focused: Boolean) {
        if (hasWindowFocus == focused) return
        hasWindowFocus = focused
        if (focused) {
            isScreenOff = false
            resetTimer()
        } else {
            stopTimer()
        }
    }

    fun onDestroy() {
        isStarted = false
        stopTimer()
    }

    /** Any input on the desktop restarts the countdown. Nothing is consumed - the caller keeps its event. */
    fun onUserInput() {
        isScreenOff = false
        resetTimer()
    }

    private fun effectiveTimeoutSeconds(): Int = if (isCharging) timeoutOnChargeSeconds else timeoutSeconds

    private fun isEligible(): Boolean =
        isStarted && hasWindowFocus && effectiveTimeoutSeconds() > 0 && !isScreenOff

    private fun resetTimer() {
        handler.removeCallbacks(screenOffRunnable)
        if (!isEligible()) return
        handler.postDelayed(screenOffRunnable, effectiveTimeoutSeconds() * MILLIS_PER_SECOND)
    }

    private fun stopTimer() {
        handler.removeCallbacks(screenOffRunnable)
    }

    private fun fireScreenOff() {
        if (!isEligible()) return
        isScreenOff = true
        Timber.d(
            "Launcher idle timeout elapsed (%ds, charging=%b) - requesting screen off",
            effectiveTimeoutSeconds(),
            isCharging,
        )
        onScreenOff()
    }

    companion object {
        private const val MILLIS_PER_SECOND = 1000L
    }
}
