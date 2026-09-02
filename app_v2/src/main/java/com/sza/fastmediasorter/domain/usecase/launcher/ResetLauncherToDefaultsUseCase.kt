package com.sza.fastmediasorter.domain.usecase.launcher

import com.sza.fastmediasorter.domain.launcher.ConfiguredWidgetInstanceCleaner
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.launcher.LauncherSettings
import com.sza.fastmediasorter.domain.repository.InstalledAppsRepository
import com.sza.fastmediasorter.domain.repository.LauncherDesktopRepository
import com.sza.fastmediasorter.domain.repository.LauncherJournalRepository
import com.sza.fastmediasorter.domain.repository.LauncherPinsRepository
import com.sza.fastmediasorter.domain.repository.LauncherShortcutSyncRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * S1400: puts the launcher back into the state a fresh install has.
 *
 * This is the single place that knows the full inventory of launcher-owned state, because the defect
 * this ticket answers is a state that gets forgotten in one of several scattered call sites. The
 * inventory:
 *
 * 1. Desktop cells of both orientations, plus the desktop-wide state row - the seeded flags go with it,
 *    which is what lets the launcher lay the starter set out again.
 * 2. Taskbar pins.
 * 3. The launch journal that feeds the recent strip.
 * 4. The launch statistics that feed the "most used" order.
 * 5. The launcher-scoped fields of [AppSettings] - and only those.
 * 6. The private copy of a user-picked wallpaper image.
 * 7. S2217: the stored instances behind configurable widget cells, cleared through
 *    [ConfiguredWidgetInstanceCleaner] - the gadget codec lives in the launcher flavor source set,
 *    so the delete hands its removed rows' targets to a seam instead of reading them here.
 * 8. S2330: the shortcut-sync baseline - the set of routes the desktop has already accounted for.
 *    Cleared to absent rather than to empty, so the re-seeded desktop is adopted silently the way a
 *    fresh install adopts it, instead of every launchable route reading as newly enabled.
 *
 * A ticket that introduces a new launcher-owned store must extend this list, otherwise the reset goes
 * silently incomplete.
 *
 * Deliberately outside the inventory: the launcher-mode toggle (it lives in General settings, and
 * switching it off here would strand the user inside a dialog of a mode that no longer exists), the
 * system HOME role, and the cached list of installed apps (a rebuildable mirror of the device, not
 * launcher state).
 *
 * S2213: also outside it, and this one must stay outside - the place the user last picked for a weather
 * gadget. The reset clears the desktop and the launcher re-seeds the starter set, so a weather cell comes
 * back without a place of its own; clearing the remembered one too would return an empty block and make
 * the user search for his city again after every reset. The desktop layout is what the reset restores -
 * a choice the user made is not the layout.
 *
 * S1613: also outside it, and this one must stay outside - the platform's record of which shortcuts other
 * apps pinned to this launcher. That record is precisely what the desktop seed reads back to restore them
 * after this reset, so releasing the pins here would delete the restore silently instead of failing
 * visibly. Clearing our own cells is the whole job; the pins are not ours to drop.
 *
 * S1886: `defaults` inside [restoreLauncherSettings] is not plain factory state - it is the state the reset
 * brings settings to, which is the factory value of every launcher field except the icon density, supplied
 * by the reset dialog.
 *
 * Re-seeding the starter desktop is NOT done here (strategic ADR-2, rewritten 2026-08-06). Only the
 * launcher knows the real grid geometry: the widths persisted in the desktop state cover just the
 * orientation that has actually been rendered, so a device that never rotated stores zero for the
 * other one. Clearing the seeded flags is the whole contract - the launcher seeds again from the
 * geometry it resolves at draw time.
 */
class ResetLauncherToDefaultsUseCase @Inject constructor(
    private val desktop: LauncherDesktopRepository,
    private val pins: LauncherPinsRepository,
    private val journal: LauncherJournalRepository,
    private val installedApps: InstalledAppsRepository,
    private val settings: SettingsRepository,
    private val storeLauncherWallpaperUseCase: StoreLauncherWallpaperUseCase,
    private val configuredWidgetInstances: ConfiguredWidgetInstanceCleaner,
    private val shortcutSyncBaseline: LauncherShortcutSyncRepository,
) {

    /** Returns whether the reset completed, so the caller can tell the user it did not happen. */
    suspend operator fun invoke(densityFactor: Float): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            // S2217: the deleted rows are the only record of which configured widget instances
            // existed - their targets come back from the delete and go straight through the seam,
            // before the rest of the inventory clears anything else.
            val clearedTargets = desktop.clearAll()
            clearedTargets.forEach { configuredWidgetInstances.clearInstanceOf(it) }
            pins.clearPins()
            journal.clearJournal()
            installedApps.clearLaunchStats()
            shortcutSyncBaseline.clearSyncedRoutes()

            restoreLauncherSettings(densityFactor)
            storeLauncherWallpaperUseCase.clear()
            true
        }.getOrElse { error ->
            Timber.e(error, "Launcher reset failed")
            false
        }
    }

    /**
     * Replaces the launcher group with its defaults, leaving every other setting untouched.
     *
     * S2300: the group is one nested field, so the reset is a single assignment - it can no longer fall
     * behind by missing a launcher setting added later.
     */
    private suspend fun restoreLauncherSettings(densityFactor: Float) {
        val defaults = LauncherSettings(densityFactor = densityFactor)
        settings.updateSettings { current ->
            // S1401/S2213: the all-apps order and the remembered weather place survive a desktop reset -
            // the first is not desktop state, the second must outlive the cell that displays it.
            current.copy(
                launcher = defaults.copy(
                    allAppsSortOrder = current.launcher.allAppsSortOrder,
                    allAppsSortDescending = current.launcher.allAppsSortDescending,
                    weatherLastLocation = current.launcher.weatherLastLocation,
                ),
            )
        }
    }
}
