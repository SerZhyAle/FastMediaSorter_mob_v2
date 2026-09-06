package com.sza.fastmediasorter.domain.usecase.launcher

import com.sza.fastmediasorter.data.local.LocalMediaScanner
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.launcher.LauncherCell
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellKind
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellPlacement
import com.sza.fastmediasorter.domain.model.launcher.LauncherOrientation
import com.sza.fastmediasorter.domain.model.launcher.LauncherResourceMode
import com.sza.fastmediasorter.domain.repository.LauncherDesktopRepository
import com.sza.fastmediasorter.domain.repository.LauncherDesktopState
import com.sza.fastmediasorter.domain.repository.LauncherShortcutSyncRepository
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.ProvisionDefaultResourcesUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S2564: pins the backfill rules the strategic spec's §11 criteria name - silent first adoption,
 * placement of the increment only, a union on write, and no removal when a type is switched off.
 */
class SyncEnabledResourceTilesUseCaseTest {

    private class FakeLauncherDesktopRepository(
        private val sectionExists: Boolean = true,
    ) : LauncherDesktopRepository {
        val sectionPlacements = mutableListOf<LauncherCell>()
        val freeSlotPlacements = mutableListOf<LauncherCell>()
        private val cellsMap = mutableMapOf<LauncherOrientation, List<LauncherCell>>()

        fun seedExisting(orientation: LauncherOrientation, cells: List<LauncherCell>) {
            cellsMap[orientation] = cells
        }

        override fun observeCells(orientation: LauncherOrientation): Flow<List<LauncherCell>> =
            flowOf(cellsMap[orientation] ?: emptyList())

        override suspend fun addCell(cell: LauncherCell, columns: Int): LauncherCellPlacement =
            LauncherCellPlacement.Placed(1L)

        override suspend fun addCellInFirstFreeSlot(cell: LauncherCell, columns: Int): Long? {
            freeSlotPlacements.add(cell)
            remember(cell)
            return 1L
        }

        override suspend fun addCellInSection(cell: LauncherCell, columns: Int, sectionKey: String): Long? {
            if (!sectionExists) return null
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

    private class FakeLauncherShortcutSyncRepository(
        var paths: Set<String>?,
    ) : LauncherShortcutSyncRepository {
        override suspend fun syncedRoutes(): Set<String>? = null
        override suspend fun setSyncedRoutes(routeKeys: Set<String>) = Unit
        override suspend fun clearSyncedRoutes() = Unit

        override suspend fun syncedResourcePaths(): Set<String>? = paths

        override suspend fun setSyncedResourcePaths(paths: Set<String>) {
            this.paths = paths
        }

        override suspend fun clearSyncedResourcePaths() {
            paths = null
        }
    }

    @Test
    fun `a first pass adopts what exists and places nothing`() = runBlocking {
        val desktop = FakeLauncherDesktopRepository()
        val baseline = FakeLauncherShortcutSyncRepository(null)

        useCaseWith(desktop, listOf(RECENT, ALL_AUDIO), baseline)()

        assertTrue(desktop.sectionPlacements.isEmpty())
        assertTrue(desktop.freeSlotPlacements.isEmpty())
        assertEquals(setOf(RECENT.path, ALL_AUDIO.path), baseline.paths)
    }

    @Test
    fun `an aggregate present since the baseline gets a tile in both orientations`() = runBlocking {
        val desktop = FakeLauncherDesktopRepository()
        val baseline = FakeLauncherShortcutSyncRepository(setOf(RECENT.path))

        useCaseWith(desktop, listOf(RECENT, ALL_AUDIO), baseline)()

        val placed = desktop.sectionPlacements
        assertEquals(2, placed.size)
        assertEquals(
            setOf(LauncherOrientation.PORTRAIT, LauncherOrientation.LANDSCAPE),
            placed.mapTo(mutableSetOf()) { it.orientation },
        )
        assertTrue(placed.all { it.target == targetOf(ALL_AUDIO) })
        assertTrue(placed.all { it.kind == LauncherCellKind.SHORTCUT })
    }

    @Test
    fun `an aggregate already in the baseline is not placed again`() = runBlocking {
        val desktop = FakeLauncherDesktopRepository()
        val baseline = FakeLauncherShortcutSyncRepository(setOf(RECENT.path, ALL_AUDIO.path))

        useCaseWith(desktop, listOf(RECENT, ALL_AUDIO), baseline)()

        assertTrue(desktop.sectionPlacements.isEmpty())
        assertTrue(desktop.freeSlotPlacements.isEmpty())
    }

    // Strategic ADR-4: switching a type off is not a removal, and the union write is what stops the
    // tile from returning when the same aggregate is provisioned again later.
    @Test
    fun `an aggregate that left the table stays in the stored baseline`() = runBlocking {
        val desktop = FakeLauncherDesktopRepository()
        val baseline = FakeLauncherShortcutSyncRepository(setOf(RECENT.path, ALL_AUDIO.path))

        useCaseWith(desktop, listOf(RECENT, ALL_VIDEO), baseline)()

        assertEquals(setOf(RECENT.path, ALL_AUDIO.path, ALL_VIDEO.path), baseline.paths)
        assertTrue(desktop.sectionPlacements.none { it.target == targetOf(ALL_AUDIO) })
    }

    @Test
    fun `a tile the desktop already carries is not placed twice`() = runBlocking {
        val desktop = FakeLauncherDesktopRepository()
        val existing = cellOf(LauncherOrientation.PORTRAIT, targetOf(ALL_AUDIO))
        desktop.seedExisting(LauncherOrientation.PORTRAIT, listOf(existing))
        val baseline = FakeLauncherShortcutSyncRepository(emptySet())

        useCaseWith(desktop, listOf(ALL_AUDIO), baseline)()

        assertEquals(listOf(LauncherOrientation.LANDSCAPE), desktop.sectionPlacements.map { it.orientation })
    }

    // Strategic ADR-5: the section is tried first, and the whole-grid scan is reached only when this
    // desktop carries no resources header at all.
    @Test
    fun `the free slot scan is used only when the section placement refuses`() = runBlocking {
        val desktop = FakeLauncherDesktopRepository(sectionExists = false)
        val baseline = FakeLauncherShortcutSyncRepository(emptySet())

        useCaseWith(desktop, listOf(ALL_AUDIO), baseline)()

        assertTrue(desktop.sectionPlacements.isEmpty())
        assertEquals(2, desktop.freeSlotPlacements.size)
    }

    // Strategic ADR-2: without the provisioning call a launcher-only device never gets the record the
    // tile needs, because MainActivity - the other caller - may never open.
    @Test
    fun `provisioning runs on every pass`() = runBlocking {
        val provision = provisionMock()
        val baseline = FakeLauncherShortcutSyncRepository(emptySet())

        useCaseWith(FakeLauncherDesktopRepository(), emptyList(), baseline, provision)()

        coVerify(exactly = 1) { provision() }
        assertEquals(emptySet<String>(), baseline.paths)
    }

    private fun provisionMock(): ProvisionDefaultResourcesUseCase {
        val provision = mockk<ProvisionDefaultResourcesUseCase>()
        coEvery { provision() } returns false
        return provision
    }

    private fun useCaseWith(
        desktop: LauncherDesktopRepository,
        present: List<MediaResource>,
        baseline: FakeLauncherShortcutSyncRepository,
        provision: ProvisionDefaultResourcesUseCase = provisionMock(),
    ): SyncEnabledResourceTilesUseCase = SyncEnabledResourceTilesUseCase(
        desktop = desktop,
        resources = mockk<ResourceRepository> {
            coEvery { getAllResourcesSync() } returns present
        },
        settings = mockk<SettingsRepository>(relaxed = true),
        provisionDefaultResources = provision,
        syncBaseline = baseline,
    )

    private fun targetOf(resource: MediaResource): String =
        LauncherCellCommand.Resource(resource.id, LauncherResourceMode.BROWSE).encode()

    private fun cellOf(orientation: LauncherOrientation, target: String): LauncherCell = LauncherCell(
        id = 7L,
        orientation = orientation,
        rowIndex = 0,
        colIndex = 0,
        spanW = 1,
        spanH = 1,
        kind = LauncherCellKind.SHORTCUT,
        target = target,
        labelOverride = null,
        addedAt = 0L,
    )

    private companion object {
        val RECENT = MediaResource(
            id = 11L,
            name = "Recent",
            path = LocalMediaScanner.VIRTUAL_PATH_RECENT,
            type = ResourceType.LOCAL,
        )
        val ALL_AUDIO = MediaResource(
            id = 12L,
            name = "All Music",
            path = LocalMediaScanner.VIRTUAL_PATH_ALL_AUDIO,
            type = ResourceType.LOCAL,
        )
        val ALL_VIDEO = MediaResource(
            id = 13L,
            name = "All Videos",
            path = LocalMediaScanner.VIRTUAL_PATH_ALL_VIDEO,
            type = ResourceType.LOCAL,
        )
    }
}
