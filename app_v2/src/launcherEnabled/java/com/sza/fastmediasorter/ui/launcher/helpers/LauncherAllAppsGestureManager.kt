package com.sza.fastmediasorter.ui.launcher.helpers

import android.annotation.SuppressLint
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs

/**
 * S1401: swipe up on free desktop space to open the full-screen app list.
 *
 * Yields to scrolling: the gesture fires only while the desktop viewport sits at the top, so a
 * scrolled desktop answers the same swipe by scrolling back up first (strategic ADR-4 - the desktop
 * already scrolls, and stealing that scroll is the feature's first-ranked risk).
 *
 * Attached as a touch listener on the container, so a press landing on a cell or an interactive gadget
 * is consumed by that child before this ever sees it - the same way the long press into edit mode
 * already behaves.
 */
class LauncherAllAppsGestureManager(
    private val container: View,
    private val viewport: View,
    private val isEnabled: () -> Boolean,
    private val onOpen: () -> Unit,
) {

    private val configuration = ViewConfiguration.get(container.context)

    private val detector = GestureDetector(
        container.context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean = handleFling(e1, e2, velocityY)
        }
    )

    // Touch listeners have no accessibility equivalent to declare here: the gesture is a shortcut for
    // the taskbar button, which is the accessible path (strategic 3.2).
    @SuppressLint("ClickableViewAccessibility")
    fun attach() {
        container.setOnTouchListener { _, event -> detector.onTouchEvent(event) && false }
    }

    private fun handleFling(e1: MotionEvent?, e2: MotionEvent, velocityY: Float): Boolean {
        if (e1 == null || !isEnabled() || !isDesktopAtTop()) return false
        return isOpeningSwipe(e1, e2, velocityY).also { if (it) onOpen() }
    }

    /** Scrolled down: this swipe belongs to the scroll, not to us. */
    private fun isDesktopAtTop(): Boolean = viewport.scrollY == 0

    private fun isOpeningSwipe(e1: MotionEvent, e2: MotionEvent, velocityY: Float): Boolean {
        val distanceY = e1.y - e2.y
        return distanceY > configuration.scaledPagingTouchSlop &&
            -velocityY > configuration.scaledMinimumFlingVelocity &&
            // A diagonal swipe belongs to whatever the horizontal axis owns.
            distanceY > abs(e1.x - e2.x)
    }
}
