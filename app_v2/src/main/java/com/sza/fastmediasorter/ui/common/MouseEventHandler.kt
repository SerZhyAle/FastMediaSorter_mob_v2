package com.sza.fastmediasorter.ui.common

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import com.sza.fastmediasorter.ui.common.input.InputAction
import timber.log.Timber

/**
 * Shared mouse parser. Emits both legacy per-button callbacks (for
 * surfaces that still rely on them) and semantic [InputAction]s for
 * surfaces that have migrated to the shared input layer.
 *
 * Features covered:
 * - single / double left click
 * - right click with cursor coordinates (anchored context menus)
 * - middle click -> [InputAction.ToggleFavourite] where supported
 * - mouse wheel with Shift / Ctrl modifiers -> [InputAction.ScrollWheel]
 * - hover enter / exit for tooltip-capable surfaces
 * - XButton1 / XButton2 (back / forward)
 */
class MouseEventHandler(
    private val callbacks: MouseEventCallbacks,
) {

    interface MouseEventCallbacks {
        /** Single left click on [view]. */
        fun onSingleClick(view: View) {}

        /** Double left click on [view] (open). */
        fun onDoubleClick(view: View) {}

        /** Right click on [view] at cursor position [x], [y] (view-local). */
        fun onRightClick(view: View, x: Float, y: Float) {}

        /** Middle click on [view]. Default contract: toggle favourite. */
        fun onMiddleClick(view: View) {}

        /** Mouse wheel scroll. Positive [deltaY] = up. */
        fun onScrollWheel(view: View, deltaY: Float, deltaX: Float, withShift: Boolean, withCtrl: Boolean) {}

        /** Hover pointer entered [view]. */
        fun onHoverEnter(view: View) {}

        /** Hover pointer left [view]. */
        fun onHoverExit(view: View) {}

        /** Mouse back button (XButton1). */
        fun onNavigateBack(view: View) {}

        /** Mouse forward button (XButton2). */
        fun onNavigateForward(view: View) {}

        /**
         * Surface-level semantic action hook. Defaults map to the
         * individual callbacks above, but surfaces that already live on
         * the semantic layer can override this and ignore the rest.
         */
        fun onInputAction(view: View, action: InputAction): Boolean = false
    }

    companion object {
        private const val DOUBLE_CLICK_THRESHOLD_MS = 300L
        private const val TAG = "MouseEventHandler"
    }

    private var lastClickTime = 0L
    private var lastClickView: View? = null

    /**
     * Entry point for `setOnTouchListener`. Returns true if the event
     * was consumed as a mouse interaction.
     */
    fun handleMotionEvent(view: View, event: MotionEvent): Boolean {
        if (!isMouseEvent(event)) return false

        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> handleMouseDown(view, event)
            MotionEvent.ACTION_UP -> handleMouseUp(view, event)
            MotionEvent.ACTION_BUTTON_PRESS -> handleButtonPress(view, event)
            else -> false
        }
    }

    /**
     * Entry point for `setOnGenericMotionListener`. Handles wheel and
     * hover events that do not arrive through the touch pipeline.
     */
    fun handleGenericMotionEvent(view: View, event: MotionEvent): Boolean {
        return when (event.actionMasked) {
            MotionEvent.ACTION_SCROLL -> handleScroll(view, event)
            MotionEvent.ACTION_HOVER_ENTER -> {
                callbacks.onHoverEnter(view)
                false
            }
            MotionEvent.ACTION_HOVER_EXIT -> {
                callbacks.onHoverExit(view)
                false
            }
            else -> false
        }
    }

    private fun isMouseEvent(event: MotionEvent): Boolean {
        val source = event.source
        return (source and InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE ||
            (source and InputDevice.SOURCE_STYLUS) == InputDevice.SOURCE_STYLUS ||
            event.buttonState != 0
    }

    private fun handleMouseDown(view: View, event: MotionEvent): Boolean {
        if (isRightClick(event)) {
            Timber.d("%s: right-click at (%.1f,%.1f)", TAG, event.x, event.y)
            callbacks.onRightClick(view, event.x, event.y)
            callbacks.onInputAction(view, InputAction.ShowContextMenuAt(event.x, event.y))
            return true
        }
        if (isMiddleClick(event)) {
            Timber.d("%s: middle-click", TAG)
            callbacks.onMiddleClick(view)
            callbacks.onInputAction(view, InputAction.ToggleFavourite)
            return true
        }
        if (isBackButton(event)) {
            callbacks.onNavigateBack(view)
            callbacks.onInputAction(view, InputAction.MouseNavigateBack)
            return true
        }
        if (isForwardButton(event)) {
            callbacks.onNavigateForward(view)
            callbacks.onInputAction(view, InputAction.MouseNavigateForward)
            return true
        }
        return false
    }

    private fun handleMouseUp(view: View, event: MotionEvent): Boolean {
        if (isRightClick(event) || isMiddleClick(event) || isBackButton(event) || isForwardButton(event)) {
            return false
        }
        val now = System.currentTimeMillis()
        val since = now - lastClickTime
        val sameView = lastClickView == view
        return if (sameView && since < DOUBLE_CLICK_THRESHOLD_MS) {
            lastClickTime = 0L
            lastClickView = null
            callbacks.onDoubleClick(view)
            callbacks.onInputAction(view, InputAction.DoubleClickOpen)
            true
        } else {
            lastClickTime = now
            lastClickView = view
            callbacks.onSingleClick(view)
            true
        }
    }

    private fun handleButtonPress(view: View, event: MotionEvent): Boolean {
        // Some mice deliver wheel-only presses; fall back to generic handling.
        return handleMouseDown(view, event)
    }

    private fun handleScroll(view: View, event: MotionEvent): Boolean {
        val vertical = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
        val horizontal = event.getAxisValue(MotionEvent.AXIS_HSCROLL)
        if (vertical == 0f && horizontal == 0f) return false
        val metaState = event.metaState
        val shift = (metaState and KeyEvent.META_SHIFT_ON) != 0
        val ctrl = (metaState and KeyEvent.META_CTRL_ON) != 0
        callbacks.onScrollWheel(view, vertical, horizontal, shift, ctrl)
        return callbacks.onInputAction(
            view,
            InputAction.ScrollWheel(
                deltaY = vertical,
                deltaX = horizontal,
                withShift = shift,
                withCtrl = ctrl,
            ),
        ) || true
    }

    private fun isRightClick(event: MotionEvent): Boolean =
        (event.buttonState and MotionEvent.BUTTON_SECONDARY) != 0

    private fun isMiddleClick(event: MotionEvent): Boolean =
        (event.buttonState and MotionEvent.BUTTON_TERTIARY) != 0

    private fun isBackButton(event: MotionEvent): Boolean =
        (event.buttonState and MotionEvent.BUTTON_BACK) != 0

    private fun isForwardButton(event: MotionEvent): Boolean =
        (event.buttonState and MotionEvent.BUTTON_FORWARD) != 0

    /** Reset transient double-click state (call when view is recycled). */
    fun reset() {
        lastClickTime = 0L
        lastClickView = null
    }

    /**
     * Convenience wrapper for `setOnTouchListener`. Falls back to
     * [fallbackTouchListener] when the event is not a mouse event.
     */
    fun createTouchListener(
        fallbackTouchListener: View.OnTouchListener? = null,
    ): View.OnTouchListener = View.OnTouchListener { view, event ->
        if (event.action == MotionEvent.ACTION_UP) view.performClick()
        if (handleMotionEvent(view, event)) true
        else fallbackTouchListener?.onTouch(view, event) ?: false
    }

    /**
     * Convenience wrapper for `setOnGenericMotionListener`.
     */
    fun createGenericMotionListener(): View.OnGenericMotionListener =
        View.OnGenericMotionListener { view, event -> handleGenericMotionEvent(view, event) }
}
