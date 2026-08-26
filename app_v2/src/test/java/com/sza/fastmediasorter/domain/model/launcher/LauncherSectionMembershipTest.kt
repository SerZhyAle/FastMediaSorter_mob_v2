package com.sza.fastmediasorter.domain.model.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S1428: positional membership (strategic §6.7) and the straddle predicate the placement layer refuses
 * a gadget on (§6.11, refined 2026-08-08).
 */
class LauncherSectionMembershipTest {

    @Test
    fun `header rows are ascending distinct rows of section cells only`() {
        val cells = listOf(
            cell(row = 4, kind = LauncherCellKind.SECTION),
            cell(row = 0, kind = LauncherCellKind.SECTION),
            cell(row = 2, kind = LauncherCellKind.SHORTCUT),
            cell(row = 6, kind = LauncherCellKind.GADGET),
        )

        assertEquals(listOf(0, 4), LauncherSectionMembership.headerRows(cells))
    }

    @Test
    fun `a negative stored row is floored rather than dropped`() {
        val cells = listOf(cell(row = -3, kind = LauncherCellKind.SECTION))

        assertEquals(listOf(0), LauncherSectionMembership.headerRows(cells))
    }

    @Test
    fun `a cell belongs to the nearest header at or above it`() {
        val headers = listOf(0, 5)

        assertEquals(0, LauncherSectionMembership.sectionHeaderRowFor(row = 1, headerRows = headers))
        assertEquals(0, LauncherSectionMembership.sectionHeaderRowFor(row = 4, headerRows = headers))
        assertEquals(5, LauncherSectionMembership.sectionHeaderRowFor(row = 5, headerRows = headers))
        assertEquals(5, LauncherSectionMembership.sectionHeaderRowFor(row = 9, headerRows = headers))
    }

    @Test
    fun `a row above every header belongs to no section`() {
        assertNull(LauncherSectionMembership.sectionHeaderRowFor(row = 1, headerRows = listOf(3, 8)))
    }

    @Test
    fun `an empty desktop has no sections at all`() {
        assertEquals(emptyList<Int>(), LauncherSectionMembership.headerRows(emptyList()))
        assertNull(LauncherSectionMembership.sectionHeaderRowFor(row = 0, headerRows = emptyList()))
    }

    @Test
    fun `a section ends at the next header and the last one runs to the bottom`() {
        val headers = listOf(0, 5)

        assertEquals(5, LauncherSectionMembership.sectionEndExclusive(headerRow = 0, headerRows = headers))
        assertNull(LauncherSectionMembership.sectionEndExclusive(headerRow = 5, headerRows = headers))
    }

    @Test
    fun `an empty section is still bounded by the next header`() {
        // Strategic §6.10: a section whose last member was removed stays a header, and "empty" is
        // indistinguishable from "nothing lies between two headers right now" - so the range is the
        // answer, not the member count.
        assertEquals(1, LauncherSectionMembership.sectionEndExclusive(headerRow = 0, headerRows = listOf(0, 1)))
    }

    @Test
    fun `a one-row cell never straddles a header`() {
        assertFalse(LauncherSectionMembership.coversHeaderRow(row = 4, spanH = 1, headerRows = listOf(5)))
    }

    @Test
    fun `a two-row cell starting directly above a header straddles it`() {
        assertTrue(LauncherSectionMembership.coversHeaderRow(row = 4, spanH = 2, headerRows = listOf(5)))
    }

    @Test
    fun `the same cell one row lower does not straddle`() {
        // Starting ON the header row is a plain overlap with the header, which the rectangle
        // intersection check refuses and reports far more precisely - so this predicate stays out of it.
        assertFalse(LauncherSectionMembership.coversHeaderRow(row = 5, spanH = 2, headerRows = listOf(5)))
    }

    @Test
    fun `a tall cell straddles a header several rows below its top`() {
        assertTrue(LauncherSectionMembership.coversHeaderRow(row = 2, spanH = 5, headerRows = listOf(6)))
    }

    @Test
    fun `a cell ending exactly above a header does not straddle it`() {
        assertFalse(LauncherSectionMembership.coversHeaderRow(row = 3, spanH = 2, headerRows = listOf(5)))
    }

    @Test
    fun `a desktop with no header can never produce a straddle`() {
        assertFalse(LauncherSectionMembership.coversHeaderRow(row = 0, spanH = 9, headerRows = emptyList()))
    }

    @Test
    fun `an unfolded desktop draws every row where it is stored`() {
        assertRoundTrip(headerRows = listOf(0, 5), collapsed = emptySet(), rows = 12)
    }

    @Test
    fun `a folded section lifts the rows below it by its own height`() {
        val headers = listOf(0, 5)

        // Section 0 owns rows 1..4, so folding it takes four rows out and row 6 is drawn at row 2.
        assertEquals(2, LauncherSectionMembership.renderRowFor(6, false, headers, setOf(0)))
        assertEquals(6, LauncherSectionMembership.storedRowFor(2, headers, setOf(0)))
        assertRoundTrip(headerRows = headers, collapsed = setOf(0), rows = 12)
    }

    @Test
    fun `two folded sections in a row stack their lifts`() {
        assertRoundTrip(headerRows = listOf(0, 4, 9), collapsed = setOf(0, 4), rows = 16)
    }

    @Test
    fun `a folded section running to the bottom lifts nothing above it`() {
        val headers = listOf(0, 5)

        assertEquals(3, LauncherSectionMembership.storedRowFor(3, headers, setOf(5)))
        assertRoundTrip(headerRows = headers, collapsed = setOf(5), rows = 12)
    }

    @Test
    fun `a press below every section on an empty desktop points at itself`() {
        assertEquals(7, LauncherSectionMembership.storedRowFor(7, emptyList(), emptySet()))
    }

    /**
     * S2033 §7: the round trip is what has to hold, not any single hand-computed number - a wrong inverse
     * moves the cell the other way and reads to the user as the very defect this ticket fixes.
     */
    private fun assertRoundTrip(headerRows: List<Int>, collapsed: Set<Int>, rows: Int) {
        for (stored in 0 until rows) {
            val drawn = LauncherSectionMembership.renderRowFor(
                row = stored,
                isHeader = false,
                headerRows = headerRows,
                collapsedHeaderRows = collapsed,
            ) ?: continue
            assertEquals(stored, LauncherSectionMembership.storedRowFor(drawn, headerRows, collapsed))
        }
    }

    private fun cell(row: Int, kind: LauncherCellKind): LauncherCell = LauncherCell(
        id = 0,
        orientation = LauncherOrientation.PORTRAIT,
        rowIndex = row,
        colIndex = 0,
        spanW = 1,
        spanH = 1,
        kind = kind,
        target = "",
        labelOverride = null,
        addedAt = 0,
    )
}
