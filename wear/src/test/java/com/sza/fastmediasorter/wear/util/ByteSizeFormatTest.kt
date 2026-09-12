package com.sza.fastmediasorter.wear.util

import org.junit.Assert.assertEquals
import org.junit.Test

private const val KIB = 1024L
private const val MIB = 1024L * 1024
private const val GIB = 1024L * 1024 * 1024

private val SYSTEM_INFO_STYLE = ByteSizeStyle(
    minUnit = ByteSizeUnit.MEGABYTES,
    maxUnit = ByteSizeUnit.GIGABYTES,
    decimals = mapOf(ByteSizeUnit.MEGABYTES to 0, ByteSizeUnit.GIGABYTES to 1)
)

/**
 * Every expectation below is a literal read off the implementation S2433 replaced, not a second
 * computation: strategic §11 criterion 2 keeps the observable output of each surface unchanged, and
 * only a literal proves that after the ladder moved.
 */
class ByteSizeFormatTest {

    @Test
    fun `the file list ladder keeps bytes below a kilobyte`() {
        val amount = byteSizeAmount(1023, ByteSizeUnit.BYTES, ByteSizeUnit.MEGABYTES)
        assertEquals(ByteSizeUnit.BYTES, amount.unit)
        assertEquals(1023.0, amount.amount, 0.0)
    }

    @Test
    fun `the file list ladder takes the kilobyte step at a full kilobyte`() {
        assertEquals(ByteSizeUnit.KILOBYTES, byteSizeAmount(KIB, ByteSizeUnit.BYTES, ByteSizeUnit.MEGABYTES).unit)
        assertEquals(ByteSizeUnit.KILOBYTES, byteSizeAmount(MIB - 1, ByteSizeUnit.BYTES, ByteSizeUnit.MEGABYTES).unit)
    }

    @Test
    fun `the file list ladder stops at megabytes even for gigabyte sizes`() {
        val amount = byteSizeAmount(GIB, ByteSizeUnit.BYTES, ByteSizeUnit.MEGABYTES)
        assertEquals(ByteSizeUnit.MEGABYTES, amount.unit)
        assertEquals(1024.0, amount.amount, 0.0)
    }

    @Test
    fun `the system info report reads in megabytes below a gigabyte`() {
        assertEquals("0 MB", formatByteSize(0, SYSTEM_INFO_STYLE))
        assertEquals("1 MB", formatByteSize(MIB, SYSTEM_INFO_STYLE))
    }

    @Test
    fun `the system info report rounds the last megabyte up rather than stepping early`() {
        assertEquals("1024 MB", formatByteSize(GIB - 1, SYSTEM_INFO_STYLE))
    }

    @Test
    fun `the system info report takes the gigabyte step at a full gigabyte`() {
        assertEquals("1.0 GB", formatByteSize(GIB, SYSTEM_INFO_STYLE))
        assertEquals("1.5 GB", formatByteSize(GIB + GIB / 2, SYSTEM_INFO_STYLE))
    }
}
