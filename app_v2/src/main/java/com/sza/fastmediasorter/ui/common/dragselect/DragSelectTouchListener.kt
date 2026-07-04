package com.sza.fastmediasorter.ui.common.dragselect

import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

/**
 * S0512: minimal drag-select for a [RecyclerView] - no AndroidX SelectionTracker.
 *
 * It only reports adapter-position ranges; the host maps positions to selection via its
 * existing range-select logic. Two entry paths:
 * - Mouse: band-select. Starts on press over an item, or on press over empty area (rubber-band
 *   from the nearest item). Active regardless of host multi-select mode.
 * - Touch: press-drag over items, but only while [Callback.isActive] returns true (the host
 *   gates this on its multi-select mode). S0817: a touch press does NOT immediately capture
 *   the gesture - it stays pending until the finger moves past TouchSlop AND the move is
 *   predominantly horizontal (a sweep across items). A predominantly vertical move is released
 *   back to the RecyclerView so a normal swipe-to-scroll still works while a selection is active.
 *
 * Pure view-layer utility: no Hilt, no ViewModel reference, no focus requests.
 */
class DragSelectTouchListener(
    private val callback: Callback,
) : RecyclerView.OnItemTouchListener {

    interface Callback {
        /** Host decides whether a touch drag-select may start now (mouse always may). */
        fun isActive(): Boolean

        /** A drag-select gesture began anchored at [position]. */
        fun onSelectionStart(position: Int)

        /** The swept range changed; [start] is the anchor, [end] the current item. */
        fun onSelectionRangeChanged(start: Int, end: Int)

        /** The gesture ended (pointer up / cancel). */
        fun onSelectionEnd()
    }

    private var dragging = false
    // Mouse band-select that began over empty area, waiting for the pointer to reach an item.
    private var pendingMouseBand = false
    private var startPosition = RecyclerView.NO_POSITION
    private var lastPosition = RecyclerView.NO_POSITION

    // S0817: a touch press is parked here until it clears TouchSlop and proves horizontal.
    private var pendingPosition = RecyclerView.NO_POSITION
    private var pendingDownX = 0f
    private var pendingDownY = 0f
    private var touchSlop = -1

    private var hostRecyclerView: RecyclerView? = null
    private var autoScrollVelocity = 0
    private var autoScrollRunnable: Runnable? = null

    override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val isMouse = e.getToolType(0) == MotionEvent.TOOL_TYPE_MOUSE
                // Touch may only start a drag-select while the host is in multi-select mode.
                // Mouse may always band-select.
                if (!isMouse && !callback.isActive()) return false
                val position = resolvePosition(rv, e)
                if (position == RecyclerView.NO_POSITION && !isMouse) return false
                beginDrag(rv, position, e, immediate = isMouse)
                // S0817: mouse captures immediately once it has a real anchor (band-select over
                // empty area still waits for the pointer to reach an item). Touch never captures
                // here - it stays pending until ACTION_MOVE clears slop and proves horizontal.
                return isMouse && position != RecyclerView.NO_POSITION
            }

            MotionEvent.ACTION_MOVE -> {
                if (dragging) return true
                if (pendingMouseBand) {
                    val position = resolvePosition(rv, e)
                    if (position != RecyclerView.NO_POSITION) {
                        commitDrag(rv, position)
                        return dragging
                    }
                    return false
                }
                return resolvePendingTouchDrag(rv, e)
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging || pendingMouseBand || pendingPosition != RecyclerView.NO_POSITION) {
                    endDrag()
                    return dragging
                }
                return false
            }
        }
        return false
    }

    /**
     * S0817: decide a parked touch press. Returns true (capture the gesture) only once the finger
     * has moved past TouchSlop with a predominantly horizontal sweep; a predominantly vertical move
     * is released so the RecyclerView scrolls; before slop it stays pending.
     */
    private fun resolvePendingTouchDrag(rv: RecyclerView, e: MotionEvent): Boolean {
        if (pendingPosition == RecyclerView.NO_POSITION) return false
        val dx = e.x - pendingDownX
        val dy = e.y - pendingDownY
        val slop = resolveTouchSlop(rv)
        val pastSlop = dx * dx + dy * dy > slop * slop
        return when {
            !pastSlop -> false
            // Predominantly horizontal sweep: commit the drag-select.
            abs(dx) >= abs(dy) -> {
                commitDrag(rv, pendingPosition)
                dragging
            }
            // Predominantly vertical: this is a scroll, not a drag-select.
            else -> {
                clearPending()
                false
            }
        }
    }

    override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
        when (e.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                if (!dragging) return
                hostRecyclerView = rv
                updateAutoScroll(rv, e)
                val position = resolvePosition(rv, e)
                if (position != RecyclerView.NO_POSITION && position != lastPosition) {
                    lastPosition = position
                    callback.onSelectionRangeChanged(startPosition, position)
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> endDrag()
        }
    }

    override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        if (disallowIntercept) endDrag()
    }

    /**
     * S0817: enter the gesture. Mouse ([immediate]) commits right away (or arms the empty-area
     * band-select); touch only records a pending start and lets ACTION_MOVE apply the slop gate.
     */
    private fun beginDrag(rv: RecyclerView, position: Int, e: MotionEvent, immediate: Boolean) {
        if (immediate) {
            commitDrag(rv, position)
            return
        }
        pendingPosition = position
        pendingDownX = e.x
        pendingDownY = e.y
    }

    private fun commitDrag(rv: RecyclerView, position: Int) {
        pendingMouseBand = position == RecyclerView.NO_POSITION
        if (position == RecyclerView.NO_POSITION) return
        clearPending()
        dragging = true
        pendingMouseBand = false
        startPosition = position
        lastPosition = position
        hostRecyclerView = rv
        callback.onSelectionStart(position)
        callback.onSelectionRangeChanged(position, position)
    }

    private fun endDrag() {
        stopAutoScroll()
        val wasActive = dragging || pendingMouseBand
        dragging = false
        pendingMouseBand = false
        clearPending()
        startPosition = RecyclerView.NO_POSITION
        lastPosition = RecyclerView.NO_POSITION
        if (wasActive) callback.onSelectionEnd()
    }

    private fun clearPending() {
        pendingPosition = RecyclerView.NO_POSITION
        pendingDownX = 0f
        pendingDownY = 0f
    }

    private fun resolveTouchSlop(rv: RecyclerView): Int {
        if (touchSlop < 0) {
            touchSlop = ViewConfiguration.get(rv.context).scaledTouchSlop
        }
        return touchSlop
    }

    private fun resolvePosition(rv: RecyclerView, e: MotionEvent): Int {
        val child = rv.findChildViewUnder(e.x, e.y) ?: return RecyclerView.NO_POSITION
        return rv.getChildAdapterPosition(child)
    }

    // --- Auto-scroll when the pointer is dragged near a vertical edge ---

    private fun updateAutoScroll(rv: RecyclerView, e: MotionEvent) {
        val hotZone = (EDGE_ZONE_DP * rv.resources.displayMetrics.density).toInt()
        val maxStep = (MAX_SCROLL_STEP_DP * rv.resources.displayMetrics.density).toInt()
        val y = e.y
        autoScrollVelocity = when {
            y < hotZone -> -scaledStep(hotZone - y, hotZone, maxStep)
            y > rv.height - hotZone -> scaledStep(y - (rv.height - hotZone), hotZone, maxStep)
            else -> 0
        }
        if (autoScrollVelocity == 0) {
            stopAutoScroll()
        } else if (autoScrollRunnable == null) {
            startAutoScroll(rv, e)
        }
    }

    private fun scaledStep(distanceIntoZone: Float, hotZone: Int, maxStep: Int): Int {
        if (hotZone <= 0) return 0
        val ratio = (abs(distanceIntoZone) / hotZone).coerceIn(0f, 1f)
        return (ratio * maxStep).toInt().coerceAtLeast(1)
    }

    private fun startAutoScroll(rv: RecyclerView, e: MotionEvent) {
        val runnable = object : Runnable {
            override fun run() {
                if (!dragging || autoScrollVelocity == 0) return
                rv.scrollBy(0, autoScrollVelocity)
                // Re-evaluate the item under the (stationary) pointer so the range keeps growing
                // while auto-scrolling without a fresh MOVE event.
                val position = resolvePosition(rv, e)
                if (position != RecyclerView.NO_POSITION && position != lastPosition) {
                    lastPosition = position
                    callback.onSelectionRangeChanged(startPosition, position)
                }
                rv.postOnAnimation(this)
            }
        }
        autoScrollRunnable = runnable
        rv.postOnAnimation(runnable)
    }

    private fun stopAutoScroll() {
        autoScrollRunnable?.let { hostRecyclerView?.removeCallbacks(it) }
        autoScrollRunnable = null
        autoScrollVelocity = 0
    }

    /** Attach to [recyclerView]; safe to call once per RecyclerView. */
    fun attach(recyclerView: RecyclerView) {
        recyclerView.addOnItemTouchListener(this)
    }

    /** Detach and clear any in-flight gesture (call on recycle to avoid leaks). */
    fun detach(recyclerView: RecyclerView) {
        endDrag()
        recyclerView.removeOnItemTouchListener(this)
        hostRecyclerView = null
    }

    private companion object {
        const val EDGE_ZONE_DP = 48f
        const val MAX_SCROLL_STEP_DP = 16f
    }
}
