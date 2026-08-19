package com.sza.fastmediasorter.domain.usecase.launcher

import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.launcher.LauncherCell
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellPlacement
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellKind
import com.sza.fastmediasorter.domain.model.launcher.LauncherOrientation
import com.sza.fastmediasorter.domain.repository.LauncherDesktopRepository
import com.sza.fastmediasorter.domain.repository.LauncherDesktopState
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.ui.player.model.TouchZoneHintType
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

        override suspend fun addCellInFirstFreeSlot(cell: LauncherCell, columns: Int): Long? {
            addedCells.add(cell to columns)
            val current = cellsMap[cell.orientation] ?: emptyList()
            cellsMap[cell.orientation] = current + cell
            return 1L
        }

        override suspend fun removeCell(id: Long) {}
        override suspend fun normalizeSectionSpans() {}
        override suspend fun moveCell(id: Long, rowIndex: Int, colIndex: Int): Boolean = true
        override suspend fun resizeCell(id: Long, spanW: Int, spanH: Int): Boolean = true
        override suspend fun updateCellTarget(id: Long, target: String): Boolean = true
        override suspend fun seedIfEmpty(orientation: LauncherOrientation, cells: List<LauncherCell>): Boolean = true
        override suspend fun clearAll() {}
        override suspend fun state(): LauncherDesktopState = LauncherDesktopState(
            seededPortrait = true,
            seededLandscape = true,
            columnsPortrait = 4,
            columnsLandscape = 6,
        )
        override suspend fun updateCellLabel(id: Long, labelOverride: String?): Boolean = true
        override suspend fun swapSectionBlock(orientation: LauncherOrientation, sectionCellId: Long, moveUp: Boolean): Boolean = true
        override suspend fun updateColumns(orientation: LauncherOrientation, columns: Int) {}
    }

    private class FakeSettingsRepository(private val appSettings: AppSettings) : SettingsRepository {
        override fun getSettings(): Flow<AppSettings> = flowOf(appSettings)
        override suspend fun updateSettings(settings: AppSettings) {}
        override suspend fun updateSettings(transform: suspend (AppSettings) -> AppSettings) {}
        override suspend fun resetToDefaults() {}
        override suspend fun setPlayerFirstRun(isFirstRun: Boolean) {}
        override suspend fun isPlayerFirstRun(): Boolean = false
        override suspend fun saveLastUsedResourceId(resourceId: Long) {}
        override suspend fun getLastUsedResourceId(): Long = -1L
        override suspend fun setResourceGridMode(isGridMode: Boolean) {}
        override suspend fun updateEmbeddedGameEnabled(enabled: Boolean) {}
        override suspend fun updateScheduledOperationsPaused(paused: Boolean) {}
        override suspend fun setStatisticsEnabled(enabled: Boolean) {}
        override suspend fun isTouchZoneHintShown(type: TouchZoneHintType): Boolean = false
        override suspend fun setTouchZoneHintShown(type: TouchZoneHintType, shown: Boolean) {}
        override suspend fun resetAllTouchZoneHints() {}
        override suspend fun isConsolidatedStorageActive(): Boolean = false
    }

    @Test
    fun `adds calculator shortcut when calculator is enabled`() = runBlocking {
        val desktopRepo = FakeLauncherDesktopRepository()
        val settingsRepo = FakeSettingsRepository(AppSettings(enableCalculator = true))
        val useCase = SyncEnabledToolShortcutsUseCase(desktopRepo, settingsRepo)

        useCase()

        assertTrue(desktopRepo.addedCells.any { it.first.target == "fn:calculator" })
    }

    @Test
    fun `does not add duplicate calculator shortcut if already present`() = runBlocking {
        val desktopRepo = FakeLauncherDesktopRepository()
        desktopRepo.addCellInFirstFreeSlot(
            LauncherCell(
                id = 1,
                orientation = LauncherOrientation.PORTRAIT,
                rowIndex = 0,
                colIndex = 0,
                spanW = 1,
                spanH = 1,
                kind = LauncherCellKind.SHORTCUT,
                target = "fn:calculator",
                labelOverride = null,
                addedAt = 0,
            ),
            4,
        )
        desktopRepo.addedCells.clear()

        val settingsRepo = FakeSettingsRepository(AppSettings(enableCalculator = true))
        val useCase = SyncEnabledToolShortcutsUseCase(desktopRepo, settingsRepo)

        useCase()

        assertTrue(desktopRepo.addedCells.none { it.first.target == "fn:calculator" && it.first.orientation == LauncherOrientation.PORTRAIT })
    }
}
