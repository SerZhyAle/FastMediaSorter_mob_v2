package com.sza.fastmediasorter.domain.usecase.launcher

import com.sza.fastmediasorter.domain.model.launcher.InstalledApp
import com.sza.fastmediasorter.domain.model.launcher.LauncherCell
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellKind
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellPlacement
import com.sza.fastmediasorter.domain.model.launcher.LauncherOrientation
import com.sza.fastmediasorter.domain.repository.InstalledAppsRepository
import com.sza.fastmediasorter.domain.repository.LauncherDesktopRepository
import com.sza.fastmediasorter.domain.repository.LauncherDesktopState
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S2018: where the import puts what it imports, and in which order.
 *
 * All three properties are invisible to every static gate and to the compiler - a regression here still
 * builds and still passes detekt, and only shows itself as a hundred shortcuts scattered through
 * sections the user arranged by hand.
 */
class ImportSystemShortcutsUseCaseTest {

    private class FakeLauncherDesktopRepository : LauncherDesktopRepository {
        val sectionPlacements = mutableListOf<Pair<LauncherCell, String>>()
        val freeSlotPlacements = mutableListOf<LauncherCell>()
        private val cellsMap = mutableMapOf<LauncherOrientation, List<LauncherCell>>()
        private var nextId = 1L

        fun seed(orientation: LauncherOrientation, cells: List<LauncherCell>) {
            cellsMap[orientation] = cells
        }

        override fun observeCells(orientation: LauncherOrientation): Flow<List<LauncherCell>> =
            flowOf(cellsMap[orientation] ?: emptyList())

        override suspend fun addCell(cell: LauncherCell, columns: Int): LauncherCellPlacement =
            LauncherCellPlacement.Placed(nextId++)

        override suspend fun addCellInFirstFreeSlot(cell: LauncherCell, columns: Int): Long? {
            freeSlotPlacements.add(cell)
            cellsMap[cell.orientation] = (cellsMap[cell.orientation] ?: emptyList()) + cell
            return nextId++
        }

        override suspend fun addCellInSection(cell: LauncherCell, columns: Int, sectionKey: String): Long? {
            sectionPlacements.add(cell to sectionKey)
            cellsMap[cell.orientation] = (cellsMap[cell.orientation] ?: emptyList()) + cell
            return nextId++
        }

        override suspend fun removeCell(id: Long) = Unit
        override suspend fun normalizeSectionSpans() = Unit
        override suspend fun moveCell(id: Long, rowIndex: Int, colIndex: Int): Boolean = true
        override suspend fun resizeCell(id: Long, spanW: Int, spanH: Int): Boolean = true
        override suspend fun updateCellTarget(id: Long, target: String): Boolean = true
        override suspend fun seedIfEmpty(orientation: LauncherOrientation, cells: List<LauncherCell>): Boolean = true
        override suspend fun clearAll(): List<String> = emptyList()
        override suspend fun state(): LauncherDesktopState = LauncherDesktopState(
            seededPortrait = true,
            seededLandscape = true,
            columnsPortrait = COLUMNS_PORTRAIT,
            columnsLandscape = COLUMNS_LANDSCAPE,
        )

        override suspend fun updateCellLabel(id: Long, labelOverride: String?): Boolean = true
        override suspend fun swapSectionBlock(
            orientation: LauncherOrientation,
            sectionCellId: Long,
            moveUp: Boolean,
        ): Boolean = true

        override suspend fun updateColumns(orientation: LauncherOrientation, columns: Int) = Unit
    }

    private fun app(packageName: String, label: String) = InstalledApp(
        packageName = packageName,
        label = label,
        firstInstallTime = 0L,
        lastUpdateTime = 0L,
        category = 0,
        isSystemApp = false,
        iconFile = null,
    )

    private fun useCaseWith(
        desktop: LauncherDesktopRepository,
        apps: List<InstalledApp>,
    ): ImportSystemShortcutsUseCase = ImportSystemShortcutsUseCase(
        installedAppsRepository = mockk<InstalledAppsRepository> {
            every { observeApps() } returns flowOf(apps)
        },
        desktopRepository = desktop,
    )

    private fun sectionCell(orientation: LauncherOrientation) = LauncherCell(
        id = 99L,
        orientation = orientation,
        rowIndex = 0,
        colIndex = 0,
        spanW = 2,
        spanH = 1,
        kind = LauncherCellKind.SECTION,
        target = DESKTOP_SECTION_TARGET,
        labelOverride = null,
        addedAt = 0L,
    )

