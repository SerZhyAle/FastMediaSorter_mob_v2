package com.sza.fastmediasorter.domain.usecase.launcher

import com.sza.fastmediasorter.core.panel.InternalRouteCatalog
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
import timber.log.Timber
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
    private val resolveColumns: ResolveLauncherColumnsUseCase,
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
            //
            // S2664: this stays a plain adoption even now that the opposite direction exists. Turning it
            // into a reconciliation would rewrite a desktop the user already accepted on the first launch
            // after an update, which strategic ADR-2 keeps refusing. What the starter set missed is still
            // frozen here - the seed is deliberately unchanged, because the App-functions budget holds
            // twelve of the registry's twenty-two and which twelve is an owner decision.
            syncBaseline.setSyncedRoutes(launchable)
        } else {
            val newlyLaunchable = launchable - baseline
            val noLongerLaunchable = baseline - launchable
            if (newlyLaunchable.isNotEmpty()) placeCellsFor(newlyLaunchable)
            if (noLongerLaunchable.isNotEmpty()) removeCellsFor(noLongerLaunchable)
            // S2664, ADR-1: the launchable set replaces the baseline instead of joining it. The union was
            // there so a route that stopped being launchable stayed accounted for and its return could not
            // restore a cell the user had deleted on purpose; the owner ruling of 2026-09-06 reverses that -
            // a shortcut's presence follows its toggle whatever the reason the cell was gone. Restoring the
            // union would restore that refusal, so read ADR-1 before treating this line as a defect.
            syncBaseline.setSyncedRoutes(launchable)
        }

        backfillStopwatchShortcut(launchable)
    }

    private suspend fun backfillStopwatchShortcut(launchable: Set<String>) {
        if (
            syncBaseline.isStopwatchShortcutBackfilled() ||
            InternalRouteCatalog.KEY_STOPWATCH !in launchable
        ) {
            return
        }
        placeCellsFor(setOf(InternalRouteCatalog.KEY_STOPWATCH))
        syncBaseline.setStopwatchShortcutBackfilled()
    }

    /**
     * Takes the shortcut away when its program is switched off, in both orientations. Removing nothing
     * is correct: the user may have deleted the cell himself, and a desktop that never carried it is
     * already in the state this is asking for.
     */
    private suspend fun removeCellsFor(routeKeys: Set<String>) {
        val targets = routeKeys.mapTo(mutableSetOf()) { LauncherCellCommand.Feature(it).encode() }
        for (orientation in listOf(LauncherOrientation.PORTRAIT, LauncherOrientation.LANDSCAPE)) {
            desktop.observeCells(orientation).first()
                .filter { it.target in targets }
                .forEach { desktop.removeCell(it.id) }
        }
    }

    private suspend fun placeCellsFor(routeKeys: Set<String>) {
        val state = desktop.state()
        val orientations = listOf(
            LauncherOrientation.PORTRAIT to state.columnsPortrait,
            LauncherOrientation.LANDSCAPE to state.columnsLandscape,
        )
        val targets = routeKeys.map { LauncherCellCommand.Feature(it).encode() }
        val now = System.currentTimeMillis()

        for ((orientation, storedColumns) in orientations) {
            val columns = resolveColumns(orientation, storedColumns)
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
                    val placed =
                        desktop.addCellInSection(cell, columns, LauncherCellCommand.SECTION_APP_FUNCTIONS)
                    if (placed == null) {
                        // This orientation carries no App-functions header, and creating one would be a
                        // placement decision with a label this caller does not own - so the shortcut still
                        // reaches the desktop by the grid-wide path rather than being dropped.
                        desktop.addCellInFirstFreeSlot(cell, columns)
                    }
                }
            }
        }
    }
}
