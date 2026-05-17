package com.sza.fastmediasorter.core.input

import android.view.InputDevice
import android.view.KeyEvent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Routes raw [KeyEvent]s from TV remotes and physical keyboards into semantic
 * [TvNavAction]s so each Activity only declares *what* to do, not *how* the key
 * maps to that action.
 *
 * Scope:
 * - Active only for [InputDevice.SOURCE_KEYBOARD] and [InputDevice.SOURCE_DPAD].
 * - [InputDevice.SOURCE_GAMEPAD] / [InputDevice.SOURCE_JOYSTICK] return null here
 *   and are handled by [GamepadInputManager] as before — no overlap.
 * - Only [KeyEvent.ACTION_DOWN] events produce an action; ACTION_UP returns null.
 *
 * Complexity: O(1) — no I/O, no allocations beyond the sealed-class instances.
 */
@Singleton
class TvKeyRouter @Inject constructor() {

    /**
     * Translate [event] into a [TvNavAction], or null if the event is not
     * handled by this router (wrong source, wrong action, unrecognised keycode).
     */
    fun route(event: KeyEvent): TvNavAction? {
        if (event.action != KeyEvent.ACTION_DOWN) return null
        if (!event.isFromTvOrKeyboard()) return null

        return when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT -> TvNavAction.Next
            KeyEvent.KEYCODE_TAB ->
                if (event.isShiftPressed) TvNavAction.Prev else TvNavAction.Next

            KeyEvent.KEYCODE_DPAD_LEFT -> TvNavAction.Prev

            KeyEvent.KEYCODE_DPAD_UP -> TvNavAction.Up
            KeyEvent.KEYCODE_DPAD_DOWN -> TvNavAction.Down

            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER -> TvNavAction.Select

            KeyEvent.KEYCODE_BACK -> TvNavAction.Back

            else -> null
        }
    }

    // ── Source predicate ───────────────────────────────────────────────────────
    // TV remotes report DPAD_* keys with SOURCE_KEYBOARD (not SOURCE_DPAD on all
    // devices). Physical keyboards also use SOURCE_KEYBOARD. Both are handled.
    // Gamepads are explicitly excluded so GamepadInputManager keeps ownership.

    private fun KeyEvent.isFromTvOrKeyboard(): Boolean {
        val src = source
        val isGamepad = (src and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                || (src and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
        if (isGamepad) return false
        return (src and InputDevice.SOURCE_KEYBOARD) == InputDevice.SOURCE_KEYBOARD
                || (src and InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD
    }
}
