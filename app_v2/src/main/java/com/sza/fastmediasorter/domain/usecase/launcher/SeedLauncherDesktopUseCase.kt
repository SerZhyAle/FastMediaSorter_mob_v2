package com.sza.fastmediasorter.domain.usecase.launcher

import android.content.Context
import com.sza.fastmediasorter.core.launcher.LauncherStarterSets
import com.sza.fastmediasorter.data.local.LocalMediaScanner
import com.sza.fastmediasorter.domain.model.launcher.LauncherCell
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellKind
import com.sza.fastmediasorter.domain.model.launcher.LauncherOrientation
import com.sza.fastmediasorter.domain.repository.DeviceProfileRepository
import com.sza.fastmediasorter.domain.repository.LauncherDesktopRepository
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.ProvisionDefaultResourcesUseCase
import com.sza.fastmediasorter.domain.usecase.apps.ResolveInstalledPackagesUseCase
import com.sza.fastmediasorter.domain.usecase.panel.ResolvePanelRouteAvailabilityUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * S0404: on first entry, fill an empty launcher desktop with a profile-appropriate starter set (ADR-4 -
 * the profile seeds the layout once, then the user owns it). Portrait and landscape are laid out
 * independently for their own column count. [LauncherDesktopRepository.seedIfEmpty] makes this a no-op
 * once a desktop exists, so a later profile change never overwrites the user's arrangement.
 *
 * This is a HOME-activity first-run path, so the whole body is wrapped in `runCatching`: a failed
 * dependency read (e.g. a Room exception) degrades to an empty desktop the user can fill, never a crash
 * loop on the device's own home surface (audit 2026-07-17, P1).
 */
class SeedLauncherDesktopUseCase @Inject constructor(
    private val desktop: LauncherDesktopRepository,
    private val profiles: DeviceProfileRepository,
    private val resources: ResourceRepository,
    private val settings: SettingsRepository,
    private val routeAvailability: ResolvePanelRouteAvailabilityUseCase,
    private val provisionDefaultResources: ProvisionDefaultResourcesUseCase,
    private val resolveInstalledPackages: ResolveInstalledPackagesUseCase,
    @ApplicationContext private val context: Context,
) {

    suspend operator fun invoke(portraitColumns: Int, landscapeColumns: Int): Unit = withContext(Dispatchers.IO) {
        runCatching {
            val state = desktop.state()
            if (state.seededPortrait && state.seededLandscape) return@runCatching

            // A device that becomes Home without ever opening MainActivity has not provisioned its
            // virtual resources yet; do it here (idempotent per slot) so the seed can include them.
            provisionDefaultResources()

            val allResources = resources.getAllResourcesSync()
            val resourceIds = allResources.mapTo(mutableSetOf()) { it.id }
            val profile = profiles.getCurrentProfile().first().type
            // Only seed resource-backed cells for ids that still exist, so a stale last-used id (its
            // resource since deleted) never becomes a permanently-dead tile.
            val lastResourceId = settings.getLastUsedResourceId().takeIf { it > 0L && it in resourceIds }
            fun idOf(path: String): Long? = allResources.firstOrNull { it.path == path }?.id
            val starterResources = LauncherStarterSets.StarterResources(
                recentId = idOf(LocalMediaScanner.VIRTUAL_PATH_RECENT),
                allAudioId = idOf(LocalMediaScanner.VIRTUAL_PATH_ALL_AUDIO),
                allImagesId = idOf(LocalMediaScanner.VIRTUAL_PATH_ALL_IMAGES),
                allVideoId = idOf(LocalMediaScanner.VIRTUAL_PATH_ALL_VIDEO),
                allDocsId = idOf(LocalMediaScanner.VIRTUAL_PATH_ALL_DOCS),
                cameraId = idOf(LocalMediaScanner.VIRTUAL_PATH_CAMERA_PHOTOS),
                lastResourceId = lastResourceId,
            )
            val routeAvailableInBuild = routeAvailability.all()
                .mapValues { (_, availability) -> availability.availableInBuild }
            // Behind the already-seeded early-exit above, so a desktop that will not be seeded never pays
            // for the package-manager probe (strategic §3.2).
            val installedPackages = resolveInstalledPackages(LauncherStarterSets.candidatePackages)

            val items = LauncherStarterSets.itemsFor(
                profile,
                starterResources,
                routeAvailableInBuild,
                installedPackages,
            )
            Timber.d(
                "S1428: seeding %d starter items, %d of them section headers",
                items.size,
                items.count { it.kind == LauncherCellKind.SECTION },
            )
            val ownPackage = context.packageName
            val now = System.currentTimeMillis()

            if (!state.seededPortrait) {
                seedOrientation(LauncherOrientation.PORTRAIT, portraitColumns, items, ownPackage, now)
            }
            if (!state.seededLandscape) {
                seedOrientation(LauncherOrientation.LANDSCAPE, landscapeColumns, items, ownPackage, now)
            }
        }.onFailure { Timber.w(it, "Launcher desktop seed failed; leaving desktop empty") }
        Unit
    }

    private suspend fun seedOrientation(
        orientation: LauncherOrientation,
        columns: Int,
        items: List<LauncherStarterSets.StarterItem>,
        ownPackage: String,
        now: Long,
    ) {
        val cells = LauncherStarterSets.place(items, columns).map { placed ->
            LauncherCell(
                id = 0,
                orientation = orientation,
                rowIndex = placed.rowIndex,
                colIndex = placed.colIndex,
                // Not placed.spanW: a section header is persisted at the widest grid it can ever be
                // drawn on, while the packer clamps it to the grid being seeded (S1428).
                spanW = placed.storedSpanW,
                spanH = placed.spanH,
                kind = placed.item.kind,
                target = placed.item.target.replace(LauncherStarterSets.OWN_APP_TOKEN, ownPackage),
                labelOverride = null,
                addedAt = now,
            )
        }
        Timber.d(
            "S1587: seeding $orientation at $columns columns, ${cells.size} cells, " +
                "last row ${cells.maxOfOrNull { it.rowIndex + it.spanH } ?: 0}",
        )
        desktop.seedIfEmpty(orientation, cells)
    }
}
