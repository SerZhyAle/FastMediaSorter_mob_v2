package com.sza.fastmediasorter.ui.cameracapture.helpers

import android.annotation.SuppressLint
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.abs

/**
 * S0566: viewfinder gesture recognizer for the in-app camera. Wraps one [GestureDetector] (tap,
 * double-tap, fling) and one [ScaleGestureDetector] (pinch) behind a single touch listener so the
 * Activity stays free of gesture math (CLAUDE.md Rule 3).
 *
 * Mapping (ADR-3/ADR-4 of S0566):
 * - single tap -> tap-to-focus at the point;
 * - double tap -> zoom toggle (1x <-> lens maximum);
 * - pinch -> continuous zoom by the scale factor;
 * - vertical fling -> lens switch;
 * - horizontal fling -> photo/video mode switch (the host gates this to multi-capture mode).
 *
 * The gesture targets duplicate the on-screen buttons/tabs, which stay focusable for D-pad/TV/mouse
 * (Rule 16); this manager never removes that parity.
 */
class CameraCaptureGestureManager(
    private val previewView: View,
    private val callbacks: Callbacks,
) {

    /** Host side-effects; the Activity forwards each to the flow/session managers. */
    interface Callbacks {
        fun onTapToFocus(x: Float, y: Float)
        fun onDoubleTapZoom()
        fun onPinchZoom(scaleFactor: Float)
        fun onSwipeLensSwitch()
        fun onSwipeModeSwitch(toNext: Boolean)
    }

    private val density = previewView.resources.displayMetrics.density
    private val minSwipeDistancePx = SWIPE_MIN_DISTANCE_DP * density
    private val minSwipeVelocityPx = SWIPE_MIN_VELOCITY_DP * density

    private val scaleDetector = ScaleGestureDetector(
        previewView.context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                callbacks.onPinchZoom(detector.scaleFactor)
                return true
            }
        },
    )

    private val gestureDetector = GestureDetector(
        previewView.context,
        object : GestureDetector.SimpleOnGestureListener() {
            // Must return true so the detector keeps receiving the rest of the stream (fling/taps).
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                // Keep the accessibility click contract intact, then focus where the user tapped.
                previewView.performClick()
                callbacks.onTapToFocus(e.x, e.y)
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                callbacks.onDoubleTapZoom()
                return true
            }

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float,
            ): Boolean {
                if (e1 == null) return false
                val dx = e2.x - e1.x
                val dy = e2.y - e1.y
                return if (abs(dx) > abs(dy)) {
                    if (abs(dx) < minSwipeDistancePx || abs(velocityX) < minSwipeVelocityPx) return false
                    // Swipe left moves to the next mode (content follows the finger).
                    callbacks.onSwipeModeSwitch(toNext = dx < 0)
                    true
                } else {
                    if (abs(dy) < minSwipeDistancePx || abs(velocityY) < minSwipeVelocityPx) return false
                    callbacks.onSwipeLensSwitch()
                    true
                }
            }
        },
    )

    @SuppressLint("ClickableViewAccessibility")
    fun attach() {
        previewView.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            // Suppress taps/flings while a pinch is mid-gesture so a zoom never doubles as a swipe.
            if (!scaleDetector.isInProgress) {
                gestureDetector.onTouchEvent(event)
            }
            true
        }
    }

    private companion object {
        const val SWIPE_MIN_DISTANCE_DP = 48f
        const val SWIPE_MIN_VELOCITY_DP = 120f
    }
}
