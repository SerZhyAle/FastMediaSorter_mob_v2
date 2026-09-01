package com.sza.fastmediasorter.data.repository

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
 * S2301: moving a desktop object - or a whole section - to another screen.
 *
 * The section case is the one worth a test: membership is positional, so a header that travelled alone
 * would hand its cells to the section above them, and the rows it vacated would stay pushed down by a
 * group that is no longer there. Neither is visible to a compiler or to any static gate.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LauncherMoveToScreenTest {

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
        col: Int = 0,
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

    private fun section(row: Int, screenIndex: Int = 0, key: String = "app_functions") =
        cell(row = row, target = "sec:$key", screenIndex = screenIndex)
            .copy(kind = LauncherCellKind.SECTION)

    private suspend fun add(cell: LauncherCell): Long? =
        repository.addCell(cell, columns = COLUMNS).idOrNull

    private suspend fun storedCell(id: Long) = dbRule.db.launcherCellDao().getById(id)

    private suspend fun move(cellId: Long, screenIndex: Int) = repository.moveCellToScreen(
        orientation = LauncherOrientation.PORTRAIT,
        cellId = cellId,
        screenIndex = screenIndex,
        columns = COLUMNS,
    )

    @Test
    fun `a shortcut lands on the target screen`() = runTest {
        val id = add(cell(row = 3, col = 2))
        assertNotNull(id)

        assertTrue(move(id!!, screenIndex = 1))

        val stored = storedCell(id)
        assertEquals(1, stored?.screenIndex)
        assertEquals("it took the target screen's first free row", 0, stored?.rowIndex)
        assertEquals(0, stored?.colIndex)
    }

    @Test
    fun `a move onto the cell's own screen changes nothing`() = runTest {
        val id = add(cell(row = 2))
        assertNotNull(id)

        assertFalse(move(id!!, screenIndex = 0))
        assertEquals(2, storedCell(id)?.rowIndex)
    }

    @Test
    fun `a section header takes its own cells with it`() = runTest {
        val header = add(section(row = 0))
        val owned = add(cell(row = 1, target = "app:com.owned"))
        assertNotNull(header)
        assertNotNull(owned)

        assertTrue(move(header!!, screenIndex = 1))

        assertEquals(1, storedCell(header)?.screenIndex)
        assertEquals("the owned cell travelled with its header", 1, storedCell(owned!!)?.screenIndex)
    }

    @Test
    fun `a moved section keeps its shape and closes the gap behind it`() = runTest {
        // Membership is positional, so the second header is what bounds the first section - without it
        // every cell below belongs to the section being moved.
        val header = add(section(row = 0))
        val owned = add(cell(row = 1, target = "app:com.owned"))
        val nextSection = add(section(row = 2, key = "resources"))
        assertNotNull(header)
        assertNotNull(owned)
        assertNotNull(nextSection)

        assertTrue(move(header!!, screenIndex = 1))

        assertEquals("the header led the block on the empty screen", 0, storedCell(header)?.rowIndex)
        assertEquals("the block kept its own spacing", 1, storedCell(owned!!)?.rowIndex)
        assertEquals("the next section moved up into the gap", 0, storedCell(nextSection!!)?.rowIndex)
        assertEquals("the next section stayed on its screen", 0, storedCell(nextSection)?.screenIndex)
    }

    @Test
    fun `an unknown id moves nothing`() = runTest {
        assertFalse(move(cellId = 4242L, screenIndex = 1))
    }

    private companion object {
        const val COLUMNS = 8
    }
}
