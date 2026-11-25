package com.sza.fastmediasorter.ui.common

import android.content.res.Resources
import android.graphics.Rect
import android.view.MotionEvent
import android.view.TouchDelegate
import android.view.View

/**
 * Row action targets under UI-SIZING 1.2 rule 4: ordinary mode keeps a 48 dp hit target around a glyph that keeps
 * its drawn size, compact mode keeps the declared 24 dp floor and is never drawn smaller.
 */
object RowTouchTarget {
    private const val ORDINARY_TARGET_DP = 48
    private const val COMPACT_TARGET_DP = 24

    fun dpToPx(resources: Resources, dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    fun targetPx(resources: Resources, compact: Boolean): Int =
        dpToPx(resources, if (compact) COMPACT_TARGET_DP else ORDINARY_TARGET_DP)

    /** Padding that keeps a glyph of [glyphDp] centered in the target of the mode. */
    fun glyphPaddingPx(resources: Resources, compact: Boolean, glyphDp: Int): Int =
        (targetPx(resources, compact) - dpToPx(resources, glyphDp)) / 2

    /** Writes the layout params back only on a change: a recycled holder re-binds the same size on every scroll. */
    fun resize(view: View, sizePx: Int) {
        val params = view.layoutParams ?: return
        if (params.width != sizePx || params.height != sizePx) {
            params.width = sizePx
            params.height = sizePx
            view.layoutParams = params
        }
    }

    /**
     * Grows the touch area of [target] to [minimumPx] on each axis without moving its drawn bounds or its layout
     * slot; zero removes it. The area is a delegate on the target's parent, so a sibling that is itself touched keeps
     * its own events and the grown area never takes a neighbouring target's touch.
     */
    fun expandHitArea(target: View, minimumPx: Int) {
        val parent = target.parent as? View ?: return
        parent.touchDelegate = if (minimumPx > 0) LiveBoundsTouchDelegate(target, minimumPx) else null
    }
}

/**
 * Reads the target's bounds when a gesture starts instead of when the delegate is installed, because a tile can
 * change width without a rebind and a bound captured at bind time would then point at empty space.
 */
private class LiveBoundsTouchDelegate(
    private val target: View,
    private val minimumPx: Int
) : TouchDelegate(Rect(), target) {

    private var gesture: TouchDelegate? = null

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_DOWN) {
            gesture = grownBounds()?.let { TouchDelegate(it, target) }
        }
        val handled = gesture?.onTouchEvent(event) ?: false
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            gesture = null
        }
        return handled
    }

    private fun grownBounds(): Rect? {
        if (target.width <= 0) return null
        val bounds = Rect(target.left, target.top, target.right, target.bottom)
        val growX = (minimumPx - bounds.width()).coerceAtLeast(0) / 2
        val growY = (minimumPx - bounds.height()).coerceAtLeast(0) / 2
        bounds.inset(-growX, -growY)
        return bounds
    }
}
