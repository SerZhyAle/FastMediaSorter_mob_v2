package com.sza.fastmediasorter.ui.common.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InputHelpRegistryTest {

    @Test
    fun `every surface has at least the global section`() {
        for (surface in UiSurface.values()) {
            val sections = InputHelpRegistry.get(surface)
            assertFalse(
                "Surface $surface has no help sections",
                sections.isEmpty(),
            )
        }
    }

    @Test
    fun `main surface exposes file operations`() {
        val sections = InputHelpRegistry.get(UiSurface.MAIN)
        val combined = sections.flatMap { it.entries }.map { it.keys }
        assertTrue(combined.any { it.contains("F5") })
        assertTrue(combined.any { it.contains("F8") || it.contains("Del") })
    }

    @Test
    fun `player surface exposes playback shortcuts`() {
        val sections = InputHelpRegistry.get(UiSurface.PLAYER)
        val combined = sections.flatMap { it.entries }.map { it.keys }
        assertTrue("missing play-pause", combined.any { it.contains("Space") })
        assertTrue("missing mute", combined.any { it == "M" })
        assertTrue("missing fullscreen", combined.any { it == "F" })
    }

    @Test
    fun `VR player reuses player help entries`() {
        assertEquals(
            InputHelpRegistry.get(UiSurface.PLAYER),
            InputHelpRegistry.get(UiSurface.VR_PLAYER),
        )
    }

    @Test
    fun `every displayed chord follows INPUT-CHORD rule 5 spelling`() {
        val violations = UiSurface.values()
            .flatMap { InputHelpRegistry.get(it) }
            .flatMap { it.entries }
            .map { it.keys }
            .distinct()
            .flatMap { it.split(ALTERNATIVE_SEPARATOR) }
            .filterNot { it == BARE_PLUS_KEY }
            .filterNot { isRuleFiveChord(it) }
        assertTrue("Chords outside rule 5 spelling: $violations", violations.isEmpty())
    }

    private fun isRuleFiveChord(chord: String): Boolean {
        val parts = chord.split(MODIFIER_SEPARATOR)
        val modifiers = parts.dropLast(1)
        val trigger = parts.last()
        val inCanonicalOrder = modifiers == MODIFIERS.filter { it in modifiers }
        return inCanonicalOrder && (trigger in NAMED_KEYS || TRIGGER_PATTERN.matches(trigger))
    }

    private companion object {
        const val ALTERNATIVE_SEPARATOR = " / "
        const val MODIFIER_SEPARATOR = "+"

        // KEYCODE_PLUS is the character key and has no rule 1 name; the contract admits only the
        // physical Equal and NumpadAdd triggers, so the one bare plus the main screen documents stays literal.
        const val BARE_PLUS_KEY = "+"

        val MODIFIERS = listOf("Ctrl", "Shift", "Alt", "Win")
        val NAMED_KEYS = setOf(
            "Space", "Enter", "Tab", "Esc", "Backspace", "Delete", "Insert", "Home", "End",
            "PageUp", "PageDown", "PrintScreen", "Left", "Right", "Up", "Down",
            "NumpadAdd", "NumpadSubtract", "NumpadMultiply", "NumpadDivide", "NumpadDecimal",
            "BracketLeft", "BracketRight", "Period", "Comma", "Equal", "Minus", "Backslash",
        )
        val TRIGGER_PATTERN = Regex("[A-Z0-9]|F([1-9]|1[0-9]|2[0-4])|Numpad[0-9]")
    }
}
