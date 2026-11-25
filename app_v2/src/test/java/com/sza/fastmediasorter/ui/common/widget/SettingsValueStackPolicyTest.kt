package com.sza.fastmediasorter.ui.common.widget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsValueStackPolicyTest {

    @Test
    fun `value fits on one line beside the caption - stays beside`() {
        assertFalse(SettingsValueStackPolicy.shouldStack(1000, 400, 300, 120))
    }

    @Test
    fun `room equals the value width - stays beside`() {
        assertFalse(SettingsValueStackPolicy.shouldStack(1000, 300, 300, 120))
    }

    @Test
    fun `value wraps at word boundaries inside a room above a third - stays beside`() {
        assertFalse(SettingsValueStackPolicy.shouldStack(1000, 400, 700, 250))
    }

    @Test
    fun `widest word wider than the room within a third of the row - stacks`() {
        assertTrue(SettingsValueStackPolicy.shouldStack(1000, 340, 700, 440))
    }

    @Test
    fun `room under a third of the row - stacks even when every word fits`() {
        assertTrue(SettingsValueStackPolicy.shouldStack(1000, 320, 700, 120))
    }

    @Test
    fun `zero room - stacks`() {
        assertTrue(SettingsValueStackPolicy.shouldStack(1000, 0, 300, 120))
    }

    @Test
    fun `negative room when the caption alone overflows the row - stacks`() {
        assertTrue(SettingsValueStackPolicy.shouldStack(1000, -60, 300, 120))
    }
}
