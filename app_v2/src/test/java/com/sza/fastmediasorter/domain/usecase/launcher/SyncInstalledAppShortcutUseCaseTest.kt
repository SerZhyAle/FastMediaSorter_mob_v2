package com.sza.fastmediasorter.domain.usecase.launcher

import com.sza.fastmediasorter.core.launcher.LauncherRoleManager
import com.sza.fastmediasorter.domain.launcher.LauncherModeContract
import com.sza.fastmediasorter.domain.model.launcher.LauncherCell
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellKind
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellOrigin
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellPlacement
import com.sza.fastmediasorter.domain.model.launcher.LauncherOrientation
import com.sza.fastmediasorter.domain.repository.LauncherDesktopRepository
import com.sza.fastmediasorter.domain.repository.LauncherDesktopState
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncInstalledAppShortcutUseCaseTest {

    @Test
    fun `install adds automatic first-screen cells in both orientations`() = runBlocking {
        val desktop = FakeDesktop()

        useCase(desktop, homeRoleHeld = true)(PACKAGE, SyncInstalledAppShortcutUseCase.Change.INSTALLED)

        assertEquals(
            setOf(LauncherOrientation.PORTRAIT, LauncherOrientation.LANDSCAPE),
            desktop.added.map { it.orientation }.toSet(),
        )
        assertTrue(desktop.added.all { it.origin == LauncherCellOrigin.AUTO_INSTALL && it.screenIndex == 0 })
    }

    @Test
    fun `inactive home role does not alter the desktop`() = runBlocking {
        val desktop = FakeDesktop()

        useCase(desktop, homeRoleHeld = false)(PACKAGE, SyncInstalledAppShortcutUseCase.Change.INSTALLED)

        assertTrue(desktop.added.isEmpty())
    }

    @Test
    fun `second install does not duplicate automatic cells`() = runBlocking {
        val desktop = FakeDesktop()
        val sync = useCase(desktop, homeRoleHeld = true)

        sync(PACKAGE, SyncInstalledAppShortcutUseCase.Change.INSTALLED)
        sync(PACKAGE, SyncInstalledAppShortcutUseCase.Change.INSTALLED)

        assertEquals(2, desktop.added.size)
    }

    @Test
    fun `removal keeps user-managed cell and removes automatic cell`() = runBlocking {
        val target = LauncherCellCommand.App(PACKAGE).encode()
        val desktop = FakeDesktop().apply {
            seed(
                LauncherOrientation.PORTRAIT,
                listOf(cell(1L, target, LauncherCellOrigin.AUTO_INSTALL), cell(2L, target, LauncherCellOrigin.USER)),
            )
        }

        useCase(desktop, homeRoleHeld = true)(PACKAGE, SyncInstalledAppShortcutUseCase.Change.REMOVED)

        assertEquals(listOf(1L), desktop.removed)
    }

    private fun useCase(desktop: LauncherDesktopRepository, homeRoleHeld: Boolean) = SyncInstalledAppShortcutUseCase(
        desktop = desktop,
        launcherMode = mockk<LauncherModeContract> { every { isAvailableInBuild } returns true },
        roleManager = mockk<LauncherRoleManager> { every { isHomeRoleHeld() } returns homeRoleHeld },
    )

    private class FakeDesktop : LauncherDesktopRepository {
        val added = mutableListOf<LauncherCell>()
        val removed = mutableListOf<Long>()
        private val cells = mutableMapOf<LauncherOrientation, List<LauncherCell>>()

        fun seed(orientation: LauncherOrientation, values: List<LauncherCell>) {
            cells[orientation] = values
        }

        override fun observeCells(orientation: LauncherOrientation): Flow<List<LauncherCell>> =
            flowOf(cells[orientation].orEmpty())

        override suspend fun addCellInFirstFreeSlot(cell: LauncherCell, columns: Int): Long? {
            added += cell
            cells[cell.orientation] = cells[cell.orientation].orEmpty() + cell
            return 1L
        }

        override suspend fun removeCell(id: Long) {
            removed += id
        }
        override suspend fun state() = LauncherDesktopState(true, true, 4, 6)
        override suspend fun addCell(cell: LauncherCell, columns: Int) = LauncherCellPlacement.Refused
        override suspend fun addCellInSection(cell: LauncherCell, columns: Int, sectionKey: String): Long? = null
        override suspend fun moveCellToScreen(
            orientation: LauncherOrientation,
            cellId: Long,
            screenIndex: Int,
            columns: Int,
        ) = false
        override suspend fun normalizeSectionSpans() = Unit
        override suspend fun moveCell(
            id: Long,
            rowIndex: Int,
            colIndex: Int,
            columns: Int,
            targetScreenIndex: Int?,
        ) = false
        override suspend fun resizeCell(id: Long, spanW: Int, spanH: Int, columns: Int) = false
        override suspend fun updateCellTarget(id: Long, target: String) = false
        override suspend fun updateCellLabel(id: Long, labelOverride: String?) = false
        override suspend fun swapSectionBlock(
            orientation: LauncherOrientation,
            sectionCellId: Long,
            moveUp: Boolean,
        ) = false
        override suspend fun relocateSectionBlock(
            orientation: LauncherOrientation,
            sectionCellId: Long,
            targetRow: Int,
        ) = false
        override suspend fun removeSection(orientation: LauncherOrientation, sectionCellId: Long) =
            emptyList<String>()
        override suspend fun resortSection(
            orientation: LauncherOrientation,
            sectionCellId: Long,
            columns: Int,
        ) = false
        override suspend fun seedIfEmpty(orientation: LauncherOrientation, cells: List<LauncherCell>) = false
        override suspend fun clearAll() = emptyList<String>()
        override suspend fun updateColumns(orientation: LauncherOrientation, columns: Int) = Unit
    }

    private fun cell(id: Long, target: String, origin: LauncherCellOrigin) = LauncherCell(
        id = id,
        orientation = LauncherOrientation.PORTRAIT,
        rowIndex = 0,
        colIndex = 0,
        spanW = 1,
        spanH = 1,
        kind = LauncherCellKind.SHORTCUT,
        target = target,
        labelOverride = null,
        addedAt = 0,
        origin = origin,
    )

    private companion object { const val PACKAGE = "com.example.app" }
}
