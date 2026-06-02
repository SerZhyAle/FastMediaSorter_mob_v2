package com.sza.fastmediasorter.ui.calculator.helpers

import com.sza.fastmediasorter.ui.calculator.helpers.CalculatorExpressionEvaluator.Result
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculatorExpressionEvaluatorTest {

    private fun value(text: String): BigDecimal {
        val result = CalculatorExpressionEvaluator.evaluate(text)
        assertTrue("expected Success for '$text' but was $result", result is Result.Success)
        return (result as Result.Success).value
    }

    private fun assertValue(expected: String, text: String) {
        assertEquals("'$text'", 0, value(text).compareTo(BigDecimal(expected)))
    }

    @Test
    fun `whitespace separated bare numbers are summed`() {
        assertValue("142.4", "125.4 8 9")
    }

    @Test
    fun `newline separated bare numbers are summed`() {
        assertValue("142.4", "125.4\n8\n9")
    }

    @Test
    fun `multiplication has higher precedence than addition`() {
        assertValue("14", "2+3*4")
    }

    @Test
    fun `parentheses override precedence`() {
        assertValue("20", "(2+3)*4")
    }

    @Test
    fun `power operator evaluates`() {
        assertValue("1024", "2^10")
    }

    @Test
    fun `square root glyph evaluates`() {
        assertValue("3", "√9")
    }

    @Test
    fun `comma is decimal separator when alone`() {
        assertValue("1.5", "1,5")
    }

    @Test
    fun `comma is thousands separator when dot also present`() {
        assertValue("1234.56", "1,234.56")
    }

    @Test
    fun `multiple dots keep only the last as decimal`() {
        assertValue("12.3", "1.2.3")
    }

    @Test
    fun `multiple commas keep only the last as decimal`() {
        assertValue("12.3", "1,2,3")
    }

    @Test
    fun `non numeric noise around operators is ignored`() {
        assertValue("27", "12кар+15биб")
    }

    @Test
    fun `mixed expression from selection evaluates`() {
        assertValue("17", "2 + 3 * 5")
    }

    @Test
    fun `single bare number is not flagged as expression`() {
        val result = CalculatorExpressionEvaluator.evaluate("555")
        assertTrue(result is Result.Success)
        assertEquals(false, (result as Result.Success).isExpression)
        assertEquals(0, result.value.compareTo(BigDecimal("555")))
    }

    @Test
    fun `division by zero is reported`() {
        assertEquals(
            Result.Kind.DIVIDE_BY_ZERO,
            (CalculatorExpressionEvaluator.evaluate("8/0") as Result.Failure).kind,
        )
    }

    @Test
    fun `square root of negative is domain failure`() {
        assertEquals(
            Result.Kind.DOMAIN,
            (CalculatorExpressionEvaluator.evaluate("√-1") as Result.Failure).kind,
        )
    }

    @Test
    fun `pure noise fails to parse`() {
        assertTrue(CalculatorExpressionEvaluator.evaluate("карбиб") is Result.Failure)
    }
}
