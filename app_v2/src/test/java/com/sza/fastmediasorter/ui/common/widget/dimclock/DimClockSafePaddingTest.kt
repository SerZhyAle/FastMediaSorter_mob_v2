package com.sza.fastmediasorter.ui.common.widget.dimclock

import androidx.core.graphics.Insets
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the dim clock's safe padding (S3369): with the bars hidden the old code read a zero inset and
 * left the notification row on the camera cutout at the bare 16 dp XML padding.
 */
class DimClockSafePaddingTest {

    @Test
    fun portraitClearsTheStatusBarAndShiftsTheStartEdgeByTheSameAmount() {
        val padding = DimClockOverlayView.contentPaddingFor(
            barsIgnoringVisibility = Insets.of(0, STATUS_BAR, 0, NAV_BAR),
            cutout = Insets.of(0, CUTOUT, 0, 0),
            basePadding = BASE,
            minimumShift = MIN_SHIFT,
            isRtl = false,
        )
        assertEquals(BASE + STATUS_BAR, padding.top)
        assertEquals(padding.top, padding.left)
        assertEquals(BASE, padding.right)
        assertEquals(BASE + NAV_BAR, padding.bottom)
    }

    @Test
    fun aCutoutTallerThanTheStatusBarDecidesTheShift() {
        val padding = DimClockOverlayView.contentPaddingFor(
            barsIgnoringVisibility = Insets.of(0, CUTOUT, 0, 0),
            cutout = Insets.of(0, STATUS_BAR, 0, 0),
            basePadding = BASE,
            minimumShift = MIN_SHIFT,
            isRtl = false,
        )
        assertEquals(BASE + STATUS_BAR, padding.top)
    }

    @Test
    fun noSafeAreaStillTakesTheMinimumShift() {
        val padding = DimClockOverlayView.contentPaddingFor(
            barsIgnoringVisibility = Insets.NONE,
            cutout = Insets.NONE,
            basePadding = BASE,
            minimumShift = MIN_SHIFT,
            isRtl = false,
        )
        assertEquals(BASE + MIN_SHIFT, padding.top)
        assertEquals(BASE + MIN_SHIFT, padding.left)
    }

    @Test
    fun landscapeCutoutOnTheStartSideAddsToTheShift() {
        val padding = DimClockOverlayView.contentPaddingFor(
            barsIgnoringVisibility = Insets.of(0, STATUS_BAR, 0, 0),
            cutout = Insets.of(CUTOUT, 0, 0, 0),
            basePadding = BASE,
            minimumShift = MIN_SHIFT,
            isRtl = false,
        )
        assertEquals(BASE + CUTOUT + STATUS_BAR, padding.left)
        assertEquals(BASE + STATUS_BAR, padding.top)
    }

    @Test
    fun rightToLeftTakesTheRightSafeInsetAsTheStart() {
        val padding = DimClockOverlayView.contentPaddingFor(
            barsIgnoringVisibility = Insets.of(0, STATUS_BAR, 0, 0),
            cutout = Insets.of(0, 0, CUTOUT, 0),
            basePadding = BASE,
            minimumShift = MIN_SHIFT,
            isRtl = true,
        )
        assertEquals(BASE + CUTOUT + STATUS_BAR, padding.left)
        assertEquals(BASE, padding.right)
    }

    private companion object {
        const val BASE = 42
        const val MIN_SHIFT = 42
        const val STATUS_BAR = 110
        const val CUTOUT = 90
        const val NAV_BAR = 63
    }
}
