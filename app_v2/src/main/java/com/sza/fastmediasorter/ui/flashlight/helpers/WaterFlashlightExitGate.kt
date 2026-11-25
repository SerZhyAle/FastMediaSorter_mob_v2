package com.sza.fastmediasorter.ui.flashlight.helpers

import android.view.KeyEvent

/**
 * Decides when a hardware key press may end the water flashlight, as `INPUT-PARITY` section 3.3 item K.3
 * pins it: the exit key held for at least [HOLD_MS], or three presses with the third at most
 * [STREAK_WINDOW_MS] after the first. One release must not leave - a key brushed in a pocket or a bag
 * would put the light out exactly where it is needed.
 *
 * The comparisons are the contract's boundaries: a hold of 1499 ms stays, one of 1500 ms leaves; a third
 * press at +2000 ms leaves, one at +2001 ms starts a new streak of its own.
 *
 * The six exit keys count as one button family, so a streak may mix them; a hold is measured on a single
 * key sequence. State is per screen session, so the activity owns one instance.
 */
class WaterFlashlightExitGate {

    private var streakStartMs = 0L
    private var streakCount = 0

    /**
     * [downTimeMs] and [eventTimeMs] are the `KeyEvent` uptime stamps rather than a clock read here, so a
     * hold is judged on when the key actually went down, not on when the activity got to see the event.
     */
    fun shouldLeave(action: Int, repeatCount: Int, downTimeMs: Long, eventTimeMs: Long): Boolean {
        val held = eventTimeMs - downTimeMs >= HOLD_MS
        return when (action) {
            KeyEvent.ACTION_DOWN -> if (repeatCount == 0) registerPress(eventTimeMs) else held
            KeyEvent.ACTION_UP -> held
            else -> false
        }
    }

    private fun registerPress(eventTimeMs: Long): Boolean {
        val continuesStreak = streakCount > 0 && eventTimeMs - streakStartMs <= STREAK_WINDOW_MS
        if (!continuesStreak) {
            streakStartMs = eventTimeMs
            streakCount = 0
        }
        streakCount++
        val leaves = streakCount >= STREAK_EVENTS
        if (leaves) {
            streakCount = 0
        }
        return leaves
    }

    companion object {
        const val HOLD_MS = 1_500L
        const val STREAK_WINDOW_MS = 2_000L
        const val STREAK_EVENTS = 3
    }
}
