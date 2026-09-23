package com.sza.fastmediasorter.wear.ui.apps.waterflashlight

/**
 * Decides whether an input on the water flashlight is the owner leaving or water pressing.
 *
 * S2516 let any key release end the light, because no per-model keycode had been measured and being
 * unable to leave was judged the worse failure. Water takes that rule: the system back gesture on a
 * wet glass reaches the app as a single back, and a button squeezed by a swimming stroke as a single
 * short press, so one event cannot be told from the owner's. Two shapes can be:
 *
 * - a key HELD past [EXIT_HOLD_MS], for a watch whose button reaches the app at all;
 * - a STREAK of [STREAK_PRESSES_TO_LEAVE] single events inside [STREAK_WINDOW_MS] - backs, or key
 *   releases whose press never arrived.
 *
 * The streak is the exit that exists everywhere. Measured on the Galaxy Watch 7 (2026-09-23), neither
 * physical button reaches the app - the top one is power, the system takes both halves of the bottom
 * one (Recents on a press, the wallet on a hold) - and the back gesture is the only input left, and it
 * carries no duration to hold.
 *
 * The keycode carries no meaning of its own, so no watch model is assumed. The clock is the caller's:
 * every entry point takes the current time, so the rules are testable without a device.
 */
class WaterFlashlightExitGate {

    private var pressedKeyCode: Long? = null
    private var pressedAtMs = 0L
    private var longPressSeen = false
    private var streakPresses = 0
    private var streakStartMs = 0L

    /**
     * A key going down. While a key is held the platform repeats this event, so only the first one
     * starts the clock - re-stamping on every repeat would make a held key look freshly pressed and no
     * hold could ever reach the threshold.
     *
     * [longPress] is the platform's own verdict (`FLAG_LONG_PRESS`, or a repeat of the same key). It
     * ends the light on its own, so a watch whose event timestamps disagree with the wall clock still
     * has a way out.
     */
    fun onKeyDown(keyCode: Long, nowMs: Long, longPress: Boolean = false) {
        if (pressedKeyCode != keyCode) {
            pressedKeyCode = keyCode
            pressedAtMs = nowMs
            longPressSeen = false
        }
        if (longPress) {
            longPressSeen = true
        }
    }

    /** True when this release is a deliberate exit. */
    fun onKeyUp(keyCode: Long, nowMs: Long): Boolean {
        val pressedAt = pressedAtMs.takeIf { pressedKeyCode == keyCode }
        val heldLong = longPressSeen
        pressedKeyCode = null
        longPressSeen = false
        if (pressedAt != null) {
            streakPresses = 0
            return heldLong || nowMs - pressedAt >= EXIT_HOLD_MS
        }
        return countStreakPress(nowMs)
    }

    /**
     * A back as the navigation dispatcher delivers it - one event per gesture or per press, with no
     * down half to time - so it can only count towards the streak. True when it completes one.
     */
    fun onBack(nowMs: Long): Boolean = countStreakPress(nowMs)

    private fun countStreakPress(nowMs: Long): Boolean {
        if (streakPresses == 0 || nowMs - streakStartMs > STREAK_WINDOW_MS) {
            streakStartMs = nowMs
            streakPresses = 0
        }
        streakPresses++
        val complete = streakPresses >= STREAK_PRESSES_TO_LEAVE
        // A completed streak is spent: a fourth back arriving before the screen is gone must start a
        // new one, or it would leave a second time and take the screen underneath with it.
        if (complete) {
            streakPresses = 0
        }
        return complete
    }

    companion object {
        const val EXIT_HOLD_MS = 1500L
        const val STREAK_WINDOW_MS = 2000L
        const val STREAK_PRESSES_TO_LEAVE = 3
    }
}
