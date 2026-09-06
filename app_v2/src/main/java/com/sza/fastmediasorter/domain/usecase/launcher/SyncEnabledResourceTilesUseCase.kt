package com.sza.fastmediasorter.domain.usecase.launcher

import com.sza.fastmediasorter.data.local.LocalMediaScanner
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.launcher.LauncherCell
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellKind
import com.sza.fastmediasorter.domain.model.launcher.LauncherOrientation
import com.sza.fastmediasorter.domain.model.launcher.LauncherResourceMode
import com.sza.fastmediasorter.domain.repository.LauncherDesktopRepository
import com.sza.fastmediasorter.domain.repository.LauncherShortcutSyncRepository
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.ProvisionDefaultResourcesUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * S2564: gives an aggregate resource its desktop tile when its media type is switched on after the
 * starter set was laid out.
 *
 * The mirror of [SyncEnabledToolShortcutsUseCase] for the other half of the desktop. The seed runs
 * once and its already-seeded early exit sits above the provisioning call, so a seeded desktop never
 * re-reads either the settings or the resource table; the route sync that closes this gap for
 * programs works on registry route keys and cannot reach a resource cell.
 *
 * What decides placement is the RESOURCE TABLE, never the media-type flag (strategic ADR-1): a tile
 * needs the id of a record that exists, and the condition that creates an aggregate is composite -
 * build capability, the type flag, and a non-empty type composition - and lives in
 * [ProvisionDefaultResourcesUseCase]. Reading flags here would be a second copy of that condition,
 * which is the hand-listing S1736 removed from the route sync.
 *
 * Provisioning runs first on every pass (strategic ADR-2). It is idempotent per virtual path, and on
 * a device whose launcher is Home `MainActivity` may never open - so without this call the newly
 * enabled type would have no record at all and there would be nothing to place.
 *
 * Like the route sync, this is a backfill and not a reconciliation (strategic ADR-4): switching a
 * type off removes no tile, the first pass adopts what it finds without placing anything, and the
 * baseline is written as a union so a tile the user deleted by hand never comes back.
 */
