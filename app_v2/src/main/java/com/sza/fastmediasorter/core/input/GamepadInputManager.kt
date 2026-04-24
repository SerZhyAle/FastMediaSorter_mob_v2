package com.sza.fastmediasorter.core.input

import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.sza.fastmediasorter.domain.model.GamepadAction
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gamepad event → [GamepadAction] router used by [PlayerActivity],
 * [VrPlayerActivity], [MainActivity] and [BrowseActivity].
 *
 * Motivation: architecture rules forbid putting input logic directly into
 * Activities. This class centralises the raw Android gamepad mapping so each
 * Activity only needs to decide *where* the resulting [GamepadAction] goes, not
 * *how* the raw `KEYCODE_BUTTON_*` / `AXIS_*` events translate.
 *
 * Behaviour:
 * - [handleKeyEvent] returns a [GamepadAction] for button presses on
 *   [InputDevice.SOURCE_GAMEPAD] / [InputDevice.SOURCE_JOYSTICK] only. Events
 *   from BT keyboards / mice are ignored (they have their own handlers).
 *   Consumed on [KeyEvent.ACTION_DOWN] to match Android gamepad conventions,
 *   with repeat-press throttling for analog triggers.
 * - [handleMotionEvent] processes analog sticks (AXIS_Y, AXIS_RZ/AXIS_Z) with a
 *   hard [DEADZONE] and rate-limits continuous events so volume / seek do not
 *   flood from stick jitter.
 */
@Singleton
class GamepadInputManager @Inject constructor() {

    @Volatile private var lastAnalogSeekMs = 0L
    @Volatile private var lastAnalogVolumeMs = 0L
    @Volatile private var lastTriggerSeekMs = 0L

    /**
     * Host surface hint — lets the manager return the right sub-tree for ambiguous buttons
     * (A = PlayPause in player, A = Select in browser).
     */
    enum class Surface { PLAYER, BROWSER }

    fun handleKeyEvent(event: KeyEvent, surface: Surface): GamepadAction? {
        if (!event.isFromGamepad()) return null
        // Consume on ACTION_DOWN — matches platform guidance for gamepad buttons.
        if (event.action != KeyEvent.ACTION_DOWN) return null
        return when (surface) {
            Surface.PLAYER -> mapPlayerButton(event)
            Surface.BROWSER -> mapBrowserButton(event)
        }
    }

    fun handleMotionEvent(event: MotionEvent, surface: Surface): GamepadAction? {
        if (!event.isFromGamepad()) return null
        if (event.action != MotionEvent.ACTION_MOVE) return null
        return when (surface) {
            Surface.PLAYER -> mapPlayerMotion(event)
            Surface.BROWSER -> mapBrowserMotion(event)
        }
    }

    private fun mapPlayerButton(event: KeyEvent): GamepadAction? = when (event.keyCode) {
        KeyEvent.KEYCODE_BUTTON_A -> GamepadAction.PlayerAction.PlayPause
        KeyEvent.KEYCODE_BUTTON_B -> GamepadAction.PlayerAction.Exit
        KeyEvent.KEYCODE_BUTTON_X -> GamepadAction.PlayerAction.Next
        KeyEvent.KEYCODE_BUTTON_Y -> GamepadAction.PlayerAction.Prev
        KeyEvent.KEYCODE_BUTTON_L1 -> rateLimitedTriggerSeek(forward = false)
        KeyEvent.KEYCODE_BUTTON_R1 -> rateLimitedTriggerSeek(forward = true)
        KeyEvent.KEYCODE_BUTTON_START -> GamepadAction.PlayerAction.ToggleHud
        KeyEvent.KEYCODE_BUTTON_SELECT -> GamepadAction.PlayerAction.ToggleHints
        else -> null
    }

    private fun mapBrowserButton(event: KeyEvent): GamepadAction? = when (event.keyCode) {
        KeyEvent.KEYCODE_BUTTON_A -> GamepadAction.BrowserAction.Select
        KeyEvent.KEYCODE_BUTTON_B -> GamepadAction.BrowserAction.Back
        KeyEvent.KEYCODE_BUTTON_X -> GamepadAction.BrowserAction.MultiSelect
        KeyEvent.KEYCODE_BUTTON_Y -> GamepadAction.BrowserAction.ContextMenu
        KeyEvent.KEYCODE_BUTTON_START -> GamepadAction.BrowserAction.Search
        KeyEvent.KEYCODE_BUTTON_L1 -> GamepadAction.BrowserAction.SwitchTab(forward = false)
        KeyEvent.KEYCODE_BUTTON_R1 -> GamepadAction.BrowserAction.SwitchTab(forward = true)
        else -> null
    }

