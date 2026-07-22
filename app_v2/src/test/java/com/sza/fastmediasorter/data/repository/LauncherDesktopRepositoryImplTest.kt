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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * S0404: the desktop's placement rules - cells never overlap, equal footprints trade places, and a
 * degenerate span never reaches the table.
 *
 * These are the rules edit mode is built on, and all three are invisible to every static gate: a broken
 * one still compiles, still passes detekt, and only shows up as two cells drawn on top of each other.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LauncherDesktopRepositoryImplTest {

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
        spanW: Int = 1,
        spanH: Int = 1,
        target: String = "app:com.example",
    ) = LauncherCell(
        id = 0,
        orientation = LauncherOrientation.PORTRAIT,
        rowIndex = row,
        colIndex = col,
        spanW = spanW,
        spanH = spanH,
        kind = LauncherCellKind.SHORTCUT,
        target = target,
        labelOverride = null,
        addedAt = 0L,
    )

    private suspend fun storedCell(id: Long) = dbRule.db.launcherCellDao().getById(id)

    @Test
    fun `adding to a free square succeeds`() = runTest {
        assertNotNull(repository.addCell(cell(row = 0, col = 0)))
    }

    @Test
    fun `adding onto an occupied square is refused`() = runTest {
        repository.addCell(cell(row = 0, col = 0))
        assertNull(repository.addCell(cell(row = 0, col = 0)))
    }

    @Test
    fun `adding onto a square covered by a gadget is refused`() = runTest {
        repository.addCell(cell(row = 0, col = 0, spanW = 2, spanH = 2))
        // (1,1) is inside the gadget but is not its anchor - the case the anchor-only predecessor missed.
        assertNull(repository.addCell(cell(row = 1, col = 1)))
    }

    @Test
    fun `a degenerate span is stored as one square`() = runTest {
        // An empty rectangle intersects nothing, so an unnormalised zero span would make findOverlapping
        // report this cell's square free forever while the renderer still drew the cell there.
        val id = repository.addCell(cell(row = 0, col = 0, spanW = 0, spanH = -3))
        assertNotNull(id)
        val stored = storedCell(id!!)
        assertEquals(1, stored?.spanW)
        assertEquals(1, stored?.spanH)
        assertNull("the normalised square must now block a second cell", repository.addCell(cell(0, 0)))
    }

    @Test
    fun `a negative index is stored at the edge`() = runTest {
        val id = repository.addCell(cell(row = -2, col = -5))
        val stored = storedCell(id!!)
        assertEquals(0, stored?.rowIndex)
        assertEquals(0, stored?.colIndex)
    }

    @Test
    fun `moving to a free square succeeds`() = runTest {
        val id = repository.addCell(cell(row = 0, col = 0))!!
        assertTrue(repository.moveCell(id, rowIndex = 3, colIndex = 2))
        val stored = storedCell(id)
        assertEquals(3, stored?.rowIndex)
        assertEquals(2, stored?.colIndex)
    }

    @Test
    fun `moving onto itself changes nothing`() = runTest {
        val id = repository.addCell(cell(row = 1, col = 1))!!
        assertFalse(repository.moveCell(id, rowIndex = 1, colIndex = 1))
    }

    @Test
    fun `equal footprints trade places`() = runTest {
        val moving = repository.addCell(cell(row = 0, col = 0, target = "app:a"))!!
        val target = repository.addCell(cell(row = 2, col = 3, target = "app:b"))!!

        assertTrue(repository.moveCell(moving, rowIndex = 2, colIndex = 3))

        assertEquals(2, storedCell(moving)?.rowIndex)
        assertEquals(3, storedCell(moving)?.colIndex)
        assertEquals("the displaced cell takes the mover's old square", 0, storedCell(target)?.rowIndex)
        assertEquals(0, storedCell(target)?.colIndex)
    }

    @Test
    fun `a gadget cannot trade places with a shortcut`() = runTest {
        val gadget = repository.addCell(cell(row = 0, col = 0, spanW = 2, spanH = 2))!!
        val shortcut = repository.addCell(cell(row = 4, col = 4))!!

        // Swapping here would put the 2x2 on the shortcut's neighbours - one overlap traded for another.
        assertFalse(repository.moveCell(gadget, rowIndex = 4, colIndex = 4))
        assertEquals(0, storedCell(gadget)?.rowIndex)
        assertEquals(4, storedCell(shortcut)?.rowIndex)
    }

    @Test
    fun `a trade lands on the anchor, not on the dropped square`() = runTest {
        val moving = repository.addCell(cell(row = 0, col = 0, spanW = 2, spanH = 2, target = "app:a"))!!
        val target = repository.addCell(cell(row = 4, col = 4, spanW = 2, spanH = 2, target = "app:b"))!!

        // Dropped on (5,5) - inside the target, but not its anchor. Honouring the finger's square would
        // put the mover at rows 5-6, which is not where the target was, and could cover a third cell.
        assertTrue(repository.moveCell(moving, rowIndex = 5, colIndex = 5))

        assertEquals(4, storedCell(moving)?.rowIndex)
        assertEquals(4, storedCell(moving)?.colIndex)
        assertEquals(0, storedCell(target)?.rowIndex)
        assertEquals(0, storedCell(target)?.colIndex)
    }

    @Test
    fun `the other orientation is untouched by a move`() = runTest {
        val portrait = repository.addCell(cell(row = 0, col = 0))!!
        val landscape = repository.addCell(
            cell(row = 1, col = 1).copy(orientation = LauncherOrientation.LANDSCAPE)
        )!!

        assertTrue(repository.moveCell(portrait, rowIndex = 1, colIndex = 1))

        assertEquals("the landscape cell must not have been treated as a blocker", 1, storedCell(landscape)?.rowIndex)
        assertEquals(1, storedCell(portrait)?.rowIndex)
    }

    @Test
    fun `resizing a gadget into free space grows and persists`() = runTest {
        val id = repository.addCell(cell(row = 0, col = 0, spanW = 2, spanH = 2))!!
        assertTrue(repository.resizeCell(id, spanW = 3, spanH = 3))
        val stored = storedCell(id)
        assertEquals(3, stored?.spanW)
        assertEquals(3, stored?.spanH)
    }

    @Test
    fun `resizing over another cell is refused and keeps the size`() = runTest {
        val gadget = repository.addCell(cell(row = 0, col = 0, spanW = 2, spanH = 2, target = "app:a"))!!
        // A neighbour one column past the gadget's right edge: growing to 3 wide would cover it.
        repository.addCell(cell(row = 0, col = 2, target = "app:b"))!!
        assertFalse(repository.resizeCell(gadget, spanW = 3, spanH = 2))
        val stored = storedCell(gadget)
        assertEquals(2, stored?.spanW)
        assertEquals(2, stored?.spanH)
    }

    @Test
    fun `shrinking a gadget always succeeds`() = runTest {
        val id = repository.addCell(cell(row = 0, col = 0, spanW = 3, spanH = 3))!!
        assertTrue(repository.resizeCell(id, spanW = 2, spanH = 2))
        assertEquals(2, storedCell(id)?.spanW)
        assertEquals(2, storedCell(id)?.spanH)
    }
}
