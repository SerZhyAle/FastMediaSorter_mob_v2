package com.sza.fastmediasorter.domain.model.launcher

/**
 * S1428: positional section membership on the launcher desktop (strategic §6.7).
 *
 * A cell belongs to the nearest header at or above its row, and that section runs down to the row of
 * the next header. Nothing else defines membership - there is no stored "which section" field, which is
 * exactly what let this ticket avoid a schema migration.
 *
 * Pure and Android-free on purpose. It landed here because `src/launcherEnabled` had no test source set
 * when this was written; S1498 has since added `src/testLauncherEnabled`, so that is no longer the
 * reason to keep it - the placement layer in `src/main` is, and it could not import a launcher-only
 * type either way. Both the placement checks in
 * [LauncherDesktopRepositoryImpl][com.sza.fastmediasorter.data.repository.LauncherDesktopRepositoryImpl]
 * and the collapse geometry of the renderer call this one definition rather than each re-deriving it.
 *
 * Rows are what this reasons about, never columns: a section boundary is a horizontal line, so a cell's
 * column has no bearing on which section owns it. S1642 keeps that true while narrowing the header to two
 * columns - the cells sharing the header's row belong to the section it opens, exactly as the rows below
 * it do. The one place the distinction surfaces is [renderRowFor], where a fold removes a header's row
 * around the header rather than whole.
 */
object LauncherSectionMembership {

    /**
     * S1642: the span every header is stored and drawn at - two columns by one row (strategic §9 ADR-1).
     *
     * One number for both, deliberately. S1428 stored a header at the widest grid there is and widened it
     * again at render time, because a header covered its whole row and the two numbers could not be the
     * same; the value of that arrangement was that no square past the header stayed free in the table while
     * covered on screen. Two columns removes the need for it - the header covers exactly what it stores, so
     * the free-square sweep and the renderer read one span and cannot disagree.
     *
     * Kept below `LauncherGridGeometry.MIN_COLUMNS`, so a header fits the narrowest grid the desktop can
     * resolve; `LauncherStarterSetsParityTest` fails the moment that stops holding.
     */
    const val HEADER_SPAN_W = 2

    /**
     * The rows carrying a header, ascending and without duplicates.
     *
     * S1645 corrects what this used to claim. While a header covered the whole row, two headers on one
     * row was unreachable and the overlap invariant refused the second; S1642 narrowed the header to
     * [HEADER_SPAN_W] columns, so a row can now carry several. The distinct here is what keeps this
     * boundary list well-formed in that case, and [sectionsInOrder] is what answers ownership when it
     * happens - a row number alone no longer can.
     */
    fun headerRows(cells: List<LauncherCell>): List<Int> = cells
        .filter { it.kind == LauncherCellKind.SECTION }
        .map { it.rowIndex.coerceAtLeast(0) }
        .distinct()
        .sorted()

    /**
     * S1645: every section header in the order the desktop reads them - by stored row ascending, then by
     * stored column ascending.
     *
     * This is the owner's ruling of 2026-08-15 expressed as a function: sections pack top to bottom and
     * left to right, in the order the user saw before collapsing. It follows a dragged header on its own,
     * so no second stored property is needed to remember an order.
     */
    fun sectionsInOrder(cells: List<LauncherCell>): List<LauncherCell> = cells
        .filter { it.kind == LauncherCellKind.SECTION }
        .sortedWith(compareBy({ it.rowIndex.coerceAtLeast(0) }, { it.colIndex.coerceAtLeast(0) }))

    /**
     * The header owning [cell], or null when [cell] precedes every header.
     *
     * A cell belongs to the last header that precedes it in [sectionsInOrder]. On a row carrying two
     * headers that means a cell belongs to the header on its left and never to both, which is the case
     * [sectionHeaderRowFor] cannot express: it compares rows only, so both headers of a shared row are
     * equally "at or above" it.
     *
     * A header is owned by itself, which is what keeps collapsing addressable by the header the user
     * tapped.
     */
    fun ownerOf(cell: LauncherCell, sectionsInOrder: List<LauncherCell>): LauncherCell? {
        val row = cell.rowIndex.coerceAtLeast(0)
        val col = cell.colIndex.coerceAtLeast(0)
        return sectionsInOrder.lastOrNull { header ->
            val headerRow = header.rowIndex.coerceAtLeast(0)
            val headerCol = header.colIndex.coerceAtLeast(0)
            headerRow < row || (headerRow == row && headerCol <= col)
        }
    }

