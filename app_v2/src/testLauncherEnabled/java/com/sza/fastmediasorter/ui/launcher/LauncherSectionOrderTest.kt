package com.sza.fastmediasorter.ui.launcher

import com.sza.fastmediasorter.domain.model.launcher.LauncherCell
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellKind
import com.sza.fastmediasorter.domain.model.launcher.LauncherOrientation
import com.sza.fastmediasorter.domain.model.launcher.LauncherSectionMembership
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * S1645: ownership of a cell once two section headers can share a row.
 *
 * Until S1642 a header covered its whole row, so the row number alone identified a section and
 * `sectionHeaderRowFor` was a complete answer. A 2x1 header makes a shared row reachable, and on such a
 * row that function returns the same result for both headers - which is the ambiguity these tests pin
 * down. None of this is visible to the compiler or to detekt: the wrong owner still builds, still
 * passes every gate, and only shows as a shortcut hidden by the wrong section.
 */
class LauncherSectionOrderTest {

    @Test
    fun `sections are ordered by row then by column`() {
        val cells = listOf(
            section(row = 2, col = 0, target = "third"),
            section(row = 0, col = 2, target = "second"),
            section(row = 0, col = 0, target = "first"),
        )

        val order = LauncherSectionMembership.sectionsInOrder(cells).map { it.target }

        assertEquals(listOf("first", "second", "third"), order)
    }

    @Test
    fun `only section cells take part in the order`() {
        val cells = listOf(
            section(row = 0, col = 0, target = "only"),
            shortcut(row = 0, col = 2),
            shortcut(row = 1, col = 0),
        )

        val order = LauncherSectionMembership.sectionsInOrder(cells)

        assertEquals(1, order.size)
        assertEquals("only", order.single().target)
    }

    @Test
    fun `on a shared row a cell belongs to the header on its left`() {
        val left = section(row = 0, col = 0, target = "left")
        val right = section(row = 0, col = 2, target = "right")
        val order = LauncherSectionMembership.sectionsInOrder(listOf(left, right))

        val underLeft = LauncherSectionMembership.ownerOf(shortcut(row = 0, col = 1), order)
        val underRight = LauncherSectionMembership.ownerOf(shortcut(row = 0, col = 4), order)

        assertEquals("left", underLeft?.target)
        assertEquals("right", underRight?.target)
    }

    @Test
    fun `a header owns itself`() {
        val header = section(row = 1, col = 0, target = "self")
        val order = LauncherSectionMembership.sectionsInOrder(listOf(header))

        assertEquals("self", LauncherSectionMembership.ownerOf(header, order)?.target)
    }

    @Test
    fun `a cell on a later row belongs to the last header before it`() {
        val order = LauncherSectionMembership.sectionsInOrder(
            listOf(
                section(row = 0, col = 0, target = "top"),
                section(row = 0, col = 2, target = "top-right"),
                section(row = 3, col = 0, target = "lower"),
            ),
        )

        val betweenRows = LauncherSectionMembership.ownerOf(shortcut(row = 2, col = 1), order)
        val belowLower = LauncherSectionMembership.ownerOf(shortcut(row = 4, col = 1), order)

        assertEquals("top-right", betweenRows?.target)
        assertEquals("lower", belowLower?.target)
    }

    @Test
    fun `a cell above every header has no owner`() {
        val order = LauncherSectionMembership.sectionsInOrder(listOf(section(row = 2, col = 0)))

        assertNull(LauncherSectionMembership.ownerOf(shortcut(row = 1, col = 0), order))
    }

    @Test
    fun `an empty desktop has no sections and no owner`() {
        val order = LauncherSectionMembership.sectionsInOrder(emptyList())

        assertEquals(emptyList<LauncherCell>(), order)
        assertNull(LauncherSectionMembership.ownerOf(shortcut(row = 0, col = 0), order))
    }

    private fun section(row: Int, col: Int, target: String = "section-$row-$col") = cell(
        row = row,
        col = col,
        spanW = LauncherSectionMembership.HEADER_SPAN_W,
        kind = LauncherCellKind.SECTION,
        target = target,
    )

    private fun shortcut(row: Int, col: Int) = cell(row = row, col = col)

    private fun cell(
        row: Int,
        col: Int,
        spanW: Int = 1,
        kind: LauncherCellKind = LauncherCellKind.SHORTCUT,
        target: String = "target-$row-$col",
    ) = LauncherCell(
        id = row * 100L + col,
        orientation = LauncherOrientation.PORTRAIT,
        rowIndex = row,
        colIndex = col,
        spanW = spanW,
        spanH = 1,
        kind = kind,
        target = target,
        labelOverride = null,
        addedAt = 0L,
    )
}
