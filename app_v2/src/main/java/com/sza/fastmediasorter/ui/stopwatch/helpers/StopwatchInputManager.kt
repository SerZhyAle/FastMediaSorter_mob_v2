package com.sza.fastmediasorter.ui.stopwatch.helpers

import android.view.KeyEvent

/**
 * One measurement command, already addressed at the participant it belongs to.
 *
 * Every input source - the on-screen button, the keyboard, the mouse, the TV remote, the volume keys -
 * is reduced to this vocabulary before it reaches the ViewModel, so adding another remote never touches
 * the measurement logic (S1411 §5.3).
 */
sealed interface StopwatchCommand {

    val participantId: Int

    data class StartOrLap(override val participantId: Int) : StopwatchCommand

    data class Stop(override val participantId: Int) : StopwatchCommand

    data class Reset(override val participantId: Int) : StopwatchCommand

    data class FocusRegion(override val participantId: Int) : StopwatchCommand
}

/**
 * Translates a raw [KeyEvent] into a [StopwatchCommand].
 *
 * It holds no view. The caller supplies the focused region and [participantCount] reports how many
 * regions are on screen, so a key aimed past the configured count yields null instead of a command for
 * a region the user cannot see - which is the one way a press in one region could silently cross into
 * another (S1411 §11.4).
 */
class StopwatchInputManager(
    private val participantCount: () -> Int,
) {

    /**
     * The command [event] means, or null to let the event survive to the system untouched.
     *
     * [focusedRegion] is the participant a region-less key applies to; a number key overrides it.
     */
    fun handleKeyEvent(event: KeyEvent, focusedRegion: Int): StopwatchCommand? {
        if (event.action != KeyEvent.ACTION_DOWN) return null
        val command = commandFor(event.keyCode, focusedRegion)
        return command?.takeIf { it.participantId in 0 until participantCount() }
    }

    private fun commandFor(keyCode: Int, focusedRegion: Int): StopwatchCommand? {
        val regionByNumberKey = REGION_KEYS[keyCode]
        return when {
            regionByNumberKey != null -> StopwatchCommand.FocusRegion(regionByNumberKey)
            keyCode in START_OR_LAP_KEYS -> StopwatchCommand.StartOrLap(focusedRegion)
            keyCode == KeyEvent.KEYCODE_S -> StopwatchCommand.Stop(focusedRegion)
            keyCode == KeyEvent.KEYCODE_R -> StopwatchCommand.Reset(focusedRegion)
            else -> null
        }
    }

    private companion object {

        /** Number and numpad keys 1..4 address a region directly, without walking the focus chain. */
        val REGION_KEYS: Map<Int, Int> = mapOf(
            KeyEvent.KEYCODE_1 to 0,
            KeyEvent.KEYCODE_NUMPAD_1 to 0,
            KeyEvent.KEYCODE_2 to 1,
            KeyEvent.KEYCODE_NUMPAD_2 to 1,
            KeyEvent.KEYCODE_3 to 2,
            KeyEvent.KEYCODE_NUMPAD_3 to 2,
            KeyEvent.KEYCODE_4 to 3,
            KeyEvent.KEYCODE_NUMPAD_4 to 3,
        )

        /**
         * BACK and ESCAPE are deliberately absent. They have to keep leaving the screen, and a
         * stopwatch that swallowed them would trap the user in the middle of a measurement.
         */
        val START_OR_LAP_KEYS: Set<Int> = setOf(
            KeyEvent.KEYCODE_SPACE,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_DPAD_CENTER,
        )
    }
}
