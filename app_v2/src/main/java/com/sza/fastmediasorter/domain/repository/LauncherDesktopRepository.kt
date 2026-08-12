package com.sza.fastmediasorter.domain.repository

import com.sza.fastmediasorter.domain.model.launcher.LauncherCell
import com.sza.fastmediasorter.domain.model.launcher.LauncherOrientation
import kotlinx.coroutines.flow.Flow

/** Desktop-wide launcher state: what was already seeded and the resolved grid width per layout. */
data class LauncherDesktopState(
    val seededPortrait: Boolean,
    val seededLandscape: Boolean,
    val columnsPortrait: Int,
    val columnsLandscape: Int,
)

/**
 * S0404: persistence facade for the launcher desktop. Portrait and landscape are separate layouts,
 * so every read and placement is scoped to one [LauncherOrientation].
 */
interface LauncherDesktopRepository {

    fun observeCells(orientation: LauncherOrientation): Flow<List<LauncherCell>>

    /**
     * Inserts, or replaces the row with the same id - the edit flow needs no separate update.
     * Returns null when the cell's footprint would overlap one already there (see [moveCell]).
     */
    suspend fun addCell(cell: LauncherCell): Long?

    /**
     * Places [cell] wherever it fits instead of at the anchor it carries: scans row-major from the top
     * and takes the first anchor whose whole `spanW x spanH` footprint is clear, appending a new row
     * below the last occupied one when no existing row has room. The caller therefore never names a
     * row or a column - the entry point that needs this (adding a widget from Settings) has no grid on
     * screen to point at.
     *
     * [columns] is a parameter rather than repository state for the same reason [addCell] does not
     * enforce the right edge: the column count belongs to the screen currently rendering the desktop,
     * not to the stored desktop, and the two orientations resolve it differently.
     *
     * Returns null like [addCell], but here null means "could not place at all", not "the requested
     * anchor was taken" - with the append-a-row fallback that is only reachable on a non-positive
     * [columns].
     */
    suspend fun addCellInFirstFreeSlot(cell: LauncherCell, columns: Int): Long?

    suspend fun removeCell(id: Long)

    /**
     * Moves a cell's anchor. Returns whether it moved. Three outcomes, in this order:
     *
     * 1. The whole `spanW x spanH` footprint is free - the cell takes it.
     * 2. Exactly one cell blocks it and has the **same** footprint - the two trade anchors, each landing
     *    on the rectangle the other already held (owner decision 2026-07-17).
     * 3. Anything else - the move is refused.
     *
     * Case 3 is the interesting one, and it is why swapping is restricted to equal footprints: a 2x2
     * gadget cannot take a 1x1 shortcut's place without landing on that shortcut's neighbours, so a
     * general "swap" would trade one overlap for another. An equal-footprint trade cannot, because both
     * rectangles were already free of every other cell by the standing invariant. That invariant is what
     * all of this protects: **cells never overlap**, at any point, so no rendering or hit-test has to
     * cope with two cells claiming one square.
     */
    suspend fun moveCell(id: Long, rowIndex: Int, colIndex: Int): Boolean

    /**
     * Changes a cell's footprint at its current anchor, but only onto free space: the new
     * `spanW x spanH` must not overlap another cell (the cell's own current squares are excluded, so
     * growth into them is allowed). Returns whether it resized. Same no-overlap invariant as [moveCell];
     * the caller (the resize gesture) reverts to the last valid size when this returns false.
     */
    suspend fun resizeCell(id: Long, spanW: Int, spanH: Int): Boolean

    /**
     * Repoints a cell at new content without moving or resizing it. Geometry is untouched, so the
     * no-overlap invariant cannot be violated and no overlap check is needed. Returns whether the cell
     * existed.
     */
    suspend fun updateCellTarget(id: Long, target: String): Boolean

    /**
     * Places [cells] only when this orientation has never been seeded and holds no cells.
     * Returns whether it seeded, so a profile change can never overwrite a desktop the user owns.
     */
    suspend fun seedIfEmpty(orientation: LauncherOrientation, cells: List<LauncherCell>): Boolean

    /**
     * Drops every cell of BOTH orientations together with the desktop-wide state row, so the seeded
     * flags and the stored column widths go with it. A later [seedIfEmpty] therefore seeds again -
     * that is what makes the one-time starter set repeatable for a reset. Read [state] before calling
     * this: the column widths are gone afterwards.
     */
    suspend fun clearAll()

    suspend fun state(): LauncherDesktopState

    suspend fun updateColumns(orientation: LauncherOrientation, columns: Int)
}
