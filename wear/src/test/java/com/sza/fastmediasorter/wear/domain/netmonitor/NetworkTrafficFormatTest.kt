package com.sza.fastmediasorter.wear.domain.netmonitor

import org.junit.Assert.assertEquals
import org.junit.Test

private const val KIB = 1024L
private const val MIB = 1024L * 1024
private const val GIB = 1024L * 1024 * 1024

/**
 * Every expectation is a literal read off the ladders the netmonitor screen file carried before
 * S2433 moved them here: strategic §11 criterion 2 keeps the screen's output unchanged, and the
 * risk §7 names is exactly a silent shift in one of these strings.
 */
class NetworkTrafficFormatTest {

    @Test
    fun `a rate below a kilobyte prints whole bytes per second`() {
        assertEquals("0 B/s", formatRate(0))
        assertEquals("1023 B/s", formatRate(1023))
    }

    @Test
    fun `a rate in kilobytes carries one decimal`() {
        assertEquals("1.0 KB/s", formatRate(KIB))
        assertEquals("1.5 KB/s", formatRate(KIB + KIB / 2))
    }

    @Test
    fun `a rate in megabytes carries two decimals and never steps to gigabytes`() {
        assertEquals("1.00 MB/s", formatRate(MIB))
        assertEquals("1024.00 MB/s", formatRate(GIB))
    }

    @Test
    fun `a total below a megabyte prints whole bytes and whole kilobytes`() {
        assertEquals("512 B", formatTrafficTotal(512))
        assertEquals("1 KB", formatTrafficTotal(KIB))
        assertEquals("2 KB", formatTrafficTotal(KIB + KIB / 2))
    }

    @Test
    fun `a total in megabytes carries one decimal`() {
        assertEquals("1.0 MB", formatTrafficTotal(MIB))
        assertEquals("1.5 MB", formatTrafficTotal(MIB + MIB / 2))
    }

    @Test
    fun `a total in gigabytes carries two decimals`() {
        assertEquals("1.00 GB", formatTrafficTotal(GIB))
        assertEquals("1.50 GB", formatTrafficTotal(GIB + GIB / 2))
    }
}
