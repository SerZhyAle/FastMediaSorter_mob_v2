package com.sza.fastmediasorter.domain.usecase.launcher

import com.sza.fastmediasorter.data.local.LocalMediaScanner
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import com.sza.fastmediasorter.domain.model.launcher.LauncherResourceMode
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
    private val resources: ResourceRepository,
    private val settings: SettingsRepository,
    private val provisionDefaultResources: ProvisionDefaultResourcesUseCase,
    private val syncBaseline: LauncherShortcutSyncRepository,
    private val placeShortcutTiles: PlaceLauncherShortcutTilesUseCase,
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

            val targets = newlyPresent.values.map {
                LauncherCellCommand.Resource(it, LauncherResourceMode.BROWSE).encode()
            }
            // A failed placement leaves the baseline untouched, so the next pass retries the
            // placement instead of silently accounting for tiles that never landed.
            if (!placeShortcutTiles(targets)) return@runCatching
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

    private companion object {
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