class SyncEnabledResourceTilesUseCase @Inject constructor(
    private val desktop: LauncherDesktopRepository,
    private val resources: ResourceRepository,
    private val settings: SettingsRepository,
    private val provisionDefaultResources: ProvisionDefaultResourcesUseCase,
    private val syncBaseline: LauncherShortcutSyncRepository,
) {
    /**
     * S2564: the media types enabled right now, as the caller's change detector.
     *
     * Exposed for the same reason [SyncEnabledToolShortcutsUseCase.launchableShortcutRoutes] is: the
     * caller decides WHEN to sync and needs a value that changes only on the event that matters. It
     * is a trigger and never a filter - which aggregate gets a tile is decided in [invoke] from the
     * resource table alone.
     */
    suspend fun enabledMediaTypes(): Set<MediaType> =
        settings.getSettings().first().getGloballyEnabledMediaTypes()

    /**
     * On the IO dispatcher and inside `runCatching`, both for the reason [SeedLauncherDesktopUseCase]
     * states: this runs on the collector of a Home-surface ViewModel, so a resource read that probes
     * volume availability would land on the main thread, and a thrown Room exception would cancel the
     * whole `viewModelScope` and take the desktop's own cell stream down with it. Failing here leaves
     * the desktop exactly as it was, which is the same desktop the user had a moment ago.
     */
    suspend operator fun invoke(): Unit = withContext(Dispatchers.IO) {
        runCatching {
            // Idempotent per virtual path, so a pass that changes nothing costs one settings read and
            // one resource read - and a pass following a newly enabled type creates the record below.
            provisionDefaultResources()

            val present = presentAggregates()
            val baseline = syncBaseline.syncedResourcePaths()
            Timber.d("S2564: sync present=%d baseline=%s", present.size, baseline?.size)

            if (baseline == null) {
                // First run on this install: adopt what exists now without placing anything. The
                // desktop this finds was composed by the starter set, and an update is not the moment
                // to hand the user cells he did not ask for (strategic §5.3).
                syncBaseline.setSyncedResourcePaths(present.keys)
                return@runCatching
            }

            val newlyPresent = present.filterKeys { it !in baseline }
            if (newlyPresent.isEmpty()) return@runCatching

            placeTilesFor(newlyPresent.values.toSet())
            // Union, never a replacement: an aggregate deleted from the table stays accounted for, or
            // provisioning it again later would restore a tile the user removed on purpose.
            syncBaseline.setSyncedResourcePaths(baseline + present.keys)
        }.onFailure { Timber.w(it, "Launcher resource tile sync failed; desktop left as it is") }
        Unit
    }

    /** The core aggregates that currently exist, as virtual path to resource id. */
    private suspend fun presentAggregates(): Map<String, Long> = resources.getAllResourcesSync()
        .filter { it.path in CORE_VIRTUAL_PATHS }
        .associate { it.path to it.id }

    private suspend fun placeTilesFor(resourceIds: Set<Long>) {
        val state = desktop.state()
        val orientations = listOf(
            LauncherOrientation.PORTRAIT to state.columnsPortrait,
            LauncherOrientation.LANDSCAPE to state.columnsLandscape,
        )
        val targets = resourceIds.map {
            LauncherCellCommand.Resource(it, LauncherResourceMode.BROWSE).encode()
        }
        val now = System.currentTimeMillis()

        for ((orientation, storedColumns) in orientations) {
            val columns = if (storedColumns > 0) storedColumns else FALLBACK_DESKTOP_COLUMNS
            val existingCells = desktop.observeCells(orientation).first()
            val existingTargets = existingCells.mapTo(mutableSetOf()) { it.target }

            for (target in targets) {
                if (target !in existingTargets) {
                    placeTile(orientation, target, columns, now)
                }
            }
        }
    }

    /**
     * The resources section first, the whole grid only as a fallback (strategic ADR-5).
     *
     * [LauncherDesktopRepository.addCellInSection] grows the band when the section is full (S2018),
     * which is what keeps the tile under its own header instead of in whichever neighbouring section
     * still had a gap. Null means this desktop carries no resources header at all - the user deleted
     * it - and the free-slot scan is then the honest answer; it prefers the same section by itself
     * whenever one does exist (S1760).
     */
    private suspend fun placeTile(
        orientation: LauncherOrientation,
        target: String,
        columns: Int,
        now: Long,
    ) {
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
        desktop.addCellInSection(cell, columns, LauncherCellCommand.SECTION_RESOURCES)
            ?: desktop.addCellInFirstFreeSlot(cell, columns)
    }

    private companion object {
        /** Used when the desktop has not stored a column count yet, so the first placement still lands. */
        const val FALLBACK_DESKTOP_COLUMNS = 4

        /**
         * The closed core set the starter table seeds, by virtual path (S2321).
         *
         * Paths and not ids, because the baseline is keyed by them: an aggregate deleted and then
         * provisioned again comes back under a new id and would read as a tile the desktop never had.
         * The predefined "All files" resource is deliberately absent - it lives at a real storage path
         * and no media-type flag governs it - and so is the user tail, which the layout budget shortens
         * on purpose.
         */
        val CORE_VIRTUAL_PATHS = setOf(
            LocalMediaScanner.VIRTUAL_PATH_RECENT,
            LocalMediaScanner.VIRTUAL_PATH_ALL_AUDIO,
            LocalMediaScanner.VIRTUAL_PATH_ALL_VIDEO,
            LocalMediaScanner.VIRTUAL_PATH_ALL_IMAGES,
            LocalMediaScanner.VIRTUAL_PATH_ALL_DOCS,
            LocalMediaScanner.VIRTUAL_PATH_CAMERA_PHOTOS,
        )
    }
}
