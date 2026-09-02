package com.sza.fastmediasorter.ui.calculator.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculatorSettingsTest {

    @Test
    fun `default groups thousands and uses the normal keypad`() {
        val default = CalculatorSettings.DEFAULT

        assertTrue(default.groupThousands)
        assertEquals(CalculatorKeypadMode.NORMAL, default.keypadMode)
        assertEquals(CalculatorSettings.DEFAULT_TEXT_SIZE_SP, default.displayTextSizeSp)
    }

    @Test
    fun `coerced raises a text size below the minimum`() {
        val coerced = CalculatorSettings.DEFAULT.copy(displayTextSizeSp = 4).coerced()

        assertEquals(CalculatorSettings.MIN_TEXT_SIZE_SP, coerced.displayTextSizeSp)
    }

    @Test
    fun `coerced lowers a text size above the maximum`() {
        val coerced = CalculatorSettings.DEFAULT.copy(displayTextSizeSp = 500).coerced()

        assertEquals(CalculatorSettings.MAX_TEXT_SIZE_SP, coerced.displayTextSizeSp)
    }

    @Test
    fun `an unknown persisted keypad mode reads back as normal`() {
        assertEquals(CalculatorKeypadMode.NORMAL, CalculatorKeypadMode.fromNameOrDefault("HUGE"))
        assertEquals(CalculatorKeypadMode.NORMAL, CalculatorKeypadMode.fromNameOrDefault(null))
        assertEquals(CalculatorKeypadMode.COMPACT, CalculatorKeypadMode.fromNameOrDefault("COMPACT"))
    }
}
