package com.sza.fastmediasorter.domain.input

import android.view.KeyEvent

sealed class InputTrigger {

    abstract fun serialize(): String

    data class Key(val keyCode: Int, val modifiers: Int = 0) : InputTrigger() {
        override fun serialize() = "key:$keyCode:$modifiers"
    }

    data class MouseButton(val button: Int) : InputTrigger() {
        override fun serialize() = "mouse_btn:$button"
    }

    data class GamepadButton(val button: Int) : InputTrigger() {
        override fun serialize() = "gamepad_btn:$button"
    }

    data class GamepadAxis(val axis: Int, val direction: Int, val threshold: Float) : InputTrigger() {
        override fun serialize() = "gamepad_axis:$axis:$direction:$threshold"
    }

    /**
     * The wrapped value is a raw `XrInputEventType` code; axis-like events are
     * pre-thresholded by the C++ bridge.
     */
    data class VrEvent(val xrEventType: Int) : InputTrigger() {
        override fun serialize() = "vr:$xrEventType"
    }

    companion object {
        fun deserialize(s: String): InputTrigger {
            val parts = s.split(":")
            return when (parts[0]) {
                "key" -> Key(parts[1].toInt(), parts.getOrElse(2) { "0" }.toInt())
                "mouse_btn" -> MouseButton(parts[1].toInt())
                "gamepad_btn" -> GamepadButton(parts[1].toInt())
                "gamepad_axis" -> GamepadAxis(parts[1].toInt(), parts[2].toInt(), parts[3].toFloat())
                "vr" -> VrEvent(parts[1].toInt())
                else -> throw IllegalArgumentException("Unknown trigger type: ${parts[0]}")
            }
        }
    }
}

// Modifier mask — bit layout matches KeyEvent.META_* values:
//   META_SHIFT_ON=0x1, META_ALT_ON=0x2, META_CTRL_ON=0x1000 (Caps/Num/Scroll ignored).
private fun extractModifiers(event: KeyEvent): Int =
    event.metaState and (KeyEvent.META_SHIFT_ON or KeyEvent.META_ALT_ON or KeyEvent.META_CTRL_ON)

fun InputTrigger.Companion.fromKeyEvent(event: KeyEvent): InputTrigger.Key =
    InputTrigger.Key(event.keyCode, extractModifiers(event))

/**
 * Build a [InputTrigger.GamepadButton] from a raw [KeyEvent.keyCode].
 * Used by [com.sza.fastmediasorter.core.input.GamepadInputManager] on every button DOWN event.
 */
fun InputTrigger.Companion.fromGamepadButton(keyCode: Int): InputTrigger.GamepadButton =
    InputTrigger.GamepadButton(keyCode)

/**
 * Build a [InputTrigger.GamepadAxis] for a single axis reading.
 *
 * @param axis     [android.view.MotionEvent] axis constant (e.g. AXIS_Y, AXIS_RZ).
 * @param value    Centred, optionally inverted axis value (caller handles AXIS_Y inversion).
 * @param deadzone Hard deadzone floor; if `abs(value) < deadzone` the trigger is null — no
 *                 binding can fire when the stick is at rest. Matches [GamepadInputManager.DEADZONE].
 * @return null when the deflection is below [deadzone], otherwise a directional trigger.
 */
fun InputTrigger.Companion.fromGamepadAxis(
    axis: Int,
    value: Float,
    deadzone: Float,
): InputTrigger.GamepadAxis? {
    if (kotlin.math.abs(value) < deadzone) return null
    return InputTrigger.GamepadAxis(axis, direction = value.sign.toInt(), threshold = deadzone)
}

private val Float.sign: Float get() = kotlin.math.sign(this)

/**
 * Build a [InputTrigger.VrEvent] for the given raw XrInputEventType code.
 * Used by [com.sza.fastmediasorter.vr.helpers.VrControllerInputManager] in [dispatchXrEvent].
 */
fun InputTrigger.Companion.fromXrInputEvent(type: Int): InputTrigger.VrEvent = InputTrigger.VrEvent(type)
