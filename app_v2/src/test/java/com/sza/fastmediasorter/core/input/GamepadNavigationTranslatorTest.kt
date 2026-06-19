package com.sza.fastmediasorter.core.input

import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [GamepadNavigationTranslator]. Pure MockK (mirrors [GamepadInputManagerTest]) -
 * Android framework objects are mocked, the clock is injected so repeat-acceleration intervals are
 * exercised with virtual time. No real gamepad / Robolectric needed. S0508.
 */
class GamepadNavigationTranslatorTest {

    private var clock = 0L
    private val translator = GamepadNavigationTranslator(now = { clock })

    private fun joystickMoveEvent(axisValues: Map<Int, Float>): MotionEvent = mockk<MotionEvent>().also { e ->
        every { e.source } returns InputDevice.SOURCE_JOYSTICK
        every { e.action } returns MotionEvent.ACTION_MOVE
        every { e.getAxisValue(any()) } returns 0f
        axisValues.forEach { (axis, value) -> every { e.getAxisValue(axis) } returns value }
    }

    @Test
    fun `non-gamepad source yields nothing`() {
        val e = mockk<MotionEvent>().also {
            every { it.source } returns InputDevice.SOURCE_TOUCHSCREEN
            every { it.action } returns MotionEvent.ACTION_MOVE
            every { it.getAxisValue(any()) } returns 0.9f
        }
        assertNull(translator.translate(e))
    }

    @Test
    fun `left stick inside deadzone yields nothing`() {
        assertNull(translator.translate(joystickMoveEvent(mapOf(MotionEvent.AXIS_X to 0.1f))))
    }

    @Test
    fun `left stick right resolves to FOCUS_RIGHT`() {
        val intent = translator.translate(joystickMoveEvent(mapOf(MotionEvent.AXIS_X to 0.8f)))
        assertEquals(GamepadNavIntent.FocusMove(View.FOCUS_RIGHT), intent)
    }

    @Test
    fun `left stick left resolves to FOCUS_LEFT`() {
        val intent = translator.translate(joystickMoveEvent(mapOf(MotionEvent.AXIS_X to -0.8f)))
        assertEquals(GamepadNavIntent.FocusMove(View.FOCUS_LEFT), intent)
    }

    @Test
    fun `raw AXIS_Y positive is stick-down and resolves to FOCUS_DOWN`() {
        // Android reports stick-down as positive AXIS_Y; the translator inverts, so +0.8 → FOCUS_DOWN.
        val intent = translator.translate(joystickMoveEvent(mapOf(MotionEvent.AXIS_Y to 0.8f)))
        assertEquals(GamepadNavIntent.FocusMove(View.FOCUS_DOWN), intent)
    }

    @Test
    fun `raw AXIS_Y negative is stick-up and resolves to FOCUS_UP`() {
        val intent = translator.translate(joystickMoveEvent(mapOf(MotionEvent.AXIS_Y to -0.8f)))
        assertEquals(GamepadNavIntent.FocusMove(View.FOCUS_UP), intent)
    }

    @Test
    fun `right stick beyond deadzone resolves to non-zero scroll`() {
        val intent = translator.translate(
            joystickMoveEvent(mapOf(MotionEvent.AXIS_Z to 0.8f, MotionEvent.AXIS_RZ to 0.8f)),
        )
        assertTrue("Expected Scroll", intent is GamepadNavIntent.Scroll)
        intent as GamepadNavIntent.Scroll
        assertTrue("dx should be positive for AXIS_Z>0", intent.dx > 0)
        assertTrue("dy should be positive for AXIS_RZ>0", intent.dy > 0)
    }

    @Test
    fun `held direction accelerates after the repeat threshold`() {
        val right = mapOf(MotionEvent.AXIS_X to 0.8f)
        // First sample emits immediately (fresh direction), repeat counter starts at 0.
        clock = 0
        assertEquals(GamepadNavIntent.FocusMove(View.FOCUS_RIGHT), translator.translate(joystickMoveEvent(right)))
        // Before the threshold a 60ms gap is below the base interval (140ms) → rate-limited.
        clock = 60
        assertNull(translator.translate(joystickMoveEvent(right)))
        // Drive the repeat counter up to the acceleration threshold using base-interval gaps.
        for (i in 1..GamepadNavigationTranslator.ACCEL_REPEAT_THRESHOLD) {
            clock += GamepadNavigationTranslator.BASE_REPEAT_MS
            assertEquals(
                GamepadNavIntent.FocusMove(View.FOCUS_RIGHT),
                translator.translate(joystickMoveEvent(right)),
            )
        }
        // Now accelerated: a shortened gap below the base interval still emits.
        clock += GamepadNavigationTranslator.FAST_REPEAT_MS
        assertEquals(
            GamepadNavIntent.FocusMove(View.FOCUS_RIGHT),
            translator.translate(joystickMoveEvent(right)),
        )
    }

    @Test
    fun `returning to deadzone resets acceleration`() {
        val right = mapOf(MotionEvent.AXIS_X to 0.8f)
        clock = 0
        translator.translate(joystickMoveEvent(right))
        for (i in 1..GamepadNavigationTranslator.ACCEL_REPEAT_THRESHOLD) {
            clock += GamepadNavigationTranslator.BASE_REPEAT_MS
            translator.translate(joystickMoveEvent(right))
        }
        // Release: stick back inside the dead-zone resets the counter and last direction.
        clock += 10
        assertNull(translator.translate(joystickMoveEvent(mapOf(MotionEvent.AXIS_X to 0.0f))))
        // Re-deflect: a fresh direction emits immediately regardless of the small time gap.
        clock += 10
        assertEquals(
            GamepadNavIntent.FocusMove(View.FOCUS_RIGHT),
            translator.translate(joystickMoveEvent(right)),
        )
    }
}
