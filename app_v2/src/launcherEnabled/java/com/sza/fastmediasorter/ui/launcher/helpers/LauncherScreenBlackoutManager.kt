package com.sza.fastmediasorter.ui.launcher.helpers

import android.app.Activity
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import timber.log.Timber
import java.lang.ref.WeakReference

/**
 * S1741: manages the launcher-private screen blackout overlay and inactivity countdown.
 *
 * Requirements:
 * 1. Shows an app-private opaque black overlay after [timeoutSeconds] of user inactivity.
 * 2. Inactivity countdown runs only while the launcher activity is started and timeoutSeconds > 0.
 * 3. First touch, mouse, key or D-pad input while overlay is visible dismisses the overlay and
 *    is consumed without passing to underlying desktop views.
 * 4. Does not mutate system bars, does not touch Android system timeout or DevicePolicyManager.
 */
class LauncherScreenBlackoutManager(
    private val activityRef: WeakReference<Activity>
) {
    var isOverlayVisible: Boolean = false
        private set

    private var timeoutSeconds: Int = 0
    private var isStarted: Boolean = false
    private var overlayView: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private val blackoutRunnable = Runnable { showBlackout() }

    fun updateTimeout(seconds: Int) {
        val coerced = seconds.coerceAtLeast(0)
        if (timeoutSeconds == coerced) return
        timeoutSeconds = coerced
        if (timeoutSeconds <= 0) {
            hideBlackout()
            stopTimer()
        } else if (isStarted && !isOverlayVisible) {
            resetTimer()
        }
    }

    fun onStart() {
        isStarted = true
        if (timeoutSeconds > 0 && !isOverlayVisible) {
            resetTimer()
        }
    }

    fun onStop() {
        isStarted = false
        stopTimer()
    }

    fun onDestroy() {
        isStarted = false
        stopTimer()
        removeOverlayView()
    }

    /**
     * Intercepts touch input before dispatch.
     * @return true if the event was consumed to dismiss the blackout overlay, false otherwise.
     */
    fun onDispatchTouchEvent(event: MotionEvent): Boolean {
        if (isOverlayVisible) {
            if (event.action == MotionEvent.ACTION_DOWN ||
                event.action == MotionEvent.ACTION_POINTER_DOWN
            ) {
                hideBlackout()
            }
            return true
        }
        if (event.action == MotionEvent.ACTION_DOWN ||
            event.action == MotionEvent.ACTION_MOVE ||
            event.action == MotionEvent.ACTION_UP
        ) {
            resetTimer()
        }
        return false
    }

    fun onDispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (isOverlayVisible) {
            if (event.action == MotionEvent.ACTION_DOWN ||
                event.action == MotionEvent.ACTION_HOVER_ENTER
            ) {
                hideBlackout()
            }
            return true
        }
        resetTimer()
        return false
    }

    fun onDispatchKeyEvent(event: KeyEvent): Boolean {
        if (isOverlayVisible) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                hideBlackout()
            }
            return true
        }
        if (event.action == KeyEvent.ACTION_DOWN) {
            resetTimer()
        }
        return false
    }

    fun resetTimer() {
        if (!isStarted || timeoutSeconds <= 0 || isOverlayVisible) return
        handler.removeCallbacks(blackoutRunnable)
        handler.postDelayed(blackoutRunnable, timeoutSeconds * MILLIS_PER_SECOND)
    }

    private fun stopTimer() {
        handler.removeCallbacks(blackoutRunnable)
    }

    fun showBlackout() {
        if (isOverlayVisible || !isStarted || timeoutSeconds <= 0) return
        val activity = activityRef.get()
        val decorView = activity?.window?.decorView as? ViewGroup ?: return

        val view = View(activity).apply {
            setBackgroundColor(Color.BLACK)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            isClickable = true
            isFocusable = true
            fitsSystemWindows = false
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    hideBlackout()
                }
                true
            }
        }
        decorView.addView(view)
        overlayView = view
        isOverlayVisible = true
        Timber.d("Blackout overlay shown (timeout=%ds)", timeoutSeconds)
    }

    fun hideBlackout() {
        if (!isOverlayVisible) return
        removeOverlayView()
        isOverlayVisible = false
        Timber.d("Blackout overlay hidden")
        resetTimer()
    }

    private fun removeOverlayView() {
        val activity = activityRef.get()
        val decorView = activity?.window?.decorView as? ViewGroup
        overlayView?.let { decorView?.removeView(it) }
        overlayView = null
    }

    companion object {
        private const val MILLIS_PER_SECOND = 1000L
    }
}
