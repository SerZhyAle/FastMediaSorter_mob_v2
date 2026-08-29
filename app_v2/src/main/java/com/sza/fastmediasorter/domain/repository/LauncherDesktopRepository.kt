package com.sza.fastmediasorter.domain.repository

import com.sza.fastmediasorter.domain.model.launcher.LauncherCell
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellPlacement
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
     *
     * S1772: the anchor the caller names is the user's choice of place, so a blocked footprint no longer
     * gives up - the desktop's tail is pushed down until the footprint is clear, and only a cell wider
     * than [columns] is refused. A section header is exempt from the push: displacing one would duplicate
     * a header rather than make room.
     */
    suspend fun addCell(cell: LauncherCell, columns: Int): LauncherCellPlacement

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

    /**
     * S2018: places [cell] inside the section keyed [sectionKey] and nowhere else.
     *
     * Deliberately without the grid-wide fallback [addCellInFirstFreeSlot] ends in. That fallback is
     * what made a bulk import land in whichever section still had a gap - on a dense desktop the first
     * free squares sit inside the widgets and resources sections, so "first free slot" and "the section
     * the caller asked for" are different places. When the section is full this pushes the rows below it
     * down and seats the cell in the row that frees up, growing the section instead of leaving it.
     *
     * Returns null when the section has no header on this orientation: creating one is a placement
     * decision of its own, and the caller owns the label it would carry.
     */
    suspend fun addCellInSection(cell: LauncherCell, columns: Int, sectionKey: String): Long?

    suspend fun removeCell(id: Long)

    /**
     * S1642: brings every stored section header to the one span a header is stored and drawn at.
     *
     * Not a Room migration and deliberately not one: no column changes, only values written by an earlier
     * build. The owner ruled on 2026-08-15 that the compact geometry applies to every desktop at once,
     * testing desktops included, and that no migration be written for it (strategic §6.3). Narrowing a
     * header only ever frees squares, so no shortcut moves and nothing can be overlapped by it.
     *
     * Idempotent - safe to call on every launcher start, including before the seed decides it has nothing
     * to do.
     */
    suspend fun normalizeSectionSpans()

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

    suspend fun updateCellLabel(id: Long, labelOverride: String?): Boolean

    /**
     * S1742 §04.1: exchanges a section block (the header and its owned cells) with an adjacent section block
     * in [orientation]. Returns whether the swap occurred (false if no adjacent section exists).
     */
    suspend fun swapSectionBlock(orientation: LauncherOrientation, sectionCellId: Long, moveUp: Boolean): Boolean

    /**
     * S2222: deletes the section header with cell id [sectionCellId] together with every cell it owns on
     * [orientation], in one transaction. Returns the `target` of every removed row read inside that
     * transaction (S2217 pattern - the caller clears stored configured-widget instances); cells below the
     * section move up so no multi-row hole remains. Returns an empty list when the id is not a section
     * header of this orientation. Block membership is [LauncherSectionMembership.ownerOf] - S1428
     * positional membership, the same rule the swap and the renderer read.
     */
    suspend fun removeSection(orientation: LauncherOrientation, sectionCellId: Long): List<String>

    /**
     * S2222: repacks the cells owned by the section header [sectionCellId] densely in their current visual
     * order (stored row, then column), starting at the first cell after the header on its own row
     * ([LauncherSectionMembership.HEADER_SPAN_W]), first-fit row-major respecting each cell's span and the
     * passed [columns]. Everything below the section shifts by the height difference the repack causes.
     * Returns whether any row or column changed - false for an unknown id or a section with no owned
     * cells. [columns] belongs to the rendering screen, same contract as [addCell].
     */
    suspend fun resortSection(orientation: LauncherOrientation, sectionCellId: Long, columns: Int): Boolean

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
     *
     * S2217: returns the `target` column of every row the delete removed, read inside the same
     * transaction, so the reset can clear whatever those rows pointed at - a configured widget cell
     * is the only thing that still knows its instance token, and after this call nothing does.
     */
    suspend fun clearAll(): List<String>

    suspend fun state(): LauncherDesktopState

    suspend fun updateColumns(orientation: LauncherOrientation, columns: Int)
}
