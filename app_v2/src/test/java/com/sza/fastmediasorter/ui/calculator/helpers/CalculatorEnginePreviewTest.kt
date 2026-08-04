package com.sza.fastmediasorter.ui.calculator.helpers

import com.sza.fastmediasorter.ui.calculator.helpers.CalculatorEngine.CalculatorError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * S1241: the live preview shown while the right operand is still being typed.
 *
 * The load-bearing case is the owner's own: `1000 ÷ 0` on the way to `1000 ÷ 0.1` must preview
 * nothing and must not arm the error - the division-by-zero verdict belongs to "=".
 */
class CalculatorEnginePreviewTest {

    private fun engineTyping(left: Int, operator: String, right: String): CalculatorEngine {
        val engine = CalculatorEngine()
        left.toString().forEach { engine.inputDigit(Character.getNumericValue(it)) }
        engine.inputOperator(operator)
        right.forEach { char ->
            if (char == '.') engine.inputDecimal() else engine.inputDigit(Character.getNumericValue(char))
        }
        return engine
    }

    @Test
    fun `previews the pending operation while the right operand is typed`() {
        assertEquals("200", engineTyping(1000, "÷", "5").previewResult())
    }

    @Test
    fun `preview follows each keystroke`() {
        val engine = CalculatorEngine()
        engine.inputDigit(1)
        engine.inputDigit(2)
        engine.inputOperator("+")

        engine.inputDigit(3)
        assertEquals("15", engine.previewResult())
        engine.inputDigit(4)
        assertEquals("46", engine.previewResult())
    }

    @Test
    fun `division by zero previews nothing and does not arm the error`() {
        val engine = engineTyping(1000, "÷", "0")

        assertNull(engine.previewResult())
        assertNull(engine.error)
        assertEquals("0", engine.display)
    }

    @Test
    fun `continuing past the zero to a real divisor previews again`() {
        val engine = engineTyping(1000, "÷", "0.1")

        assertEquals("10000", engine.previewResult())
        assertNull(engine.error)
    }

    @Test
    fun `pressing equals on a zero divisor still raises the error`() {
        val engine = engineTyping(1000, "÷", "0")

        engine.inputEquals()

        assertEquals(CalculatorError.DIVISION_BY_ZERO, engine.error)
    }

    @Test
    fun `no preview before the right operand is typed`() {
        val engine = CalculatorEngine()
        engine.inputDigit(7)
        engine.inputOperator("×")

        assertNull(engine.previewResult())
    }

    @Test
    fun `no preview without a pending operation`() {
        val engine = CalculatorEngine()
        engine.inputDigit(7)

        assertNull(engine.previewResult())
    }

    @Test
    fun `no preview once the operation is committed`() {
        val engine = engineTyping(6, "+", "4")
        assertEquals("10", engine.previewResult())

        engine.inputEquals()

        assertNull(engine.previewResult())
    }

    @Test
    fun `no preview while an error is showing`() {
        val engine = engineTyping(1000, "÷", "0")
        engine.inputEquals()

        assertNull(engine.previewResult())
    }

    @Test
    fun `modulo by zero previews nothing`() {
        assertNull(engineTyping(10, "mod", "0").previewResult())
    }

    @Test
    fun `integer division by zero previews nothing`() {
        assertNull(engineTyping(10, "DIV", "0").previewResult())
    }

    @Test
    fun `preview and equals agree on the same number`() {
        val engine = engineTyping(22, "÷", "7")
        val previewed = engine.previewResult()

        engine.inputEquals()

        assertEquals(previewed, engine.display)
    }
}
