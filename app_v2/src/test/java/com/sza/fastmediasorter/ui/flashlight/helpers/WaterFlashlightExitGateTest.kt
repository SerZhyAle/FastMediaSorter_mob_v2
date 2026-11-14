package com.sza.fastmediasorter.ui.flashlight.helpers

import android.view.KeyEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the two boundary comparisons of `INPUT-PARITY` section 3.3 item K.3. A device run can only
 * inject a synthetic key event, so the 1499/1500 ms and +2000/+2001 ms edges are proved here.
 * [KeyEvent.ACTION_DOWN] and [KeyEvent.ACTION_UP] are int literals and safe to reference without the framework.
 */
class WaterFlashlightExitGateTest {

    private val gate = WaterFlashlightExitGate()

    private fun press(atMs: Long): Boolean =
        gate.shouldLeave(KeyEvent.ACTION_DOWN, repeatCount = 0, downTimeMs = atMs, eventTimeMs = atMs)

    private fun release(downMs: Long, heldMs: Long): Boolean =
        gate.shouldLeave(KeyEvent.ACTION_UP, repeatCount = 0, downTimeMs = downMs, eventTimeMs = downMs + heldMs)

    private fun keyRepeat(downMs: Long, heldMs: Long): Boolean =
        gate.shouldLeave(KeyEvent.ACTION_DOWN, repeatCount = 1, downTimeMs = downMs, eventTimeMs = downMs + heldMs)

    @Test
    fun `a lone press does not leave`() {
        assertFalse(press(START_MS))
    }

    @Test
    fun `a release after 1499 ms does not leave`() {
        assertFalse(release(START_MS, WaterFlashlightExitGate.HOLD_MS - 1))
    }

    @Test
    fun `a release after 1500 ms leaves`() {
        assertTrue(release(START_MS, WaterFlashlightExitGate.HOLD_MS))
    }

    @Test
    fun `a key repeat at 1499 ms does not leave and one at 1500 ms does`() {
        assertFalse(keyRepeat(START_MS, WaterFlashlightExitGate.HOLD_MS - 1))
        assertTrue(keyRepeat(START_MS, WaterFlashlightExitGate.HOLD_MS))
    }

    @Test
    fun `two presses do not leave`() {
        assertFalse(press(START_MS))
        assertFalse(press(START_MS + SECOND_PRESS_OFFSET_MS))
    }

    @Test
    fun `a third press exactly 2000 ms after the first leaves`() {
        press(START_MS)
        press(START_MS + SECOND_PRESS_OFFSET_MS)

        assertTrue(press(START_MS + WaterFlashlightExitGate.STREAK_WINDOW_MS))
    }

    @Test
    fun `a third press 2001 ms after the first starts a new streak instead`() {
        press(START_MS)
        press(START_MS + SECOND_PRESS_OFFSET_MS)

        assertFalse(press(START_MS + WaterFlashlightExitGate.STREAK_WINDOW_MS + 1))
    }

    @Test
    fun `the press that started the new streak counts as its first`() {
        press(START_MS)
        press(START_MS + SECOND_PRESS_OFFSET_MS)
        val restart = START_MS + WaterFlashlightExitGate.STREAK_WINDOW_MS + 1
        press(restart)
        press(restart + SECOND_PRESS_OFFSET_MS)

        assertTrue(press(restart + WaterFlashlightExitGate.STREAK_WINDOW_MS))
    }

    @Test
    fun `leaving clears the streak so the next press starts from one`() {
        press(START_MS)
        press(START_MS + SECOND_PRESS_OFFSET_MS)
        assertTrue(press(START_MS + THIRD_PRESS_OFFSET_MS))

        assertFalse(press(START_MS + AFTER_LEAVE_OFFSET_MS))
    }

    @Test
    fun `a release is not a press and never counts toward the streak`() {
        press(START_MS)
        release(START_MS, SECOND_PRESS_OFFSET_MS)

        assertFalse(press(START_MS + THIRD_PRESS_OFFSET_MS))
    }

    private companion object {
        const val START_MS = 10_000L
        const val SECOND_PRESS_OFFSET_MS = 700L
        const val THIRD_PRESS_OFFSET_MS = 1_400L
        const val AFTER_LEAVE_OFFSET_MS = 1_500L
    }
}
