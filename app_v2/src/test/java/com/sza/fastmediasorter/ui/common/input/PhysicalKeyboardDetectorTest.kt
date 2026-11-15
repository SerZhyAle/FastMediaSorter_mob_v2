package com.sza.fastmediasorter.ui.common.input

import android.view.InputDevice
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhysicalKeyboardDetectorTest {

    @Test
    fun `a real alphabetic keyboard counts`() {
        assertTrue(
            PhysicalKeyboardDetector.isAlphabeticKeyboard(
                isVirtual = false,
                sources = InputDevice.SOURCE_KEYBOARD,
                keyboardType = InputDevice.KEYBOARD_TYPE_ALPHABETIC,
            ),
        )
    }

    @Test
    fun `a virtual device never counts`() {
        assertFalse(
            PhysicalKeyboardDetector.isAlphabeticKeyboard(
                isVirtual = true,
                sources = InputDevice.SOURCE_KEYBOARD,
                keyboardType = InputDevice.KEYBOARD_TYPE_ALPHABETIC,
            ),
        )
    }

    @Test
    fun `a gamepad without keyboard source does not count`() {
        assertFalse(
            PhysicalKeyboardDetector.isAlphabeticKeyboard(
                isVirtual = false,
                sources = InputDevice.SOURCE_GAMEPAD,
                keyboardType = InputDevice.KEYBOARD_TYPE_ALPHABETIC,
            ),
        )
    }

    @Test
    fun `a non-alphabetic keypad such as a remote does not count`() {
        assertFalse(
            PhysicalKeyboardDetector.isAlphabeticKeyboard(
                isVirtual = false,
                sources = InputDevice.SOURCE_KEYBOARD,
                keyboardType = InputDevice.KEYBOARD_TYPE_NON_ALPHABETIC,
            ),
        )
    }
}
