package com.sza.fastmediasorter.ui.keybinding.helpers

import android.content.Context
import android.view.KeyEvent
import android.view.MotionEvent
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.input.InputTrigger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Translates [InputTrigger] instances to human-readable strings.
 * Also resolves command label keys from resource names.
 *
 * All display strings use [resources.getString] - never hardcoded.
 */
class KeybindingRowLabelFormatter @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun format(trigger: InputTrigger): String = when (trigger) {
        is InputTrigger.Key -> formatKey(trigger)
        is InputTrigger.GamepadButton -> formatGamepadButton(trigger.button)
        is InputTrigger.MouseButton -> formatMouseButton(trigger.button)
        is InputTrigger.GamepadAxis -> formatAxis(trigger)
        is InputTrigger.VrEvent -> formatVrEvent(trigger.xrEventType)
    }

    /** Resolves a command label from the string resource name convention. Falls back to raw commandId. */
    fun resolveCommandLabel(commandId: String): String {
        val resName = "keybinding_label_" + commandId.replace(".", "_")
        val id = context.resources.getIdentifier(resName, "string", context.packageName)
        return if (id != 0) context.getString(id) else commandId
    }

    /** Resolves a group header label from the string resource name convention. */
    fun resolveGroupLabel(resName: String): String {
        val id = context.resources.getIdentifier(resName, "string", context.packageName)
        return if (id != 0) context.getString(id) else resName
    }

    // ----- private formatters -----

    private fun formatKey(trigger: InputTrigger.Key): String {
        val modParts = buildList {
            if (trigger.modifiers and KeyEvent.META_CTRL_ON != 0) add(context.getString(R.string.keybinding_input_mod_ctrl))
            if (trigger.modifiers and KeyEvent.META_SHIFT_ON != 0) add(context.getString(R.string.keybinding_input_mod_shift))
            if (trigger.modifiers and KeyEvent.META_ALT_ON != 0) add(context.getString(R.string.keybinding_input_mod_alt))
        }
        val keyLabel = keyLabel(trigger.keyCode)
        return if (modParts.isEmpty()) keyLabel else modParts.joinToString("+") + "+$keyLabel"
    }

    private fun keyLabel(keyCode: Int): String {
        val raw = KeyEvent.keyCodeToString(keyCode)
        return if (raw.startsWith("KEYCODE_")) {
            raw.removePrefix("KEYCODE_").replace("_", " ")
        } else {
            context.getString(R.string.keycode_unknown_label, keyCode)
        }
    }

    private fun formatGamepadButton(button: Int): String = when (button) {
        KeyEvent.KEYCODE_BUTTON_A -> context.getString(R.string.keybinding_input_gamepad_a)
        KeyEvent.KEYCODE_BUTTON_B -> context.getString(R.string.keybinding_input_gamepad_b)
        KeyEvent.KEYCODE_BUTTON_X -> context.getString(R.string.keybinding_input_gamepad_x)
        KeyEvent.KEYCODE_BUTTON_Y -> context.getString(R.string.keybinding_input_gamepad_y)
        KeyEvent.KEYCODE_BUTTON_L1 -> context.getString(R.string.keybinding_input_gamepad_l1)
        KeyEvent.KEYCODE_BUTTON_R1 -> context.getString(R.string.keybinding_input_gamepad_r1)
        KeyEvent.KEYCODE_BUTTON_L2 -> context.getString(R.string.keybinding_input_gamepad_l2)
        KeyEvent.KEYCODE_BUTTON_R2 -> context.getString(R.string.keybinding_input_gamepad_r2)
        KeyEvent.KEYCODE_BUTTON_THUMBL -> context.getString(R.string.keybinding_input_gamepad_thumbl)
        KeyEvent.KEYCODE_BUTTON_THUMBR -> context.getString(R.string.keybinding_input_gamepad_thumbr)
        KeyEvent.KEYCODE_BUTTON_START -> context.getString(R.string.keybinding_input_gamepad_start)
        KeyEvent.KEYCODE_BUTTON_SELECT -> context.getString(R.string.keybinding_input_gamepad_select)
        KeyEvent.KEYCODE_BUTTON_MODE -> context.getString(R.string.keybinding_input_gamepad_mode)
        KeyEvent.KEYCODE_DPAD_UP -> context.getString(R.string.keybinding_input_gamepad_dpad_up)
        KeyEvent.KEYCODE_DPAD_DOWN -> context.getString(R.string.keybinding_input_gamepad_dpad_down)
        KeyEvent.KEYCODE_DPAD_LEFT -> context.getString(R.string.keybinding_input_gamepad_dpad_left)
        KeyEvent.KEYCODE_DPAD_RIGHT -> context.getString(R.string.keybinding_input_gamepad_dpad_right)
        else -> context.getString(R.string.keybinding_fmt_gamepad_unknown, button)
    }

    private fun formatMouseButton(button: Int): String = when (button) {
        MotionEvent.BUTTON_PRIMARY -> context.getString(R.string.keybinding_input_mouse_left)
        MotionEvent.BUTTON_SECONDARY -> context.getString(R.string.keybinding_input_mouse_right)
        MotionEvent.BUTTON_TERTIARY -> context.getString(R.string.keybinding_input_mouse_middle)
        MotionEvent.BUTTON_BACK -> context.getString(R.string.keybinding_input_mouse_back)
        MotionEvent.BUTTON_FORWARD -> context.getString(R.string.keybinding_input_mouse_forward)
        else -> context.getString(R.string.keybinding_fmt_mouse_unknown, button)
    }

    private fun formatAxis(trigger: InputTrigger.GamepadAxis): String {
        val axisName = when (trigger.axis) {
            MotionEvent.AXIS_X -> context.getString(R.string.keybinding_input_axis_left_x)
            MotionEvent.AXIS_Y -> context.getString(R.string.keybinding_input_axis_left_y)
            MotionEvent.AXIS_Z -> context.getString(R.string.keybinding_input_axis_right_x)
            MotionEvent.AXIS_RZ -> context.getString(R.string.keybinding_input_axis_right_y)
            MotionEvent.AXIS_HAT_X -> context.getString(R.string.keybinding_input_axis_hat_x)
            MotionEvent.AXIS_HAT_Y -> context.getString(R.string.keybinding_input_axis_hat_y)
            MotionEvent.AXIS_LTRIGGER -> context.getString(R.string.keybinding_input_axis_ltrigger)
            MotionEvent.AXIS_RTRIGGER -> context.getString(R.string.keybinding_input_axis_rtrigger)
            else -> "Axis[${trigger.axis}]"
        }
        val dir = if (trigger.direction > 0) "+" else "–"
        return "$axisName$dir (>${trigger.threshold})"
    }

    private fun formatVrEvent(type: Int): String {
        val resName = "keybinding_input_vr_$type"
        val id = context.resources.getIdentifier(resName, "string", context.packageName)
        return if (id != 0) context.getString(id) else "VR[$type]"
    }
}
