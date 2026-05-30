package com.sza.fastmediasorter.domain.input

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [InputTrigger] serialization and deserialization.
 *
 * The `fromKeyEvent`/`fromGamepad*` extensions touch android.view.KeyEvent and are excluded -
 * the pure serialize/deserialize contract is the testable logic on plain JVM.
 */
class InputTriggerTest {

    @Test
    fun `Key serializes with key code and modifiers`() {
        assertEquals("key:42:4096", InputTrigger.Key(42, 4096).serialize())
        assertEquals("key:10:0", InputTrigger.Key(10).serialize())
    }

    @Test
    fun `MouseButton GamepadButton GamepadAxis and VrEvent serialize with their prefixes`() {
        assertEquals("mouse_btn:2", InputTrigger.MouseButton(2).serialize())
        assertEquals("gamepad_btn:99", InputTrigger.GamepadButton(99).serialize())
        assertEquals("gamepad_axis:1:-1:0.5", InputTrigger.GamepadAxis(1, -1, 0.5f).serialize())
        assertEquals("vr:7", InputTrigger.VrEvent(7).serialize())
    }

    @Test
    fun `deserialize reconstructs each trigger type`() {
        assertEquals(InputTrigger.Key(42, 4096), InputTrigger.deserialize("key:42:4096"))
        assertEquals(InputTrigger.MouseButton(2), InputTrigger.deserialize("mouse_btn:2"))
        assertEquals(InputTrigger.GamepadButton(99), InputTrigger.deserialize("gamepad_btn:99"))
        assertEquals(InputTrigger.GamepadAxis(1, -1, 0.5f), InputTrigger.deserialize("gamepad_axis:1:-1:0.5"))
        assertEquals(InputTrigger.VrEvent(7), InputTrigger.deserialize("vr:7"))
    }

    @Test
    fun `deserialize defaults Key modifiers to zero when omitted`() {
        assertEquals(InputTrigger.Key(10, 0), InputTrigger.deserialize("key:10"))
    }

    @Test
    fun `serialize then deserialize round-trips`() {
        val triggers = listOf(
            InputTrigger.Key(5, 1),
            InputTrigger.MouseButton(1),
            InputTrigger.GamepadButton(96),
            InputTrigger.GamepadAxis(0, 1, 0.25f),
            InputTrigger.VrEvent(3),
        )
        triggers.forEach { trigger ->
            assertEquals(trigger, InputTrigger.deserialize(trigger.serialize()))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `deserialize rejects an unknown trigger type`() {
        InputTrigger.deserialize("teleport:1")
    }
}
