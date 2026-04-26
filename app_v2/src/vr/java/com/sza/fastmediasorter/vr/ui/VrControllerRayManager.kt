package com.sza.fastmediasorter.vr.ui

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewGroup
import com.sza.fastmediasorter.vr.openxr.XrHand
import timber.log.Timber

/**
 * Controller aim-ray pointer bridge — translates normalised NDC coordinates emitted
 * by the C++ controller aim-pose hit-test into Android `MotionEvent`s delivered to
 * the VR overlay hierarchy (spec_vr-immersive-controls-panel Phase 02).
 *
 * Differences from [VrHandRayManager]:
 *   - No cursor dot — Touch controller users receive hardware LED + haptic feedback.
 *   - Trigger-click latches position at ACTION_DOWN; ACTION_UP fires at the latched
 *     coordinates so mid-hold drift does not shift the target.
 *   - Hover throttle matches VrHandRayManager at [HOVER_THROTTLE_MS] (≈60 Hz).
 *
 * Thread safety: [onControllerPointerMove] is called on the xr-render-thread.
 * All dispatch is hopped to the main looper via [mainHandler].
 */
class VrControllerRayManager(private val activity: Activity) {

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile private var currentX = Float.NaN
    @Volatile private var currentY = Float.NaN
    @Volatile private var lastDownX = Float.NaN
    @Volatile private var lastDownY = Float.NaN

    private var isDown = false
    private var lastHoverMs = 0L

    @Volatile private var released = false

    /**
     * Receive a controller aim-ray hit-test result from the xr-render-thread.
     * NDC ∈ [-1,1]² when the ray intersects the UI plane; outside that range when
     * it misses. Off-plane events suppress hover dispatch without cancelling any
     * in-progress touch sequence.
     */
    fun onControllerPointerMove(hand: Int, ndcX: Float, ndcY: Float) {
        if (released) return
        if (!ndcX.isFinite() || !ndcY.isFinite()) return
        if (ndcX < -1f || ndcX > 1f || ndcY < -1f || ndcY > 1f) {
            currentX = Float.NaN
            currentY = Float.NaN
            return
        }
        // Capture decor dimensions on render thread for the upcoming post.
        val decor = activity.window?.decorView ?: return
        val w = decor.width.toFloat()
        val h = decor.height.toFloat()
        if (w <= 0f || h <= 0f) return

        // NDC → Android pixel coordinates (+Y down).
        val px = (ndcX * 0.5f + 0.5f) * w
        val py = (-ndcY * 0.5f + 0.5f) * h
        currentX = px
        currentY = py

        val now = SystemClock.uptimeMillis()
        if (now - lastHoverMs < HOVER_THROTTLE_MS) return
        lastHoverMs = now

        val capturedX = px
        val capturedY = py
        val capturedHand = hand
        mainHandler.post {
            if (!released) dispatchHover(capturedHand, capturedX, capturedY)
        }
    }

    /**
     * Receive a trigger press/release from the controller input system.
     * [down] = true on first trigger crossing the active threshold; false on release.
     */
    fun onTriggerClick(hand: Int, down: Boolean) {
        if (released) return
        mainHandler.post {
            if (released) return@post
            if (down) {
                val x = currentX
                val y = currentY
                if (!x.isFinite() || !y.isFinite()) {
                    Timber.v("VrControllerRay: trigger down without valid position — ignored (hand=%d)", hand)
                    return@post
                }
                lastDownX = x
                lastDownY = y
                isDown = true
                dispatchTouch(MotionEvent.ACTION_DOWN, x, y)
            } else {
                if (isDown) {
                    dispatchTouch(MotionEvent.ACTION_UP, lastDownX, lastDownY)
                    isDown = false
                }
            }
        }
    }

    fun release() {
        released = true
        mainHandler.post {
            if (isDown) {
                val x = if (lastDownX.isFinite()) lastDownX else 0f
                val y = if (lastDownY.isFinite()) lastDownY else 0f
                dispatchTouch(MotionEvent.ACTION_CANCEL, x, y)
                isDown = false
            }
        }
    }

    // ── Internal ────────────────────────────────────────────────────────────

    private fun dispatchHover(hand: Int, x: Float, y: Float) {
        val decor = activity.window?.decorView as? ViewGroup ?: return
        val event = buildMotionEvent(MotionEvent.ACTION_HOVER_MOVE, x, y)
        try {
            decor.dispatchGenericMotionEvent(event)
        } finally {
            event.recycle()
        }
        Timber.v("VrControllerRay: hover hand=%d px=(%.1f,%.1f)", hand, x, y)
    }

    private fun dispatchTouch(action: Int, x: Float, y: Float) {
        val decor = activity.window?.decorView as? ViewGroup ?: return
        val event = buildMotionEvent(action, x, y)
        try {
            decor.dispatchTouchEvent(event)
        } finally {
            event.recycle()
        }
    }

    private fun buildMotionEvent(action: Int, x: Float, y: Float): MotionEvent {
        val now = SystemClock.uptimeMillis()
        return MotionEvent.obtain(now, now, action, x, y, 0).apply {
            source = android.view.InputDevice.SOURCE_CLASS_POINTER or
                    android.view.InputDevice.SOURCE_TOUCHSCREEN
        }
    }

    companion object {
        const val HOVER_THROTTLE_MS = 16L

        @Suppress("unused")
        private const val UNUSED_LEFT = XrHand.LEFT
    }
}
