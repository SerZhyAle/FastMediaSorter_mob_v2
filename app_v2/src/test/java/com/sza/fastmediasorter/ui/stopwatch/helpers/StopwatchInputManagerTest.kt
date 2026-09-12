package com.sza.fastmediasorter.ui.stopwatch.helpers

import android.view.KeyEvent
import com.sza.fastmediasorter.domain.model.stopwatch.StopwatchScreenState
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pure JVM tests for the key-to-command mapping of [StopwatchInputManager].
 *
 * The input layer is where a mis-addressed key would silently cross from one participant region into
 * another (S1411 §11.4), and a device run can only inject a synthetic event, so the mapping is pinned
 * here. [KeyEvent.KEYCODE_*] and [KeyEvent.ACTION_*] are plain int literals and are safe to reference
 * even though the framework stubs are not functional.
 */
class StopwatchInputManagerTest {

    private var participantCount = FOUR_PARTICIPANTS

    private val manager = StopwatchInputManager(participantCount = { participantCount })

    private fun keyDown(keyCode: Int): KeyEvent = mockk<KeyEvent>().also {
        every { it.action } returns KeyEvent.ACTION_DOWN
        every { it.keyCode } returns keyCode
    }

    @Test
    fun `a number key selects its own region`() {
        val command = manager.handleKeyEvent(keyDown(KeyEvent.KEYCODE_3), focusedRegion = 0)

        assertEquals(StopwatchCommand.FocusRegion(THIRD_REGION), command)
    }

    @Test
    fun `space starts or laps the focused region`() {
        val command = manager.handleKeyEvent(keyDown(KeyEvent.KEYCODE_SPACE), focusedRegion = 1)

        assertEquals(StopwatchCommand.StartOrLap(1), command)
    }

    @Test
    fun `an unmapped key yields nothing so the event survives to the system`() {
        assertNull(manager.handleKeyEvent(keyDown(KeyEvent.KEYCODE_BACK), focusedRegion = 0))
    }

    @Test
    fun `a key aimed past the configured count yields nothing`() {
        participantCount = StopwatchScreenState.PAIR_PARTICIPANTS

        assertNull(manager.handleKeyEvent(keyDown(KeyEvent.KEYCODE_4), focusedRegion = 0))
    }

    @Test
    fun `a start key aimed at a hidden focused region yields nothing`() {
        participantCount = 1

        assertNull(manager.handleKeyEvent(keyDown(KeyEvent.KEYCODE_ENTER), focusedRegion = 1))
    }

    @Test
    fun `a key release yields nothing so only the press acts`() {
        val release = mockk<KeyEvent>().also {
            every { it.action } returns KeyEvent.ACTION_UP
            every { it.keyCode } returns KeyEvent.KEYCODE_SPACE
        }

        assertNull(manager.handleKeyEvent(release, focusedRegion = 0))
    }

    @Test
    fun `the stop and reset mnemonics address the focused region`() {
        assertEquals(
            StopwatchCommand.Stop(1),
            manager.handleKeyEvent(keyDown(KeyEvent.KEYCODE_S), focusedRegion = 1),
        )
        assertEquals(
            StopwatchCommand.Reset(1),
            manager.handleKeyEvent(keyDown(KeyEvent.KEYCODE_R), focusedRegion = 1),
        )
    }

    private companion object {
        const val FOUR_PARTICIPANTS = StopwatchScreenState.MAX_PARTICIPANTS
        const val THIRD_REGION = 2
    }
}
