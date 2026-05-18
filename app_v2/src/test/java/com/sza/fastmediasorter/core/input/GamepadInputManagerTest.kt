package com.sza.fastmediasorter.core.input

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.sza.fastmediasorter.domain.input.CommandId
import com.sza.fastmediasorter.domain.input.InputSurface
import com.sza.fastmediasorter.domain.input.InputTrigger
import com.sza.fastmediasorter.domain.model.GamepadAction
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [GamepadInputManager] resolver path (Phase 04).
 * All Android framework objects are mocked via MockK - no Robolectric needed.
 */
class GamepadInputManagerTest {

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun gamepadKeyEvent(keyCode: Int): KeyEvent = mockk<KeyEvent>().also { e ->
        every { e.source } returns InputDevice.SOURCE_GAMEPAD
        every { e.action } returns KeyEvent.ACTION_DOWN
        every { e.keyCode } returns keyCode
    }

    private fun joystickMoveEvent(axisValues: Map<Int, Float>): MotionEvent = mockk<MotionEvent>().also { e ->
        every { e.source } returns InputDevice.SOURCE_JOYSTICK
        every { e.action } returns MotionEvent.ACTION_MOVE
        // Default all axes to 0 (below deadzone); override for tested axes.
        every { e.getAxisValue(any()) } returns 0f
        axisValues.forEach { (axis, value) ->
            every { e.getAxisValue(axis) } returns value
        }
    }

    // ── test 1: BUTTON_A → PlayPause via resolver ─────────────────────────────

    @Test
    fun `BUTTON_A resolves to PlayPause for PLAYER surface`() {
        val mockManager = mockk<KeyBindingManager>()
        // Define catch-all first, then specific (MockK last-wins for overlapping stubs).
        every { mockManager.resolve(any(), any()) } returns null
        every {
            mockManager.resolve(
                InputTrigger.GamepadButton(KeyEvent.KEYCODE_BUTTON_A),
                InputSurface.PLAYER,
            )
        } returns CommandId.PAUSE_PLAY

        val result = GamepadInputManager(mockManager).handleKeyEvent(
            gamepadKeyEvent(KeyEvent.KEYCODE_BUTTON_A),
            GamepadInputManager.Surface.PLAYER,
        )
        assertTrue("Expected PlayerAction.PlayPause", result is GamepadAction.PlayerAction.PlayPause)
    }

    // ── test 2: AXIS_Y above deadzone resolves to Volume ─────────────────────

    @Test
    fun `AXIS_Y deflection above deadzone resolves to volume action`() {
        val mockManager = mockk<KeyBindingManager>()
        // Raw AXIS_Y = 0.8 → inverted to -0.8 → direction = -1.
        val trigger = InputTrigger.GamepadAxis(MotionEvent.AXIS_Y, -1, GamepadInputManager.DEADZONE)
        every { mockManager.resolve(any(), any()) } returns null
        every { mockManager.resolve(trigger, InputSurface.PLAYER) } returns CommandId.VOLUME_DOWN

        val result = GamepadInputManager(mockManager).handleMotionEvent(
            joystickMoveEvent(mapOf(MotionEvent.AXIS_Y to 0.8f)),
            GamepadInputManager.Surface.PLAYER,
        )
        assertNotNull("Expected Volume action for AXIS_Y = 0.8", result)
        assertTrue("Expected PlayerAction.Volume", result is GamepadAction.PlayerAction.Volume)
    }

    // ── test 3: AXIS_Y below deadzone → null ─────────────────────────────────

    @Test
    fun `AXIS_Y deflection below deadzone emits nothing`() {
        val mockManager = mockk<KeyBindingManager>()
        every { mockManager.resolve(any(), any()) } returns CommandId.VOLUME_DOWN

        val result = GamepadInputManager(mockManager).handleMotionEvent(
            joystickMoveEvent(mapOf(MotionEvent.AXIS_Y to 0.1f)), // 0.1 < DEADZONE 0.15
            GamepadInputManager.Surface.PLAYER,
        )
        assertNull("AXIS_Y = 0.1 is below deadzone - no action expected", result)
    }

    // ── test 4: button seek rate-limiting ─────────────────────────────────────

    @Test
    fun `rapid button presses within TRIGGER_SEEK_INTERVAL_MS are rate-limited`() {
        val mockManager = mockk<KeyBindingManager>()
        every { mockManager.resolve(any(), any()) } returns null
        every {
            mockManager.resolve(InputTrigger.GamepadButton(KeyEvent.KEYCODE_BUTTON_R1), InputSurface.PLAYER)
        } returns CommandId.SEEK_FORWARD_5S

        val manager = GamepadInputManager(mockManager)
        val event = gamepadKeyEvent(KeyEvent.KEYCODE_BUTTON_R1)

        val first = manager.handleKeyEvent(event, GamepadInputManager.Surface.PLAYER)
        val second = manager.handleKeyEvent(event, GamepadInputManager.Surface.PLAYER)

        // First press fires (lastTriggerSeekMs initialised to negative offset);
        // second within TRIGGER_SEEK_INTERVAL_MS is suppressed.
        assertNotNull("First seek press should fire", first)
        assertNull("Second rapid seek press should be rate-limited", second)
    }

    // ── test 5: BROWSER surface uses legacy literal tree, not resolver ────────

    @Test
    fun `BROWSER surface dispatches BrowserAction without calling resolver`() {
        val mockManager = mockk<KeyBindingManager>()
        // Resolver must NOT be called for BROWSER surface - an invocation would be an error.
        every { mockManager.resolve(any(), any()) } throws AssertionError("resolver called for BROWSER surface")

        val result = GamepadInputManager(mockManager).handleKeyEvent(
            gamepadKeyEvent(KeyEvent.KEYCODE_BUTTON_A),
            GamepadInputManager.Surface.BROWSER,
        )
        assertTrue("Expected BrowserAction.Select", result is GamepadAction.BrowserAction.Select)
    }
}
