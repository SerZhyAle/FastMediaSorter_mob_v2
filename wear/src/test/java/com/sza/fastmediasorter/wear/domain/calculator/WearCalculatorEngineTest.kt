package com.sza.fastmediasorter.wear.domain.calculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WearCalculatorEngineTest {

    private val engine = WearCalculatorEngine()

    @Test
    fun `chained operators evaluate left to right like the phone`() {
        engine.enter("2")
        engine.inputOperator("+")
        engine.enter("3")
        engine.inputOperator("×")
        engine.enter("4")

        assertEquals("20", engine.inputEquals())
    }

    @Test
    fun `decimal addition is exact`() {
        engine.enter("0.1")
        engine.inputOperator("+")
        engine.enter("0.2")

        assertEquals("0.3", engine.inputEquals())
        assertFalse(engine.isError)
    }

    @Test
    fun `division by zero raises the error state instead of throwing`() {
        engine.enter("5")
        engine.inputOperator("÷")
        engine.enter("0")

        engine.inputEquals()

        assertTrue(engine.isError)
        assertEquals(null, engine.lastExpression)
    }

    @Test
    fun `the next digit after an error starts a clean entry`() {
        engine.enter("5")
        engine.inputOperator("÷")
        engine.enter("0")
        engine.inputEquals()

        assertEquals("7", engine.inputDigit(7))
        assertFalse(engine.isError)
    }

    @Test
    fun `sign toggle and backspace on an empty display do nothing`() {
        assertEquals("0", engine.toggleSign())
        assertEquals("0", engine.backspace())
    }

    @Test
    fun `equals records the expression for the history`() {
        engine.enter("6")
        engine.inputOperator("×")
        engine.enter("7")

        assertEquals("42", engine.inputEquals())
        assertEquals("6 × 7", engine.lastExpression)
    }

    @Test
    fun `every function returns its expected value for a representative argument`() {
        val expected = mapOf(
            WearCalculatorFunction.SINE to ("90" to "1"),
            WearCalculatorFunction.COSINE to ("0" to "1"),
            WearCalculatorFunction.TANGENT to ("45" to "1"),
            WearCalculatorFunction.COTANGENT to ("45" to "1"),
            WearCalculatorFunction.SQUARE_ROOT to ("9" to "3"),
            WearCalculatorFunction.CUBE_ROOT to ("27" to "3"),
            WearCalculatorFunction.SQUARE to ("3" to "9"),
            WearCalculatorFunction.RECIPROCAL to ("4" to "0.25"),
            WearCalculatorFunction.LOG10 to ("100" to "2"),
            WearCalculatorFunction.NATURAL_LOG to ("1" to "0"),
            WearCalculatorFunction.FACTORIAL to ("5" to "120"),
            WearCalculatorFunction.PERCENT to ("50" to "0.5"),
            WearCalculatorFunction.ROUND to ("2.6" to "3")
        )

        // PI takes no argument, so it is asserted separately rather than left out of the coverage below.
        assertEquals(
            WearCalculatorFunction.entries.size,
            expected.size + 1
        )

        expected.forEach { (function, sample) ->
            val fresh = WearCalculatorEngine()
            fresh.enter(sample.first)

            assertEquals("$function", sample.second, fresh.apply(function))
            assertFalse("$function", fresh.isError)
        }
    }

    @Test
    fun `pi produces the constant regardless of the display`() {
        engine.enter("42")

        assertTrue(engine.apply(WearCalculatorFunction.PI).startsWith("3.14"))
    }

    @Test
    fun `an argument outside a function domain raises the error state`() {
        val undefined = mapOf(
            WearCalculatorFunction.SQUARE_ROOT to "-1",
            WearCalculatorFunction.RECIPROCAL to "0",
            WearCalculatorFunction.LOG10 to "0",
            WearCalculatorFunction.NATURAL_LOG to "0",
            WearCalculatorFunction.FACTORIAL to "0.5",
            WearCalculatorFunction.COTANGENT to "0"
        )

        undefined.forEach { (function, sample) ->
            val fresh = WearCalculatorEngine()
            fresh.enter(sample)

            fresh.apply(function)

            assertTrue("$function", fresh.isError)
        }
    }

    @Test
    fun `clear resets the whole engine`() {
        engine.enter("12")
        engine.inputOperator("+")
        engine.enter("3")
        engine.inputEquals()

        assertEquals("0", engine.clear())
        assertEquals(null, engine.lastExpression)
        assertFalse(engine.isError)
    }
}

/** Types [text] key by key, so a test states what the user pressed rather than a private field. */
private fun WearCalculatorEngine.enter(text: String) {
    var negative = false
    text.forEach { symbol ->
        when (symbol) {
            '-' -> negative = true
            '.' -> inputDecimal()
            else -> inputDigit(Character.getNumericValue(symbol))
        }
    }
    if (negative) {
        toggleSign()
    }
}
