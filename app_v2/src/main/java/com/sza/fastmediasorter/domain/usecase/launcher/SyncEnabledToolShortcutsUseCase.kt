package com.sza.fastmediasorter.domain.usecase.launcher

import com.sza.fastmediasorter.core.panel.InternalRouteCatalog
import com.sza.fastmediasorter.domain.model.launcher.LauncherCell
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellKind
import com.sza.fastmediasorter.domain.model.launcher.LauncherOrientation
import com.sza.fastmediasorter.domain.repository.LauncherDesktopRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * S1746: Ensures enabled tools (calculator, games) have corresponding shortcuts
 * on the launcher desktop across orientations, appending them to the first free slot if missing.
 */
class SyncEnabledToolShortcutsUseCase @Inject constructor(
    private val desktop: LauncherDesktopRepository,
    private val settings: SettingsRepository,
) {
    suspend operator fun invoke() {
        val appSettings = settings.getSettings().first()
        val state = desktop.state()
        val orientations = listOf(
            LauncherOrientation.PORTRAIT to state.columnsPortrait,
            LauncherOrientation.LANDSCAPE to state.columnsLandscape,
        )

        val toolsToSync = mutableListOf<String>()
        if (appSettings.enableCalculator) {
            toolsToSync.add(LauncherCellCommand.Feature(InternalRouteCatalog.KEY_CALCULATOR).encode())
        }
        if (appSettings.embeddedGameEnabled) {
            toolsToSync.add(LauncherCellCommand.Feature(InternalRouteCatalog.KEY_GAME).encode())
        }

        if (toolsToSync.isEmpty()) return

        val now = System.currentTimeMillis()
        for ((orientation, cols) in orientations) {
            val columns = if (cols > 0) cols else 4
            val existingCells = desktop.observeCells(orientation).first()
            val existingTargets = existingCells.mapTo(mutableSetOf()) { it.target }

            for (target in toolsToSync) {
                if (target !in existingTargets) {
                    val cell = LauncherCell(
                        id = 0,
                        orientation = orientation,
                        rowIndex = 0,
                        colIndex = 0,
                        spanW = 1,
                        spanH = 1,
                        kind = LauncherCellKind.SHORTCUT,
                        target = target,
                        labelOverride = null,
                        addedAt = now,
                    )
                    desktop.addCellInFirstFreeSlot(cell, columns)
                }
            }
        }
    }
}