    @Test
    fun `every imported shortcut is addressed to the desktop section`() = runBlocking {
        val desktop = FakeLauncherDesktopRepository()

        useCaseWith(desktop, listOf(app("com.b", "Beta"), app("com.a", "Alpha")))()

        assertTrue(desktop.sectionPlacements.isNotEmpty())
        assertTrue(
            "no shortcut may be placed by the grid-wide fallback",
            desktop.sectionPlacements.all { it.second == LauncherCellCommand.SECTION_DESKTOP },
        )
        assertTrue(
            "the only free-slot placement allowed is the section header itself",
            desktop.freeSlotPlacements.all { it.kind == LauncherCellKind.SECTION },
        )
    }

    @Test
    fun `shortcuts are imported in alphabetical order`() = runBlocking {
        val desktop = FakeLauncherDesktopRepository()

        useCaseWith(
            desktop,
            listOf(app("com.c", "cherry"), app("com.a", "Apple"), app("com.b", "banana")),
        )()

        val portraitLabels = desktop.sectionPlacements
            .map { it.first }
            .filter { it.orientation == LauncherOrientation.PORTRAIT }
            .mapNotNull { it.labelOverride }
        // Case-insensitive, so a lowercase name is not sorted below every capitalised one.
        assertEquals(listOf("Apple", "banana", "cherry"), portraitLabels)
    }

    @Test
    fun `the desktop section header is created once per orientation`() = runBlocking {
        val desktop = FakeLauncherDesktopRepository()

        useCaseWith(desktop, listOf(app("com.a", "Alpha"), app("com.b", "Beta")))()

        val headers = desktop.freeSlotPlacements.filter { it.target == DESKTOP_SECTION_TARGET }
        assertEquals(2, headers.size)
        assertEquals(
            setOf(LauncherOrientation.PORTRAIT, LauncherOrientation.LANDSCAPE),
            headers.map { it.orientation }.toSet(),
        )
        // A null override is what lets the caption follow the device language via the section catalog.
        assertTrue(headers.all { it.labelOverride == null })
    }

    @Test
    fun `a second import reuses the existing header instead of stacking another`() = runBlocking {
        val desktop = FakeLauncherDesktopRepository()
        desktop.seed(LauncherOrientation.PORTRAIT, listOf(sectionCell(LauncherOrientation.PORTRAIT)))
        desktop.seed(LauncherOrientation.LANDSCAPE, listOf(sectionCell(LauncherOrientation.LANDSCAPE)))

        useCaseWith(desktop, listOf(app("com.a", "Alpha")))()

        assertTrue(
            "the fixed section key is what makes the previous run's header findable",
            desktop.freeSlotPlacements.none { it.target == DESKTOP_SECTION_TARGET },
        )
    }

    @Test
    fun `an app already on the desktop is not imported twice`() = runBlocking {
        val desktop = FakeLauncherDesktopRepository()
        val existing = LauncherCell(
            id = 7L,
            orientation = LauncherOrientation.PORTRAIT,
            rowIndex = 3,
            colIndex = 0,
            spanW = 1,
            spanH = 1,
            kind = LauncherCellKind.SHORTCUT,
            target = LauncherCellCommand.App("com.a").encode(),
            labelOverride = "Alpha",
            addedAt = 0L,
        )
        desktop.seed(LauncherOrientation.PORTRAIT, listOf(existing))

        useCaseWith(desktop, listOf(app("com.a", "Alpha")))()

        assertFalse(
            desktop.sectionPlacements.any {
                it.first.orientation == LauncherOrientation.PORTRAIT &&
                    it.first.target == existing.target
            },
        )
    }

    @Test
    fun `an empty app list reports failure and touches nothing`() = runBlocking {
        val desktop = FakeLauncherDesktopRepository()

        val result = useCaseWith(desktop, emptyList())()

        assertFalse(result)
        assertTrue(desktop.sectionPlacements.isEmpty())
        assertTrue(desktop.freeSlotPlacements.isEmpty())
    }

    private companion object {
        const val COLUMNS_PORTRAIT = 4
        const val COLUMNS_LANDSCAPE = 6
        val DESKTOP_SECTION_TARGET: String =
            LauncherCellCommand.Section(LauncherCellCommand.SECTION_DESKTOP).encode()
    }
}
