package com.sza.fastmediasorter.ui.calculator

import com.sza.fastmediasorter.ui.calculator.helpers.CalculatorEngine
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * S1549: a rotation now rebuilds the calculator's views, so the in-progress expression travels through
 * [CalculatorEngine.snapshot] and back. Each case runs the same key sequence twice - once uninterrupted, once
 * with a snapshot/restore inserted mid-expression - and asserts the two produce the same result, because the
 * result is what the user loses if the round trip drops a field.
 */
class CalculatorEngineStateTest {

    private fun engineAfterRoundTrip(source: CalculatorEngine): CalculatorEngine =
        CalculatorEngine().apply { restore(source.snapshot()) }

    @Test
    fun `pending binary operator survives the round trip`() {
        val uninterrupted = CalculatorEngine().apply {
            inputDigit(1)
            inputDigit(2)
            inputOperator("+")
            inputDigit(3)
        }
        val expected = uninterrupted.inputEquals()

        val interrupted = CalculatorEngine().apply {
            inputDigit(1)
            inputDigit(2)
            inputOperator("+")
        }
        val restored = engineAfterRoundTrip(interrupted).apply { inputDigit(3) }

        assertEquals(expected, restored.inputEquals())
    }

    @Test
    fun `repeat operation survives the round trip`() {
        val uninterrupted = CalculatorEngine().apply {
            inputDigit(5)
            inputOperator("×")
            inputDigit(3)
            inputEquals()
        }
        val expected = uninterrupted.inputEquals()

        val interrupted = CalculatorEngine().apply {
            inputDigit(5)
            inputOperator("×")
            inputDigit(3)
            inputEquals()
        }
        val restored = engineAfterRoundTrip(interrupted)

        assertEquals(expected, restored.inputEquals())
    }

    @Test
    fun `fresh-input boundary survives the round trip`() {
        // Right after equals the next digit starts a new number rather than appending to the result.
        val uninterrupted = CalculatorEngine().apply {
            inputDigit(8)
            inputOperator("+")
            inputDigit(1)
            inputEquals()
        }
        val expected = uninterrupted.inputDigit(7)

        val interrupted = CalculatorEngine().apply {
            inputDigit(8)
            inputOperator("+")
            inputDigit(1)
            inputEquals()
        }
        val restored = engineAfterRoundTrip(interrupted)

        assertEquals(expected, restored.inputDigit(7))
    }

    @Test
    fun `memory and history travel with the snapshot`() {
        val source = CalculatorEngine().apply {
            inputDigit(4)
            inputOperator("+")
            inputDigit(6)
            inputEquals()
            memoryAdd()
        }

        val restored = engineAfterRoundTrip(source)

        assertEquals(source.memoryDisplay, restored.memoryDisplay)
        assertEquals(source.calculationHistory, restored.calculationHistory)
        assertEquals(source.operationHistory, restored.operationHistory)
    }

    @Test
    fun `display text is carried verbatim`() {
        val source = CalculatorEngine().apply {
            inputDigit(9)
            inputDecimal()
            inputDigit(5)
        }

        assertEquals(source.display, engineAfterRoundTrip(source).display)
    }
}
