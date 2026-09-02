package com.sza.fastmediasorter.ui.launcher.helpers

import android.graphics.Rect
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import timber.log.Timber
import kotlin.math.abs

/**
 * Recognizes directional swipes on free desktop space.
 *
 * Yields to scrolling: an upward swipe fires only at the lower desktop boundary, and a downward
 * swipe fires only at the upper boundary. Horizontal swipes do not inspect vertical scrolling.
 *
 * The Activity forwards its raw event stream before child dispatch, because the device can route a desktop
 * touch around the NestedScrollView. A gesture still starts only within the scroll viewport.
 */
class LauncherAllAppsGestureManager(
    private val container: View,
    private val viewport: View,
    private val isEnabled: () -> Boolean,
    private val isTouchOnInteractiveCell: (MotionEvent) -> Boolean,
    private val onSwipe: (DesktopSwipeDirection) -> Unit,
    private val onDoubleTap: (() -> Unit)? = null,
) {

    enum class DesktopSwipeDirection {
        UP,
        DOWN,
        LEFT,
        RIGHT,
    }

    private val configuration = ViewConfiguration.get(container.context)

    private val viewportBounds = Rect()

    private var gestureStartedOnFreeDesktop = false

    private val detector = GestureDetector(
        container.context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onDoubleTap(e: MotionEvent): Boolean = handleDoubleTap()

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean = handleFling(e1, e2, velocityX, velocityY)
        }
    )

    /** Receives the Activity's raw event stream without changing normal child dispatch. */
    fun onTouchEvent(event: MotionEvent) {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            gestureStartedOnFreeDesktop = isTouchWithinViewport(event) && !isTouchOnInteractiveCell(event)
            if (gestureStartedOnFreeDesktop) {
                Timber.d("S2262: desktop gesture dispatch")
            }
        }
        if (!gestureStartedOnFreeDesktop) return
        detector.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            gestureStartedOnFreeDesktop = false
        }
    }

    private fun handleFling(
        e1: MotionEvent?,
        e2: MotionEvent,
        velocityX: Float,
        velocityY: Float,
    ): Boolean = e1
        ?.takeIf { isEnabled() }
        ?.let { classifyDirection(it, e2, velocityX, velocityY) }
        ?.takeIf(::isEligibleAtViewportBoundary)
        ?.also { direction ->
            Timber.d("S2221: dispatch desktop swipe direction=%s", direction)
            onSwipe(direction)
        } != null

    private fun handleDoubleTap(): Boolean {
        val callback = onDoubleTap
        if (callback == null || !isEnabled()) return false
        callback()
        return true
    }

    private fun isTouchWithinViewport(event: MotionEvent): Boolean =
        viewport.getGlobalVisibleRect(viewportBounds) &&
            viewportBounds.contains(event.rawX.toInt(), event.rawY.toInt())

    private fun classifyDirection(
        e1: MotionEvent,
        e2: MotionEvent,
        velocityX: Float,
        velocityY: Float,
    ): DesktopSwipeDirection? {
        val distanceX = e2.x - e1.x
        val distanceY = e2.y - e1.y
        return if (abs(distanceY) > abs(distanceX)) {
            verticalDirection(distanceY, velocityY)
        } else if (abs(distanceX) > abs(distanceY)) {
            horizontalDirection(distanceX, velocityX)
        } else {
            null
        }
    }

    private fun verticalDirection(distance: Float, velocity: Float): DesktopSwipeDirection? = when {
        distance < -configuration.scaledPagingTouchSlop &&
            velocity < -configuration.scaledMinimumFlingVelocity -> DesktopSwipeDirection.UP
        distance > configuration.scaledPagingTouchSlop &&
            velocity > configuration.scaledMinimumFlingVelocity -> DesktopSwipeDirection.DOWN
        else -> null
    }

    private fun horizontalDirection(distance: Float, velocity: Float): DesktopSwipeDirection? = when {
        distance < -configuration.scaledPagingTouchSlop &&
            velocity < -configuration.scaledMinimumFlingVelocity -> DesktopSwipeDirection.LEFT
        distance > configuration.scaledPagingTouchSlop &&
            velocity > configuration.scaledMinimumFlingVelocity -> DesktopSwipeDirection.RIGHT
        else -> null
    }

    private fun isEligibleAtViewportBoundary(direction: DesktopSwipeDirection): Boolean = when (direction) {
        DesktopSwipeDirection.UP -> !viewport.canScrollVertically(1)
        DesktopSwipeDirection.DOWN -> !viewport.canScrollVertically(-1)
        DesktopSwipeDirection.LEFT,
        DesktopSwipeDirection.RIGHT,
        -> true
    }
}
