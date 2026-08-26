package com.sza.fastmediasorter.ui.launcher.grid

import com.sza.fastmediasorter.domain.model.launcher.LauncherCell
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellKind
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellUi
import com.sza.fastmediasorter.domain.model.launcher.LauncherOrientation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S0404: the desktop grid is a 2D canvas whose column count is resolved at render time, while every
 * cell's span and column were persisted against whatever count was current when the user placed it.
 * Rotation and the density factor both change that count underneath, so [LauncherGridGeometry] is the
 * single point where a stored layout meets a different grid - which is exactly what is worth testing.
 *
 * Every rule below is invisible to the compiler and to detekt: a broken clamp still builds, still
 * passes every static gate, and only shows up as two cells drawn on the same square.
 *
 * S1498 moved this here from src/testStandard and extended it. The old home was mounted into one
 * flavor while the subject ships in two, so none of this ran on noLegal; and coverage had stopped at
 * the functions that existed in July, leaving the section-collapse projection (S1428) and the viewport
 * row count (S1288) untested. No Robolectric runtime - the subject has no Android import.
 */
class LauncherGridGeometryTest {

    // --- columns -----------------------------------------------------------------------------

    @Test
    fun `column count grows with the available width`() {
        assertEquals(4, LauncherGridGeometry.columns(availableWidthDp = 400f, densityFactor = 1f))
        assertEquals(8, LauncherGridGeometry.columns(availableWidthDp = 800f, densityFactor = 1f))
    }

    @Test
    fun `a higher density factor shrinks cells so more fit across`() {
        val normal = LauncherGridGeometry.columns(availableWidthDp = 400f, densityFactor = 1f)
        val denser = LauncherGridGeometry.columns(availableWidthDp = 400f, densityFactor = 2f)

        assertEquals(4, normal)
        assertEquals(8, denser)
    }

    @Test
    fun `the column count stays inside its bounds on absurd inputs`() {
        // A head unit reporting a narrow width must still get a usable grid, not zero columns.
        assertEquals(
            LauncherGridGeometry.MIN_COLUMNS,
            LauncherGridGeometry.columns(availableWidthDp = 1f, densityFactor = 1f)
        )
        assertEquals(
            LauncherGridGeometry.MAX_COLUMNS,
            LauncherGridGeometry.columns(availableWidthDp = 10_000f, densityFactor = 1f)
        )
    }

    @Test
    fun `a non-positive density factor falls back to the minimum instead of dividing by zero`() {
        assertEquals(
            LauncherGridGeometry.MIN_COLUMNS,
            LauncherGridGeometry.columns(availableWidthDp = 800f, densityFactor = 0f)
        )
    }

    // --- cell size and viewport rows ---------------------------------------------------------

    @Test
    fun `cell edge is the width split evenly across the columns`() {
        assertEquals(100, LauncherGridGeometry.cellSizePx(availableWidthPx = 400, columns = 4))
    }

    @Test
    fun `a zero column count yields the whole width rather than a division by zero`() {
        assertEquals(400, LauncherGridGeometry.cellSizePx(availableWidthPx = 400, columns = 0))
    }

    @Test
    fun `a partially visible bottom row counts as a row`() {
        // Rounding up is the point: rounding down leaves the strip of bare wallpaper edit mode exists
        // to remove.
        assertEquals(4, LauncherGridGeometry.rowsForViewport(availableHeightPx = 301, cellSizePx = 100))
        assertEquals(3, LauncherGridGeometry.rowsForViewport(availableHeightPx = 300, cellSizePx = 100))
    }

    @Test
    fun `an unmeasured viewport reports no rows so the caller can fall back`() {
        assertEquals(0, LauncherGridGeometry.rowsForViewport(availableHeightPx = 0, cellSizePx = 100))
        assertEquals(0, LauncherGridGeometry.rowsForViewport(availableHeightPx = 800, cellSizePx = 0))
    }

    // --- rowsFor -----------------------------------------------------------------------------

    @Test
    fun `canvas height is the lowest cell bottom, not the count of cells`() {
        val cells = listOf(
            cell(row = 0, col = 0),
            cell(row = 3, col = 1, spanH = 2),
            cell(row = 1, col = 2)
        )

        assertEquals(5, LauncherGridGeometry.rowsFor(cells))
    }

