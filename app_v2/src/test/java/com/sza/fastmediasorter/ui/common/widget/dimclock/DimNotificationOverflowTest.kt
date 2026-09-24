package com.sza.fastmediasorter.ui.common.widget.dimclock

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * S3475: the notification row fits the screen - as many chips as fit before the end edge, and on
 * overflow one slot goes to the "N+" cell rather than a chip running off the screen.
 */
@Suppress("FunctionNaming") // backtick test names, project convention
class DimNotificationOverflowTest {

    @Test
    fun `slots count the spacing between chips but not after the last`() {
        assertEquals(1, DimClockOverlayView.notificationSlotsFor(availablePx = 24, chipPx = 24, spacingPx = 6))
        assertEquals(2, DimClockOverlayView.notificationSlotsFor(availablePx = 54, chipPx = 24, spacingPx = 6))
        assertEquals(1, DimClockOverlayView.notificationSlotsFor(availablePx = 53, chipPx = 24, spacingPx = 6))
        assertEquals(0, DimClockOverlayView.notificationSlotsFor(availablePx = 0, chipPx = 24, spacingPx = 6))
    }

    @Test
    fun `a row that fits shows every chip`() {
        assertEquals(10, DimClockOverlayView.visibleNotificationCount(total = 10, slots = 10))
        assertEquals(3, DimClockOverlayView.visibleNotificationCount(total = 3, slots = 10))
    }

    @Test
    fun `an overflowing row gives its last slot to the count`() {
        assertEquals(9, DimClockOverlayView.visibleNotificationCount(total = 14, slots = 10))
    }

    @Test
    fun `a single slot holds only the count`() {
        assertEquals(0, DimClockOverlayView.visibleNotificationCount(total = 5, slots = 1))
        assertEquals(0, DimClockOverlayView.visibleNotificationCount(total = 5, slots = 0))
    }
}
