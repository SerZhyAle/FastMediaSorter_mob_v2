package com.sza.fastmediasorter.ui.keybinding.helpers

import com.sza.fastmediasorter.domain.input.InputTrigger
import org.junit.Assert.assertEquals
import org.junit.Test

class KeybindingDeviceOptionsTest {

    @Test
    fun `empty bindings still offers keyboard and gamepad`() {
        assertEquals(listOf("keyboard", "gamepad"), remappableDevices(emptyMap()))
    }

    @Test
    fun `keyboard-only binding does not duplicate keyboard and still offers gamepad`() {
        val bindings = mapOf("keyboard" to listOf(InputTrigger.Key(62, 0)))
        assertEquals(listOf("keyboard", "gamepad"), remappableDevices(bindings))
    }

    @Test
    fun `existing gamepad binding is not duplicated`() {
        val bindings = mapOf("gamepad" to listOf(InputTrigger.GamepadButton(96)))
        assertEquals(listOf("keyboard", "gamepad"), remappableDevices(bindings))
    }

    @Test
    fun `extra bound devices are appended after keyboard and gamepad`() {
        val bindings = linkedMapOf(
            "keyboard" to listOf(InputTrigger.Key(62, 0)),
            "mouse" to listOf(InputTrigger.MouseButton(1)),
        )
        assertEquals(listOf("keyboard", "gamepad", "mouse"), remappableDevices(bindings))
    }
}
