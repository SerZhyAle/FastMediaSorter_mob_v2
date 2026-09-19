package com.sza.fastmediasorter.domain.usecase.launcher

import com.sza.fastmediasorter.core.panel.SubProgramCatalog
import com.sza.fastmediasorter.core.panel.SubProgramSurface
import com.sza.fastmediasorter.domain.model.launcher.LauncherCell
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellKind
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellPlacement
import com.sza.fastmediasorter.domain.model.launcher.LauncherOrientation
import com.sza.fastmediasorter.domain.repository.LauncherDesktopRepository
import com.sza.fastmediasorter.domain.repository.LauncherDesktopState
import com.sza.fastmediasorter.domain.repository.LauncherShortcutSyncRepository
import com.sza.fastmediasorter.domain.usecase.panel.ResolvePanelRouteAvailabilityUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncEnabledToolShortcutsUseCaseTest {

    private class FakeLauncherDesktopRepository : LauncherDesktopRepository {
        val addedCells = mutableListOf<Pair<LauncherCell, Int>>()
        private val cellsMap = mutableMapOf<LauncherOrientation, List<LauncherCell>>()

        override fun observeCells(orientation: LauncherOrientation): Flow<List<LauncherCell>> =
            flowOf(cellsMap[orientation] ?: emptyList())

        override suspend fun addCell(cell: LauncherCell, columns: Int): LauncherCellPlacement =
            LauncherCellPlacement.Placed(1L)

        private var nextId = 1L

        override suspend fun addCellInFirstFreeSlot(cell: LauncherCell, columns: Int): Long? {
            addedCells.add(cell to columns)
            val id = nextId++
            val current = cellsMap[cell.orientation] ?: emptyList()
            cellsMap[cell.orientation] = current + cell.copy(id = id)
            return id
        }

        val sectionPlacements = mutableListOf<Pair<LauncherCell, String>>()

        override suspend fun addCellInSection(cell: LauncherCell, columns: Int, sectionKey: String): Long? {
            sectionPlacements.add(cell to sectionKey)
            return addCellInFirstFreeSlot(cell, columns)
        }

        val removedIds = mutableListOf<Long>()

        override suspend fun removeCell(id: Long) {
            removedIds.add(id)
        }

        override suspend fun moveCellToScreen(
            orientation: LauncherOrientation,
            cellId: Long,
            screenIndex: Int,
            columns: Int,
        ): Boolean = false
        override suspend fun normalizeSectionSpans() = Unit
        override suspend fun moveCell(
            id: Long,
            rowIndex: Int,
            colIndex: Int,
            columns: Int,
            targetScreenIndex: Int?,
        ): Boolean = true
        override suspend fun resizeCell(id: Long, spanW: Int, spanH: Int, columns: Int): Boolean = true
        override suspend fun updateCellTarget(id: Long, target: String): Boolean = true
        override suspend fun seedIfEmpty(orientation: LauncherOrientation, cells: List<LauncherCell>): Boolean = true
        override suspend fun clearAll(): List<String> = emptyList()
        var storedLandscapeColumns = 6

        override suspend fun state(): LauncherDesktopState = LauncherDesktopState(
            seededPortrait = true,
            seededLandscape = true,
            columnsPortrait = 4,
            columnsLandscape = storedLandscapeColumns,
        )
        override suspend fun updateCellLabel(id: Long, labelOverride: String?): Boolean = true
        override suspend fun swapSectionBlock(
            orientation: LauncherOrientation,
            sectionCellId: Long,
            moveUp: Boolean,
        ): Boolean = true

        override suspend fun relocateSectionBlock(
            orientation: LauncherOrientation,
            sectionCellId: Long,
            targetRow: Int,
        ): Boolean = false

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

    private class FakeLauncherShortcutSyncRepository(
        var routes: Set<String>?,
    ) : LauncherShortcutSyncRepository {
        var stopwatchShortcutBackfilled = false
        override suspend fun syncedRoutes(): Set<String>? = routes

        override suspend fun setSyncedRoutes(routeKeys: Set<String>) {
            routes = routeKeys
        }

        override suspend fun clearSyncedRoutes() {
            routes = null
        }

        override suspend fun isStopwatchShortcutBackfilled(): Boolean = stopwatchShortcutBackfilled

        override suspend fun setStopwatchShortcutBackfilled() {
            stopwatchShortcutBackfilled = true
        }

        // S2564: the resource baseline shares this repository but no route test reads it, so the
        // three members answer for the contract without keeping state this file would never assert.
        override suspend fun syncedResourcePaths(): Set<String>? = null

        override suspend fun setSyncedResourcePaths(paths: Set<String>) = Unit

        override suspend fun clearSyncedResourcePaths() = Unit

        // S2859: the Add-resource tile flag shares the same store; no route test reads it either.
        override suspend fun isResourcesAddTileBackfilled(): Boolean = false

        override suspend fun setResourcesAddTileBackfilled() = Unit

        override suspend fun clearResourcesAddTileBackfilled() = Unit
    }

    /**
     * S1736: the use case no longer reads settings itself - it asks the availability chain, the one
     * place that folds the build axis and the user axis together. [enabled] names the routes that
     * answer launchable; every other registry route answers compiled-out and switched-off.
     *
     * S2330: [baseline] defaults to present-and-empty rather than to absent, because that is the
     * state in which the transition rule and the old reconcile rule agree - which is what lets the
     * six tests above go on pinning the S1736 contract without their bodies moving.
     */
    private fun useCaseWith(
        desktop: LauncherDesktopRepository,
        enabled: Set<String>,
        baseline: FakeLauncherShortcutSyncRepository = FakeLauncherShortcutSyncRepository(emptySet()),
    ): SyncEnabledToolShortcutsUseCase = SyncEnabledToolShortcutsUseCase(
        desktop = desktop,
        resolveRouteAvailability = mockk<ResolvePanelRouteAvailabilityUseCase> {
            coEvery { all() } returns SubProgramCatalog.all().associate { entry ->
                val on = entry.routeKey in enabled
                entry.routeKey to ResolvePanelRouteAvailabilityUseCase.Availability(
                    availableInBuild = on,
                    enabledAtRuntime = on,
                )
            }
        },
        syncBaseline = baseline,
        resolveColumns = ResolveLauncherColumnsUseCase(desktop),
    )

    private fun launcherEntryKeys(): List<String> =
        SubProgramCatalog.forSurface(SubProgramSurface.LAUNCHER_SHORTCUT).map { it.routeKey }

    private fun cellAt(orientation: LauncherOrientation, target: String): LauncherCell = LauncherCell(
        id = 1,
        orientation = orientation,
        rowIndex = 0,
        colIndex = 0,
        spanW = 1,
        spanH = 1,
        kind = LauncherCellKind.SHORTCUT,
        target = target,
        labelOverride = null,
        addedAt = 0,
    )

    @Test
    fun `adds calculator shortcut when calculator is enabled`() = runBlocking {
        val desktopRepo = FakeLauncherDesktopRepository()

        useCaseWith(desktopRepo, setOf(CALCULATOR_KEY))()

        assertTrue(desktopRepo.addedCells.any { it.first.target == CALCULATOR_TARGET })
    }

    @Test
    fun `does not add duplicate calculator shortcut if already present`() = runBlocking {
        val desktopRepo = FakeLauncherDesktopRepository()
        desktopRepo.addCellInFirstFreeSlot(cellAt(LauncherOrientation.PORTRAIT, CALCULATOR_TARGET), COLUMNS)
        desktopRepo.addedCells.clear()

        useCaseWith(desktopRepo, setOf(CALCULATOR_KEY))()

        assertTrue(
            desktopRepo.addedCells.none {
                it.first.target == CALCULATOR_TARGET && it.first.orientation == LauncherOrientation.PORTRAIT
            },
        )
    }

    @Test
    fun `a newly enabled entry is added to both orientations`() = runBlocking {
        val desktopRepo = FakeLauncherDesktopRepository()

        useCaseWith(desktopRepo, setOf(CALCULATOR_KEY))()

        val orientations = desktopRepo.addedCells
            .filter { it.first.target == CALCULATOR_TARGET }
            .map { it.first.orientation }
            .toSet()
        assertEquals(
            "an enabled program must reach both desktops, not only the one in use",
            setOf(LauncherOrientation.PORTRAIT, LauncherOrientation.LANDSCAPE),
            orientations,
        )
    }

    @Test
    fun `an entry present in one orientation is still added to the other`() = runBlocking {
        val desktopRepo = FakeLauncherDesktopRepository()
        desktopRepo.addCellInFirstFreeSlot(cellAt(LauncherOrientation.PORTRAIT, CALCULATOR_TARGET), COLUMNS)
        desktopRepo.addedCells.clear()

        useCaseWith(desktopRepo, setOf(CALCULATOR_KEY))()

        val orientations = desktopRepo.addedCells
            .filter { it.first.target == CALCULATOR_TARGET }
            .map { it.first.orientation }
        assertEquals(
            "the orientation that already had the cell must not block the one that did not",
            listOf(LauncherOrientation.LANDSCAPE),
            orientations,
        )
    }

    @Test
    fun `a disabled entry is not added at all`() = runBlocking {
        val desktopRepo = FakeLauncherDesktopRepository()

        useCaseWith(desktopRepo, enabled = emptySet())()

        assertEquals(
            "a program nobody switched on reached the desktop anyway",
            emptyList<String>(),
            desktopRepo.addedCells.map { it.first.target },
        )
    }

    @Test
    fun `every launcher shortcut entry gets a cell once it is enabled`() = runBlocking {
        val keys = launcherEntryKeys()
        val desktopRepo = FakeLauncherDesktopRepository()

        useCaseWith(desktopRepo, keys.toSet())()

        val placed = desktopRepo.addedCells.map { it.first.target }.toSet()
        val missing = keys.filterNot { "fn:$it" in placed }
        assertEquals("registry entries that reached no desktop cell: $missing", emptyList<String>(), missing)
    }

    // S2330 strategic 11 criterion 2. Eight of the nine registry routes missing from the starter set
    // are launchable by default, so an install that meets this mechanism for the first time must be
    // adopted, not corrected - otherwise an update alone hands the user those eight cells.
    @Test
    fun `an absent baseline places nothing and adopts the launchable set`() = runBlocking {
        val desktopRepo = FakeLauncherDesktopRepository()
        val baseline = FakeLauncherShortcutSyncRepository(routes = null)

        useCaseWith(desktopRepo, setOf(CALCULATOR_KEY), baseline)()

        assertEquals(
            "an install that never ran the sync was handed cells anyway",
            emptyList<String>(),
            desktopRepo.addedCells.map { it.first.target },
        )
        assertEquals(setOf(CALCULATOR_KEY), baseline.routes)
    }

    // S2330 strategic 11 criterion 3: the comparison is against the baseline, never against the
    // desktop, so a cell the user deleted by hand stays deleted.
    @Test
    fun `a route re-enabled after its cell was deleted by hand gets the cell back`() = runBlocking {
        val desktopRepo = FakeLauncherDesktopRepository()
        // The route left the launchable set, so the baseline no longer holds it - which is what makes
        // its return an appearance rather than a no-op. The desktop carries no cell for it: the user
        // deleted the one the sync placed.
        val baseline = FakeLauncherShortcutSyncRepository(emptySet())

        useCaseWith(desktopRepo, setOf(CALCULATOR_KEY), baseline)()

        assertTrue(
            "S2664 ADR-1: presence follows the toggle, so the cell comes back whatever the reason it was gone",
            desktopRepo.addedCells.any { it.first.target == CALCULATOR_TARGET },
        )
    }

    // S2330 strategic 11 criterion 1 - the ticket's whole point: a tool switched on after seeding
    // reaches both desktops and is recorded so the next pass leaves it alone.
    @Test
    fun `a route missing from the baseline is placed on both orientations and joins it`() = runBlocking {
        val desktopRepo = FakeLauncherDesktopRepository()
        val baseline = FakeLauncherShortcutSyncRepository(emptySet())

        useCaseWith(desktopRepo, setOf(CALCULATOR_KEY), baseline)()

        val orientations = desktopRepo.addedCells
            .filter { it.first.target == CALCULATOR_TARGET }
            .map { it.first.orientation }
            .toSet()
        assertEquals(
            setOf(LauncherOrientation.PORTRAIT, LauncherOrientation.LANDSCAPE),
            orientations,
        )
        assertTrue("the placed route did not join the baseline", CALCULATOR_KEY in baseline.routes.orEmpty())
    }

    // S2664 strategic 11 criteria 6 and 7, ADR-1: switching a program off takes its cell away in both
    // orientations, and the baseline shrinks so the next switch-on registers as an appearance.
    @Test
    fun `a route that stops being launchable loses its cell in both orientations`() = runBlocking {
        val desktopRepo = FakeLauncherDesktopRepository()
        desktopRepo.addCellInFirstFreeSlot(cellAt(LauncherOrientation.PORTRAIT, CALCULATOR_TARGET), COLUMNS)
        desktopRepo.addCellInFirstFreeSlot(cellAt(LauncherOrientation.LANDSCAPE, CALCULATOR_TARGET), COLUMNS)
        desktopRepo.addedCells.clear()
        val baseline = FakeLauncherShortcutSyncRepository(setOf(CALCULATOR_KEY))

        useCaseWith(desktopRepo, enabled = emptySet(), baseline = baseline)()

        assertEquals("both orientations should have lost the cell", 2, desktopRepo.removedIds.size)
        assertEquals(emptySet<String>(), baseline.routes)
    }

    @Test
    fun `a new shortcut is seated inside the app-functions section`() = runBlocking {
        val desktopRepo = FakeLauncherDesktopRepository()

        useCaseWith(desktopRepo, setOf(CALCULATOR_KEY))()

        val sections = desktopRepo.sectionPlacements
            .filter { it.first.target == CALCULATOR_TARGET }
            .map { it.second }
            .toSet()
        assertEquals(setOf(LauncherCellCommand.SECTION_APP_FUNCTIONS), sections)
    }

    @Test
    fun `an unchanged launchable set writes and removes nothing`() = runBlocking {
        val desktopRepo = FakeLauncherDesktopRepository()
        val baseline = FakeLauncherShortcutSyncRepository(setOf(CALCULATOR_KEY))

        useCaseWith(desktopRepo, setOf(CALCULATOR_KEY), baseline)()

        assertEquals(emptyList<String>(), desktopRepo.addedCells.map { it.first.target })
        assertEquals(emptyList<Long>(), desktopRepo.removedIds)
    }

    @Test
    fun `backfills Stopwatch once when the old baseline already contains it`() = runBlocking {
        val desktopRepo = FakeLauncherDesktopRepository()
        val baseline = FakeLauncherShortcutSyncRepository(setOf(STOPWATCH_KEY))

        useCaseWith(desktopRepo, setOf(STOPWATCH_KEY), baseline)()
        useCaseWith(desktopRepo, setOf(STOPWATCH_KEY), baseline)()

        val stopwatchCells = desktopRepo.addedCells.filter { it.first.target == STOPWATCH_TARGET }
        assertEquals(2, stopwatchCells.size)
        assertTrue(baseline.stopwatchShortcutBackfilled)
    }

    @Test
    fun `does not complete Stopwatch backfill while Stopwatch is unavailable`() = runBlocking {
        val desktopRepo = FakeLauncherDesktopRepository()
        val baseline = FakeLauncherShortcutSyncRepository(setOf(STOPWATCH_KEY))

        useCaseWith(desktopRepo, emptySet(), baseline)()

        assertTrue(desktopRepo.addedCells.isEmpty())
        assertTrue(!baseline.stopwatchShortcutBackfilled)
    }

    /**
     * S2679: the landscape width stays 0 on a desktop the user has never rotated, and the placement
     * used to answer that with a constant four - narrower than the seven columns the seeded section
     * already occupied, so the free square inside it was invisible and the shortcut started a new row.
     */
    @Test
    fun `an unrecorded landscape width is taken from the seeded desktop, not from a constant`() = runBlocking {
        val desktopRepo = FakeLauncherDesktopRepository()
        desktopRepo.storedLandscapeColumns = 0
        desktopRepo.addCellInFirstFreeSlot(
            cellAt(LauncherOrientation.LANDSCAPE, "fn:seeded").copy(colIndex = 10),
            COLUMNS,
        )
        desktopRepo.addedCells.clear()

        useCaseWith(desktopRepo, setOf(CALCULATOR_KEY))()

        val landscapeColumns = desktopRepo.addedCells
            .filter { it.first.target == CALCULATOR_TARGET && it.first.orientation == LauncherOrientation.LANDSCAPE }
            .map { it.second }
        assertEquals(
            "placement must scan the width the desktop actually occupies",
            listOf(SEEDED_LANDSCAPE_COLUMNS),
            landscapeColumns,
        )
    }

    private companion object {
        const val CALCULATOR_KEY = "calculator"
        const val CALCULATOR_TARGET = "fn:calculator"
        const val STOPWATCH_KEY = "stopwatch"
        const val STOPWATCH_TARGET = "fn:stopwatch"
        const val COLUMNS = 4
        const val SEEDED_LANDSCAPE_COLUMNS = 11
    }
}
