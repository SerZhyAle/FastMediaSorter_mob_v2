package com.sza.fastmediasorter.domain.usecase.launcher

import com.sza.fastmediasorter.core.panel.SubProgramCatalog
import com.sza.fastmediasorter.core.panel.SubProgramSurface
import com.sza.fastmediasorter.domain.model.launcher.LauncherCell
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellKind
import com.sza.fastmediasorter.domain.model.launcher.LauncherOrientation
import com.sza.fastmediasorter.domain.repository.LauncherDesktopRepository
import com.sza.fastmediasorter.domain.usecase.panel.ResolvePanelRouteAvailabilityUseCase
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * S1746: Ensures enabled tools have corresponding shortcuts on the launcher desktop across
 * orientations, appending them to the first free slot if missing.
 *
 * S1736: which tools those are is no longer stated here. The set is every registry entry declaring
 * [SubProgramSurface.LAUNCHER_SHORTCUT], so switching a program on seeds its cell without this file
 * being edited - before this, three programs were hand-listed and every other one silently had no
 * desktop cell, which is the concrete shape of the broken "enable it and it appears everywhere".
 */
class SyncEnabledToolShortcutsUseCase @Inject constructor(
    private val desktop: LauncherDesktopRepository,
    private val resolveRouteAvailability: ResolvePanelRouteAvailabilityUseCase,
) {
    suspend operator fun invoke() {
        val state = desktop.state()
        val orientations = listOf(
            LauncherOrientation.PORTRAIT to state.columnsPortrait,
            LauncherOrientation.LANDSCAPE to state.columnsLandscape,
        )

        // Both axes, in one settings read: a program compiled out of this build and a program the
        // user switched off are equally "no cell", and the resolver is the single place that knows.
        val availability = resolveRouteAvailability.all()
        val toolsToSync = SubProgramCatalog.forSurface(SubProgramSurface.LAUNCHER_SHORTCUT)
            .filter { availability[it.routeKey]?.isLaunchable == true }
            .map { LauncherCellCommand.Feature(it.routeKey).encode() }

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
