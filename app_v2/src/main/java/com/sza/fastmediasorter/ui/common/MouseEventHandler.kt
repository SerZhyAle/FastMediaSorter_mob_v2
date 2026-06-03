package com.sza.fastmediasorter.ui.common

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import com.sza.fastmediasorter.core.input.KeyBindingManager
import com.sza.fastmediasorter.domain.input.CommandId
import com.sza.fastmediasorter.domain.input.InputSurface as DomainInputSurface
import com.sza.fastmediasorter.domain.input.InputTrigger
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
 * - middle click → [InputAction.ToggleFavourite] where supported
 * - mouse wheel with Shift / Ctrl modifiers → [InputAction.ScrollWheel]
 * - hover enter / exit for tooltip-capable surfaces
 * - XButton1 / XButton2 (back / forward)
 *
 * Secondary, tertiary, back, and forward buttons are resolved through
 * [keyBindingManager] so user bindings are respected. Left-click and
 * scroll-wheel are not remappable in v1 (see spec §7 + §10).
 *
 * @param keyBindingManager Resolver for remappable mouse buttons. Null = legacy-only mode.
 * @param surface Domain surface used when resolving (default: PLAYER).
 */
class MouseEventHandler(
    private val keyBindingManager: KeyBindingManager?,
    private val surface: DomainInputSurface = DomainInputSurface.PLAYER,
    private val callbacks: MouseEventCallbacks,
    /**
     * When false, the primary (left) click is NOT consumed: [handleMouseUp] returns false so the
     * event keeps propagating to the view under the cursor for normal click handling. Activity-level
     * dispatch (see [ActivityMouseDispatchHelper]) sets this false - it only owns secondary buttons,
     * wheel and hover; stealing the left click there breaks click delivery to every control. Surfaces
     * that genuinely want a left-click action (player tap-to-toggle, etc.) keep the default true.
     */
    private val consumePrimaryClick: Boolean = true,
) {

    /** Legacy constructor for callers that have not yet migrated to the resolver path. */
    constructor(
        callbacks: MouseEventCallbacks,
        consumePrimaryClick: Boolean = true,
    ) : this(
        keyBindingManager = null,
        surface = DomainInputSurface.PLAYER,
        callbacks = callbacks,
        consumePrimaryClick = consumePrimaryClick,
    )

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
        // S0289: do NOT rely on event.source - on the Android emulator, Quest3 controller
        // pointer and TV/Chromebook devices with touchpad the touchscreen-injected events
        // can carry SOURCE_MOUSE in event.source while still being finger taps. Misclassifying
        // them as mouse-up consumes the UP and breaks click delivery to MaterialButton etc.
        // getToolType(pointerIndex) reports the real input tool per pointer and is the only
        // reliable discriminator across these devices.
        val tool = event.getToolType(0)
        if (tool == MotionEvent.TOOL_TYPE_FINGER) return false
        return tool == MotionEvent.TOOL_TYPE_MOUSE ||
            tool == MotionEvent.TOOL_TYPE_STYLUS ||
            tool == MotionEvent.TOOL_TYPE_ERASER ||
            event.buttonState != 0
    }

    private fun handleMouseDown(view: View, event: MotionEvent): Boolean {
        // ACTION_DOWN: actionButton == 0 for primary, non-zero for secondary buttons on some devices.
        // Secondary buttons are reliably reported via ACTION_BUTTON_PRESS (API 23+); check here
        // only as a fallback for devices that skip that event.
        val button = event.actionButton
        if (button != 0 && button != MotionEvent.BUTTON_PRIMARY) {
            return dispatchSecondaryButton(view, event, button)
        }
        return false
    }

    private fun handleMouseUp(view: View, event: MotionEvent): Boolean {
        // Skip UP for non-primary button releases.
        val released = event.actionButton
        if (released != 0 && released != MotionEvent.BUTTON_PRIMARY) return false
        if (released == 0 && event.buttonState != 0) return false
        // Activity-level dispatch must not steal the primary (left) click from the view under the
        // cursor - consuming the UP here stops super.dispatchTouchEvent and breaks click delivery to
        // buttons, dropdowns and every other control. Only surfaces that opt in consume the click.
        if (!consumePrimaryClick) {
            Timber.d("S0333: mouse primary click passthrough")
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
        val button = event.actionButton
        if (button == 0 || button == MotionEvent.BUTTON_PRIMARY) return false
        return dispatchSecondaryButton(view, event, button)
    }

    /**
     * Dispatch a secondary button press via [keyBindingManager] (resolver path) or
     * fall back to the shared legacy defaults when the current surface has not
     * been migrated onto the resolver path yet.
     * Wheel is not remappable in v1 (see spec §7 + §10 "analog threshold UX" item).
     */
    private fun dispatchSecondaryButton(view: View, event: MotionEvent, button: Int): Boolean {
        val manager = keyBindingManager
        if (manager != null) {
            val trigger = InputTrigger.MouseButton(button)
            val commandId = manager.resolve(trigger, surface) ?: return false
            Timber.d("%s: mouse button=%d resolved → %s", TAG, button, commandId)
            return when (commandId) {
                CommandId.NEXT_FILE -> {
                    callbacks.onNavigateForward(view)
                    callbacks.onInputAction(view, InputAction.MouseNavigateForward)
                    true
                }
                CommandId.PREVIOUS_FILE -> {
                    callbacks.onNavigateBack(view)
                    callbacks.onInputAction(view, InputAction.MouseNavigateBack)
                    true
                }
                CommandId.FAVOURITE -> {
                    callbacks.onMiddleClick(view)
                    callbacks.onInputAction(view, InputAction.ToggleFavourite)
                    true
                }
                CommandId.FILE_OPS -> {
                    callbacks.onRightClick(view, event.x, event.y)
                    callbacks.onInputAction(view, InputAction.ShowContextMenuAt(event.x, event.y))
                    true
                }
                else -> {
                    // Resolved but no specific legacy bridge - action consumed; callers may
                    // intercept via onInputAction(view, ShowContextMenu) for catch-all right-click.
                    true
                }
            }
        }

        return when (button) {
            MotionEvent.BUTTON_SECONDARY -> {
                callbacks.onRightClick(view, event.x, event.y)
                callbacks.onInputAction(view, InputAction.ShowContextMenuAt(event.x, event.y))
                true
            }
            MotionEvent.BUTTON_TERTIARY -> {
                callbacks.onMiddleClick(view)
                callbacks.onInputAction(view, InputAction.ToggleFavourite)
                true
            }
            MotionEvent.BUTTON_BACK -> {
                callbacks.onNavigateBack(view)
                callbacks.onInputAction(view, InputAction.MouseNavigateBack)
                true
            }
            MotionEvent.BUTTON_FORWARD -> {
                callbacks.onNavigateForward(view)
                callbacks.onInputAction(view, InputAction.MouseNavigateForward)
                true
            }
            else -> false
        }
    }

    private fun handleScroll(view: View, event: MotionEvent): Boolean {
        val vertical = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
        val horizontal = event.getAxisValue(MotionEvent.AXIS_HSCROLL)
        if (vertical == 0f && horizontal == 0f) return false
        val metaState = event.metaState
        val shift = (metaState and KeyEvent.META_SHIFT_ON) != 0
        val ctrl = (metaState and KeyEvent.META_CTRL_ON) != 0
        callbacks.onScrollWheel(view, vertical, horizontal, shift, ctrl)
        // Wheel is not remappable in v1 (see spec §7 + §10 "analog threshold UX" item).
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
