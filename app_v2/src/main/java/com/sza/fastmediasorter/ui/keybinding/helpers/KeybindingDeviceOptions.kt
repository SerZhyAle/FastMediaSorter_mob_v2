package com.sza.fastmediasorter.ui.keybinding.helpers

import com.sza.fastmediasorter.domain.input.InputTrigger

/**
 * Devices a command can be remapped for, in display order.
 *
 * Keyboard and gamepad are ALWAYS offered so a gamepad binding can be assigned even when the
 * command currently has none - that was the S0509 gap: the remap row only exposed the first
 * existing device (`bindings.keys.firstOrNull()`), so a gamepad binding was visible but never
 * editable. Any other device that already has a binding (mouse / vr) is appended after, so
 * existing bindings stay editable without inventing remap UX for devices that aren't bound.
 */
fun remappableDevices(bindings: Map<String, List<InputTrigger>>): List<String> {
    val base = listOf("keyboard", "gamepad")
    return base + bindings.keys.filter { it !in base }
}
