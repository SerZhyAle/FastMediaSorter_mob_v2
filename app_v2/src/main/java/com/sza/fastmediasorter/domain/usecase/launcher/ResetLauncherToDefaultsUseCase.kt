package com.sza.fastmediasorter.domain.usecase.launcher

import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.repository.InstalledAppsRepository
import com.sza.fastmediasorter.domain.repository.LauncherDesktopRepository
import com.sza.fastmediasorter.domain.repository.LauncherJournalRepository
import com.sza.fastmediasorter.domain.repository.LauncherPinsRepository
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
 *
 * A ticket that introduces a new launcher-owned store must extend this list, otherwise the reset goes
 * silently incomplete.
 *
 * Deliberately outside the inventory: the launcher-mode toggle (it lives in General settings, and
 * switching it off here would strand the user inside a dialog of a mode that no longer exists), the
 * system HOME role, and the cached list of installed apps (a rebuildable mirror of the device, not
 * launcher state).
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
) {

    /** Returns whether the reset completed, so the caller can tell the user it did not happen. */
    suspend operator fun invoke(densityFactor: Float): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            desktop.clearAll()
            pins.clearPins()
            journal.clearJournal()
            installedApps.clearLaunchStats()

            restoreLauncherSettings(densityFactor)
            storeLauncherWallpaperUseCase.clear()
            true
        }.getOrElse { error ->
            Timber.e(error, "Launcher reset failed")
            false
        }
    }

    /**
     * Copies back the launcher fields one by one instead of replacing the whole settings object,
     * so nothing outside the launcher is touched.
     */
    private suspend fun restoreLauncherSettings(densityFactor: Float) {
        val defaults = AppSettings().copy(launcherDensityFactor = densityFactor)
        settings.updateSettings { current ->
            current.copy(
                launcherDensityFactor = defaults.launcherDensityFactor,
                launcherTaskbarShowRecents = defaults.launcherTaskbarShowRecents,
                launcherTaskbarShowPinned = defaults.launcherTaskbarShowPinned,
                launcherTaskbarShowTray = defaults.launcherTaskbarShowTray,
                launcherReplaceSystemStatusArea = defaults.launcherReplaceSystemStatusArea,
                launcherTopStatusStripMode = defaults.launcherTopStatusStripMode,
                launcherForeignNotificationsEnabled = defaults.launcherForeignNotificationsEnabled,
                launcherTaskbarPlacement = defaults.launcherTaskbarPlacement,
                launcherTrayShowClock = defaults.launcherTrayShowClock,
                launcherTrayShowBluetooth = defaults.launcherTrayShowBluetooth,
                launcherTrayShowSim1 = defaults.launcherTrayShowSim1,
                launcherTrayShowSim2 = defaults.launcherTrayShowSim2,
                launcherTrayShowNetwork = defaults.launcherTrayShowNetwork,
                launcherTrayShowBattery = defaults.launcherTrayShowBattery,
                launcherTrayShowSpeed = defaults.launcherTrayShowSpeed,
                launcherRotationHintShown = defaults.launcherRotationHintShown,
                launcherDesktopLocked = defaults.launcherDesktopLocked,
                launcherWallpaperMode = defaults.launcherWallpaperMode,
                launcherWallpaperImagePath = defaults.launcherWallpaperImagePath,
                launcherWallpaperCameraId = defaults.launcherWallpaperCameraId,
                launcherScreenBlackoutTimeoutSeconds = defaults.launcherScreenBlackoutTimeoutSeconds,
                launcherWidgetBackdropAlpha = defaults.launcherWidgetBackdropAlpha,
            )
        }
    }
}
