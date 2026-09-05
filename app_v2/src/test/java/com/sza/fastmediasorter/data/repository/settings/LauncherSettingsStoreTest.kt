package com.sza.fastmediasorter.data.repository.settings

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.LauncherDesktopSwipeAction
import com.sza.fastmediasorter.domain.model.LauncherDesktopSwipeDirection
import com.sza.fastmediasorter.domain.model.ScreenshotGestureAction
import com.sza.fastmediasorter.domain.model.launcher.LauncherSettings
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

        // S2320: dense is the shipped grid density; literal for the same reason as the alpha below.
        assertEquals(1.25f, values.densityFactor, 0.0f)
        assertTrue(values.taskbarShowRecents)
        assertTrue(values.trayShowBattery)
        // S2017: the tray clock is the one taskbar exception - off by default, duplicates the top bar clock.
        assertFalse(values.trayShowClock)
        // S2017: system status bar hidden by default.
        assertTrue(values.replaceSystemStatusArea)
        // S2017 ADR-1: default flipped to ON, superseding S1465 ADR-4's off-by-default rationale.
        assertTrue(values.foreignNotificationsEnabled)
        assertFalse(values.desktopLocked)
        assertTrue(values.desktopDoubleTapLockEnabled)
        assertFalse(values.allAppsSortDescending)
        assertEquals("", values.wallpaperImagePath)
        assertEquals(0, values.screenBlackoutTimeoutSeconds)
        // S2320: the shared launcher backdrop starts at 25% opacity, so a fresh install reads its
        // surfaces as plates over the wallpaper. Pinned as a literal - reading the constant the store
        // itself reads would compare it with itself and pin nothing.
        assertEquals(0.25f, values.widgetBackdropAlpha, 0.0f)
        assertEquals(AppSettings.LAUNCHER_TASKBAR_PLACEMENT_BOTTOM, values.taskbarPlacement)
        assertEquals(AppSettings.LAUNCHER_WALLPAPER_BRANDED, values.wallpaperMode)
        // S2213: no saved place yet is the state a fresh install is in, and the branch a device pass is
        // least likely to reach - the tester has picked a city before he thinks to test this.
        assertEquals("", values.weatherLastLocation)
        // S2223: dynamic palette by default.
        assertEquals(AppSettings.ANIMATION_PALETTE_DYNAMIC, values.animationPalette)
    }

    @Test
    fun `a stored backdrop alpha outside the option list reads back as the nearest option`() {
        val prefs = mutablePreferencesOf()

        // 0.02f was the shipped default until S2320 removed that option, so it is the value an
        // upgraded install actually carries. ADR-2 keeps such an install looking as it did: the read
        // resolves to the nearest option rather than to the new 25% default.
        LauncherSettingsStore.write(prefs, AppSettings(launcher = LauncherSettings(widgetBackdropAlpha = 0.02f)))

        assertEquals(0.0f, LauncherSettingsStore.read(prefs).widgetBackdropAlpha, 0.0f)
    }

    @Test
    fun `all-apps sort direction round-trips`() {
        val prefs = mutablePreferencesOf()

        LauncherSettingsStore.write(prefs, AppSettings(launcher = LauncherSettings(allAppsSortDescending = true)))

        assertTrue(LauncherSettingsStore.read(prefs).allAppsSortDescending)
    }

    @Test
    fun `desktop swipe keeps a shared edge-gesture action`() {
        val settings = AppSettings(
            launcher = LauncherSettings(
                desktopSwipeLeftAction = LauncherDesktopSwipeAction.EdgeGestureAction(
                    ScreenshotGestureAction.TOGGLE_FLASHLIGHT,
                ),
                desktopSwipeLeftPayload = "com.example.flashlight",
            ),
        )
        val prefs = mutablePreferencesOf()

        LauncherSettingsStore.write(prefs, settings)

        assertEquals(
            settings.launcherDesktopSwipeLeftAction,
            LauncherSettingsStore.read(prefs).desktopSwipeLeftAction,
        )
        assertEquals(
            settings.launcherDesktopSwipeLeftPayload,
            LauncherSettingsStore.read(prefs).desktopSwipeLeftPayload,
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
        val settings = AppSettings(
            launcher = LauncherSettings(desktopSwipeUpAction = LauncherDesktopSwipeAction.OpenAllApps)
        )
        val prefs = mutablePreferencesOf()

        LauncherSettingsStore.write(prefs, settings)

        assertEquals(
            LauncherDesktopSwipeAction.OpenAllApps,
            LauncherSettingsStore.read(prefs).desktopSwipeUpAction,
        )
        assertEquals(
            ScreenshotGestureAction.OPEN_ALL_APPS.name,
            LauncherDesktopSwipeAction.OpenAllApps.persistedName,
        )
    }

    /**
     * S2301: the two paging routes are launcher-local values with no enum constant behind them, so a
     * slot holding one round-trips only while the parser resolves its token before the enum scan.
     */
    @Test
    fun `desktop swipe round-trips the screen paging routes`() {
        val settings = AppSettings(
            launcher = LauncherSettings(
                desktopSwipeLeftAction = LauncherDesktopSwipeAction.NextScreen,
                desktopSwipeRightAction = LauncherDesktopSwipeAction.PreviousScreen,
            )
        )
        val prefs = mutablePreferencesOf()

        LauncherSettingsStore.write(prefs, settings)

        val read = LauncherSettingsStore.read(prefs)
        assertEquals(LauncherDesktopSwipeAction.NextScreen, read.desktopSwipeLeftAction)
        assertEquals(LauncherDesktopSwipeAction.PreviousScreen, read.desktopSwipeRightAction)
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
            launcher = LauncherSettings(
                densityFactor = 1.5f,
                taskbarShowRecents = false,
                taskbarShowPinned = false,
                taskbarShowTray = false,
                trayShowClock = false,
                trayShowBluetooth = false,
                trayShowSim1 = false,
                trayShowSim2 = false,
                trayShowNetwork = false,
                trayShowBattery = false,
                replaceSystemStatusArea = true,
                topStatusStripMode = true,
                foreignNotificationsEnabled = true,
                rotationHintShown = true,
                desktopLocked = true,
                desktopDoubleTapLockEnabled = false,
                desktopSwipeUpPayload = "https://example.com",
                desktopSwipeDownPayload = "com.example.app",
                desktopSwipeLeftPayload = "https://example.org",
                desktopSwipeRightPayload = "com.example.other",
                wallpaperImagePath = "/storage/emulated/0/wall.png",
                allAppsSortDescending = true,
                screenBlackoutTimeoutSeconds = 45,
                widgetBackdropAlpha = 0.25f,
                weatherLastLocation = "50.45,30.52,Kyiv",
                animationPalette = AppSettings.ANIMATION_PALETTE_GREEN,
            ),
        )

        val prefs = mutablePreferencesOf()
        LauncherSettingsStore.write(prefs, settings)
        val values = LauncherSettingsStore.read(prefs)

        assertEquals(settings.launcherDensityFactor, values.densityFactor, 0.0f)
        assertEquals(settings.launcherTaskbarShowRecents, values.taskbarShowRecents)
        assertEquals(settings.launcherTaskbarShowPinned, values.taskbarShowPinned)
        assertEquals(settings.launcherTaskbarShowTray, values.taskbarShowTray)
        assertEquals(settings.launcherTrayShowClock, values.trayShowClock)
        assertEquals(settings.launcherTrayShowBluetooth, values.trayShowBluetooth)
        assertEquals(settings.launcherTrayShowSim1, values.trayShowSim1)
        assertEquals(settings.launcherTrayShowSim2, values.trayShowSim2)
        assertEquals(settings.launcherTrayShowNetwork, values.trayShowNetwork)
        assertEquals(settings.launcherTrayShowBattery, values.trayShowBattery)
        assertEquals(settings.launcherReplaceSystemStatusArea, values.replaceSystemStatusArea)
        assertEquals(settings.launcherTopStatusStripMode, values.topStatusStripMode)
        assertEquals(
            settings.launcherForeignNotificationsEnabled,
            values.foreignNotificationsEnabled,
        )
        assertEquals(settings.launcherRotationHintShown, values.rotationHintShown)
        assertEquals(settings.launcherDesktopLocked, values.desktopLocked)
        assertEquals(settings.launcherDesktopDoubleTapLockEnabled, values.desktopDoubleTapLockEnabled)
        assertEquals(settings.launcherDesktopSwipeUpPayload, values.desktopSwipeUpPayload)
        assertEquals(settings.launcherDesktopSwipeDownPayload, values.desktopSwipeDownPayload)
        assertEquals(settings.launcherDesktopSwipeLeftPayload, values.desktopSwipeLeftPayload)
        assertEquals(settings.launcherDesktopSwipeRightPayload, values.desktopSwipeRightPayload)
        assertEquals(settings.launcherWallpaperImagePath, values.wallpaperImagePath)
        assertEquals(settings.allAppsSortDescending, values.allAppsSortDescending)
        assertEquals(
            settings.launcherScreenBlackoutTimeoutSeconds,
            values.screenBlackoutTimeoutSeconds,
        )
        assertEquals(settings.launcherWidgetBackdropAlpha, values.widgetBackdropAlpha, 0.0f)
        assertEquals(settings.launcherTaskbarPlacement, values.taskbarPlacement)
        assertEquals(settings.launcherWallpaperMode, values.wallpaperMode)
        assertEquals(settings.allAppsSortOrder, values.allAppsSortOrder)
        assertEquals(settings.launcherWeatherLastLocation, values.weatherLastLocation)
        assertEquals(settings.launcherAnimationPalette, values.animationPalette)
    }

    @Test
    fun `an unknown animation palette token falls back to DYNAMIC`() {
        val prefs = mutablePreferencesOf()
        LauncherSettingsStore.write(
            prefs,
            AppSettings(launcher = LauncherSettings(animationPalette = "UNKNOWN_PALETTE"))
        )

        assertEquals(AppSettings.ANIMATION_PALETTE_DYNAMIC, LauncherSettingsStore.read(prefs).animationPalette)
    }

    // S2300: `read` answers with the launcher group itself, so these mirror the production direction
    // accessors on the read side. The write side still goes through the production ones, which is the
    // mapping these tests are here to pin.
    private fun LauncherSettings.payloadOf(direction: LauncherDesktopSwipeDirection): String =
        when (direction) {
            LauncherDesktopSwipeDirection.UP -> desktopSwipeUpPayload
            LauncherDesktopSwipeDirection.DOWN -> desktopSwipeDownPayload
            LauncherDesktopSwipeDirection.LEFT -> desktopSwipeLeftPayload
            LauncherDesktopSwipeDirection.RIGHT -> desktopSwipeRightPayload
        }

    private fun LauncherSettings.actionOf(
        direction: LauncherDesktopSwipeDirection,
    ): LauncherDesktopSwipeAction = when (direction) {
        LauncherDesktopSwipeDirection.UP -> desktopSwipeUpAction
        LauncherDesktopSwipeDirection.DOWN -> desktopSwipeDownAction
        LauncherDesktopSwipeDirection.LEFT -> desktopSwipeLeftAction
        LauncherDesktopSwipeDirection.RIGHT -> desktopSwipeRightAction
    }
}
