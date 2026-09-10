package com.sza.fastmediasorter.wear.domain.stopwatch

import org.junit.Assert.assertEquals
import org.junit.Test

class WearStopwatchTimeFormatterTest {

    @Test
    fun `sub-minute readings keep a single minute digit`() {
        assertEquals("0:00.00", WearStopwatchTimeFormatter.format(0L))
        assertEquals("0:01.23", WearStopwatchTimeFormatter.format(1_234L))
        assertEquals("0:59.99", WearStopwatchTimeFormatter.format(59_999L))
    }

    @Test
    fun `minutes and seconds are zero padded`() {
        assertEquals("1:00.00", WearStopwatchTimeFormatter.format(60_000L))
        assertEquals("12:05.07", WearStopwatchTimeFormatter.format(725_070L))
    }

    @Test
    fun `the hour band adds a field rather than wrapping`() {
        assertEquals("1:00:00.00", WearStopwatchTimeFormatter.format(3_600_000L))
        assertEquals("2:03:04.05", WearStopwatchTimeFormatter.format(7_384_050L))
    }

    @Test
    fun `hours are never wrapped into days`() {
        assertEquals("25:00:00.00", WearStopwatchTimeFormatter.format(90_000_000L))
    }

    @Test
    fun `a negative input reads as zero`() {
        assertEquals("0:00.00", WearStopwatchTimeFormatter.format(-5_000L))
    }

    @Test
    fun `hundredths are truncated, not rounded`() {
        assertEquals("0:00.09", WearStopwatchTimeFormatter.format(99L))
    }
}
