package com.sza.fastmediasorter.ui.calculator.helpers

import com.sza.fastmediasorter.ui.calculator.helpers.CalculatorEngine.CalculatorError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalculatorEngineTest {

    @Test
    fun `addition returns sum`() {
        val engine = CalculatorEngine()

        engine.inputDigit(2)
        engine.inputOperator("+")
        engine.inputDigit(3)
        engine.inputEquals()

        assertEquals("5", engine.display)
        assertEquals("2+3=", engine.operationHistory)
        assertEquals(listOf("2+3=5"), engine.calculationHistory)
    }

    @Test
    fun `equals repeats last operation`() {
        val engine = CalculatorEngine()

        engine.inputDigit(5)
        engine.inputOperator("+")
        engine.inputDigit(9)
        engine.inputEquals()
        assertEquals("14", engine.display)
        assertEquals("5+9=", engine.operationHistory)

        engine.inputEquals()
        assertEquals("23", engine.display)
        assertEquals("14+9=", engine.operationHistory)
        assertEquals(listOf("5+9=14", "14+9=23"), engine.calculationHistory)
    }

    @Test
    fun `clear history keeps current result`() {
        val engine = CalculatorEngine()

        engine.inputDigit(5)
        engine.inputOperator("+")
        engine.inputDigit(9)
        engine.inputEquals()
        engine.clearHistory()

        assertEquals("14", engine.display)
        assertEquals("", engine.operationHistory)
        assertEquals(emptyList<String>(), engine.calculationHistory)
    }

    @Test
    fun `percent with add operator applies percentage of accumulator`() {
        val engine = CalculatorEngine()

        engine.inputDigit(1)
        engine.inputDigit(0)
        engine.inputDigit(0)
        engine.inputOperator("+")
        engine.inputDigit(5)
        engine.inputDigit(0)
        engine.inputPercent()
        engine.inputEquals()

        assertEquals("150", engine.display)
    }

    @Test
    fun `percent with subtract operator applies percentage of accumulator`() {
        val engine = CalculatorEngine()

        engine.inputDigit(1)
        engine.inputDigit(0)
        engine.inputDigit(0)
        engine.inputOperator("-")
        engine.inputDigit(2)
        engine.inputDigit(5)
        engine.inputPercent()
        engine.inputEquals()

        assertEquals("75", engine.display)
    }

    @Test
    fun `percent with multiply operator treats percentage as decimal fraction`() {
        val engine = CalculatorEngine()

        engine.inputDigit(2)
        engine.inputOperator("×")
        engine.inputDigit(5)
        engine.inputDigit(0)
        engine.inputPercent()
        engine.inputEquals()

        assertEquals("1", engine.display)
    }

    @Test
    fun `round display rounds current value`() {
        val engine = CalculatorEngine()

        engine.inputDigit(1)
        engine.inputDecimal()
        engine.inputDigit(5)
        engine.roundDisplay()

        assertEquals("2", engine.display)
    }

    @Test
    fun `new digit after equals clears previous operation history`() {
        val engine = CalculatorEngine()

        engine.inputDigit(5)
        engine.inputOperator("+")
        engine.inputDigit(9)
        engine.inputEquals()
        engine.inputDigit(3)

        assertEquals("3", engine.display)
        assertEquals("", engine.operationHistory)
    }

    @Test
    fun `paste expression records history and result`() {
        val engine = CalculatorEngine()

        engine.inputNumber("12+15")

        assertEquals("27", engine.display)
        assertEquals("12+15=", engine.operationHistory)
        assertEquals(listOf("12+15=27"), engine.calculationHistory)
    }

    @Test
    fun `paste expression ignores non numeric noise`() {
        val engine = CalculatorEngine()

        engine.inputNumber("12кар+15биб")

        assertEquals("27", engine.display)
        assertEquals("12+15=", engine.operationHistory)
        assertEquals(listOf("12+15=27"), engine.calculationHistory)
    }

    @Test
    fun `canParseInput accepts noisy numeric expression`() {
        val engine = CalculatorEngine()

        assertEquals(true, engine.canParseInput("12кар+15биб"))
    }

    @Test
    fun `canParseInput rejects non numeric noise`() {
        val engine = CalculatorEngine()

        assertEquals(false, engine.canParseInput("карбиб"))
    }

    @Test
    fun `paste expression accepts comma and dot decimal separators`() {
        val engine = CalculatorEngine()

        engine.inputNumber("12,5кар+1.25биб")

        assertEquals("13.75", engine.display)
        assertEquals("12.5+1.25=", engine.operationHistory)
        assertEquals(listOf("12.5+1.25=13.75"), engine.calculationHistory)
    }

    @Test
    fun `paste expression skips malformed trailing operators`() {
        val engine = CalculatorEngine()

        engine.inputNumber("12кар++15биб+")

        assertEquals("27", engine.display)
        assertEquals("12+15=", engine.operationHistory)
        assertEquals(listOf("12+15=27"), engine.calculationHistory)
    }

    @Test
    fun `subtraction returns difference`() {
        val engine = CalculatorEngine()

        engine.inputDigit(9)
        engine.inputOperator("-")
        engine.inputDigit(4)
        engine.inputEquals()

        assertEquals("5", engine.display)
    }

    @Test
    fun `multiplication returns product`() {
        val engine = CalculatorEngine()

        engine.inputDigit(6)
        engine.inputOperator("×")
        engine.inputDigit(7)
        engine.inputEquals()

        assertEquals("42", engine.display)
    }

    @Test
    fun `division returns quotient`() {
        val engine = CalculatorEngine()

        engine.inputDigit(8)
        engine.inputOperator("÷")
        engine.inputDigit(2)
        engine.inputEquals()

        assertEquals("4", engine.display)
    }

    @Test
    fun `decimal input keeps fraction`() {
        val engine = CalculatorEngine()

        engine.inputDigit(1)
        engine.inputDecimal()
        engine.inputDigit(5)

        assertEquals("1.5", engine.display)
    }

    @Test
    fun `percent divides current entry by one hundred`() {
        val engine = CalculatorEngine()

        engine.inputDigit(5)
        engine.inputDigit(0)
        engine.inputPercent()

        assertEquals("0.5", engine.display)
    }

    @Test
    fun `sign change toggles current entry`() {
        val engine = CalculatorEngine()

        engine.inputDigit(5)
        engine.toggleSign()
        assertEquals("-5", engine.display)

        engine.toggleSign()
        assertEquals("5", engine.display)
    }

    @Test
    fun `clear entry replaces only current entry`() {
        val engine = CalculatorEngine()

        engine.inputDigit(1)
        engine.inputDigit(2)
        engine.inputOperator("+")
        engine.inputDigit(9)
        engine.clearEntry()
        engine.inputDigit(3)
        engine.inputEquals()

        assertEquals("15", engine.display)
    }

    @Test
    fun `backspace removes last digit`() {
        val engine = CalculatorEngine()

        engine.inputDigit(1)
        engine.inputDigit(2)
        engine.inputDigit(3)
        engine.backspace()

        assertEquals("12", engine.display)
    }

    @Test
    fun `divisionByZero stores error and resets display`() {
        val engine = CalculatorEngine()

        engine.inputDigit(8)
        engine.inputOperator("÷")
        engine.inputDigit(0)
        engine.inputEquals()

        assertEquals("0", engine.display)
        assertEquals(CalculatorError.DIVISION_BY_ZERO, engine.error)

        engine.inputDigit(5)
        assertEquals("5", engine.display)
        assertNull(engine.error)
    }
}
