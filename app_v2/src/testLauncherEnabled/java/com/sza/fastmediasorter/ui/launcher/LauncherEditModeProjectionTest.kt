package com.sza.fastmediasorter.ui.launcher

import com.sza.fastmediasorter.domain.model.launcher.LauncherCell
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellKind
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellUi
import com.sza.fastmediasorter.domain.model.launcher.LauncherOrientation
import com.sza.fastmediasorter.domain.model.launcher.LauncherSectionMembership
import com.sza.fastmediasorter.ui.launcher.grid.LauncherGridGeometry
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * S1645: the desktop must be drawn unpacked while the user arranges it.
 *
 * `LauncherCellViewBinder` expresses edit mode by handing the projection an empty collapsed set, so
 * these tests exercise that exact input rather than the binder itself - a drop maps a pixel row and
 * column straight to a stored square (strategic §6.3), and a packed header would break that mapping.
 */
class LauncherEditModeProjectionTest {

    private val columns = 4

    @Test
    fun `an empty collapsed set draws every cell on its stored square`() {
        val plan = LauncherGridGeometry.renderPlan(
            cells = desktop(),
            collapsedSections = emptySet(),
            columns = columns,
        )

        val positions = plan.associate { it.item.cell.target to (it.renderRow to it.renderCol) }

        assertEquals(0 to 0, positions["first"])
        assertEquals(1 to 0, positions["second"])
        assertEquals(2 to 0, positions["with-content"])
        assertEquals(2 to 2, positions["shortcut"])
    }

    @Test
    fun `the same desktop packs once sections are collapsed`() {
        val plan = LauncherGridGeometry.renderPlan(
            cells = desktop(),
            collapsedSections = setOf("first", "second"),
            columns = columns,
        )

        val positions = plan.associate { it.item.cell.target to (it.renderRow to it.renderCol) }

        assertEquals(0 to 0, positions["first"])
        assertEquals(0 to 2, positions["second"])
    }

    @Test
    fun `an empty section stays on its stored square while nothing is collapsed`() {
        // The packing rule treats an empty section as chainable, so this is the case that would move a
        // header during a drag if packing were not gated on something actually being collapsed.
        val cells = listOf(
            ui(section(row = 0, col = 0, target = "empty-a")),
            ui(section(row = 1, col = 0, target = "empty-b")),
        )

        val plan = LauncherGridGeometry.renderPlan(cells, collapsedSections = emptySet(), columns = columns)
        val positions = plan.associate { it.item.cell.target to (it.renderRow to it.renderCol) }

        assertEquals(0 to 0, positions["empty-a"])
        assertEquals(1 to 0, positions["empty-b"])
    }

    private fun desktop() = listOf(
        ui(section(row = 0, col = 0, target = "first")),
        ui(section(row = 1, col = 0, target = "second")),
        ui(section(row = 2, col = 0, target = "with-content")),
        ui(cell(row = 2, col = 2, target = "shortcut")),
    )

    private fun ui(cell: LauncherCell) = LauncherCellUi(cell = cell, visual = null, modeBadge = null)

    private fun section(row: Int, col: Int, target: String) = cell(
        row = row,
        col = col,
        spanW = LauncherSectionMembership.HEADER_SPAN_W,
        kind = LauncherCellKind.SECTION,
        target = target,
    )

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
