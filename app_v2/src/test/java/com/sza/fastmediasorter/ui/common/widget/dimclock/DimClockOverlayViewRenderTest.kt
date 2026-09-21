package com.sza.fastmediasorter.ui.common.widget.dimclock

import com.sza.fastmediasorter.R
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the battery text and color ladder the dim overlay renders (S3361): the committed code
 * shipped a bare "%" because nothing pinned the percent value into the string.
 */
class DimClockOverlayViewRenderTest {

    @Test
    fun batteryTextChargingCarriesBoltAndNumber() {
        assertEquals("⚡ 85%", DimClockOverlayView.batteryTextFor(percent = 85, isCharging = true))
    }

    @Test
    fun batteryTextDischargingCarriesNumber() {
        assertEquals("42%", DimClockOverlayView.batteryTextFor(percent = 42, isCharging = false))
    }

    @Test
    fun batteryColorFollowsCriticalWarningChargingLadder() {
        assertEquals(R.color.error_color, DimClockOverlayView.batteryColorResFor(percent = 10, isCharging = false))
        assertEquals(R.color.warning_color, DimClockOverlayView.batteryColorResFor(percent = 15, isCharging = false))
        assertEquals(R.color.warning_color, DimClockOverlayView.batteryColorResFor(percent = 29, isCharging = false))
        assertEquals(R.color.white, DimClockOverlayView.batteryColorResFor(percent = 30, isCharging = false))
        assertEquals(R.color.success_color, DimClockOverlayView.batteryColorResFor(percent = 60, isCharging = true))
        assertEquals(R.color.white, DimClockOverlayView.batteryColorResFor(percent = 60, isCharging = false))
    }
}
