package com.sza.fastmediasorter.domain.usecase.launcher

import com.sza.fastmediasorter.core.panel.SubProgramCatalog
import com.sza.fastmediasorter.core.panel.SubProgramSurface
import com.sza.fastmediasorter.domain.model.launcher.LauncherCell
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellKind
import com.sza.fastmediasorter.domain.model.launcher.LauncherOrientation
import com.sza.fastmediasorter.domain.repository.LauncherDesktopRepository
import com.sza.fastmediasorter.domain.repository.LauncherShortcutSyncRepository
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
 *
 * S2330: it now reacts to a route BECOMING launchable rather than reconciling the desktop to the
 * launchable set. Eight of the nine registry routes missing from the starter set are launchable
 * under default settings, so reconciling would not repair a gap - it would rewrite a desktop the
 * user already accepted, up to eight cells per orientation, at the first run on an existing install
 * (strategic ADR-2). The difference is carried entirely by [LauncherShortcutSyncRepository]: what
 * gets a cell is what is missing from the BASELINE, never what is missing from the desktop, which is
 * also why a cell the user deleted by hand is not restored.
 */
class SyncEnabledToolShortcutsUseCase @Inject constructor(
    private val desktop: LauncherDesktopRepository,
    private val resolveRouteAvailability: ResolvePanelRouteAvailabilityUseCase,
    private val syncBaseline: LauncherShortcutSyncRepository,
) {
    /**
     * S2330: the launchable subset of the registry's launcher-shortcut surface, as route keys.
     *
     * Exposed because the caller that decides WHEN to sync watches this very set for growth, and a
     * second copy of the filter there would be the hand-listing S1736 removed, reintroduced one
     * layer up (strategic 5.3).
     */
    suspend fun launchableShortcutRoutes(): Set<String> {
        // Both axes, in one settings read: a program compiled out of this build and a program the
        // user switched off are equally "no cell", and the resolver is the single place that knows.
        val availability = resolveRouteAvailability.all()
        return SubProgramCatalog.forSurface(SubProgramSurface.LAUNCHER_SHORTCUT)
            .filter { availability[it.routeKey]?.isLaunchable == true }
            .mapTo(mutableSetOf()) { it.routeKey }
    }

    suspend operator fun invoke() {
        val launchable = launchableShortcutRoutes()
        val baseline = syncBaseline.syncedRoutes()

        if (baseline == null) {
            // First run on this install: adopt what is launchable now without placing anything. The
            // desktop this finds was composed by the starter set alone, and an update is not the
            // moment to hand the user eight cells he did not ask for (strategic 5.1).
            syncBaseline.setSyncedRoutes(launchable)
            return
        }

        val newlyLaunchable = launchable - baseline
        if (newlyLaunchable.isEmpty()) return

        placeCellsFor(newlyLaunchable)
        // Union, never a replacement: a route that stopped being launchable stays accounted for, or
        // re-enabling it later would restore a cell the user deleted on purpose (strategic 5.2).
        syncBaseline.setSyncedRoutes(baseline + launchable)
    }

    private suspend fun placeCellsFor(routeKeys: Set<String>) {
        val state = desktop.state()
        val orientations = listOf(
            LauncherOrientation.PORTRAIT to state.columnsPortrait,
            LauncherOrientation.LANDSCAPE to state.columnsLandscape,
        )
        val targets = routeKeys.map { LauncherCellCommand.Feature(it).encode() }
        val now = System.currentTimeMillis()

        for ((orientation, cols) in orientations) {
            val columns = if (cols > 0) cols else FALLBACK_DESKTOP_COLUMNS
            val existingCells = desktop.observeCells(orientation).first()
            val existingTargets = existingCells.mapTo(mutableSetOf()) { it.target }

            for (target in targets) {
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
