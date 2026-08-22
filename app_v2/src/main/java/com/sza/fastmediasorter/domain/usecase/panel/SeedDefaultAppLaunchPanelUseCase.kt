package com.sza.fastmediasorter.domain.usecase.panel

import com.sza.fastmediasorter.core.panel.OsShortcutCatalog
import com.sza.fastmediasorter.core.panel.SubProgramCatalog
import com.sza.fastmediasorter.core.panel.SubProgramSurface
import com.sza.fastmediasorter.data.local.LocalMediaScanner
import com.sza.fastmediasorter.domain.model.APP_LAUNCH_PANEL_SLOT_COUNT
import com.sza.fastmediasorter.domain.model.AppLaunchPanelTile
import com.sza.fastmediasorter.domain.model.AppLaunchPanelTileType
import com.sza.fastmediasorter.domain.model.panel.AppLaunchPanelRouteTarget
import com.sza.fastmediasorter.domain.repository.AppLaunchPanelRepository
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * First-run seed: FMS at slot 0, then the available curated feature routes (ours first, strategic
 * S0663 §5.1.E), then a specific resource, then a base OS target (Settings). Unavailable-in-build
 * features are skipped and the next available one shifts up - no holes (§6.2). No-op once any tile
 * exists. Stops before the last slots so empty slots remain for the user's apps (§6.5).
 */
class SeedDefaultAppLaunchPanelUseCase @Inject constructor(
    private val repository: AppLaunchPanelRepository,
    private val resourceRepository: ResourceRepository,
    private val resolveRouteAvailability: ResolvePanelRouteAvailabilityUseCase,
) {
    suspend operator fun invoke() = withContext(Dispatchers.IO) {
        if (repository.count() > 0) return@withContext
        val now = System.currentTimeMillis()
        var slot = 0
        repository.setTile(AppLaunchPanelTile(slot++, AppLaunchPanelTileType.OWN_APP, null, null, now))

        // S1736: ours first, taken from the one registry of sub-programs in its one order, no longer
        // from a list this use case curated for itself.
        for (entry in SubProgramCatalog.forSurface(SubProgramSurface.QUICK_ACCESS_PANEL)) {
            if (slot >= SEED_FEATURE_SLOT_LIMIT) break
            if (!resolveRouteAvailability(entry.routeKey).availableInBuild) continue
            slot = seedRouteTile(slot, AppLaunchPanelRouteTarget.Feature(entry.routeKey), now)
        }

        // A specific resource (§5.1.C): only if a real default exists, else skip (no dangling id, §04.2).
        defaultResourceId()?.let { resourceId ->
            if (slot < SEED_SLOT_LIMIT) {
                slot = seedRouteTile(slot, AppLaunchPanelRouteTarget.Resource(resourceId), now)
            }
        }

        // Base OS part: Settings always rounds out the curated default.
        if (slot < SEED_SLOT_LIMIT) {
            seedRouteTile(slot, AppLaunchPanelRouteTarget.OsShortcut(OsShortcutCatalog.KEY_SETTINGS), now)
        }
    }

    private suspend fun seedRouteTile(slot: Int, target: AppLaunchPanelRouteTarget, now: Long): Int {
        repository.setTile(
            AppLaunchPanelTile(slot, AppLaunchPanelTileType.INTERNAL_ROUTE, target.encode(), null, now)
        )
        return slot + 1
    }

    /** A broad virtual "all media" resource if one is seeded, else the first configured resource. */
    private suspend fun defaultResourceId(): Long? {
        val resources = resourceRepository.getAllResourcesSync()
        return resources.firstOrNull { it.path in VIRTUAL_ALL_PATHS }?.id ?: resources.firstOrNull()?.id
    }

    private companion object {
        // Leave a guaranteed band of empty slots as an invitation to add the user's own apps (§6.5).
        const val SEED_SLOT_LIMIT = APP_LAUNCH_PANEL_SLOT_COUNT - 4

        // S1736: the registry offers more panel-eligible programs than the curated list it replaces, so
        // feature seeding stops two slots early to keep the resource tile and the Settings tile that
        // follow it. Without the reservation the programs alone reach SEED_SLOT_LIMIT and both trailing
        // tiles are silently dropped, which is a change to the first-run panel this ticket never asked
        // for - it is a migration of where the composition comes from, not of what the default is.
        const val SEED_FEATURE_SLOT_LIMIT = SEED_SLOT_LIMIT - 2

        val VIRTUAL_ALL_PATHS = setOf(
            LocalMediaScanner.VIRTUAL_PATH_ALL_IMAGES,
            LocalMediaScanner.VIRTUAL_PATH_ALL_VIDEO,
            LocalMediaScanner.VIRTUAL_PATH_ALL_AUDIO,
            LocalMediaScanner.VIRTUAL_PATH_ALL_DOCS,
        )
    }
}
