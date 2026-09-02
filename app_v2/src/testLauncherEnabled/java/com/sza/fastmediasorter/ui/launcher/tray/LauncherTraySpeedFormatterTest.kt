package com.sza.fastmediasorter.ui.launcher.tray

import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherTraySpeedFormatterTest {

    @Test
    fun format_zero_returns_zero_bytes() {
        val result = LauncherTraySpeedFormatter.format(0.0)
        assertEquals("0.0", result.value)
        assertEquals(SpeedUnit.BYTES, result.unit)
    }

    @Test
    fun format_negative_formats_as_zero() {
        val result = LauncherTraySpeedFormatter.format(-100.0)
        assertEquals("0.0", result.value)
        assertEquals(SpeedUnit.BYTES, result.unit)
    }

    @Test
    fun format_below_10_bytes_uses_one_decimal() {
        val result = LauncherTraySpeedFormatter.format(5.4)
        assertEquals("5.4", result.value)
        assertEquals(SpeedUnit.BYTES, result.unit)
    }

    @Test
    fun format_at_or_above_10_bytes_uses_no_decimal() {
        val result = LauncherTraySpeedFormatter.format(12.0)
        assertEquals("12", result.value)
        assertEquals(SpeedUnit.BYTES, result.unit)
    }

    @Test
    fun format_kilobytes_boundary() {
        val result = LauncherTraySpeedFormatter.format(1024.0)
        assertEquals("1.0", result.value)
        assertEquals(SpeedUnit.KILOBYTES, result.unit)

        val result10kb = LauncherTraySpeedFormatter.format(10240.0)
        assertEquals("10", result10kb.value)
        assertEquals(SpeedUnit.KILOBYTES, result10kb.unit)
    }

    @Test
    fun format_megabytes_boundary() {
        val result = LauncherTraySpeedFormatter.format(1024.0 * 1024.0 * 2.5)
        assertEquals("2.5", result.value)
        assertEquals(SpeedUnit.MEGABYTES, result.unit)

        val result15mb = LauncherTraySpeedFormatter.format(1024.0 * 1024.0 * 15.0)
        assertEquals("15", result15mb.value)
        assertEquals(SpeedUnit.MEGABYTES, result15mb.unit)
    }

    @Test
    fun format_gigabytes_boundary() {
        val result = LauncherTraySpeedFormatter.format(1024.0 * 1024.0 * 1024.0 * 1.2)
        assertEquals("1.2", result.value)
        assertEquals(SpeedUnit.GIGABYTES, result.unit)
    }
}