    private fun mapPlayerMotion(event: MotionEvent): GamepadAction? {
        val leftY = event.getCenteredAxis(MotionEvent.AXIS_Y)
        val rightY = event.getCenteredAxis(MotionEvent.AXIS_RZ, fallback = MotionEvent.AXIS_Z)
        // Volume on left stick — up = positive Y inverted (Android reports up as negative).
        if (leftY.isOutsideDeadzone()) {
            return rateLimitedVolume(up = leftY < 0f)
        }
        // Seek on right stick — deflection scales the seek amount.
        if (rightY.isOutsideDeadzone()) {
            return rateLimitedAnalogSeek(rightY)
        }
        return null
    }

    private fun mapBrowserMotion(event: MotionEvent): GamepadAction? {
        // D-pad-equivalent navigation from the left stick is delegated to Android's
        // built-in focus search — returning null lets the Activity call super.
        // We do NOT emit BrowserAction.Select here; analog sticks only drive focus.
        return null
    }

    private fun rateLimitedTriggerSeek(forward: Boolean): GamepadAction.PlayerAction.Seek? {
        val now = SystemClock.uptimeMillis()
        if (now - lastTriggerSeekMs < TRIGGER_SEEK_INTERVAL_MS) return null
        lastTriggerSeekMs = now
        val delta = if (forward) TRIGGER_SEEK_MS else -TRIGGER_SEEK_MS
        return GamepadAction.PlayerAction.Seek(delta)
    }

    private fun rateLimitedVolume(up: Boolean): GamepadAction.PlayerAction.Volume? {
        val now = SystemClock.uptimeMillis()
        if (now - lastAnalogVolumeMs < ANALOG_VOLUME_INTERVAL_MS) return null
        lastAnalogVolumeMs = now
        return GamepadAction.PlayerAction.Volume(up)
    }

    private fun rateLimitedAnalogSeek(deflection: Float): GamepadAction.PlayerAction.Seek? {
        val now = SystemClock.uptimeMillis()
        if (now - lastAnalogSeekMs < ANALOG_SEEK_INTERVAL_MS) return null
        lastAnalogSeekMs = now
        // Scale seek by how far the stick is deflected beyond the deadzone, then
        // invert — pushing the stick up (negative Y) should seek forward.
        val magnitude = (kotlin.math.abs(deflection) - DEADZONE) / (1f - DEADZONE)
        val ms = (MAX_ANALOG_SEEK_MS * magnitude).toLong().coerceAtLeast(MIN_ANALOG_SEEK_MS)
        return GamepadAction.PlayerAction.Seek(if (deflection < 0f) ms else -ms)
    }

    private fun KeyEvent.isFromGamepad(): Boolean =
        (source and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
            (source and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK

    private fun MotionEvent.isFromGamepad(): Boolean =
        (source and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK ||
            (source and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD

    private fun MotionEvent.getCenteredAxis(axis: Int, fallback: Int? = null): Float {
        val device = device ?: return 0f
        val range = device.getMotionRange(axis, source)
            ?: fallback?.let { device.getMotionRange(it, source) }
            ?: return 0f
        val value = if (range.axis == axis) getAxisValue(axis) else getAxisValue(range.axis)
        return if (kotlin.math.abs(value) < range.flat) 0f else value
    }

    private fun Float.isOutsideDeadzone(): Boolean = kotlin.math.abs(this) >= DEADZONE

    companion object {
        /** Hard deadzone for analog sticks to ignore rest-position jitter. */
        const val DEADZONE = 0.15f
        /** Minimum interval between analog seek emissions (smooth, non-flooding). */
        const val ANALOG_SEEK_INTERVAL_MS = 100L
        /** Minimum interval between analog volume emissions. */
        const val ANALOG_VOLUME_INTERVAL_MS = 150L
        /** Minimum interval between L1/R1 trigger seeks when the user holds the button. */
        const val TRIGGER_SEEK_INTERVAL_MS = 120L
        /** Step size for L1/R1 seek. */
        const val TRIGGER_SEEK_MS = 10_000L
        /** Maximum per-tick analog seek magnitude when stick is fully deflected. */
        const val MAX_ANALOG_SEEK_MS = 5_000L
        /** Minimum analog seek magnitude when stick is just past the deadzone. */
        const val MIN_ANALOG_SEEK_MS = 250L
    }
}
