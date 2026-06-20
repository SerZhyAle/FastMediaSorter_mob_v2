package com.sza.fastmediasorter.core.input

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

/**
 * Translates raw gamepad/joystick motion into a discrete [GamepadNavIntent]: left stick → focus
 * move (D-pad equivalent), right stick → continuous scroll. Pure and unit-testable; the time source
 * is injectable so repeat-acceleration intervals can be exercised with a virtual clock. S0508.
 *
 * Stateful (per-Activity instance): tracks the held focus direction and a synthesized repeat counter
 * to accelerate sustained navigation, mirroring the D-pad acceleration model used by
 * [com.sza.fastmediasorter.ui.common.FocusManager] (held repeats past a threshold escalate). Hat axes
 * are intentionally NOT read here - Android delivers the hat switch as `KEYCODE_DPAD_*` key events,
 * which already drive focus traversal; reading hat axes too would double-step.
 */
class GamepadNavigationTranslator(
    private val now: () -> Long = { SystemClock.uptimeMillis() },
) {

    private var lastFocusDirection = 0
    private var focusRepeatCount = 0
    // Half of MIN_VALUE, not MIN_VALUE itself: keeps the first `now() - last` delta a large positive
    // (so the first sample always passes the interval gate) without overflowing the subtraction.
    private var lastFocusEmitMs = Long.MIN_VALUE / 2
    private var lastScrollMs = Long.MIN_VALUE / 2

    /**
     * @return the navigation intent for this motion sample, or null when the event is not a gamepad
     * move, the sticks rest inside the dead-zone, or the emission is rate-limited.
     */
    fun translate(event: MotionEvent): GamepadNavIntent? {
        if (!event.isFromGamepad()) return null
        if (event.action != MotionEvent.ACTION_MOVE) return null

        val focus = resolveFocusMove(event)
        if (focus != null) return focus
        return resolveScroll(event)
    }

    private fun resolveFocusMove(event: MotionEvent): GamepadNavIntent.FocusMove? {
        val x = event.getAxisValue(MotionEvent.AXIS_X)
        // Invert AXIS_Y so stick-up reads as positive, matching GamepadInputManager's convention.
        val y = -event.getAxisValue(MotionEvent.AXIS_Y)
        val direction = when {
            abs(x) <= GamepadInputManager.DEADZONE && abs(y) <= GamepadInputManager.DEADZONE -> 0
            abs(x) >= abs(y) -> if (x > 0f) View.FOCUS_RIGHT else View.FOCUS_LEFT
            else -> if (y > 0f) View.FOCUS_UP else View.FOCUS_DOWN
        }

        if (direction == 0) {
            // Stick returned to rest: reset acceleration so the next deflection starts un-accelerated.
            lastFocusDirection = 0
            focusRepeatCount = 0
            return null
        }

        val timeNow = now()
        if (direction != lastFocusDirection) {
            // Fresh direction (or reversal): emit immediately and restart the repeat counter.
            lastFocusDirection = direction
            focusRepeatCount = 0
            lastFocusEmitMs = timeNow
            return GamepadNavIntent.FocusMove(direction)
        }

        val interval = if (focusRepeatCount >= ACCEL_REPEAT_THRESHOLD) FAST_REPEAT_MS else BASE_REPEAT_MS
        if (timeNow - lastFocusEmitMs < interval) return null
        focusRepeatCount++
        lastFocusEmitMs = timeNow
        return GamepadNavIntent.FocusMove(direction)
    }

    private fun resolveScroll(event: MotionEvent): GamepadNavIntent.Scroll? {
        val z = event.getAxisValue(MotionEvent.AXIS_Z)
        val rz = event.getAxisValue(MotionEvent.AXIS_RZ)
        if (abs(z) <= GamepadInputManager.DEADZONE && abs(rz) <= GamepadInputManager.DEADZONE) return null

        val timeNow = now()
        if (timeNow - lastScrollMs < SCROLL_INTERVAL_MS) return null
        lastScrollMs = timeNow

        // Stick-down (positive RZ) scrolls content down → positive dy, as View.scrollBy expects.
        val dx = scrollStep(z)
        val dy = scrollStep(rz)
        if (dx == 0 && dy == 0) return null
        return GamepadNavIntent.Scroll(dx, dy)
    }

    /** Pixels for one scroll tick: deflection past the dead-zone, normalised then scaled. */
    private fun scrollStep(axisValue: Float): Int {
        val magnitude = abs(axisValue)
        if (magnitude <= GamepadInputManager.DEADZONE) return 0
        val normalised = (magnitude - GamepadInputManager.DEADZONE) / (1f - GamepadInputManager.DEADZONE)
        val px = (MAX_SCROLL_STEP_PX * normalised).toInt()
        return if (axisValue > 0f) px else -px
    }

    private fun MotionEvent.isFromGamepad(): Boolean =
        (source and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK ||
            (source and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD

    companion object {
        /**
         * Repeat count past which held navigation accelerates. Mirrors the D-pad acceleration model
         * of [com.sza.fastmediasorter.ui.common.FocusManager] (its `DPAD_ACCEL_REPEAT_THRESHOLD`);
         * re-declared here to keep this core-layer class free of a ui-layer dependency.
         */
        const val ACCEL_REPEAT_THRESHOLD = 6
        /** Base interval between focus steps while the stick is held (un-accelerated). */
        const val BASE_REPEAT_MS = 140L
        /** Shortened interval once the repeat counter passes the acceleration threshold. */
        const val FAST_REPEAT_MS = 60L
        /** Minimum interval between scroll emissions (~one frame) so motion events do not flood. */
        const val SCROLL_INTERVAL_MS = 16L
        /** Maximum pixels scrolled per tick when a stick is fully deflected. */
        const val MAX_SCROLL_STEP_PX = 48
    }
}
