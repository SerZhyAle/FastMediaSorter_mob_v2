package com.sza.fastmediasorter.core.input

import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.sza.fastmediasorter.domain.input.CommandId
import com.sza.fastmediasorter.domain.input.InputSurface as DomainSurface
import com.sza.fastmediasorter.domain.input.InputTrigger
import com.sza.fastmediasorter.domain.input.fromGamepadAxis
import com.sza.fastmediasorter.domain.input.fromGamepadButton
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
 * - [handleKeyEvent]: PLAYER surface routes through [KeyBindingManager]; BROWSER
 *   uses the legacy literal tree in [mapBrowserButton] (browser remapping is
 *   out of scope for this feature).
 * - [handleMotionEvent]: iterates [TRACKED_AXES], resolves each through the
 *   manager, preserves rate-limiting and deadzone filtering.
 */
@Singleton
class GamepadInputManager @Inject constructor(
    private val keyBindingManager: KeyBindingManager,
) {

    @Volatile private var lastAnalogSeekMs = -(ANALOG_SEEK_INTERVAL_MS + 1)
    @Volatile private var lastAnalogVolumeMs = -(ANALOG_VOLUME_INTERVAL_MS + 1)
    @Volatile private var lastTriggerSeekMs = -(TRIGGER_SEEK_INTERVAL_MS + 1)

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
            Surface.PLAYER -> {
                val trigger = InputTrigger.fromGamepadButton(event.keyCode)
                val commandId = keyBindingManager.resolve(trigger, DomainSurface.PLAYER) ?: return null
                mapCommandToGamepadAction(commandId, Surface.PLAYER, trigger, axisValue = null)
            }
            Surface.BROWSER -> mapBrowserButton(event)
        }
    }

    fun handleMotionEvent(event: MotionEvent, surface: Surface): GamepadAction? {
        if (!event.isFromGamepad()) return null
        if (event.action != MotionEvent.ACTION_MOVE) return null
        val domainSurface = if (surface == Surface.PLAYER) DomainSurface.PLAYER else DomainSurface.BROWSER
        for (axis in TRACKED_AXES) {
            val rawValue = event.getAxisValue(axis)
            // Invert AXIS_Y: Android reports stick-up as negative; we want up = positive direction.
            val value = if (axis == MotionEvent.AXIS_Y) -rawValue else rawValue
            val trigger = InputTrigger.fromGamepadAxis(axis, value, DEADZONE) ?: continue
            val commandId = keyBindingManager.resolve(trigger, domainSurface) ?: continue
            val action = mapCommandToGamepadAction(commandId, surface, trigger, axisValue = kotlin.math.abs(rawValue))
            if (action != null) return action
        }
        return null
    }

    // ── Browser: legacy literal tree (browser remapping is out-of-scope for player-keybinding) ─

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

    // ── Command → GamepadAction mapping with rate-limiting ──────────────────────────────────────

    private fun mapCommandToGamepadAction(
        commandId: String,
        surface: Surface,
        trigger: InputTrigger,
        axisValue: Float?,
    ): GamepadAction? = when (commandId) {
        CommandId.PAUSE_PLAY, CommandId.PLAY, CommandId.PAUSE ->
            GamepadAction.PlayerAction.PlayPause
        CommandId.EXIT ->
            GamepadAction.PlayerAction.Exit
        CommandId.NEXT_FILE -> when (surface) {
            Surface.PLAYER -> GamepadAction.PlayerAction.Next
            Surface.BROWSER -> GamepadAction.BrowserAction.SwitchTab(forward = true)
        }
        CommandId.PREVIOUS_FILE -> when (surface) {
            Surface.PLAYER -> GamepadAction.PlayerAction.Prev
            Surface.BROWSER -> GamepadAction.BrowserAction.SwitchTab(forward = false)
        }
        CommandId.SEEK_FORWARD_5S, CommandId.SEEK_FORWARD_30S,
        CommandId.SEEK_MACRO_FORWARD, CommandId.SEEK_MICRO_FORWARD -> when {
            trigger is InputTrigger.GamepadAxis && axisValue != null ->
                rateLimitedAnalogSeek(-axisValue) // negative = forward
            else -> rateLimitedTriggerSeek(forward = true)
        }
        CommandId.SEEK_BACKWARD_5S, CommandId.SEEK_BACKWARD_30S,
        CommandId.SEEK_MACRO_BACKWARD, CommandId.SEEK_MICRO_BACKWARD -> when {
            trigger is InputTrigger.GamepadAxis && axisValue != null ->
                rateLimitedAnalogSeek(axisValue) // positive = backward
            else -> rateLimitedTriggerSeek(forward = false)
        }
        CommandId.VOLUME_UP -> rateLimitedVolume(up = true)
        CommandId.VOLUME_DOWN -> rateLimitedVolume(up = false)
        CommandId.TOGGLE_CONTROLS -> GamepadAction.PlayerAction.ToggleHud
        CommandId.SHOW_HELP -> GamepadAction.PlayerAction.ToggleHints
        CommandId.SEARCH -> GamepadAction.BrowserAction.Search
        else -> null
    }

    // ── Rate-limiters (constants from temp/phase1/debounce-literals.md) ─────────────────────────

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
        // Scale seek by how far the stick is deflected beyond the deadzone, then use
        // sign of deflection: negative = forward, positive = backward.
        val magnitude = (kotlin.math.abs(deflection) - DEADZONE) / (1f - DEADZONE)
        val ms = (MAX_ANALOG_SEEK_MS * magnitude).toLong().coerceAtLeast(MIN_ANALOG_SEEK_MS)
        return GamepadAction.PlayerAction.Seek(if (deflection < 0f) ms else -ms)
    }

    // ── Source type checks ───────────────────────────────────────────────────────────────────────

    private fun KeyEvent.isFromGamepad(): Boolean =
        (source and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
            (source and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK

    private fun MotionEvent.isFromGamepad(): Boolean =
        (source and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK ||
            (source and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD

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

        /** Axes iterated in [handleMotionEvent]. Private list — literals acceptable here. */
        private val TRACKED_AXES = intArrayOf(
            MotionEvent.AXIS_X, MotionEvent.AXIS_Y,
            MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ,
            MotionEvent.AXIS_HAT_X, MotionEvent.AXIS_HAT_Y,
        )
    }
}
