package com.sza.fastmediasorter.core.input

import android.view.InputDevice
import android.view.KeyEvent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Routes raw [KeyEvent]s from every non-gamepad input device into semantic
 * [TvNavAction]s so each Activity only declares *what* to do, not *how* the
 * raw key code maps to that action.
 *
 * Sources covered (non-exhaustive — the router never inspects vendor IDs):
 * - TV remotes (D-pad) — typically arrive with [InputDevice.SOURCE_KEYBOARD]
 *   or [InputDevice.SOURCE_DPAD].
 * - Physical USB / Bluetooth keyboards.
 * - Car steering-wheel media buttons (Android Auto, Bluetooth HID) — arrive
 *   as `KEYCODE_MEDIA_*` from various sources.
 * - Wired / wireless headset hooks (`KEYCODE_HEADSETHOOK`, `KEYCODE_MEDIA_PLAY_PAUSE`).
 * - Hardware buttons on phones / tablets / set-top boxes — volume, mute,
 *   menu, search.
 *
 * Scope guard:
 * - [InputDevice.SOURCE_GAMEPAD] and [InputDevice.SOURCE_JOYSTICK] events
 *   return `null` here. Gamepad input is owned by [GamepadInputManager] which
 *   has its own rate-limiting, analog-axis handling and key-binding remap.
 * - Only [KeyEvent.ACTION_DOWN] produces an action; ACTION_UP returns null.
 *
 * Complexity: O(1) — no I/O, no allocations beyond the sealed-class instances.
 */
@Singleton
class TvKeyRouter @Inject constructor() {

    /**
     * Translate [event] into a [TvNavAction], or `null` if the event is not
     * handled by this router (gamepad source, wrong action, unrecognised
     * keycode).
     */
    fun route(event: KeyEvent): TvNavAction? {
        if (event.action != KeyEvent.ACTION_DOWN) return null
        if (event.isFromGamepad()) return null

        return when (event.keyCode) {
            // ── Nav (TV remote, keyboard) ────────────────────────────────────
            // TAB is INTENTIONALLY not routed: it must remain the conventional
            // focus-traversal key (and SHIFT+TAB the reverse), which is what the
            // Android framework already does by default. Routing it to Next/Prev
            // would consume the event and block focus from reaching widgets like
            // a language picker on a Welcome slide (S0230 device test, 2026-05-17).
            // DPAD_LEFT / DPAD_RIGHT remain mapped to slider-page semantics for
            // TV remotes; consumers that don't override `onTvNavigation` still
            // get default focus traversal because the hook returns false.
            KeyEvent.KEYCODE_DPAD_RIGHT -> TvNavAction.Next
            KeyEvent.KEYCODE_DPAD_LEFT -> TvNavAction.Prev

            KeyEvent.KEYCODE_DPAD_UP -> TvNavAction.Up
            KeyEvent.KEYCODE_DPAD_DOWN -> TvNavAction.Down

            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER -> TvNavAction.Select

            KeyEvent.KEYCODE_BACK -> TvNavAction.Back

            // ── Media (car wheel, BT headset, AAC remote) ────────────────────
            // HEADSETHOOK is the wired-headset single-tap "play/pause" key —
            // semantically identical to MEDIA_PLAY_PAUSE.
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_HEADSETHOOK -> TvNavAction.PlayPause

            KeyEvent.KEYCODE_MEDIA_PLAY -> TvNavAction.Play
            KeyEvent.KEYCODE_MEDIA_PAUSE -> TvNavAction.Pause
            KeyEvent.KEYCODE_MEDIA_STOP -> TvNavAction.Stop

            KeyEvent.KEYCODE_MEDIA_NEXT -> TvNavAction.MediaNext
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> TvNavAction.MediaPrev

            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> TvNavAction.FastForward
            KeyEvent.KEYCODE_MEDIA_REWIND -> TvNavAction.Rewind

            // ── Hardware (physical device buttons) ───────────────────────────
            KeyEvent.KEYCODE_VOLUME_UP -> TvNavAction.VolumeUp
            KeyEvent.KEYCODE_VOLUME_DOWN -> TvNavAction.VolumeDown
            KeyEvent.KEYCODE_VOLUME_MUTE -> TvNavAction.VolumeMute
            KeyEvent.KEYCODE_MENU -> TvNavAction.Menu
            KeyEvent.KEYCODE_SEARCH -> TvNavAction.Search

            else -> null
        }
    }

    // ── Source predicate ──────────────────────────────────────────────────────
    // Anything that is NOT a gamepad/joystick is in scope: TV remotes report
    // DPAD_* via SOURCE_KEYBOARD, BT car remotes report MEDIA_* via various
    // sources, phone volume buttons report SOURCE_KEYBOARD. The gamepad guard
    // is enough to keep [GamepadInputManager] ownership intact.

    private fun KeyEvent.isFromGamepad(): Boolean {
        val src = source
        return (src and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
            (src and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
    }
}
