package com.sza.fastmediasorter.data.repository

import com.sza.fastmediasorter.data.local.db.LauncherCellEntity
import com.sza.fastmediasorter.domain.model.launcher.LauncherCell
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellKind
import com.sza.fastmediasorter.domain.model.launcher.LauncherOrientation
import com.sza.fastmediasorter.testing.InMemoryRoomRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * S2599: a cell is stored at the column it is drawn at, including against the grid's right edge.
 *
 * Separate from [LauncherDesktopRepositoryImplTest] for two reasons: that class already sits at the
 * `LargeClass` ceiling, and every case here needs a deliberately NARROW grid, while its seeding helper
 * uses a wide one so no placement is ever refused for width alone.
 *
 * The defect these cover was invisible to either side alone. The table cleared a rectangle that ran off
 * the grid, so it reported no overlap; the renderer pulled that rectangle back inside, so it drew the
 * cell on top of its neighbour. Both answered consistently - only the pair disagreed, which is why each
 * case asserts the stored column AND [assertNoOverlap] over the whole table.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LauncherGridEdgePlacementTest {

    @get:Rule
    val dbRule = InMemoryRoomRule { RuntimeEnvironment.getApplication() }

    private val repository by lazy {
        LauncherDesktopRepositoryImpl(
            db = dbRule.db,
            cellDao = dbRule.db.launcherCellDao(),
            stateDao = dbRule.db.launcherStateDao(),
        )
    }

    @Test
    fun `a wide gadget added in the last column is stored where it is drawn`() = runTest {
        // The reported case: five columns, a 2-wide gadget already on columns 2-3, the empty "+" in the
        // last column tapped and a second one placed there. Unseated, the table cleared columns 4-5 -
        // one of which does not exist - and the renderer drew the new cell on top of its neighbour.
        val neighbour = seed(row = 0, col = 2, target = "app:a")
        val placed = repository.addCell(
            gadget(row = 0, col = 4, target = "app:b"),
            columns = GRID,
        ).idOrNull!!

        val stored = storedCell(placed)!!
        assertEquals("the last column a 2-wide cell fits in on a 5-column grid", 3, stored.colIndex)
        assertEquals("its footprint has to end inside the grid", GRID, stored.colIndex + stored.spanW)
        assertNotNull("the neighbour is still on the desktop", storedCell(neighbour))
        assertNoOverlap()
    }

    @Test
    fun `a wide gadget dropped in the last column is stored where it is drawn`() = runTest {
        // The drop point arrives clamped for a single square, which for a 2-wide cell is one column too
        // far right - the same divergence reached through the drag gesture instead of the add flow.
        val moving = seed(row = 3, col = 0, target = "app:a")

        assertTrue(repository.moveCell(moving, rowIndex = 3, colIndex = 4, columns = GRID))

        assertEquals(3, storedCell(moving)?.colIndex)
        assertNoOverlap()
    }

    @Test
    fun `growing a gadget past the last column stops at the grid`() = runTest {
        // The resize ceiling used to be the whole grid whatever the anchor, so a cell at column 3 could
        // be stored four wide on a five-column grid and then drawn from column 1, across two columns it
        // never claimed.
        val growing = seed(row = 0, col = 3, target = "app:a")

        repository.resizeCell(growing, spanW = 4, spanH = 1, columns = GRID)

        val stored = storedCell(growing)!!
        assertEquals("growth stops at the right edge instead of running past it", 2, stored.spanW)
        assertEquals("the anchor does not move under the resize handle", 3, stored.colIndex)
        assertNoOverlap()
    }

    @Test
    fun `a one-wide cell still reaches the last column`() = runTest {
        // Seating is per width, not a blanket ban on the last column - a shortcut has to keep fitting
        // there, or the fix would cost the user a whole column.
        val shortcut = repository.addCell(
            gadget(row = 1, col = 4, target = "app:a").copy(spanW = 1, kind = LauncherCellKind.SHORTCUT),
            columns = GRID,
        ).idOrNull!!

        assertEquals(4, storedCell(shortcut)?.colIndex)
        assertNoOverlap()
    }

    private fun gadget(row: Int, col: Int, target: String) = LauncherCell(
        id = 0,
        orientation = LauncherOrientation.PORTRAIT,
        rowIndex = row,
        colIndex = col,
        spanW = WIDE_SPAN,
        spanH = 1,
        kind = LauncherCellKind.GADGET,
        target = target,
        labelOverride = null,
        addedAt = 0L,
    )

    private suspend fun seed(row: Int, col: Int, target: String): Long =
        repository.addCell(gadget(row, col, target), columns = GRID).idOrNull!!

    private suspend fun storedCell(id: Long) = dbRule.db.launcherCellDao().getById(id)

    /**
     * The invariant the desktop rests on, asserted over the whole table rather than through one
     * placement result: the defect was two cells the table considered disjoint, so no single call's
     * return value could have shown it.
     */
    private suspend fun assertNoOverlap() {
        val cells = dbRule.db.launcherCellDao().getAllCellsSync()
        cells
            .flatMapIndexed { index, first -> cells.drop(index + 1).map { first to it } }
            .filter { (first, second) ->
                first.orientation == second.orientation && first.screenIndex == second.screenIndex
            }
            .forEach { (first, second) ->
                assertFalse(
                    "cells ${first.id} and ${second.id} share a square",
                    meets(first, second),
                )
            }
    }

    private fun meets(first: LauncherCellEntity, second: LauncherCellEntity): Boolean {
        val rowsMeet = first.rowIndex < second.rowIndex + second.spanH &&
            second.rowIndex < first.rowIndex + first.spanH
        val colsMeet = first.colIndex < second.colIndex + second.spanW &&
            second.colIndex < first.colIndex + first.spanW
        return rowsMeet && colsMeet
    }

    private companion object {
        /** Narrow on purpose: the defect only shows where a 2-wide cell cannot fit in the last column. */
        const val GRID = 5

        /** The footprint every weather-family gadget seeds at, and the one the ticket was reported on. */
        const val WIDE_SPAN = 2
    }
}
