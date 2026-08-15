package com.sza.fastmediasorter.domain.model.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * S1428: the row arithmetic collapsing a section (strategic §6.8, §11.9-§11.11).
 *
 * Every case here is about what a stored row is *drawn* at. Nothing in this file writes a row, which is
 * the property the feature rests on: expanding restores the arrangement exactly because collapsing never
 * moved it.
 */
class LauncherSectionCollapseTest {

    @Test
    fun `nothing collapsed draws every cell at its stored row`() {
        val headers = listOf(0, 5)

        assertEquals(0, renderRow(0, headers, collapsed = emptySet()))
        assertEquals(3, renderRow(3, headers, collapsed = emptySet()))
        assertEquals(9, renderRow(9, headers, collapsed = emptySet()))
    }

    @Test
    fun `a collapsed section keeps its own header on screen`() {
        assertEquals(0, renderRow(0, headerRows = listOf(0, 5), collapsed = setOf(0), isHeader = true))
    }

    @Test
    fun `a cell sharing a collapsed header's row is not drawn`() {
        // S1642: the header takes two columns and its section owns the rest of that row, so folding the
        // section has to take those cells with it.
        assertNull(renderRow(0, headerRows = listOf(0, 5), collapsed = setOf(0)))
    }

    @Test
    fun `a cell sharing an expanded header's row is drawn beside it`() {
        assertEquals(0, renderRow(0, headerRows = listOf(0, 5), collapsed = setOf(5)))
    }

    @Test
    fun `a header on a folded row is drawn at its lifted row`() {
        assertEquals(1, renderRow(5, headerRows = listOf(0, 5), collapsed = setOf(0, 5), isHeader = true))
    }

    @Test
    fun `a cell inside a collapsed section is not drawn`() {
        val headers = listOf(0, 5)

        assertNull(renderRow(1, headers, collapsed = setOf(0)))
        assertNull(renderRow(4, headers, collapsed = setOf(0)))
    }

    @Test
    fun `the lift stops at the next header`() {
        // Rows 1..4 belong to the section headed at 0, so folding it lifts everything from row 5 down by
        // exactly those four rows - and by no more, whatever lies further below.
        val headers = listOf(0, 5)

        assertEquals(1, renderRow(5, headers, collapsed = setOf(0)))
        assertEquals(2, renderRow(6, headers, collapsed = setOf(0)))
        assertEquals(5, renderRow(9, headers, collapsed = setOf(0)))
    }

    @Test
    fun `a cell below the next header stays visible when the section above it folds`() {
        // Strategic §11.10: a collapsed section may hide nothing past its own boundary.
        assertEquals(1, renderRow(5, headerRows = listOf(0, 5), collapsed = setOf(0)))
        assertEquals(4, renderRow(8, headerRows = listOf(0, 5), collapsed = setOf(0)))
    }

    @Test
    fun `two collapsed sections lift by the sum of both`() {
        val headers = listOf(0, 3, 7)

        // Section 0 owns rows 1..2 and section 3 owns rows 4..6 - five folded rows above row 7. Each
        // header stays on screen, so only the rows between them fold.
        assertEquals(2, renderRow(7, headers, collapsed = setOf(0, 3)))
        assertEquals(3, renderRow(8, headers, collapsed = setOf(0, 3)))
    }

    @Test
    fun `folding the last section hides everything under it and lifts nothing`() {
        val headers = listOf(0, 5)

        assertEquals(5, renderRow(5, headers, collapsed = setOf(5), isHeader = true))
        assertNull(renderRow(6, headers, collapsed = setOf(5)))
        assertNull(renderRow(30, headers, collapsed = setOf(5)))
    }

    @Test
    fun `a row above every header is untouched by a fold below it`() {
        assertEquals(0, renderRow(0, headerRows = listOf(2), collapsed = setOf(2)))
        assertEquals(1, renderRow(1, headerRows = listOf(2), collapsed = setOf(2)))
    }

    @Test
    fun `a negative stored row is floored rather than lifted off the canvas`() {
        assertEquals(0, renderRow(-4, headerRows = listOf(0, 5), collapsed = emptySet()))
    }

    @Test
    fun `a desktop with no sections at all draws every row unchanged`() {
        assertEquals(7, renderRow(7, headerRows = emptyList(), collapsed = emptySet()))
    }

    private fun renderRow(
        row: Int,
        headerRows: List<Int>,
        collapsed: Set<Int>,
        isHeader: Boolean = false,
    ): Int? = LauncherSectionMembership.renderRowFor(row, isHeader, headerRows, collapsed)
}
