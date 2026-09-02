package com.sza.fastmediasorter.data.repository

import com.sza.fastmediasorter.domain.model.launcher.LauncherCell
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellKind
import com.sza.fastmediasorter.domain.model.launcher.LauncherOrientation
import com.sza.fastmediasorter.testing.InMemoryRoomRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * S2301: the desktop's placement rules answer per screen, not per orientation.
 *
 * Screens carry independent row coordinates - the starter set packs each one from row 0 - so a query
 * that filtered on orientation alone reported a free square of screen 1 as taken by screen 0, and pushed
 * a screen the user was not editing. Both halves are invisible to every static gate: the wrong scope
 * still compiles and only shows as a cell that refused to land, or a screen that shifted on its own.
 *
 * The single-screen cases are the regression guard: every desktop written before screens existed carries
 * screen 0 only, and its answers must not change.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LauncherScreenScopedPlacementTest {

    @get:Rule
    val dbRule = InMemoryRoomRule { RuntimeEnvironment.getApplication() }

    private val repository by lazy {
        LauncherDesktopRepositoryImpl(
            db = dbRule.db,
            cellDao = dbRule.db.launcherCellDao(),
            stateDao = dbRule.db.launcherStateDao(),
        )
    }

    private fun cell(
        row: Int,
        col: Int,
        screenIndex: Int = 0,
        target: String = "app:com.example",
    ) = LauncherCell(
        id = 0,
        orientation = LauncherOrientation.PORTRAIT,
        rowIndex = row,
        colIndex = col,
        spanW = 1,
        spanH = 1,
        kind = LauncherCellKind.SHORTCUT,
        target = target,
        labelOverride = null,
        addedAt = 0L,
        screenIndex = screenIndex,
    )

    private suspend fun add(cell: LauncherCell): Long? =
        repository.addCell(cell, columns = COLUMNS).idOrNull

    private suspend fun storedCell(id: Long) = dbRule.db.launcherCellDao().getById(id)

    @Test
    fun `an anchor taken on another screen is still free`() = runTest {
        val onScreenZero = add(cell(row = 0, col = 0))
        assertNotNull(onScreenZero)

        val onScreenOne = add(cell(row = 0, col = 0, screenIndex = 1, target = "app:com.other"))
        assertNotNull(onScreenOne)

        val stored = storedCell(onScreenOne!!)
        assertEquals("the second screen kept the anchor it asked for", 0, stored?.rowIndex)
        assertEquals(0, stored?.colIndex)
        assertEquals(1, stored?.screenIndex)
    }

    @Test
    fun `placing on one screen never moves another`() = runTest {
        val untouched = add(cell(row = 0, col = 0))
        assertNotNull(untouched)

        add(cell(row = 0, col = 0, screenIndex = 1, target = "app:com.other"))

        assertEquals("screen 0 stayed where it was", 0, storedCell(untouched!!)?.rowIndex)
    }

    @Test
    fun `a taken anchor still pushes the tail of its own screen`() = runTest {
        val first = add(cell(row = 0, col = 0, screenIndex = 1))
        assertNotNull(first)

        val second = add(cell(row = 0, col = 0, screenIndex = 1, target = "app:com.other"))
        assertNotNull(second)

        assertEquals("the blocked screen pushed its own tail down", 1, storedCell(first!!)?.rowIndex)
        assertEquals(0, storedCell(second!!)?.rowIndex)
    }

    @Test
    fun `the free-slot scan starts at row zero of its own screen`() = runTest {
        // Screen 0 is filled down two rows; screen 1 must still take its own first row.
        for (col in 0 until COLUMNS) {
            add(cell(row = 0, col = col, target = "app:com.example$col"))
        }

        val onScreenOne = repository.addCellInFirstFreeSlot(
            cell(row = 0, col = 0, screenIndex = 1, target = "app:com.other"),
            columns = COLUMNS,
        )
        assertNotNull(onScreenOne)
        assertEquals(0, storedCell(onScreenOne!!)?.rowIndex)
    }

    @Test
    fun `a single-screen desktop places exactly as before`() = runTest {
        val first = add(cell(row = 0, col = 0))
        val second = add(cell(row = 0, col = 0, target = "app:com.other"))
        assertNotNull(first)
        assertNotNull(second)

        assertEquals("the older cell was pushed down", 1, storedCell(first!!)?.rowIndex)
        assertEquals(0, storedCell(second!!)?.rowIndex)
    }

    private companion object {
        const val COLUMNS = 8
    }
}
