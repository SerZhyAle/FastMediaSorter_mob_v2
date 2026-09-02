package com.sza.fastmediasorter.ui.calculator.helpers

import org.junit.Assert.assertEquals
import org.junit.Test

class CalculatorDisplayFormatterTest {

    private val formatter = CalculatorDisplayFormatter()

    private fun grouped(raw: String) = formatter.format(raw, groupThousands = true)

    @Test
    fun `groups the integer part in threes from the right`() {
        assertEquals("1,234,567", grouped("1234567"))
        assertEquals("1,234", grouped("1234"))
        assertEquals("12,345", grouped("12345"))
    }

    @Test
    fun `groups the fractional part in threes from the left`() {
        assertEquals("1,234.567,89", grouped("1234.56789"))
        assertEquals("0.123,456", grouped("0.123456"))
    }

    @Test
    fun `keeps the leading minus sign`() {
        assertEquals("-1,234,567", grouped("-1234567"))
        assertEquals("-0.123,456", grouped("-0.123456"))
    }

    @Test
    fun `leaves short numbers untouched`() {
        assertEquals("123", grouped("123"))
        assertEquals("0", grouped("0"))
        assertEquals("12.345", grouped("12.345"))
    }

    @Test
    fun `keeps a trailing decimal point the user has typed`() {
        assertEquals("12.", grouped("12."))
        assertEquals("1,234.", grouped("1234."))
    }

    @Test
    fun `returns the raw string when grouping is disabled`() {
        assertEquals("1234567", formatter.format("1234567", groupThousands = false))
        assertEquals("1234.56789", formatter.format("1234.56789", groupThousands = false))
    }

    @Test
    fun `returns non-numeric text unchanged`() {
        assertEquals("", grouped(""))
        assertEquals("Division by zero", grouped("Division by zero"))
        assertEquals("1.2.3", grouped("1.2.3"))
        assertEquals("1e10", grouped("1e10"))
    }
}
