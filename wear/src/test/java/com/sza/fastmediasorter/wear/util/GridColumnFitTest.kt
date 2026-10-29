package com.sza.fastmediasorter.wear.util

import com.sza.fastmediasorter.wear.domain.model.WearViewMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The widths below are the inscribed-square figures measured on 2026-08-18: about 170 dp usable on a
 * 240x240 dp round watch and about 127 dp on a 180 dp one.
 *
 * Three screens now depend on this refusal, not one: S2003 put the home screen and both category
 * screens behind the same call, on displays nobody has measured. That is why the last test guards the
 * invariant across a width sweep instead of pinning one more example.
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

    @Test
    fun `every width from 60 to 240 dp leaves a tappable cell in every mode`() {
        for (widthDp in SWEEP_MIN_WIDTH_DP..SWEEP_MAX_WIDTH_DP) {
            for (mode in WearViewMode.entries) {
                val columns = GridColumnFit.columnsFor(mode, availableWidthDp = widthDp)
                val gaps = GridColumnFit.DEFAULT_GAP_DP * (columns - 1)
                val cellWidthDp = (widthDp - gaps) / columns
                assertTrue(
                    "mode=$mode width=$widthDp columns=$columns cell=$cellWidthDp",
                    cellWidthDp >= GridColumnFit.DEFAULT_MIN_TARGET_DP
                )
            }
        }
    }

    /**
     * S4469: "Изображения" is about 74 dp wide at the 12sp caption floor, and a three-column cell on the
     * 170 dp usable width is 54 dp - the label reached the glass as "Изображен..".
     */
    @Test
    fun `a label that fits the cell keeps the requested columns`() {
        assertEquals(3, GridColumnFit.columnsFittingLabel(columns = 3, availableWidthDp = 170, widestLabelDp = 40))
    }

    @Test
    fun `a label wider than a three column cell steps down to two`() {
        assertEquals(2, GridColumnFit.columnsFittingLabel(columns = 3, availableWidthDp = 170, widestLabelDp = 74))
    }

    @Test
    fun `a label wider than every shared cell lands on one column`() {
        assertEquals(1, GridColumnFit.columnsFittingLabel(columns = 3, availableWidthDp = 170, widestLabelDp = 90))
    }

    @Test
    fun `fitting a label never adds a column`() {
        for (requested in 1..LABEL_SWEEP_MAX_COLUMNS) {
            for (labelDp in 0..LABEL_SWEEP_MAX_WIDTH_DP) {
                val fitted = GridColumnFit.columnsFittingLabel(
                    columns = requested,
                    availableWidthDp = 170,
                    widestLabelDp = labelDp
                )
                assertTrue("requested=$requested label=$labelDp fitted=$fitted", fitted in 1..requested)
            }
        }
    }

    private companion object {
        const val SWEEP_MIN_WIDTH_DP = 60
        const val SWEEP_MAX_WIDTH_DP = 240
        const val LABEL_SWEEP_MAX_COLUMNS = 3
        const val LABEL_SWEEP_MAX_WIDTH_DP = 200
    }
}
