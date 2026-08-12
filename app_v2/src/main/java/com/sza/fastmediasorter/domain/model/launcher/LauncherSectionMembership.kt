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
 * Rows are what this reasons about, never columns: a header is drawn across the full width of its row,
 * so a section boundary is a horizontal line and a cell's column has no bearing on which section owns it.
 */
object LauncherSectionMembership {

    /**
     * The span every header is stored with, mirroring `LauncherGridGeometry.MAX_COLUMNS`: that object
     * lives in `src/launcherEnabled` and cannot be imported here, the same constraint that makes
     * [LauncherStarterSets][com.sza.fastmediasorter.core.launcher.LauncherStarterSets] duplicate the
     * gadget keys. `LauncherStarterSetsParityTest` fails the moment this copy drifts.
     *
     * A header is drawn at the live column count whatever it was stored with, so storing it at the
     * current one leaves the rest of its row free in the database while covered on screen - and a cell
     * dropped there lands under the header. Storing it at the widest grid it can ever be drawn on closes
     * that gap from the storage side, as [coversHeaderRow] closes it from the placement side.
     */
    const val HEADER_STORED_SPAN_W = 12

    /**
     * The rows carrying a header, ascending and without duplicates.
     *
     * Two headers on one row is not a state the desktop can reach - a header covers the whole row, so
     * the overlap invariant refuses the second - but a row is still distinct-ed here rather than
     * asserted: this reads persisted data, and a defensive distinct costs nothing next to a boundary
     * list that silently contains the same row twice.
     */
    fun headerRows(cells: List<LauncherCell>): List<Int> = cells
        .filter { it.kind == LauncherCellKind.SECTION }
        .map { it.rowIndex.coerceAtLeast(0) }
        .distinct()
        .sorted()

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
     * The header's own row is never folded away: it is the only thing left to tap to expand.
     */
    fun renderRowFor(row: Int, headerRows: List<Int>, collapsedHeaderRows: Set<Int>): Int? {
        val top = row.coerceAtLeast(0)
        var lift = 0
        for (headerRow in headerRows) {
            if (headerRow !in collapsedHeaderRows) continue
            val firstFolded = headerRow + 1
            // Null means the section runs to the bottom of the desktop, so everything under its header
            // is folded and nothing below it is left to lift.
            val end = sectionEndExclusive(headerRow, headerRows)
            if (top >= firstFolded && (end == null || top < end)) return null
            if (end != null && top >= end) lift += end - firstFolded
        }
        return top - lift
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
