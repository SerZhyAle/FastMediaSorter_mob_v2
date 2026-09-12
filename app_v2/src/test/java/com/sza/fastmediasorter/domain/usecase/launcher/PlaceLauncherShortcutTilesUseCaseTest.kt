package com.sza.fastmediasorter.domain.usecase.launcher

import com.sza.fastmediasorter.domain.model.launcher.LauncherCell
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellKind
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellPlacement
import com.sza.fastmediasorter.domain.model.launcher.LauncherOrientation
import com.sza.fastmediasorter.domain.repository.LauncherDesktopRepository
import com.sza.fastmediasorter.domain.repository.LauncherDesktopState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * S2859: pins the placement contract every caller of the Resources-section tile path inherits -
 * both orientations, no duplicates, section first with the free-slot scan as the only fallback,
 * and a failed pass that leaves the desktop untouched and reports false.
 */
class PlaceLauncherShortcutTilesUseCaseTest {

    private class FakeLauncherDesktopRepository(
        private val sectionExists: Boolean = true,
    ) : LauncherDesktopRepository {
        val sectionPlacements = mutableListOf<LauncherCell>()
        val freeSlotPlacements = mutableListOf<LauncherCell>()
        private val cellsMap = mutableMapOf<LauncherOrientation, List<LauncherCell>>()
        var failNextWrite: Boolean = false

        fun seedExisting(orientation: LauncherOrientation, cells: List<LauncherCell>) {
            cellsMap[orientation] = cells
        }

        override fun observeCells(orientation: LauncherOrientation): Flow<List<LauncherCell>> =
            flowOf(cellsMap[orientation] ?: emptyList())

        override suspend fun addCell(cell: LauncherCell, columns: Int): LauncherCellPlacement =
            LauncherCellPlacement.Placed(1L)

        override suspend fun addCellInFirstFreeSlot(cell: LauncherCell, columns: Int): Long? {
            if (failNextWrite) throw IOException("disk full")
            freeSlotPlacements.add(cell)
            remember(cell)
            return 1L
        }

        override suspend fun addCellInSection(cell: LauncherCell, columns: Int, sectionKey: String): Long? {
            if (!sectionExists) return null
            if (failNextWrite) throw IOException("disk full")
            sectionPlacements.add(cell)
            remember(cell)
            return 1L
        }

        private fun remember(cell: LauncherCell) {
            cellsMap[cell.orientation] = (cellsMap[cell.orientation] ?: emptyList()) + cell
        }

        override suspend fun removeCell(id: Long) = Unit
        override suspend fun moveCellToScreen(
            orientation: LauncherOrientation,
            cellId: Long,
            screenIndex: Int,
            columns: Int,
        ): Boolean = false
        override suspend fun normalizeSectionSpans() = Unit
        override suspend fun moveCell(id: Long, rowIndex: Int, colIndex: Int, columns: Int): Boolean = true
        override suspend fun resizeCell(id: Long, spanW: Int, spanH: Int, columns: Int): Boolean = true
        override suspend fun updateCellTarget(id: Long, target: String): Boolean = true
        override suspend fun seedIfEmpty(orientation: LauncherOrientation, cells: List<LauncherCell>): Boolean = true
        override suspend fun clearAll(): List<String> = emptyList()
        override suspend fun state(): LauncherDesktopState = LauncherDesktopState(
            seededPortrait = true,
            seededLandscape = true,
            columnsPortrait = 4,
            columnsLandscape = 6,
        )
        override suspend fun updateCellLabel(id: Long, labelOverride: String?): Boolean = true
        override suspend fun swapSectionBlock(
            orientation: LauncherOrientation,
            sectionCellId: Long,
            moveUp: Boolean,
        ): Boolean = true

        override suspend fun removeSection(
            orientation: LauncherOrientation,
            sectionCellId: Long,
        ): List<String> = emptyList()

        override suspend fun resortSection(
            orientation: LauncherOrientation,
            sectionCellId: Long,
            columns: Int,
        ): Boolean = false

        override suspend fun updateColumns(orientation: LauncherOrientation, columns: Int) = Unit
    }

    @Test
    fun `a missing target lands in the Resources section of both orientations`() = runBlocking {
        val desktop = FakeLauncherDesktopRepository()

        val placed = useCaseWith(desktop)(listOf(TARGET_ADD_RESOURCE))

        assertTrue(placed)
        assertEquals(
            setOf(LauncherOrientation.PORTRAIT, LauncherOrientation.LANDSCAPE),
            desktop.sectionPlacements.mapTo(mutableSetOf()) { it.orientation },
        )
        assertTrue(desktop.sectionPlacements.all { it.target == TARGET_ADD_RESOURCE })
        assertTrue(desktop.sectionPlacements.all { it.kind == LauncherCellKind.SHORTCUT })
    }

    @Test
    fun `a target the desktop already carries is not placed twice`() = runBlocking {
        val desktop = FakeLauncherDesktopRepository()
        desktop.seedExisting(LauncherOrientation.PORTRAIT, listOf(cellOf(LauncherOrientation.PORTRAIT)))

        val placed = useCaseWith(desktop)(listOf(TARGET_ADD_RESOURCE))

        assertTrue(placed)
        assertEquals(listOf(LauncherOrientation.LANDSCAPE), desktop.sectionPlacements.map { it.orientation })
    }

    // S2564 strategic ADR-5: the section is tried first, and the whole-grid scan is reached only
    // when this desktop carries no resources header at all.
    @Test
    fun `the free slot scan is used only when the section placement refuses`() = runBlocking {
        val desktop = FakeLauncherDesktopRepository(sectionExists = false)

        val placed = useCaseWith(desktop)(listOf(TARGET_ADD_RESOURCE))

        assertTrue(placed)
        assertTrue(desktop.sectionPlacements.isEmpty())
        assertEquals(2, desktop.freeSlotPlacements.size)
    }

    // Strategic S2859 ADR-5: a failed placement reports false so a caller keeping its own baseline
    // leaves the accounting untouched and retries on the next pass.
    @Test
    fun `a repository throw leaves the desktop unchanged and reports false`() = runBlocking {
        val desktop = FakeLauncherDesktopRepository()
        desktop.failNextWrite = true

        val placed = useCaseWith(desktop)(listOf(TARGET_ADD_RESOURCE))

        assertFalse(placed)
        assertTrue(desktop.sectionPlacements.isEmpty())
        assertTrue(desktop.freeSlotPlacements.isEmpty())
    }

    @Test
    fun `an empty target set reports success without touching the desktop`() = runBlocking {
        val desktop = FakeLauncherDesktopRepository()

        val placed = useCaseWith(desktop)(emptyList())

        assertTrue(placed)
        assertTrue(desktop.sectionPlacements.isEmpty())
        assertTrue(desktop.freeSlotPlacements.isEmpty())
    }

    private fun useCaseWith(desktop: LauncherDesktopRepository) =
        PlaceLauncherShortcutTilesUseCase(desktop, ResolveLauncherColumnsUseCase(desktop))

    private fun cellOf(orientation: LauncherOrientation): LauncherCell = LauncherCell(
        id = 7L,
        orientation = orientation,
        rowIndex = 0,
        colIndex = 0,
        spanW = 1,
        spanH = 1,
        kind = LauncherCellKind.SHORTCUT,
        target = TARGET_ADD_RESOURCE,
        labelOverride = null,
        addedAt = 0L,
    )

    private companion object {
        val TARGET_ADD_RESOURCE: String =
            LauncherCellCommand.LauncherAction("create_resource").encode()
    }
}
