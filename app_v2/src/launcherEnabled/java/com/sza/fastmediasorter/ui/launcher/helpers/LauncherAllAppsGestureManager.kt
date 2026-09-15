package com.sza.fastmediasorter.ui.launcher.helpers

import android.graphics.Rect
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs

/**
 * Recognizes directional swipes from an eligible surface in its gesture area.
 *
 * Yields to scrolling: an upward swipe fires only at the lower desktop boundary, and a downward
 * swipe fires only at the upper boundary. Horizontal swipes do not inspect vertical scrolling; instead
 * the host may refuse the horizontal axis for a gesture that started inside a horizontally scrolling
 * child of its own gesture area.
 *
 * The Activity forwards its raw event stream before child dispatch, because the device can route a desktop
 * touch around the NestedScrollView. Each host supplies the area and eligibility policy that match
 * its own child interactions.
 */
class LauncherAllAppsGestureManager(
    private val container: View,
    private val viewport: View,
    private val isEnabled: () -> Boolean,
    private val isTouchOnInteractiveCell: (MotionEvent) -> Boolean,
    private val onSwipe: (DesktopSwipeDirection, Boolean) -> Unit,
    private val onDoubleTap: (() -> Unit)? = null,
    private val gestureArea: View = viewport,
    private val isGestureStartAllowed: (MotionEvent) -> Boolean = { event ->
        !isTouchOnInteractiveCell(event)
    },
    // S2728: a host whose surface carries a horizontally scrolling child answers false there, so the
    // child's own scroll is not also read as a desktop swipe. Vertical swipes stay unaffected.
    private val isHorizontalSwipeAllowedAtStart: (MotionEvent) -> Boolean = { true },
) {

    enum class DesktopSwipeDirection {
        UP,
        DOWN,
        LEFT,
        RIGHT,
    }

    private val configuration = ViewConfiguration.get(container.context)

    private val viewportBounds = Rect()

    private var gestureStartedOnEligibleSurface = false

    // Decided at ACTION_DOWN and held for the gesture: by the time the fling is classified the finger has
    // usually left the strip it started in, so the start point is the only trustworthy answer.
    private var horizontalSwipeAllowedForGesture = true

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
            gestureStartedOnEligibleSurface =
                isTouchWithinGestureArea(event) && isGestureStartAllowed(event)
            horizontalSwipeAllowedForGesture = isHorizontalSwipeAllowedAtStart(event)
        }
        if (!gestureStartedOnEligibleSurface) return
        detector.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            gestureStartedOnEligibleSurface = false
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
        ?.takeIf(::isAxisAdmitted)
        ?.also { direction ->
            onSwipe(direction, startedOnRightHalf(e1))
        } != null

    private fun handleDoubleTap(): Boolean {
        val callback = onDoubleTap
        if (callback == null || !isEnabled()) return false
        callback()
        return true
    }

    private fun isTouchWithinGestureArea(event: MotionEvent): Boolean =
        gestureArea.getGlobalVisibleRect(viewportBounds) &&
            viewportBounds.contains(event.rawX.toInt(), event.rawY.toInt())

    private fun startedOnRightHalf(event: MotionEvent): Boolean =
        gestureArea.getGlobalVisibleRect(viewportBounds) && event.rawX >= viewportBounds.centerX()

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

    /** S2728: the axis the gesture ended up on, judged against where it started. */
    private fun isAxisAdmitted(direction: DesktopSwipeDirection): Boolean = when (direction) {
        DesktopSwipeDirection.LEFT,
        DesktopSwipeDirection.RIGHT,
        -> horizontalSwipeAllowedForGesture
        DesktopSwipeDirection.UP,
        DesktopSwipeDirection.DOWN,
        -> true
    }
}
