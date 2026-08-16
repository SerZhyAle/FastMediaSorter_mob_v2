package com.sza.fastmediasorter.ui.launcher

import com.sza.fastmediasorter.domain.model.launcher.LauncherCell
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellKind
import com.sza.fastmediasorter.domain.model.launcher.LauncherOrientation
import com.sza.fastmediasorter.domain.model.launcher.LauncherSectionMembership
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S1645: where collapsed section headers are drawn once consecutive ones share a row.
 *
 * The rules under test are the owner's ruling of 2026-08-15 (strategic §6.1-§6.2) plus ADR-2: pack in
 * stored order, never split a header across rows, an expanded section with content ends the chain, an
 * empty section packs like a collapsed one. ADR-1 adds the one that outranks them - the projection may
 * not touch a stored coordinate - which the last test pins down.
 */
class LauncherSectionPackingTest {

    private val fourColumns = 4

    @Test
    fun `two collapsed headers share a row and the third starts the next`() {
        val cells = listOf(
            section(row = 0, col = 0, target = "a"),
            section(row = 1, col = 0, target = "b"),
            section(row = 2, col = 0, target = "c"),
        )

        val packed = pack(cells, collapsed = setOf("a", "b", "c"))

        assertEquals(LauncherSectionMembership.PackedPosition(row = 0, col = 0), packed["a"])
        assertEquals(LauncherSectionMembership.PackedPosition(row = 0, col = 2), packed["b"])
        assertEquals(LauncherSectionMembership.PackedPosition(row = 1, col = 0), packed["c"])
    }

    @Test
    fun `a header that does not fit the remaining width is never split`() {
        val cells = listOf(
            section(row = 0, col = 0, target = "a"),
            section(row = 1, col = 0, target = "b"),
        )

        val packed = pack(cells, collapsed = setOf("a", "b"), columns = 3)

        // Three columns hold one 2-wide header and one leftover column, so the second header moves down
        // rather than starting in a column it cannot finish in.
        assertEquals(LauncherSectionMembership.PackedPosition(row = 0, col = 0), packed["a"])
        assertEquals(LauncherSectionMembership.PackedPosition(row = 1, col = 0), packed["b"])
    }

    @Test
    fun `an expanded section with content ends the chain`() {
        val cells = listOf(
            section(row = 0, col = 0, target = "collapsed-first"),
            section(row = 1, col = 0, target = "expanded"),
            shortcut(row = 1, col = 2),
            section(row = 3, col = 0, target = "collapsed-later"),
        )

        val packed = pack(cells, collapsed = setOf("collapsed-first", "collapsed-later"))

        assertEquals(LauncherSectionMembership.PackedPosition(row = 0, col = 0), packed["collapsed-first"])
        assertNull(packed["expanded"])
        // The chain restarted, so the later header keeps its own drawn row instead of joining row 0.
        assertEquals(LauncherSectionMembership.PackedPosition(row = 3, col = 0), packed["collapsed-later"])
    }

    @Test
    fun `an empty expanded section packs like a collapsed one`() {
        val cells = listOf(
            section(row = 0, col = 0, target = "collapsed"),
            section(row = 1, col = 0, target = "empty-but-expanded"),
        )

        val packed = pack(cells, collapsed = setOf("collapsed"))

        assertEquals(LauncherSectionMembership.PackedPosition(row = 0, col = 0), packed["collapsed"])
        assertEquals(
            LauncherSectionMembership.PackedPosition(row = 0, col = 2),
            packed["empty-but-expanded"],
        )
    }

    @Test
    fun `packing reads the drawn row, not the stored one`() {
        val cells = listOf(
            section(row = 5, col = 0, target = "a"),
            section(row = 6, col = 0, target = "b"),
        )

        // A caller that has already lifted the desktop by four rows must see the chain start there.
        val packed = LauncherSectionMembership.packedHeaderPositions(
            cells = cells,
            collapsedTargets = setOf("a", "b"),
            columns = fourColumns,
            renderRowOf = { cell -> cell.rowIndex - 4 },
        )

        assertEquals(LauncherSectionMembership.PackedPosition(row = 1, col = 0), packed["a"])
        assertEquals(LauncherSectionMembership.PackedPosition(row = 1, col = 2), packed["b"])
    }

    @Test
    fun `a header the fold hides is skipped`() {
        val cells = listOf(section(row = 0, col = 0, target = "hidden"))

        val packed = LauncherSectionMembership.packedHeaderPositions(
            cells = cells,
            collapsedTargets = setOf("hidden"),
            columns = fourColumns,
            renderRowOf = { null },
        )

        assertTrue(packed.isEmpty())
    }

    @Test
    fun `nothing is packed when no section is collapsed`() {
        // Edit mode folds nothing by design, and an empty section is chainable regardless of its
        // collapsed flag - so without this guard an empty header would move while the user drags.
        val cells = listOf(
            section(row = 0, col = 0, target = "empty-a"),
            section(row = 1, col = 0, target = "empty-b"),
        )

        val packed = pack(cells, collapsed = emptySet())

        assertTrue(packed.isEmpty())
    }

    @Test
    fun `packed headers keep their stored order when they share a row`() {
        // The visual chain must follow the order the user saw before collapsing, because that is the
        // order TalkBack will announce once the container adds children by drawn position.
        val cells = listOf(
            section(row = 1, col = 0, target = "second"),
            section(row = 0, col = 0, target = "first"),
        )

        val packed = pack(cells, collapsed = setOf("first", "second"))

        assertEquals(LauncherSectionMembership.PackedPosition(row = 0, col = 0), packed["first"])
        assertEquals(LauncherSectionMembership.PackedPosition(row = 0, col = 2), packed["second"])
    }

    @Test
    fun `packing leaves every stored coordinate untouched`() {
        val cells = listOf(
            section(row = 0, col = 0, target = "a"),
            section(row = 1, col = 0, target = "b"),
            shortcut(row = 2, col = 3),
        )
        val before = cells.map { it.copy() }

        pack(cells, collapsed = setOf("a", "b"))

        assertEquals(before, cells)
    }

    private fun pack(
        cells: List<LauncherCell>,
        collapsed: Set<String>,
        columns: Int = fourColumns,
    ): Map<String, LauncherSectionMembership.PackedPosition> =
        LauncherSectionMembership.packedHeaderPositions(
            cells = cells,
            collapsedTargets = collapsed,
            columns = columns,
            renderRowOf = { cell -> cell.rowIndex },
        )

    private fun section(row: Int, col: Int, target: String) = cell(
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
