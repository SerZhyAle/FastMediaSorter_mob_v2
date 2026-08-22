package com.sza.fastmediasorter.domain.usecase.launcher

import com.sza.fastmediasorter.domain.model.launcher.LauncherCell
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellKind
import com.sza.fastmediasorter.domain.model.launcher.LauncherOrientation
import com.sza.fastmediasorter.domain.repository.InstalledAppsRepository
import com.sza.fastmediasorter.domain.repository.LauncherDesktopRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * S1761: Imports system desktop shortcuts (installed applications) onto the launcher desktop grid.
 */
class ImportSystemShortcutsUseCase @Inject constructor(
    private val installedAppsRepository: InstalledAppsRepository,
    private val desktopRepository: LauncherDesktopRepository,
) {
    suspend operator fun invoke(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val apps = installedAppsRepository.observeApps().first()
            if (apps.isEmpty()) return@runCatching false

            val state = desktopRepository.state()
            val portraitCols = if (state.columnsPortrait > 0) state.columnsPortrait else 4
            val landscapeCols = if (state.columnsLandscape > 0) state.columnsLandscape else 6

            val portraitCells = desktopRepository.observeCells(LauncherOrientation.PORTRAIT).first()
            val landscapeCells = desktopRepository.observeCells(LauncherOrientation.LANDSCAPE).first()

            val portraitTargets = portraitCells.map { it.target }.toSet()
            val landscapeTargets = landscapeCells.map { it.target }.toSet()

            val now = System.currentTimeMillis()

            apps.forEach { app ->
                val target = LauncherCellCommand.App(app.packageName).encode()

                if (!portraitTargets.contains(target)) {
                    val portraitCell = LauncherCell(
                        id = 0L,
                        orientation = LauncherOrientation.PORTRAIT,
                        rowIndex = 0,
                        colIndex = 0,
                        spanW = 1,
                        spanH = 1,
                        kind = LauncherCellKind.SHORTCUT,
                        target = target,
                        labelOverride = app.label,
                        addedAt = now,
                    )
                    desktopRepository.addCellInFirstFreeSlot(portraitCell, portraitCols)
                }

                if (!landscapeTargets.contains(target)) {
                    val landscapeCell = LauncherCell(
                        id = 0L,
                        orientation = LauncherOrientation.LANDSCAPE,
                        rowIndex = 0,
                        colIndex = 0,
                        spanW = 1,
                        spanH = 1,
                        kind = LauncherCellKind.SHORTCUT,
                        target = target,
                        labelOverride = app.label,
                        addedAt = now,
                    )
                    desktopRepository.addCellInFirstFreeSlot(landscapeCell, landscapeCols)
                }
            }
            true
        }.getOrElse { error ->
            Timber.e(error, "Failed to import system desktop shortcuts")
            false
        }
    }
}
