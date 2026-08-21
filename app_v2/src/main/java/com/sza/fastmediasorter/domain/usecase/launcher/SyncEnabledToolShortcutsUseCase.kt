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
 * S1746: Ensures enabled tools (calculator, game, and since S1883 the Wear companion) have corresponding
 * shortcuts on the launcher desktop across orientations, appending them to the first free slot if missing.
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
        // S1883: no capability check beside the switch, unlike the panel's availability chain. The
        // launcher ships only in standard and noLegal, and both carry the watch bridge, so a build that
        // can run this code can always run the companion; a stale cell would be refused by that chain
        // anyway, which is the gate that decides whether a placed cell launches.
        if (appSettings.enableWearCompanion) {
            toolsToSync.add(LauncherCellCommand.Feature(InternalRouteCatalog.KEY_WEAR_COMPANION).encode())
        }

        if (toolsToSync.isEmpty()) return

        val now = System.currentTimeMillis()
        for ((orientation, cols) in orientations) {
            val columns = if (cols > 0) cols else FALLBACK_DESKTOP_COLUMNS
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

    private companion object {
        /** Used when the desktop has not stored a column count yet, so the first placement still lands. */
        const val FALLBACK_DESKTOP_COLUMNS = 4
    }
}
