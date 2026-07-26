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

    suspend fun removeCell(id: Long)

    /**
     * Moves a cell's anchor, but only onto free space: the whole `spanW x spanH` footprint must be
     * clear. Returns whether it moved.
     *
     * Deliberately does NOT swap with the occupant, which is what an earlier draft of this plan asked
     * for. Swapping is well-defined only between equal-sized cells - a 2x2 gadget cannot take a 1x1
     * shortcut's place without landing on that shortcut's neighbours, so "swap" would trade one overlap
     * for another. Windows does not swap desktop icons either. The invariant this buys is worth more
     * than the gesture: **cells never overlap**, at any point, so no rendering or hit-test has to cope
     * with two cells claiming one square.
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

    suspend fun state(): LauncherDesktopState

    suspend fun updateColumns(orientation: LauncherOrientation, columns: Int)
}