    @Test
    fun `an empty desktop is still one row tall`() {
        assertEquals(1, LauncherGridGeometry.rowsFor(emptyList()))
    }

    // --- footprint clamps --------------------------------------------------------------------

    @Test
    fun `a cell saved on a wider grid is pulled back inside the current one`() {
        // Without this clamp the cell lays out past the right edge and silently disappears.
        val footprint = LauncherGridGeometry.footprint(row = 2, col = 9, spanW = 2, spanH = 1, columns = 4)

        assertEquals(2, footprint.col)
        assertEquals(2, footprint.spanW)
    }

    @Test
    fun `a span wider than the grid is narrowed to the grid`() {
        val footprint = LauncherGridGeometry.footprint(row = 0, col = 0, spanW = 99, spanH = 1, columns = 4)

        assertEquals(4, footprint.spanW)
        assertEquals(0, footprint.col)
    }

    @Test
    fun `negative rows and degenerate spans are floored, not passed through`() {
        val footprint = LauncherGridGeometry.footprint(row = -3, col = -7, spanW = 0, spanH = -1, columns = 4)

        assertEquals(0, footprint.row)
        assertEquals(0, footprint.col)
        assertEquals(1, footprint.spanW)
        assertEquals(1, footprint.spanH)
    }

    @Test
    fun `a grid with no columns still produces a placeable square`() {
        val footprint = LauncherGridGeometry.footprint(row = 1, col = 5, spanW = 3, spanH = 1, columns = 0)

        assertEquals(0, footprint.col)
        assertEquals(1, footprint.spanW)
    }

    @Test
    fun `the occupied squares follow from the origin and the spans`() {
        val footprint = LauncherGridGeometry.footprint(row = 2, col = 1, spanW = 2, spanH = 3, columns = 6)

        assertEquals(listOf(2, 3, 4), footprint.rows.toList())
        assertEquals(listOf(1, 2), footprint.cols.toList())
    }

    // --- header widening ---------------------------------------------------------------------

    @Test
    fun `a section header is drawn two cells wide whatever span it was stored with`() {
        // S1642: a desktop written by an S1428 build stored its headers at the widest grid there is, so
        // drawing one at its stored span would cover squares the table has since freed.
        val legacy = cell(row = 0, col = 0, spanW = 12, kind = LauncherCellKind.SECTION)
        val current = cell(row = 0, col = 0, spanW = 2, kind = LauncherCellKind.SECTION)

        assertEquals(2, LauncherGridGeometry.renderSpanW(legacy, columns = 8))
        assertEquals(2, LauncherGridGeometry.renderSpanW(current, columns = 8))
    }

    @Test
    fun `an ordinary cell keeps its stored span`() {
        val shortcut = cell(row = 0, col = 0, spanW = 2)

        assertEquals(2, LauncherGridGeometry.renderSpanW(shortcut, columns = 8))
    }

    @Test
    fun `the normalized header span reaches the empty-slot sweep through footprintOf`() {
        // The sweep and the renderer must agree, or edit mode draws "tap to add" on top of a live header -
        // and, since S1642, leaves the squares beside a header unofferable when it disagrees the other way.
        val header = cell(row = 1, col = 0, spanW = 12, kind = LauncherCellKind.SECTION)

        val footprint = LauncherGridGeometry.footprintOf(header, columns = 6)

        assertEquals(0, footprint.col)
        assertEquals(2, footprint.spanW)
    }

    // --- collapsed-section projection ---------------------------------------------------------

    @Test
    fun `an expanded desktop renders every cell on its stored row`() {
        val plan = LauncherGridGeometry.renderPlan(
            cells = listOf(
                ui(cell(row = 0, col = 0, kind = LauncherCellKind.SECTION, target = "top")),
                ui(cell(row = 1, col = 0)),
                ui(cell(row = 2, col = 0))
            ),
            collapsedSections = emptySet(),
            columns = 4
        )

        assertEquals(listOf(0, 1, 2), plan.map { it.renderRow })
    }