    /**
     * S1645: where each collapsed header is drawn once consecutive collapsed sections share rows.
     *
     * Returns a position per collapsed header, keyed by that header's [LauncherCell.target] - the stable
     * identity a section is addressed by, never its row. A header takes the first position of the
     * current packed row that leaves room for its whole [HEADER_SPAN_W]; when the remainder is narrower,
     * it starts the next row, because ADR-2 forbids a header split across rows.
     *
     * A chain is a run of consecutive sections that show nothing: collapsed ones, and empty ones whether
     * collapsed or not (owner, 2026-08-15 - a whole row spent on a section with nothing to show is
     * wasted). A section with visible content ends the chain, and the next chain starts below whatever
     * that content occupies.
     *
     * [renderRowOf] supplies the row a header is already drawn on - [renderRowFor]'s answer, not the
     * stored row. Packing is a second pass over the fold, so it must continue that lift rather than
     * restart from storage; a header [renderRowOf] hides is skipped.
     *
     * Pure arithmetic: nothing here reads or writes a cell, which is what keeps expanding able to land
     * every shortcut back on its own square (ADR-1).
     */
    fun packedHeaderPositions(
        cells: List<LauncherCell>,
        collapsedTargets: Set<String>,
        columns: Int,
        renderRowOf: (LauncherCell) -> Int?,
    ): Map<String, PackedPosition> {
        // Nothing folded, nothing packed. An empty section is chainable by the rule above whether or not
        // it is collapsed, so without this a desktop with no collapsed section at all - and edit mode,
        // which folds nothing by design - would still see its empty headers moved off their squares.
        if (collapsedTargets.isEmpty()) {
            return emptyMap()
        }
        val width = columns.coerceAtLeast(HEADER_SPAN_W)
        val sections = sectionsInOrder(cells)
        // One pass over the content cells instead of one per header: the render path runs this on every
        // fold, and asking "does this section own anything" per header would be quadratic.
        val ownersWithContent = cells
            .filter { it.kind != LauncherCellKind.SECTION }
            .mapNotNull { ownerOf(it, sections)?.target }
            .toSet()
        val positions = mutableMapOf<String, PackedPosition>()
        var packRow = -1
        var nextCol = width
        for (header in sections) {
            val chainable = header.target in collapsedTargets || header.target !in ownersWithContent
            if (!chainable) {
                packRow = -1
                nextCol = width
            }
            // The chain starts at the row the header is DRAWN on, not the row it is stored at: folding
            // has already lifted everything below a collapsed section, and packing on top of stored rows
            // would fight that lift instead of continuing it. A header the fold hides has no drawn row
            // and takes no position, while leaving the chain it belongs to intact.
            val startRow = if (chainable) renderRowOf(header) else null
            if (startRow != null) {
                if (packRow < 0) {
                    packRow = startRow.coerceAtLeast(0)
                    nextCol = 0
                }
                if (nextCol + HEADER_SPAN_W > width) {
                    packRow += 1
                    nextCol = 0
                }
                positions[header.target] = PackedPosition(row = packRow, col = nextCol)
                nextCol += HEADER_SPAN_W
            }
        }
        return positions
    }

    /**
     * S2214: calculating the row compression lift caused by consecutive collapsed/empty section headers
     * packed onto shared rows.
     *
     * Returns a map from section header target to accumulated row compression lift (number of rows saved).
     */
    fun packingLifts(
        cells: List<LauncherCell>,
        collapsedTargets: Set<String>,
        columns: Int,
        renderRowOf: (LauncherCell) -> Int?,
    ): Map<String, Int> {
        val packed = packedHeaderPositions(cells, collapsedTargets, columns, renderRowOf)
        if (packed.isEmpty()) return emptyMap()
        val sections = sectionsInOrder(cells)
        val lifts = mutableMapOf<String, Int>()
        var currentLift = 0
        for (header in sections) {
            val drawnRow = renderRowOf(header)
            val packedPos = packed[header.target]
            if (drawnRow != null && packedPos != null) {
                currentLift = (drawnRow - packedPos.row).coerceAtLeast(0)
            }
            lifts[header.target] = currentLift
        }
        return lifts
    }

    /**
     * S2214: calculating the accumulated packing lift to invert for a given rendered row.
     */
    fun packingLiftForRenderRow(
        renderRow: Int,
        sections: List<LauncherCell>,
        packedPositions: Map<String, PackedPosition>,
        renderRowOf: (LauncherCell) -> Int?,
    ): Int {
        var lift = 0
        for (header in sections) {
            val packedPos = packedPositions[header.target]
            val drawnRow = renderRowOf(header)
            if (packedPos != null && drawnRow != null) {
                if (renderRow > packedPos.row) {
                    lift = (drawnRow - packedPos.row).coerceAtLeast(0)
                }
            }
        }
        return lift
    }

    /** The row and column a packed header is drawn at. Never stored - see [packedHeaderPositions]. */
    data class PackedPosition(val row: Int, val col: Int)

    /**
     * The header row owning [row], or null when [row] lies above every header.
     *
     * A header belongs to its own section, which is what makes collapsing addressable by the header's
     * own row.
     */
    fun sectionHeaderRowFor(row: Int, headerRows: List<Int>): Int? =
        headerRows.lastOrNull { it <= row }

