package com.sza.fastmediasorter.ui.stopwatch.helpers

import org.junit.Assert.assertEquals
import org.junit.Test

/** S1411 phase 02 - the reading shown in the large digits, including both band edges. */
class StopwatchTimeFormatterTest {

    @Test
    fun `zero renders both fast fields at full width`() {
        assertEquals("0:00.00", StopwatchTimeFormatter.format(0L))
    }

    @Test
    fun `a sub-second value renders as hundredths`() {
        assertEquals("0:00.07", StopwatchTimeFormatter.format(70L))
        assertEquals("0:00.99", StopwatchTimeFormatter.format(999L))
    }

    @Test
    fun `exactly one minute crosses into the minute field`() {
        assertEquals("1:00.00", StopwatchTimeFormatter.format(60_000L))
        assertEquals("0:59.99", StopwatchTimeFormatter.format(59_999L))
    }

    @Test
    fun `exactly one hour widens the reading to the hour band`() {
        assertEquals("59:59.99", StopwatchTimeFormatter.format(3_599_999L))
        assertEquals("1:00:00.00", StopwatchTimeFormatter.format(3_600_000L))
    }

    @Test
    fun `past a day the hours keep counting rather than wrapping`() {
        assertEquals("25:00:00.00", StopwatchTimeFormatter.format(90_000_000L))
    }

    @Test
    fun `the fast fields keep their width across a whole second`() {
        val widths = (0L until 1_000L step 10L)
            .map { StopwatchTimeFormatter.format(it).length }
            .distinct()

        assertEquals(listOf(7), widths)
    }

    @Test
    fun `a negative reading is clamped rather than rendered with a sign`() {
        assertEquals("0:00.00", StopwatchTimeFormatter.format(-5_000L))
    }
}