    @Test
    fun `folding a section drops its cells and lifts everything below it`() {
        val plan = LauncherGridGeometry.renderPlan(
            cells = listOf(
                ui(cell(row = 0, col = 0, kind = LauncherCellKind.SECTION, target = "top")),
                ui(cell(row = 1, col = 0, target = "inside")),
                ui(cell(row = 2, col = 0, kind = LauncherCellKind.SECTION, target = "next")),
                ui(cell(row = 3, col = 0, target = "below"))
            ),
            collapsedSections = setOf("top"),
            columns = 4
        )

        // The header itself survives - it is the only thing left to tap to expand.
        assertEquals(listOf("top", "next", "below"), plan.map { it.item.cell.target })
        assertEquals(listOf(0, 1, 2), plan.map { it.renderRow })
        assertNull(plan.firstOrNull { it.item.cell.target == "inside" })
    }

    @Test
    fun `a section is addressed by its header target, not by the row it sits on`() {
        // A header the user drags elsewhere is the same section; a row that later carries a different
        // header is not.
        val plan = LauncherGridGeometry.renderPlan(
            cells = listOf(
                ui(cell(row = 0, col = 0, kind = LauncherCellKind.SECTION, target = "top")),
                ui(cell(row = 1, col = 0, target = "inside"))
            ),
            collapsedSections = setOf("some-other-header"),
            columns = 4
        )

        assertEquals(listOf(0, 1), plan.map { it.renderRow })
    }

    @Test
    fun `the canvas shrinks with the fold instead of scrolling over the hidden height`() {
        val rendered = LauncherGridGeometry.renderPlan(
            cells = listOf(
                ui(cell(row = 0, col = 0, kind = LauncherCellKind.SECTION, target = "top")),
                ui(cell(row = 1, col = 0, target = "inside")),
                ui(cell(row = 2, col = 0, kind = LauncherCellKind.SECTION, target = "next")),
                ui(cell(row = 3, col = 0, target = "below"))
            ),
            collapsedSections = setOf("top"),
            columns = 4
        )

        assertEquals(3, LauncherGridGeometry.rowsForRendered(rendered))
    }

    @Test
    fun `an empty render plan is still one row tall`() {
        assertEquals(1, LauncherGridGeometry.rowsForRendered(emptyList()))
    }

    @Test
    fun `a folded cell is laid out at its drawn row, not its stored one`() {
        val below = ui(cell(row = 3, col = 1, target = "below"))
        val rendered = LauncherGridGeometry.renderPlan(
            cells = listOf(
                ui(cell(row = 0, col = 0, kind = LauncherCellKind.SECTION, target = "top")),
                ui(cell(row = 1, col = 0, target = "inside")),
                ui(cell(row = 2, col = 0, kind = LauncherCellKind.SECTION, target = "next")),
                below
            ),
            collapsedSections = setOf("top"),
            columns = 4
        ).first { it.item.cell.target == "below" }

        val footprint = LauncherGridGeometry.footprintOfRendered(rendered, columns = 4)

        assertEquals(2, footprint.row)
        assertEquals(1, footprint.col)
    }

    // --- pixel projection ---------------------------------------------------------------------

    @Test
    fun `pixel bounds are the footprint scaled by the cell edge`() {
        val bounds = LauncherGridGeometry.boundsOf(
            LauncherGridGeometry.CellFootprint(row = 2, col = 1, spanW = 2, spanH = 3),
            cellSize = 50
        )

        assertEquals(50, bounds.left)
        assertEquals(100, bounds.top)
        assertEquals(100, bounds.width)
        assertEquals(150, bounds.height)
    }

    @Test
    fun `a plain cell lands at its row and column`() {
        val bounds = LauncherGridGeometry.boundsFor(cell(row = 2, col = 1), cellSize = 100, columns = 4)

        assertEquals(
            LauncherGridGeometry.CellBounds(left = 100, top = 200, width = 100, height = 100),
            bounds
        )
    }

    @Test
    fun `a gadget is sized by both spans`() {
        val bounds = LauncherGridGeometry.boundsFor(
            cell(row = 1, col = 0, spanW = 2, spanH = 2),
            cellSize = 100,
            columns = 4
        )

        assertEquals(
            LauncherGridGeometry.CellBounds(left = 0, top = 100, width = 200, height = 200),
            bounds
        )
    }

