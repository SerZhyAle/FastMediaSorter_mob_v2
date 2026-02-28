package com.sza.fastmediasorter.ui.player.helpers

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import kotlin.math.abs

interface PlayerGestureCallback {
    fun onSwipeLeft()
    fun onSwipeRight()
    fun onSwipeUp()
    fun onSwipeDown()

    fun onSingleTap(x: Float, y: Float)
    fun onDoubleTap(x: Float, y: Float)
    fun onLongPress(x: Float, y: Float)

    fun onPinch(scaleFactor: Float, focusX: Float, focusY: Float)
    fun onZoomRequested(targetScale: Float, focusX: Float, focusY: Float)

    fun getCurrentScale(): Float
    fun getMinScale(): Float
    fun getMaxScale(): Float

    fun canHandleGestures(): Boolean
}

class PlayerGestureManager(
    context: Context,
    private val callback: PlayerGestureCallback
) {

    private val gestureDetector = GestureDetector(context, GestureListener())
    private val scaleDetector = ScaleGestureDetector(context, ScaleListener())

    private var isScaling = false

    fun onTouchEvent(event: MotionEvent): Boolean {
        if (!callback.canHandleGestures()) {
            return false
        }

        val scaleHandled = scaleDetector.onTouchEvent(event)
        val gestureHandled = if (isScaling) false else gestureDetector.onTouchEvent(event)

        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            isScaling = false
        }

        return scaleHandled || gestureHandled
    }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {

        override fun onDown(e: MotionEvent): Boolean = true

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            callback.onSingleTap(e.x, e.y)
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            callback.onDoubleTap(e.x, e.y)
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            callback.onLongPress(e.x, e.y)
        }

        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            val start = e1 ?: return false

            val deltaX = e2.x - start.x
            val deltaY = e2.y - start.y

            val absDeltaX = abs(deltaX)
            val absDeltaY = abs(deltaY)

            if (absDeltaX < MIN_SWIPE_DISTANCE_PX && absDeltaY < MIN_SWIPE_DISTANCE_PX) {
                return false
            }

            if (absDeltaX > absDeltaY) {
                if (abs(velocityX) < MIN_SWIPE_VELOCITY_PX) return false
                if (deltaX > 0f) callback.onSwipeRight() else callback.onSwipeLeft()
            } else {
                if (abs(velocityY) < MIN_SWIPE_VELOCITY_PX) return false
                if (deltaY > 0f) callback.onSwipeDown() else callback.onSwipeUp()
            }

            return true
        }
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {

        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            isScaling = true
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val scaleFactor = detector.scaleFactor
            callback.onPinch(scaleFactor, detector.focusX, detector.focusY)

            val targetScale = (callback.getCurrentScale() * scaleFactor)
                .coerceIn(callback.getMinScale(), callback.getMaxScale())

            callback.onZoomRequested(targetScale, detector.focusX, detector.focusY)
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            isScaling = false
        }
    }

    companion object {
        private const val MIN_SWIPE_DISTANCE_PX = 120f
        private const val MIN_SWIPE_VELOCITY_PX = 200f
    }
}
