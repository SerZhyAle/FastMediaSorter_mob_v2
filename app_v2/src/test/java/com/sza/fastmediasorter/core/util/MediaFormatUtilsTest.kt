package com.sza.fastmediasorter.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class MediaFormatUtilsTest {

    @Test
    fun `formatMediaDuration returns null for null input`() {
        assertNull(formatMediaDuration(null))
    }

    @Test
    fun `formatMediaDuration returns null for zero duration`() {
        assertNull(formatMediaDuration(0L))
    }

    @Test
    fun `formatMediaDuration returns null for negative duration`() {
        assertNull(formatMediaDuration(-1000L))
    }

    @Test
    fun `formatMediaDuration formats seconds-only correctly`() {
        // 45 seconds
        val result = formatMediaDuration(45_000L)
        assertEquals("00:45", result)
    }

    @Test
    fun `formatMediaDuration formats minutes and seconds without hours`() {
        // 1 min 30 sec = 90 000ms
        val result = formatMediaDuration(90_000L)
        assertEquals("01:30", result)
    }

    @Test
    fun `formatMediaDuration formats exactly 59 minutes 59 seconds`() {
        val result = formatMediaDuration(3_599_000L)
        assertEquals("59:59", result)
    }

    @Test
    fun `formatMediaDuration formats 1 hour boundary`() {
        // Exactly 1 hour
        val result = formatMediaDuration(3_600_000L)
        assertEquals("1:00:00", result)
    }

    @Test
    fun `formatMediaDuration formats hours minutes seconds`() {
        // 1h 1m 1s = 3 661 000ms
        val result = formatMediaDuration(3_661_000L)
        assertEquals("1:01:01", result)
    }

    @Test
    fun `formatMediaDuration handles large duration correctly`() {
        // 11h 22min 33sec
        val durationMs = ((11 * 3600) + (22 * 60) + 33) * 1000L
        val result = formatMediaDuration(durationMs)
        assertEquals("11:22:33", result)
    }
}