    /**
     * The first row past the section headed at [headerRow], or null when that section runs to the
     * bottom of the desktop.
     *
     * Null is a real answer, not a missing one: the last section on the desktop genuinely has no lower
     * bound. Strategic §6.12 keeps that from mattering on a seeded desktop by placing a second header,
     * but a user who removes it puts the desktop back in this shape, so callers must handle null rather
     * than assume a next header exists.
     */
    fun sectionEndExclusive(headerRow: Int, headerRows: List<Int>): Int? =
        headerRows.firstOrNull { it > headerRow }

    /**
     * The row a cell stored at [row] is drawn on once every section headed in [collapsedHeaderRows] is
     * folded shut, or null when that cell sits inside a folded section and is not drawn at all.
     *
     * Collapsing is arithmetic on the way out, never a write on the way in: the stored row is what this
     * reads and never what it changes, which is the whole reason expanding lands every cell back on its
     * own square (strategic §5.1.6, §11.9). Restoring an arrangement the desktop had overwritten would
     * need a record of it that nothing keeps.
     *
     * The lift stops at the next header, because that is where the folded section stops owning rows
     * (strategic §6.7). Lifting past it would drag a cell belonging to the section below up under a
     * header that does not own it - and hide it behind one, which §7 rates a foreign cell vanishing
     * until the section above is expanded again.
     *
     * The header itself is never folded away: it is the only thing left to tap to expand. Everything else
     * standing on that same row is folded, because S1642 gives the header two columns and hands the rest of
     * its row to its own section - leaving those cells drawn would show a section's content beside the
     * header that claims to have hidden it.
     */
    fun renderRowFor(row: Int, isHeader: Boolean, headerRows: List<Int>, collapsedHeaderRows: Set<Int>): Int? {
        val top = row.coerceAtLeast(0)
        var lift = 0
        for (headerRow in headerRows) {
            if (headerRow !in collapsedHeaderRows) continue
            val firstFolded = headerRow + 1
            // Null means the section runs to the bottom of the desktop, so everything under its header
            // is folded and nothing below it is left to lift.
            val end = sectionEndExclusive(headerRow, headerRows)
            val hidden = if (top == headerRow) {
                !isHeader
            } else {
                top >= firstFolded && (end == null || top < end)
            }
            if (hidden) return null
            if (end != null && top >= end) lift += end - firstFolded
        }
        return top - lift
    }

    /**
     * S2033: the stored row a press on drawn row [renderRow] points at - the inverse of [renderRowFor].
     *
     * Well defined because a fold hides whole sections rather than thinning them: every stored row still
     * drawn projects onto a drawn row exactly one past the previous drawn one, so a drawn row has one
     * visible stored row behind it and never two.
     *
     * It exists because a coordinate read off a folded desktop can otherwise only be written back as if
     * nothing were folded, which lands a new cell short by the height of every collapsed section above
     * the press - often inside one of them, where [renderRowFor] returns null and nothing is drawn at all.
     *
     * A collapsed section running to the bottom of the desktop adds nothing, for the same reason it lifts
     * nothing in [renderRowFor]: it has no rows below it that a fold could have raised.
     */
    fun storedRowFor(renderRow: Int, headerRows: List<Int>, collapsedHeaderRows: Set<Int>): Int {
        var stored = renderRow.coerceAtLeast(0)
        for (headerRow in headerRows.filter { it in collapsedHeaderRows }) {
            val end = sectionEndExclusive(headerRow, headerRows)
            val firstFolded = headerRow + 1
            // Each fold is undone in turn, so a lower section is compared against a row the sections
            // above it have already pushed back down - the same order [renderRowFor] accumulates in.
            if (end != null && stored >= firstFolded) stored += end - firstFolded
        }
        return stored
    }

    /**
     * Whether a rectangle [spanH] rows tall starting at [row] covers a header row other than a header
     * sitting exactly at [row].
     *
     * This is the predicate the placement layer refuses a gadget on (strategic §6.11, as refined by the
     * owner on 2026-08-08). It removes the case that has no defined answer: a cell taller than one row
     * that starts inside one section and ends inside the next belongs to neither.
     *
     * A header at [row] itself is excluded because that is a plain overlap with the header, which the
     * existing rectangle-intersection check already refuses and reports far more precisely.
     *
     * Note this cannot be replaced by that intersection check. `findOverlapping` is a predicate over the
     * **stored** span, while the renderer widens a header to the **live** column count, so once a
     * density change or a rotation widens the grid, the squares past a header's stored span are free in
     * the database and covered on screen.
     */
    fun coversHeaderRow(row: Int, spanH: Int, headerRows: List<Int>): Boolean {
        val top = row.coerceAtLeast(0)
        val height = spanH.coerceAtLeast(1)
        if (height == 1) return false
        return headerRows.any { it > top && it < top + height }
    }
}
