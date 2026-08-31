package com.sza.fastmediasorter.data.repository.settings

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.LauncherDesktopSwipeAction
import com.sza.fastmediasorter.domain.model.LauncherDesktopSwipeDirection
import com.sza.fastmediasorter.domain.model.ScreenshotGestureAction
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
        assertTrue(values.launcherDesktopDoubleTapLockEnabled)
        assertFalse(values.allAppsSortDescending)
        assertEquals("", values.launcherWallpaperImagePath)
        assertEquals(0, values.launcherScreenBlackoutTimeoutSeconds)
        // S2253 ADR: the shared launcher backdrop starts fully transparent, so a fresh install shows
        // the wallpaper through every plate. Pinned as a literal - reading the constant the store
        // itself reads would compare it with itself and pin nothing.
        assertEquals(0.0f, values.launcherWidgetBackdropAlpha, 0.0f)
        assertEquals(AppSettings.LAUNCHER_TASKBAR_PLACEMENT_BOTTOM, values.launcherTaskbarPlacement)
        assertEquals(AppSettings.LAUNCHER_WALLPAPER_BRANDED, values.launcherWallpaperMode)
        // S2213: no saved place yet is the state a fresh install is in, and the branch a device pass is
        // least likely to reach - the tester has picked a city before he thinks to test this.
        assertEquals("", values.launcherWeatherLastLocation)
    }

    @Test
    fun `all-apps sort direction round-trips`() {
        val prefs = mutablePreferencesOf()

        LauncherSettingsStore.write(prefs, AppSettings(allAppsSortDescending = true))

        assertTrue(LauncherSettingsStore.read(prefs).allAppsSortDescending)
    }

    @Test
    fun `desktop swipe keeps a shared edge-gesture action`() {
        val settings = AppSettings(
            launcherDesktopSwipeLeftAction = LauncherDesktopSwipeAction.EdgeGestureAction(
                ScreenshotGestureAction.TOGGLE_FLASHLIGHT,
            ),
            launcherDesktopSwipeLeftPayload = "com.example.flashlight",
        )
        val prefs = mutablePreferencesOf()

        LauncherSettingsStore.write(prefs, settings)

        assertEquals(
            settings.launcherDesktopSwipeLeftAction,
            LauncherSettingsStore.read(prefs).launcherDesktopSwipeLeftAction,
        )
        assertEquals(
            settings.launcherDesktopSwipeLeftPayload,
            LauncherSettingsStore.read(prefs).launcherDesktopSwipeLeftPayload,
        )
    }

    /**
     * S2256: the launcher route now exists twice - as the desktop-local [LauncherDesktopSwipeAction.OpenAllApps]
     * and as the shared [ScreenshotGestureAction.OPEN_ALL_APPS] - and both spell the same persisted token.
     * The desktop parser must keep resolving that token to the local value, so the desktop swipe reuses the
     * already-open home task instead of routing through the overlay seam.
     */
    @Test
    fun `desktop swipe resolves the shared all-apps token to the launcher-local value`() {
        val settings = AppSettings(launcherDesktopSwipeUpAction = LauncherDesktopSwipeAction.OpenAllApps)
        val prefs = mutablePreferencesOf()

        LauncherSettingsStore.write(prefs, settings)

        assertEquals(
            LauncherDesktopSwipeAction.OpenAllApps,
            LauncherSettingsStore.read(prefs).launcherDesktopSwipeUpAction,
        )
        assertEquals(
            ScreenshotGestureAction.OPEN_ALL_APPS.name,
            LauncherDesktopSwipeAction.OpenAllApps.persistedName,
        )
    }

    @Test
    fun `desktop swipe falls back to the direction default for an unknown token`() {
        val fallback = LauncherDesktopSwipeAction.EdgeGestureAction(ScreenshotGestureAction.DO_NOT_USE)

        assertEquals(fallback, LauncherDesktopSwipeAction.fromName("NO_SUCH_ACTION", fallback))
    }

    /**
     * S2256: the swipe target is now user-editable per direction, so the field a direction writes and the
     * field it reads have to be the same one - four near-identical `copy` arms are exactly where that goes
     * wrong, and nothing else would notice a direction writing into its neighbour's payload.
     */
    @Test
    fun `each direction writes and reads its own target`() {
        LauncherDesktopSwipeDirection.entries.forEach { direction ->
            val prefs = mutablePreferencesOf()
            val written = direction.withPayload(AppSettings(), "target-for-$direction")

            LauncherSettingsStore.write(prefs, written)
            val read = LauncherSettingsStore.read(prefs)

            assertEquals("target-for-$direction", read.payloadOf(direction))
            LauncherDesktopSwipeDirection.entries
                .filter { it != direction }
                .forEach { other -> assertEquals("", read.payloadOf(other)) }
        }
    }

    @Test
    fun `clearing a target empties only that direction`() {
        var settings = AppSettings()
        LauncherDesktopSwipeDirection.entries.forEach { settings = it.withPayload(settings, "https://example.com") }
        settings = LauncherDesktopSwipeDirection.LEFT.withPayload(settings, "")
        val prefs = mutablePreferencesOf()

        LauncherSettingsStore.write(prefs, settings)
        val read = LauncherSettingsStore.read(prefs)

        assertEquals("", read.payloadOf(LauncherDesktopSwipeDirection.LEFT))
        LauncherDesktopSwipeDirection.entries
            .filter { it != LauncherDesktopSwipeDirection.LEFT }
            .forEach { assertEquals("https://example.com", read.payloadOf(it)) }
    }

    /**
     * S2256: an empty target keeps today's fallback, so the dispatcher needs no new branch for it - the
     * action still round-trips, and only the payload is blank.
     */
    @Test
    fun `an action with an empty target survives the round trip unchanged`() {
        val action = LauncherDesktopSwipeAction.EdgeGestureAction(ScreenshotGestureAction.OPEN_URL)
        val settings = LauncherDesktopSwipeDirection.DOWN.withAction(AppSettings(), action)
        val prefs = mutablePreferencesOf()

        LauncherSettingsStore.write(prefs, settings)
        val read = LauncherSettingsStore.read(prefs)

        assertEquals(action, read.actionOf(LauncherDesktopSwipeDirection.DOWN))
        assertEquals("", read.payloadOf(LauncherDesktopSwipeDirection.DOWN))
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
            launcherDesktopDoubleTapLockEnabled = false,
            launcherDesktopSwipeUpPayload = "https://example.com",
            launcherDesktopSwipeDownPayload = "com.example.app",
            launcherDesktopSwipeLeftPayload = "https://example.org",
            launcherDesktopSwipeRightPayload = "com.example.other",
            launcherWallpaperImagePath = "/storage/emulated/0/wall.png",
            allAppsSortDescending = true,
            launcherScreenBlackoutTimeoutSeconds = 45,
            launcherWidgetBackdropAlpha = 0.25f,
            launcherWeatherLastLocation = "50.45,30.52,Kyiv",
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
        assertEquals(settings.launcherDesktopDoubleTapLockEnabled, values.launcherDesktopDoubleTapLockEnabled)
        assertEquals(settings.launcherDesktopSwipeUpPayload, values.launcherDesktopSwipeUpPayload)
        assertEquals(settings.launcherDesktopSwipeDownPayload, values.launcherDesktopSwipeDownPayload)
        assertEquals(settings.launcherDesktopSwipeLeftPayload, values.launcherDesktopSwipeLeftPayload)
        assertEquals(settings.launcherDesktopSwipeRightPayload, values.launcherDesktopSwipeRightPayload)
        assertEquals(settings.launcherWallpaperImagePath, values.launcherWallpaperImagePath)
        assertEquals(settings.allAppsSortDescending, values.allAppsSortDescending)
        assertEquals(
            settings.launcherScreenBlackoutTimeoutSeconds,
            values.launcherScreenBlackoutTimeoutSeconds,
        )
        assertEquals(settings.launcherWidgetBackdropAlpha, values.launcherWidgetBackdropAlpha, 0.0f)
        assertEquals(settings.launcherTaskbarPlacement, values.launcherTaskbarPlacement)
        assertEquals(settings.launcherWallpaperMode, values.launcherWallpaperMode)
        assertEquals(settings.allAppsSortOrder, values.allAppsSortOrder)
        assertEquals(settings.launcherWeatherLastLocation, values.launcherWeatherLastLocation)
    }

    // `read` answers with the store's own `Values`, not `AppSettings`, so the production direction
    // accessors cannot be reused on the read side. The write side still goes through them, which is the
    // mapping these tests are here to pin.
    private fun LauncherSettingsStore.Values.payloadOf(direction: LauncherDesktopSwipeDirection): String =
        when (direction) {
            LauncherDesktopSwipeDirection.UP -> launcherDesktopSwipeUpPayload
            LauncherDesktopSwipeDirection.DOWN -> launcherDesktopSwipeDownPayload
            LauncherDesktopSwipeDirection.LEFT -> launcherDesktopSwipeLeftPayload
            LauncherDesktopSwipeDirection.RIGHT -> launcherDesktopSwipeRightPayload
        }

    private fun LauncherSettingsStore.Values.actionOf(
        direction: LauncherDesktopSwipeDirection,
    ): LauncherDesktopSwipeAction = when (direction) {
        LauncherDesktopSwipeDirection.UP -> launcherDesktopSwipeUpAction
        LauncherDesktopSwipeDirection.DOWN -> launcherDesktopSwipeDownAction
        LauncherDesktopSwipeDirection.LEFT -> launcherDesktopSwipeLeftAction
        LauncherDesktopSwipeDirection.RIGHT -> launcherDesktopSwipeRightAction
    }
}