    @Test
    fun `boundsFor clamps through the footprint before projecting to pixels`() {
        val bounds = LauncherGridGeometry.boundsFor(
            cell = cell(row = 0, col = 9, spanW = 2),
            cellSize = 50,
            columns = 4
        )

        // col 9 on a 4-column grid is pulled to 2, so the rect must start at 2 * 50, not 9 * 50.
        assertEquals(100, bounds.left)
    }

    // --- shortcut layout scaling -------------------------------------------------------------

    @Test
    fun `shortcutLayoutSpec at normal size returns standard dimensions`() {
        val spec = LauncherGridGeometry.shortcutLayoutSpec(cellSizeDp = 96f)
        assertEquals(44f, spec.iconSizeDp, 0.01f)
        assertEquals(16f, spec.monogramTextSizeSp, 0.01f)
        assertEquals(18f, spec.modeBadgeSizeDp, 0.01f)
        assertEquals(4f, spec.contentPaddingVerticalDp, 0.01f)
        assertEquals(3f, spec.labelMarginTopDp, 0.01f)
    }

    @Test
    fun `shortcutLayoutSpec at dense size scales down icon and paddings`() {
        val spec = LauncherGridGeometry.shortcutLayoutSpec(cellSizeDp = 64f)
        assertTrue(spec.iconSizeDp < 44f)
        assertTrue(spec.iconSizeDp >= 26f)
        assertTrue(spec.contentPaddingVerticalDp < 4f)
        assertTrue(spec.labelMarginTopDp < 3f)
    }

    @Test
    fun `storedSlotFor pushes a press below a folded section back down by its height`() {
        // Header at 0 owning rows 1..4, header at 5 below it. Folding the first takes four rows out.
        val cells = listOf(
            ui(cell(row = 0, col = 0, kind = LauncherCellKind.SECTION, target = "top")),
            ui(cell(row = 1, col = 0)),
            ui(cell(row = 5, col = 0, kind = LauncherCellKind.SECTION, target = "bottom")),
            ui(cell(row = 6, col = 2)),
        )

        val stored = LauncherGridGeometry.storedSlotFor(
            slot = LauncherGridGeometry.Slot(row = 2, col = 3),
            cells = cells,
            collapsedSections = setOf("top"),
        )

        assertEquals(6, stored.row)
        assertEquals(3, stored.col)
    }

    @Test
    fun `storedSlotFor leaves a press unchanged when nothing is folded`() {
        val cells = listOf(
            ui(cell(row = 0, col = 0, kind = LauncherCellKind.SECTION, target = "top")),
            ui(cell(row = 5, col = 0, kind = LauncherCellKind.SECTION, target = "bottom")),
        )

        val stored = LauncherGridGeometry.storedSlotFor(
            slot = LauncherGridGeometry.Slot(row = 6, col = 1),
            cells = cells,
            collapsedSections = emptySet(),
        )

        assertEquals(6, stored.row)
        assertEquals(1, stored.col)
    }

    @Test
    fun `storedSlotFor leaves a press above every folded section alone`() {
        val cells = listOf(
            ui(cell(row = 2, col = 0, kind = LauncherCellKind.SECTION, target = "mid")),
            ui(cell(row = 6, col = 0, kind = LauncherCellKind.SECTION, target = "low")),
        )

        val stored = LauncherGridGeometry.storedSlotFor(
            slot = LauncherGridGeometry.Slot(row = 1, col = 2),
            cells = cells,
            collapsedSections = setOf("mid"),
        )

        assertEquals(1, stored.row)
        assertEquals(2, stored.col)
    }

    // --- fixtures -----------------------------------------------------------------------------

    private fun cell(
        row: Int,
        col: Int,
        spanW: Int = 1,
        spanH: Int = 1,
        kind: LauncherCellKind = LauncherCellKind.SHORTCUT,
        target: String = "target-$row-$col"
    ) = LauncherCell(
        id = row * 100L + col,
        orientation = LauncherOrientation.PORTRAIT,
        rowIndex = row,
        colIndex = col,
        spanW = spanW,
        spanH = spanH,
        kind = kind,
        target = target,
        labelOverride = null,
        addedAt = 0L
    )

    private fun ui(cell: LauncherCell) = LauncherCellUi(cell = cell, visual = null, modeBadge = null)
}
