package com.sza.fastmediasorter.data.repository.settings

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.sza.fastmediasorter.domain.model.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S1798: regression coverage for [LauncherSettingsStore]. The store methods are pure over
 * `Preferences`, so an in-memory `mutablePreferencesOf()` exercises the same read/write path
 * used on device without Robolectric.
 *
 * The defect this pins: `all_apps_sort_descending` used to be written and never read, so the
 * all-apps sort direction snapped back to its default on the next emission. A write-only key
 * is invisible to a write-then-read assertion only if the assertion is missing, hence the
 * round-trip test below covers every field the store persists, not just the one that broke.
 */
class LauncherSettingsStoreTest {

    @Test
    fun `absent keys resolve to the documented defaults`() {
        val values = LauncherSettingsStore.read(mutablePreferencesOf())

        assertEquals(1.0f, values.launcherDensityFactor, 0.0f)
        assertTrue(values.launcherTaskbarShowRecents)
        assertTrue(values.launcherTrayShowBattery)
        // S2017: the tray clock is the one taskbar exception - off by default, duplicates the top bar clock.
        assertFalse(values.launcherTrayShowClock)
        // S2017: system status bar hidden by default.
        assertTrue(values.launcherReplaceSystemStatusArea)
        // S2017 ADR-1: default flipped to ON, superseding S1465 ADR-4's off-by-default rationale.
        assertTrue(values.launcherForeignNotificationsEnabled)
        assertFalse(values.launcherDesktopLocked)
        assertFalse(values.allAppsSortDescending)
        assertEquals("", values.launcherWallpaperImagePath)
        assertEquals(0, values.launcherScreenBlackoutTimeoutSeconds)
        assertEquals(AppSettings.LAUNCHER_TASKBAR_PLACEMENT_BOTTOM, values.launcherTaskbarPlacement)
        assertEquals(AppSettings.LAUNCHER_WALLPAPER_BRANDED, values.launcherWallpaperMode)
    }

    @Test
    fun `all-apps sort direction round-trips`() {
        val prefs = mutablePreferencesOf()

        LauncherSettingsStore.write(prefs, AppSettings(allAppsSortDescending = true))

        assertTrue(LauncherSettingsStore.read(prefs).allAppsSortDescending)
    }

    @Test
    fun `every persisted launcher field round-trips through write then read`() {
        val settings = AppSettings(
            launcherDensityFactor = 1.5f,
            launcherTaskbarShowRecents = false,
            launcherTaskbarShowPinned = false,
            launcherTaskbarShowTray = false,
            launcherTrayShowClock = false,
            launcherTrayShowBluetooth = false,
            launcherTrayShowSim1 = false,
            launcherTrayShowSim2 = false,
            launcherTrayShowNetwork = false,
            launcherTrayShowBattery = false,
            launcherReplaceSystemStatusArea = true,
            launcherTopStatusStripMode = true,
            launcherForeignNotificationsEnabled = true,
            launcherRotationHintShown = true,
            launcherDesktopLocked = true,
            launcherWallpaperImagePath = "/storage/emulated/0/wall.png",
            allAppsSortDescending = true,
            launcherScreenBlackoutTimeoutSeconds = 45,
        )

        val prefs = mutablePreferencesOf()
        LauncherSettingsStore.write(prefs, settings)
        val values = LauncherSettingsStore.read(prefs)

        assertEquals(settings.launcherDensityFactor, values.launcherDensityFactor, 0.0f)
        assertEquals(settings.launcherTaskbarShowRecents, values.launcherTaskbarShowRecents)
        assertEquals(settings.launcherTaskbarShowPinned, values.launcherTaskbarShowPinned)
        assertEquals(settings.launcherTaskbarShowTray, values.launcherTaskbarShowTray)
        assertEquals(settings.launcherTrayShowClock, values.launcherTrayShowClock)
        assertEquals(settings.launcherTrayShowBluetooth, values.launcherTrayShowBluetooth)
        assertEquals(settings.launcherTrayShowSim1, values.launcherTrayShowSim1)
        assertEquals(settings.launcherTrayShowSim2, values.launcherTrayShowSim2)
        assertEquals(settings.launcherTrayShowNetwork, values.launcherTrayShowNetwork)
        assertEquals(settings.launcherTrayShowBattery, values.launcherTrayShowBattery)
        assertEquals(settings.launcherReplaceSystemStatusArea, values.launcherReplaceSystemStatusArea)
        assertEquals(settings.launcherTopStatusStripMode, values.launcherTopStatusStripMode)
        assertEquals(
            settings.launcherForeignNotificationsEnabled,
            values.launcherForeignNotificationsEnabled,
        )
        assertEquals(settings.launcherRotationHintShown, values.launcherRotationHintShown)
        assertEquals(settings.launcherDesktopLocked, values.launcherDesktopLocked)
        assertEquals(settings.launcherWallpaperImagePath, values.launcherWallpaperImagePath)
        assertEquals(settings.allAppsSortDescending, values.allAppsSortDescending)
        assertEquals(
            settings.launcherScreenBlackoutTimeoutSeconds,
            values.launcherScreenBlackoutTimeoutSeconds,
        )
        assertEquals(settings.launcherTaskbarPlacement, values.launcherTaskbarPlacement)
        assertEquals(settings.launcherWallpaperMode, values.launcherWallpaperMode)
        assertEquals(settings.allAppsSortOrder, values.allAppsSortOrder)
    }
}
