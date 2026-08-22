package com.sza.fastmediasorter.wear.util

import com.sza.fastmediasorter.wear.domain.model.WearViewMode
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The widths below are the inscribed-square figures measured on 2026-08-18: about 170 dp usable on a
 * 240x240 dp round watch and about 127 dp on a 180 dp one.
 */
class GridColumnFitTest {

    @Test
    fun `list mode is always a single column`() {
        assertEquals(1, GridColumnFit.columnsFor(WearViewMode.LIST, availableWidthDp = 170))
        assertEquals(1, GridColumnFit.columnsFor(WearViewMode.LIST, availableWidthDp = 60))
        assertEquals(1, GridColumnFit.columnsFor(WearViewMode.LIST, availableWidthDp = 0))
    }

    @Test
    fun `grid3 keeps three columns on the large round watch`() {
        assertEquals(3, GridColumnFit.columnsFor(WearViewMode.GRID_3, availableWidthDp = 170))
    }

    @Test
    fun `grid3 steps down to two columns on the small round watch`() {
        assertEquals(2, GridColumnFit.columnsFor(WearViewMode.GRID_3, availableWidthDp = 127))
    }

    @Test
    fun `grid2 keeps two columns on the small round watch`() {
        assertEquals(2, GridColumnFit.columnsFor(WearViewMode.GRID_2, availableWidthDp = 127))
    }

    @Test
    fun `grid2 steps down to one column when the cell would miss the target`() {
        assertEquals(1, GridColumnFit.columnsFor(WearViewMode.GRID_2, availableWidthDp = 60))
    }

    @Test
    fun `column count never drops below one`() {
        assertEquals(1, GridColumnFit.columnsFor(WearViewMode.GRID_3, availableWidthDp = 0))
    }
}
