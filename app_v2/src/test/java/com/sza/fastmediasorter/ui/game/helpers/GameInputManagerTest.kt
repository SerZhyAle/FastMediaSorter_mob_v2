package com.sza.fastmediasorter.ui.game.helpers

import android.view.KeyEvent
import com.sza.fastmediasorter.domain.game.GameDirection
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for the restart binding in [GameInputManager]. The game screen consumes key events
 * before the focus system runs, so the key map is the only route to the command and the only place
 * a regression could hide - a device run can only inject a synthetic event, this pins the mapping.
 *
 * [KeyEvent.KEYCODE_*] and [KeyEvent.ACTION_*] are plain int literals and are safe to reference in a
 * unit test even though the framework stubs are not functional.
 */
class GameInputManagerTest {

    private val directions = mutableListOf<GameDirection>()
    private var primaryActions = 0
    private var restarts = 0
    private var backs = 0

    private val manager = GameInputManager(
        onDirection = { directions += it },
        onPrimaryAction = { primaryActions++ },
        onRestartLevel = { restarts++ },
        onBack = { backs++ }
    )

    private fun keyDown(keyCode: Int): KeyEvent = mockk<KeyEvent>().also {
        every { it.action } returns KeyEvent.ACTION_DOWN
        every { it.keyCode } returns keyCode
    }

    @Test
    fun `R restarts the level`() {
        assertTrue(manager.handleKeyEvent(keyDown(KeyEvent.KEYCODE_R)))

        assertEquals(1, restarts)
    }

    @Test
    fun `gamepad Y restarts the level`() {
        assertTrue(manager.handleKeyEvent(keyDown(KeyEvent.KEYCODE_BUTTON_Y)))

        assertEquals(1, restarts)
    }

    @Test
    fun `restart keys touch no other command`() {
        manager.handleKeyEvent(keyDown(KeyEvent.KEYCODE_R))
        manager.handleKeyEvent(keyDown(KeyEvent.KEYCODE_BUTTON_Y))

        assertEquals(emptyList<GameDirection>(), directions)
        assertEquals(0, primaryActions)
        assertEquals(0, backs)
    }

    @Test
    fun `key release does not restart`() {
        val release = mockk<KeyEvent>().also {
            every { it.action } returns KeyEvent.ACTION_UP
            every { it.keyCode } returns KeyEvent.KEYCODE_R
        }

        assertFalse(manager.handleKeyEvent(release))
        assertEquals(0, restarts)
    }

    @Test
    fun `movement and exit keys still reach their own commands`() {
        assertTrue(manager.handleKeyEvent(keyDown(KeyEvent.KEYCODE_DPAD_UP)))
        assertTrue(manager.handleKeyEvent(keyDown(KeyEvent.KEYCODE_ENTER)))
        assertTrue(manager.handleKeyEvent(keyDown(KeyEvent.KEYCODE_ESCAPE)))

        assertEquals(listOf(GameDirection.UP), directions)
        assertEquals(1, primaryActions)
        assertEquals(1, backs)
        assertEquals(0, restarts)
    }
}
