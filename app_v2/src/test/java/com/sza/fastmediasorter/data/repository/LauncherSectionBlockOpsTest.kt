package com.sza.fastmediasorter.data.repository

import com.sza.fastmediasorter.data.local.db.LauncherCellEntity
import com.sza.fastmediasorter.domain.model.launcher.LauncherCell
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellKind
import com.sza.fastmediasorter.domain.model.launcher.LauncherOrientation
import com.sza.fastmediasorter.domain.model.launcher.LauncherSectionMembership
import com.sza.fastmediasorter.testing.InMemoryRoomRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * S1742 / S2018 / S2217: the desktop operations that move, delete or re-pack a whole SECTION BLOCK -
 * swap, remove, resort, and the clear-all that reports every target it deleted.
 *
 * Split out of [LauncherDesktopRepositoryImplTest] (S2599), which had grown past the LargeClass
 * ceiling. The seam is the subject, not the size: that class covers where ONE cell may sit, this one
 * covers what happens to a band of rows as a unit, and the two share only the seeding helpers below.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LauncherSectionBlockOpsTest {

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

    @Test
    fun `deleting a section removes its header and content and returns their targets`() = runTest {
        val dao = dbRule.db.launcherCellDao()
        val header = dao.upsert(entity(LauncherOrientation.PORTRAIT, LauncherCellKind.SECTION, "sec:alpha", spanW = 2))
        val childOnHeaderRow = dao.upsert(
            entity(LauncherOrientation.PORTRAIT, LauncherCellKind.SHORTCUT, "app:a", col = 2)
        )
        val childBelow = dao.upsert(
            entity(LauncherOrientation.PORTRAIT, LauncherCellKind.GADGET, GADGET_TARGET, row = 1, spanH = 2)
        )
        val nextHeader = dao.upsert(
            entity(LauncherOrientation.PORTRAIT, LauncherCellKind.SECTION, "sec:beta", row = 3, spanW = 2)
        )

        val targets = repository.removeSection(LauncherOrientation.PORTRAIT, header)

        assertEquals(setOf("sec:alpha", "app:a", GADGET_TARGET), targets.toSet())
        assertNull(storedCell(header))
        assertNull(storedCell(childOnHeaderRow))
        assertNull(storedCell(childBelow))
        assertEquals(0, storedCell(nextHeader)?.rowIndex)
    }

    @Test
    fun `deleting a section pulls the rows below up by the band height`() = runTest {
        val dao = dbRule.db.launcherCellDao()
        val header = dao.upsert(
            entity(LauncherOrientation.PORTRAIT, LauncherCellKind.SECTION, "sec:alpha", row = 1, spanW = 2)
        )
        dao.upsert(entity(LauncherOrientation.PORTRAIT, LauncherCellKind.SHORTCUT, "app:a", row = 2))
        val nextHeader = dao.upsert(
            entity(LauncherOrientation.PORTRAIT, LauncherCellKind.SECTION, "sec:beta", row = 4, spanW = 2)
        )

        repository.removeSection(LauncherOrientation.PORTRAIT, header)

        assertEquals(1, storedCell(nextHeader)?.rowIndex)
    }

    @Test
    fun `deleting a section that runs to the bottom leaves nothing to shift`() = runTest {
        val dao = dbRule.db.launcherCellDao()
        val header = dao.upsert(entity(LauncherOrientation.PORTRAIT, LauncherCellKind.SECTION, "sec:alpha", spanW = 2))
        dao.upsert(entity(LauncherOrientation.PORTRAIT, LauncherCellKind.SHORTCUT, "app:a", row = 1))

        repository.removeSection(LauncherOrientation.PORTRAIT, header)

        assertEquals(0, dao.countByOrientation(LauncherOrientation.PORTRAIT.name))
    }

    @Test
    fun `deleting one of two headers sharing a row keeps the co-section and compacts the band`() = runTest {
        val dao = dbRule.db.launcherCellDao()
        val leftHeader = dao.upsert(
            entity(LauncherOrientation.PORTRAIT, LauncherCellKind.SECTION, "sec:left", spanW = 2)
        )
        dao.upsert(entity(LauncherOrientation.PORTRAIT, LauncherCellKind.SHORTCUT, "app:left", col = 2))
        val rightHeader = dao.upsert(
            entity(LauncherOrientation.PORTRAIT, LauncherCellKind.SECTION, "sec:right", col = 4, spanW = 2)
        )
        val rightChild = dao.upsert(
            entity(LauncherOrientation.PORTRAIT, LauncherCellKind.SHORTCUT, "app:right", row = 1)
        )

        repository.removeSection(LauncherOrientation.PORTRAIT, leftHeader)

        assertEquals(0, storedCell(rightHeader)?.rowIndex)
        assertEquals(1, storedCell(rightChild)?.rowIndex)
    }

    @Test
    fun `deleting an unknown or non-section id returns an empty list`() = runTest {
        val nonSection = add(cell(row = 0, col = 0))!!

        assertTrue(repository.removeSection(LauncherOrientation.PORTRAIT, nonSection).isEmpty())
        assertTrue(repository.removeSection(LauncherOrientation.PORTRAIT, Long.MAX_VALUE).isEmpty())
    }

    @Test
    fun `resorting packs scattered children densely after the header`() = runTest {
        val dao = dbRule.db.launcherCellDao()
        val header = dao.upsert(entity(LauncherOrientation.PORTRAIT, LauncherCellKind.SECTION, "sec:alpha", spanW = 2))
        val first = dao.upsert(
            entity(LauncherOrientation.PORTRAIT, LauncherCellKind.SHORTCUT, "app:first", row = 2, col = 3)
        )
        val second = dao.upsert(
            entity(LauncherOrientation.PORTRAIT, LauncherCellKind.SHORTCUT, "app:second", row = 3, col = 3)
        )
        val nextHeader = dao.upsert(
            entity(LauncherOrientation.PORTRAIT, LauncherCellKind.SECTION, "sec:beta", row = 5, spanW = 2)
        )

        assertTrue(repository.resortSection(LauncherOrientation.PORTRAIT, header, columns = COLUMNS))

        assertEquals(0, storedCell(first)?.rowIndex)
        assertEquals(2, storedCell(first)?.colIndex)
        assertEquals(0, storedCell(second)?.rowIndex)
        assertEquals(3, storedCell(second)?.colIndex)
        assertEquals(1, storedCell(nextHeader)?.rowIndex)
    }

    @Test
    fun `resorting pulls the rows below up when the section shrinks`() = runTest {
        val dao = dbRule.db.launcherCellDao()
        val header = dao.upsert(entity(LauncherOrientation.PORTRAIT, LauncherCellKind.SECTION, "sec:alpha", spanW = 2))
        dao.upsert(entity(LauncherOrientation.PORTRAIT, LauncherCellKind.SHORTCUT, "app:a", row = 3, col = 3))
        val nextHeader = dao.upsert(
            entity(LauncherOrientation.PORTRAIT, LauncherCellKind.SECTION, "sec:beta", row = 4, spanW = 2)
        )

        assertTrue(repository.resortSection(LauncherOrientation.PORTRAIT, header, columns = COLUMNS))

        assertEquals(1, storedCell(nextHeader)?.rowIndex)
    }

    @Test
    fun `resorting pushes the rows below down when the section grows`() = runTest {
        val dao = dbRule.db.launcherCellDao()
        val header = dao.upsert(entity(LauncherOrientation.PORTRAIT, LauncherCellKind.SECTION, "sec:alpha", spanW = 2))
        dao.upsert(entity(LauncherOrientation.PORTRAIT, LauncherCellKind.SHORTCUT, "app:a", col = 2))
        dao.upsert(entity(LauncherOrientation.PORTRAIT, LauncherCellKind.SHORTCUT, "app:b", col = 3))
        val nextHeader = dao.upsert(
            entity(LauncherOrientation.PORTRAIT, LauncherCellKind.SECTION, "sec:beta", row = 1, spanW = 2)
        )

        assertTrue(repository.resortSection(LauncherOrientation.PORTRAIT, header, columns = 3))

        assertEquals(2, storedCell(nextHeader)?.rowIndex)
    }

    @Test
    fun `resorting keeps a tall gadget inside the section band`() = runTest {
        val dao = dbRule.db.launcherCellDao()
        val header = dao.upsert(entity(LauncherOrientation.PORTRAIT, LauncherCellKind.SECTION, "sec:alpha", spanW = 2))
        val tallGadget = dao.upsert(
            entity(
                LauncherOrientation.PORTRAIT,
                LauncherCellKind.GADGET,
                GADGET_TARGET,
                row = 2,
                col = 2,
                spanW = 3,
                spanH = 2
            ),
        )
        val nextHeader = dao.upsert(
            entity(LauncherOrientation.PORTRAIT, LauncherCellKind.SECTION, "sec:beta", row = 4, spanW = 2)
        )

        assertTrue(repository.resortSection(LauncherOrientation.PORTRAIT, header, columns = 4))

        val stored = storedCell(tallGadget)
        assertEquals(1, stored?.rowIndex)
        assertTrue((stored?.rowIndex ?: 0) + (stored?.spanH ?: 0) <= (storedCell(nextHeader)?.rowIndex ?: 0))
    }

    @Test
    fun `resorting an empty section or unknown id returns false`() = runTest {
        val dao = dbRule.db.launcherCellDao()
        val header = dao.upsert(entity(LauncherOrientation.PORTRAIT, LauncherCellKind.SECTION, "sec:alpha", spanW = 2))

        assertFalse(repository.resortSection(LauncherOrientation.PORTRAIT, header, columns = COLUMNS))
        assertFalse(repository.resortSection(LauncherOrientation.PORTRAIT, Long.MAX_VALUE, columns = COLUMNS))
    }

    /**
     * S2217: the reset owes each deleted configured widget cell an instance cleanup it can only
     * perform while it still knows the cell's target, so the delete hands those columns back. A
     * dropped or invented target here means a leaked or wrongly cleared widget instance.
     */
    @Test
    fun `clearAll returns every deleted target of both orientations and empties the table`() = runTest {
        val dao = dbRule.db.launcherCellDao()
        listOf(
            entity(orientation = LauncherOrientation.PORTRAIT, kind = LauncherCellKind.GADGET, target = GADGET_TARGET),
            entity(
                orientation = LauncherOrientation.PORTRAIT,
                kind = LauncherCellKind.SHORTCUT,
                target = "app:com.example"
            ),
            entity(
                orientation = LauncherOrientation.LANDSCAPE,
                kind = LauncherCellKind.GADGET,
                target = "app:com.other"
            ),
        ).forEach { dao.upsert(it) }

        val targets = repository.clearAll()

        assertEquals(setOf(GADGET_TARGET, "app:com.example", "app:com.other"), targets.toSet())
        assertEquals(0, dao.countByOrientation(LauncherOrientation.PORTRAIT.name))
        assertEquals(0, dao.countByOrientation(LauncherOrientation.LANDSCAPE.name))
    }
    private fun entity(
        orientation: LauncherOrientation,
        kind: LauncherCellKind,
        target: String,
        row: Int = 0,
        col: Int = 0,
        spanW: Int = 1,
        spanH: Int = 1,
    ) = LauncherCellEntity(
        id = 0,
        orientation = orientation.name,
        rowIndex = row,
        colIndex = col,
        spanW = spanW,
        spanH = spanH,
        kind = kind.name,
        target = target,
        labelOverride = null,
        addedAt = 0L,
    )

    private fun cell(
        row: Int,
        col: Int,
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
    )

    private fun section(row: Int) =
        cell(row = row, col = 0, target = "sec:app_functions").copy(kind = LauncherCellKind.SECTION)

    private suspend fun add(cell: LauncherCell): Long? =
        repository.addCell(cell, columns = COLUMNS).idOrNull

    private suspend fun storedCell(id: Long) = dbRule.db.launcherCellDao().getById(id)

    private companion object {
        /** Wide enough that no seeding call is refused for width alone. */
        const val COLUMNS = 8

        /** A configured gadget cell's target carries its instance token as the param (S1930 codec). */
        const val GADGET_TARGET = "gadget:random_photo_frame/-1000001"
    }
}
