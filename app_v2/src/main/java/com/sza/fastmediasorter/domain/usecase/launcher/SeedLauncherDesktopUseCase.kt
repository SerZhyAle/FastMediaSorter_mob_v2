package com.sza.fastmediasorter.domain.usecase.launcher

import android.content.Context
import com.sza.fastmediasorter.core.launcher.LauncherStarterSets
import com.sza.fastmediasorter.core.util.GmsAvailabilityChecker
import com.sza.fastmediasorter.data.launcher.AppShortcutDataSource
import com.sza.fastmediasorter.data.local.LocalMediaScanner
import com.sza.fastmediasorter.domain.model.launcher.LauncherCell
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellKind
import com.sza.fastmediasorter.domain.model.launcher.LauncherOrientation
import com.sza.fastmediasorter.domain.repository.DeviceProfileRepository
import com.sza.fastmediasorter.domain.repository.LauncherDesktopRepository
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.ProvisionDefaultResourcesUseCase
import com.sza.fastmediasorter.domain.usecase.apps.QueryThirdPartyAppsUseCase
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
 * S1644: the launcher reset needs no code of its own to rebuild the conditional Google section.
 * [ResetLauncherToDefaultsUseCase] calls `clearAll`, which deletes the desktop state row, and
 * `state()` then answers from defaults with both seeded flags false - so the next draw re-enters this
 * seed and re-resolves the services verdict and the installed set against the device as it is now,
 * rather than replaying whatever was true at first install (verified 2026-08-16).
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
    private val queryThirdPartyApps: QueryThirdPartyAppsUseCase,
    private val appShortcuts: AppShortcutDataSource,
    @ApplicationContext private val context: Context,
) {

    suspend operator fun invoke(portraitColumns: Int, landscapeColumns: Int): Unit = withContext(Dispatchers.IO) {
        runCatching {
            // S1642: ahead of the already-seeded exit on purpose - a desktop seeded by an earlier build is
            // exactly the one carrying full-row headers, and it is the one this would otherwise skip.
            desktop.normalizeSectionSpans()

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

            val virtualPaths = setOf(
                LocalMediaScanner.VIRTUAL_PATH_RECENT,
                LocalMediaScanner.VIRTUAL_PATH_ALL_AUDIO,
                LocalMediaScanner.VIRTUAL_PATH_ALL_IMAGES,
                LocalMediaScanner.VIRTUAL_PATH_ALL_VIDEO,
                LocalMediaScanner.VIRTUAL_PATH_ALL_DOCS,
                LocalMediaScanner.VIRTUAL_PATH_CAMERA_PHOTOS,
            )
            val userResourceIds = allResources
                .filterNot { it.path in virtualPaths }
                .map { it.id }

            val starterResources = LauncherStarterSets.StarterResources(
                recentId = idOf(LocalMediaScanner.VIRTUAL_PATH_RECENT),
                allAudioId = idOf(LocalMediaScanner.VIRTUAL_PATH_ALL_AUDIO),
                allImagesId = idOf(LocalMediaScanner.VIRTUAL_PATH_ALL_IMAGES),
                allVideoId = idOf(LocalMediaScanner.VIRTUAL_PATH_ALL_VIDEO),
                allDocsId = idOf(LocalMediaScanner.VIRTUAL_PATH_ALL_DOCS),
                cameraId = idOf(LocalMediaScanner.VIRTUAL_PATH_CAMERA_PHOTOS),
                lastResourceId = lastResourceId,
                userResourceIds = userResourceIds,
            )
            val routeAvailableInBuild = routeAvailability.all()
                .mapValues { (_, availability) -> availability.availableInBuild }
            // Behind the already-seeded early-exit above, so a desktop that will not be seeded never pays
            // for the package-manager probe (strategic §3.2).
            val installedPackages = resolveInstalledPackages(LauncherStarterSets.candidatePackages)
            // S1644: one live probe per seed, behind the same early exit as the package snapshot, so a
            // desktop that will not be seeded never pays for it. Outdated services still launch the
            // installed Google apps, so UPDATE_REQUIRED counts as present - only a device that has no
            // Play Services at all goes without the section (strategic §6.2).
            val googleServicesAvailable = when (GmsAvailabilityChecker.evaluateLive(context)) {
                GmsAvailabilityChecker.Status.OK,
                GmsAvailabilityChecker.Status.UPDATE_REQUIRED -> true
                GmsAvailabilityChecker.Status.UNAVAILABLE -> false
            }
            // S1613: behind the same early exit, so a desktop that will not be seeded never pays for it.
            val importedShortcuts = appShortcuts.allPinned().map { shortcut ->
                LauncherStarterSets.StarterItem(
                    kind = LauncherCellKind.SHORTCUT,
                    target = LauncherCellCommand.PinnedShortcut(
                        packageName = shortcut.packageName,
                        shortcutId = shortcut.id,
                        label = shortcut.label,
                    ).encode(),
                )
            }

            // S2015: the user's own applications for the Apps section, behind the same early exit as
            // every other probe above. The exclusion set is the whole starter table's candidate list,
            // which already contains GOOGLE_APP_PACKAGES - so whatever the Google section takes, and
            // whatever the table can place by name, cannot come back a second time through this list.
            val thirdPartyApps = queryThirdPartyApps(LauncherStarterSets.candidatePackages)

            val items = LauncherStarterSets.itemsFor(
                profile,
                starterResources,
                routeAvailableInBuild,
                installedPackages,
                googleServicesAvailable = googleServicesAvailable,
                importedShortcuts = importedShortcuts,
                thirdPartyApps = thirdPartyApps,
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
                spanW = placed.spanW,
                spanH = placed.spanH,
                kind = placed.item.kind,
                target = placed.item.target.replace(LauncherStarterSets.OWN_APP_TOKEN, ownPackage),
                labelOverride = null,
                addedAt = now,
            )
        }
        desktop.seedIfEmpty(orientation, cells)
    }
}
