package com.sza.fastmediasorter.data.repository

import com.sza.fastmediasorter.data.local.db.LauncherCellEntity
import com.sza.fastmediasorter.domain.model.launcher.LauncherCell
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellKind
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellPlacement
import com.sza.fastmediasorter.domain.model.launcher.LauncherOrientation
import com.sza.fastmediasorter.domain.model.launcher.LauncherSectionMembership
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

    private fun section(row: Int, col: Int = 0) =
        cell(row = row, col = col, target = "sec:app_functions").copy(kind = LauncherCellKind.SECTION)

    private fun gadget(row: Int, col: Int = 0, spanH: Int = 2) =
        cell(row = row, col = col, spanH = spanH).copy(kind = LauncherCellKind.GADGET)

    /**
     * A header stored narrower than the grid it is drawn on, written straight through the DAO because no
     * repository path produces it any more - every header is now stored at the widest grid there is.
     *
     * It is still the state raising `LauncherGridGeometry.MAX_COLUMNS` would leave every already-stored
     * header in, and the only one where `findOverlapping` cannot see the squares a header covers. So it is
     * the state that tells the S1428 straddle rule apart from plain rectangle intersection.
     */
    private suspend fun insertNarrowHeader(row: Int) = insertHeaderWithSpan(row, spanW = 1)

    /**
     * A header written straight through the DAO at an arbitrary span, which no repository path produces -
     * every write normalizes it. It is how a desktop seeded by another build is reconstructed: span 1 for a
     * header narrower than the grid it is drawn on, span 12 for one an S1428 build stored full-row.
     */
    private suspend fun insertHeaderWithSpan(row: Int, spanW: Int) = dbRule.db.launcherCellDao().upsert(
        LauncherCellEntity(
            id = 0,
            orientation = LauncherOrientation.PORTRAIT.name,
            rowIndex = row,
            colIndex = 0,
            spanW = spanW,
            spanH = 1,
            kind = LauncherCellKind.SECTION.name,
            target = "sec:app_functions",
            labelOverride = null,
            addedAt = 0L,
        )
    )

    private suspend fun storedCell(id: Long) = dbRule.db.launcherCellDao().getById(id)

    /**
     * Seeds a cell and hands back its id, so a suite about placement rules is not rewritten every time
     * the placement result gains a case (S1772 turned it from a nullable id into a typed outcome).
     */
    private suspend fun add(cell: LauncherCell): Long? =
        repository.addCell(cell, columns = COLUMNS).idOrNull

    private suspend fun placement(cell: LauncherCell, columns: Int = COLUMNS) =
        repository.addCell(cell, columns = columns)

    private suspend fun rowOf(id: Long?) = storedCell(id!!)?.rowIndex

    @Test
    fun `adding to a free square succeeds`() = runTest {
        assertNotNull(add(cell(row = 0, col = 0)))
    }

    @Test
    fun `a header placed in the first free slot never lands off column 0`() = runTest {
        // S1642: the free-slot scan writes the anchor it found rather than the normalized column, so it is
        // the one path where a header narrow enough to fit beside another could be seated at column 1+.
        insertNarrowHeader(row = 0)
        val id = repository.addCellInFirstFreeSlot(section(row = 0), columns = 8)
        assertNotNull(id)
        val stored = storedCell(id!!)
        assertEquals(0, stored?.colIndex)
        assertTrue("header stayed on the occupied row", (stored?.rowIndex ?: 0) > 0)
    }

    @Test
    fun `a second header on a row that already carries one is refused`() = runTest {
        assertNotNull(add(section(row = 0)))
        assertNull(add(section(row = 0)))
    }

    @Test
    fun `a shortcut may be placed beside a header on the header's own row`() = runTest {
        // Strategic §2.2: the point of the compact header is that content fills the rest of its row, so the
        // column pin above must keep headers apart without keeping shortcuts out.
        insertNarrowHeader(row = 0)
        val id = repository.addCellInFirstFreeSlot(cell(row = 0, col = 0), columns = 8)
        assertNotNull(id)
        val stored = storedCell(id!!)
        assertEquals(0, stored?.rowIndex)
        assertEquals(1, stored?.colIndex)
    }

    @Test
    fun `a resource shortcut is placed inside the resources section when present`() = runTest {
        add(section(row = 0).copy(target = "sec:widgets"))
        add(section(row = 4).copy(target = "sec:resources"))
        val resourceCell = cell(row = 0, col = 0, target = "res:123")
        val id = repository.addCellInFirstFreeSlot(resourceCell, columns = 8)
        assertNotNull(id)
        val stored = storedCell(id!!)
        assertEquals(4, stored?.rowIndex)
        assertEquals(2, stored?.colIndex)
    }

    @Test
    fun `adding onto an occupied square pushes the occupant down`() = runTest {
        // S1772: the anchor is the user's choice of place, so the desktop makes room instead of refusing.
        val occupant = add(cell(row = 0, col = 0))
        val arriving = add(cell(row = 0, col = 0))
        assertNotNull(arriving)
        assertEquals("the new cell took the square it was pointed at", 0, rowOf(arriving))
        assertEquals("the occupant moved one row down", 1, rowOf(occupant))
    }

    @Test
    fun `a widget lands on a square covered by a gadget and pushes it down`() = runTest {
        // (1,1) is inside the gadget but is not its anchor - the case the anchor-only predecessor missed.
        val gadget = add(cell(row = 0, col = 0, spanW = 2, spanH = 2))
        val arriving = add(cell(row = 1, col = 1))
        assertNotNull(arriving)
        assertEquals(1, rowOf(arriving))
        // The gadget started above the anchor and reached into it, so the push had to start at its own
        // row - the case a shift keyed on the anchor alone would have left in the way.
        assertEquals("the straddling gadget moved below the new cell", 2, rowOf(gadget))
    }

    @Test
    fun `a footprint wider than the grid is refused with a reason`() = runTest {
        val outcome = placement(cell(row = 0, col = 0, spanW = COLUMNS + 1), columns = COLUMNS)
        assertEquals(LauncherCellPlacement.TooWide, outcome)
    }

    @Test
    fun `a push keeps the desktop's order below the insertion point`() = runTest {
        val first = add(cell(row = 0, col = 0, target = "app:a"))
        val second = add(cell(row = 1, col = 0, target = "app:b"))
        val arriving = add(cell(row = 0, col = 0, spanH = 2, target = "app:c"))
        assertNotNull(arriving)
        // Both existing cells moved by the same amount, so what was above stays above (ADR-1).
        assertEquals(2, rowOf(first))
        assertEquals(3, rowOf(second))
    }

    @Test
    fun `a degenerate span is stored as one square`() = runTest {
        // An empty rectangle intersects nothing, so an unnormalised zero span would make findOverlapping
        // report this cell's square free forever while the renderer still drew the cell there.
        val id = add(cell(row = 0, col = 0, spanW = 0, spanH = -3))
        assertNotNull(id)
        val stored = storedCell(id!!)
        assertEquals(1, stored?.spanW)
        assertEquals(1, stored?.spanH)
        // S1772: a taken square now yields rather than blocks, so the proof that the span was normalised
        // is that the arriving cell displaced it - an unnormalised empty rectangle would collide with
        // nothing and leave the first cell exactly where it was.
        assertNotNull(add(cell(0, 0)))
        assertEquals("the normalised square was pushed down by the second cell", 1, rowOf(id))
    }

    @Test
    fun `a negative index is stored at the edge`() = runTest {
        val id = add(cell(row = -2, col = -5))
        val stored = storedCell(id!!)
        assertEquals(0, stored?.rowIndex)
        assertEquals(0, stored?.colIndex)
    }

    @Test
    fun `the first free slot on an empty desktop is the top-left square`() = runTest {
        val id = repository.addCellInFirstFreeSlot(cell(row = 9, col = 9), columns = 4)!!
        val stored = storedCell(id)
        assertEquals("the carried anchor must be ignored, not honoured", 0, stored?.rowIndex)
        assertEquals(0, stored?.colIndex)
    }

    @Test
    fun `the scan skips occupied squares row-major`() = runTest {
        add(cell(row = 0, col = 0))
        add(cell(row = 0, col = 1))
        val id = repository.addCellInFirstFreeSlot(cell(row = 0, col = 0), columns = 3)!!
        val stored = storedCell(id)
        assertEquals(0, stored?.rowIndex)
        assertEquals("the third column of the first row is still free", 2, stored?.colIndex)
    }

    @Test
    fun `a full row pushes the cell onto a new row below`() = runTest {
        add(cell(row = 0, col = 0))
        add(cell(row = 0, col = 1))
        val id = repository.addCellInFirstFreeSlot(cell(row = 0, col = 0), columns = 2)!!
        assertEquals(1, storedCell(id)?.rowIndex)
        assertEquals(0, storedCell(id)?.colIndex)
    }

    @Test
    fun `a wide gadget skips a row that cannot hold its whole span`() = runTest {
        add(cell(row = 0, col = 1))
        // Row 0 has a free square at column 0, but a 2-wide footprint there would cover the occupant.
        val id = repository.addCellInFirstFreeSlot(cell(row = 0, col = 0, spanW = 2), columns = 2)!!
        assertEquals(1, storedCell(id)?.rowIndex)
        assertEquals(0, storedCell(id)?.colIndex)
    }

    @Test
    fun `a span wider than the grid is clamped instead of refused`() = runTest {
        val id = repository.addCellInFirstFreeSlot(cell(row = 0, col = 0, spanW = 5), columns = 3)!!
        assertEquals("a footprint wider than the screen could never be rendered", 3, storedCell(id)?.spanW)
    }

    @Test
    fun `placement is refused when the grid has no columns`() = runTest {
        assertNull(repository.addCellInFirstFreeSlot(cell(row = 0, col = 0), columns = 0))
    }

    @Test
    fun `the other orientation never blocks a free-slot placement`() = runTest {
        add(cell(row = 0, col = 0).copy(orientation = LauncherOrientation.LANDSCAPE))
        val id = repository.addCellInFirstFreeSlot(cell(row = 0, col = 0), columns = 2)!!
        assertEquals(0, storedCell(id)?.rowIndex)
        assertEquals(0, storedCell(id)?.colIndex)
    }

    @Test
    fun `moving to a free square succeeds`() = runTest {
        val id = add(cell(row = 0, col = 0))!!
        assertTrue(repository.moveCell(id, rowIndex = 3, colIndex = 2))
        val stored = storedCell(id)
        assertEquals(3, stored?.rowIndex)
        assertEquals(2, stored?.colIndex)
    }

    @Test
    fun `moving onto itself changes nothing`() = runTest {
        val id = add(cell(row = 1, col = 1))!!
        assertFalse(repository.moveCell(id, rowIndex = 1, colIndex = 1))
    }

    @Test
    fun `equal footprints trade places`() = runTest {
        val moving = add(cell(row = 0, col = 0, target = "app:a"))!!
        val target = add(cell(row = 2, col = 3, target = "app:b"))!!

        assertTrue(repository.moveCell(moving, rowIndex = 2, colIndex = 3))

        assertEquals(2, storedCell(moving)?.rowIndex)
        assertEquals(3, storedCell(moving)?.colIndex)
        assertEquals("the displaced cell takes the mover's old square", 0, storedCell(target)?.rowIndex)
        assertEquals(0, storedCell(target)?.colIndex)
    }

    @Test
    fun `a gadget cannot trade places with a shortcut`() = runTest {
        val gadget = add(cell(row = 0, col = 0, spanW = 2, spanH = 2))!!
        val shortcut = add(cell(row = 4, col = 4))!!

        // Swapping here would put the 2x2 on the shortcut's neighbours - one overlap traded for another.
        assertFalse(repository.moveCell(gadget, rowIndex = 4, colIndex = 4))
        assertEquals(0, storedCell(gadget)?.rowIndex)
        assertEquals(4, storedCell(shortcut)?.rowIndex)
    }

    @Test
    fun `a trade lands on the anchor, not on the dropped square`() = runTest {
        val moving = add(cell(row = 0, col = 0, spanW = 2, spanH = 2, target = "app:a"))!!
        val target = add(cell(row = 4, col = 4, spanW = 2, spanH = 2, target = "app:b"))!!

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
        val portrait = add(cell(row = 0, col = 0))!!
        val landscape = add(
            cell(row = 1, col = 1).copy(orientation = LauncherOrientation.LANDSCAPE)
        )!!

        assertTrue(repository.moveCell(portrait, rowIndex = 1, colIndex = 1))

        assertEquals("the landscape cell must not have been treated as a blocker", 1, storedCell(landscape)?.rowIndex)
        assertEquals(1, storedCell(portrait)?.rowIndex)
    }

    @Test
    fun `resizing a gadget into free space grows and persists`() = runTest {
        val id = add(cell(row = 0, col = 0, spanW = 2, spanH = 2))!!
        assertTrue(repository.resizeCell(id, spanW = 3, spanH = 3))
        val stored = storedCell(id)
        assertEquals(3, stored?.spanW)
        assertEquals(3, stored?.spanH)
    }

    @Test
    fun `resizing over another cell is refused and keeps the size`() = runTest {
        val gadget = add(cell(row = 0, col = 0, spanW = 2, spanH = 2, target = "app:a"))!!
        // A neighbour one column past the gadget's right edge: growing to 3 wide would cover it.
        add(cell(row = 0, col = 2, target = "app:b"))!!
        assertFalse(repository.resizeCell(gadget, spanW = 3, spanH = 2))
        val stored = storedCell(gadget)
        assertEquals(2, stored?.spanW)
        assertEquals(2, stored?.spanH)
    }

    @Test
    fun `shrinking a gadget always succeeds`() = runTest {
        val id = add(cell(row = 0, col = 0, spanW = 3, spanH = 3))!!
        assertTrue(repository.resizeCell(id, spanW = 2, spanH = 2))
        assertEquals(2, storedCell(id)?.spanW)
        assertEquals(2, storedCell(id)?.spanH)
    }

    // ── S1428: section headers ──────────────────────────────────────────────

    @Test
    fun `a header is stored at column zero at the compact span`() = runTest {
        val id = add(section(row = 2, col = 3))!!
        val stored = storedCell(id)
        assertEquals("a header opens its own row wherever it was requested", 0, stored?.colIndex)
        assertEquals(LauncherSectionMembership.HEADER_SPAN_W, stored?.spanW)
    }

    @Test
    fun `a header leaves the rest of its row free for its own content`() = runTest {
        // Strategic §2.2: the compact header exists so the section's shortcuts start in the same row.
        add(section(row = 0))
        assertNotNull(add(cell(0, 3)))
    }

    @Test
    fun `a header restored on the narrowest grid keeps its span`() = runTest {
        // MIN_COLUMNS is 3 and the header is 2, so the compact span fits every grid the desktop resolves -
        // the case that used to place nothing at all, when the stored span exceeded the grid being scanned.
        val id = repository.addCellInFirstFreeSlot(section(row = 0), columns = 3)
        assertNotNull("a header must be placeable on the narrowest grid", id)
        val stored = storedCell(id!!)
        assertEquals(0, stored?.colIndex)
        assertEquals(LauncherSectionMembership.HEADER_SPAN_W, stored?.spanW)
    }

    @Test
    fun `normalizing narrows a header written by an earlier build and moves no shortcut`() = runTest {
        // Strategic §6.3/§11.6: the compact rule reaches desktops seeded before it, and narrowing a header
        // only frees squares - no shortcut may shift for it.
        val headerId = insertHeaderWithSpan(row = 0, spanW = 12)
        val shortcutId = add(cell(row = 3, col = 2))!!

        repository.normalizeSectionSpans()

        assertEquals(LauncherSectionMembership.HEADER_SPAN_W, storedCell(headerId)?.spanW)
        val shortcut = storedCell(shortcutId)
        assertEquals(3, shortcut?.rowIndex)
        assertEquals(2, shortcut?.colIndex)
        assertEquals(1, shortcut?.spanW)
    }

    @Test
    fun `a moved header stays anchored at column zero`() = runTest {
        val id = add(section(row = 0))!!
        assertTrue(repository.moveCell(id, rowIndex = 4, colIndex = 2))
        assertEquals(4, storedCell(id)?.rowIndex)
        assertEquals("moveCell writes a column without going through normalized()", 0, storedCell(id)?.colIndex)
    }

    @Test
    fun `the free-slot scan skips a row a gadget would straddle`() = runTest {
        add(section(row = 1))
        val id = repository.addCellInFirstFreeSlot(gadget(row = 0), columns = 4)!!
        // Refusing outright would make a tall gadget unplaceable whenever the first free anchor sits
        // just above a header; the scan is expected to move past that row and keep looking.
        val stored = storedCell(id)
        assertEquals("row 0 would cover the header on row 1", 1, stored?.rowIndex)
        // S1642: it lands beside the header rather than below it. Starting on the header's own row is not
        // a straddle - both rows the gadget covers belong to the section that header opens, which is the
        // whole point of the compact geometry. Only a cell crossing into the *next* section is refused.
        assertEquals(2, stored?.colIndex)
    }

    @Test
    fun `a gadget added over a header row pushes that header down`() = runTest {
        insertNarrowHeader(row = 2)
        // S1772 §5 item 4: the header travels down with the cells it owns rather than blocking the add.
        assertNotNull(add(gadget(row = 1, col = 3)))
    }

    @Test
    fun `a one-row cell past such a header is still allowed`() = runTest {
        // The rule is scoped to gadgets, and only a cell taller than one row can start in one section and
        // end in the next - so a shortcut pays nothing for it.
        insertNarrowHeader(row = 2)
        assertNotNull(add(cell(row = 2, col = 3)))
    }

    @Test
    fun `moving a gadget onto such a header row is refused`() = runTest {
        insertNarrowHeader(row = 4)
        val moving = add(gadget(row = 0, col = 3))!!
        assertFalse(repository.moveCell(moving, rowIndex = 3, colIndex = 3))
        assertEquals("the refused move must leave the cell where it was", 0, storedCell(moving)?.rowIndex)
    }

    @Test
    fun `growing a gadget down onto such a header row is refused`() = runTest {
        insertNarrowHeader(row = 3)
        val growing = add(gadget(row = 1, col = 3))!!
        assertFalse(repository.resizeCell(growing, spanW = 1, spanH = 3))
        assertEquals("the refused resize must keep the last valid size", 2, storedCell(growing)?.spanH)
    }

    // ── S1742: section block swapping ────────────────────────────────────────

    @Test
    fun `swapping adjacent section blocks exchanges position and preserves ownership and internal order`() = runTest {
        val secA = add(section(row = 0).copy(target = "sec:alpha"))!!
        val scA1 = add(cell(row = 0, col = 2, target = "app:a1"))!!
        val scA2 = add(cell(row = 1, col = 0, target = "app:a2"))!!

        val secB = add(section(row = 2).copy(target = "sec:beta"))!!
        val scB1 = add(cell(row = 2, col = 2, target = "app:b1"))!!
        val scB2 = add(cell(row = 3, col = 1, target = "app:b2"))!!

        val result = repository.swapSectionBlock(LauncherOrientation.PORTRAIT, secA, moveUp = false)
        assertTrue(result)

        assertEquals(0, storedCell(secB)?.rowIndex)
        assertEquals(0, storedCell(scB1)?.rowIndex)
        assertEquals(2, storedCell(scB1)?.colIndex)
        assertEquals(1, storedCell(scB2)?.rowIndex)
        assertEquals(1, storedCell(scB2)?.colIndex)

        assertEquals(2, storedCell(secA)?.rowIndex)
        assertEquals(2, storedCell(scA1)?.rowIndex)
        assertEquals(2, storedCell(scA1)?.colIndex)
        assertEquals(3, storedCell(scA2)?.rowIndex)
        assertEquals(0, storedCell(scA2)?.colIndex)

        val entities = dbRule.db.launcherCellDao().getAllCellsSync()
        val domainCells = entities.mapNotNull {
            val o = LauncherOrientation.entries.firstOrNull { e -> e.name == it.orientation }
            val k = LauncherCellKind.entries.firstOrNull { e -> e.name == it.kind }
            if (o != null && k != null) {
                LauncherCell(
                    id = it.id,
                    orientation = o,
                    rowIndex = it.rowIndex,
                    colIndex = it.colIndex,
                    spanW = it.spanW,
                    spanH = it.spanH,
                    kind = k,
                    target = it.target,
                    labelOverride = it.labelOverride,
                    addedAt = it.addedAt,
                )
            } else {
                null
            }
        }
        val sectionsInOrder = LauncherSectionMembership.sectionsInOrder(domainCells)

        val b1Cell = domainCells.first { it.id == scB1 }
        val b2Cell = domainCells.first { it.id == scB2 }
        val a1Cell = domainCells.first { it.id == scA1 }
        val a2Cell = domainCells.first { it.id == scA2 }

        assertEquals("sec:beta", LauncherSectionMembership.ownerOf(b1Cell, sectionsInOrder)?.target)
        assertEquals("sec:beta", LauncherSectionMembership.ownerOf(b2Cell, sectionsInOrder)?.target)
        assertEquals("sec:alpha", LauncherSectionMembership.ownerOf(a1Cell, sectionsInOrder)?.target)
        assertEquals("sec:alpha", LauncherSectionMembership.ownerOf(a2Cell, sectionsInOrder)?.target)
    }

    @Test
    fun `swapping past the first or last section is a no-op returning false`() = runTest {
        val secA = add(section(row = 0).copy(target = "sec:alpha"))!!
        val secB = add(section(row = 2).copy(target = "sec:beta"))!!

        assertFalse(repository.swapSectionBlock(LauncherOrientation.PORTRAIT, secA, moveUp = true))
        assertFalse(repository.swapSectionBlock(LauncherOrientation.PORTRAIT, secB, moveUp = false))

        assertEquals(0, storedCell(secA)?.rowIndex)
        assertEquals(2, storedCell(secB)?.rowIndex)
    }

    private companion object {
        /** Wide enough that no seeding call is refused for width alone. */
        const val COLUMNS = 8
    }
}
