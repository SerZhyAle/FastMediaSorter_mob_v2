package com.sza.fastmediasorter.domain.model.launcher

import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.LauncherAllAppsSwipeAction
import com.sza.fastmediasorter.domain.model.LauncherDesktopSwipeAction
import com.sza.fastmediasorter.domain.model.ScreenshotGestureAction

/**
 * S2300: the launcher-mode desktop settings, held by [AppSettings] as one nested field.
 *
 * They live here rather than inline in [AppSettings] because a JVM method descriptor may carry at most
 * 255 slots including `this`, and Kotlin's synthetic default-argument constructor spends one slot per
 * parameter (two per non-nullable `Long`) plus one bitmask int per 32 parameters plus a marker. At 236
 * inline parameters that descriptor reached 256 slots, which kotlinc emitted without complaint and ART
 * rejected at class-verification time - every `AppSettings()` and every `AppSettings.copy(..)` in the
 * process, so the app died in `Application.onCreate`. Grouping a domain into a nested class costs one
 * slot instead of one per field.
 */
data class LauncherSettings(
    // S0404: launcher-mode desktop tuning. Grid geometry is computed from the screen; this factor is
    // the user's manual nudge on top of it (higher factor = smaller cells = more columns), needed
    // because head units and TV boxes report unreliable densities. Desktop content itself lives in
    // Room, not here - a device profile seeds it once and never re-applies (ADR-4).
    // S2320: dense is the shipped default; the profile preset may still loosen it for a device that
    // asks for it, and the value is named in AppSettings so the settings rows derive their index.
    val densityFactor: Float = AppSettings.DEFAULT_LAUNCHER_DENSITY_FACTOR,
    // S2251: number of desktop screens in launcher mode (1..5, default 2).
    val screenCount: Int = 2,
    // S1643: which screen edge the whole taskbar composition is anchored to, one of
    // [AppSettings.LAUNCHER_TASKBAR_PLACEMENT_OPTIONS]. Stored as a token (like [wallpaperMode]) so an
    // unknown value from a newer build degrades to the bottom edge. Defaults to the bottom edge
    // because an update must not move an existing user's bar before the user asks for it (ADR-2);
    // the car head unit profile overrides it to the top edge through the preset CSV.
    val taskbarPlacement: String = AppSettings.LAUNCHER_TASKBAR_PLACEMENT_BOTTOM,
    val taskbarShowRecents: Boolean = true,
    val taskbarShowPinned: Boolean = true,
    val taskbarShowTray: Boolean = true,
    // S2017: hides the system status bar by default so a fresh install looks like the requested
    // "Hide system status bar" state without a manual toggle.
    val replaceSystemStatusArea: Boolean = true,
    // S1431: moves the clock and the indicator set off the taskbar tray and onto the freed top band,
    // which frees the Start panel for a longer recents list. Meaningful only while
    // [replaceSystemStatusArea] is on, since without it there is no freed band to draw in.
    val topStatusStripMode: Boolean = false,
    // S1465 ADR-4 default OFF was superseded by S2017 ADR-1 (explicit owner instruction, 2026-08-25):
    // whether the top strip also shows other applications' pending notifications as an icon and a count.
    // The store-policy risk ADR-4 raised - reusing a notification-listener grant given for an unrelated
    // purpose - is real but narrow (only a pre-existing install that never touched this row and already
    // holds that grant); S2017 records it as an accepted risk rather than blocking the default flip.
    val foreignNotificationsEnabled: Boolean = true,
    // S1415: composition of the tray itself, one switch per indicator, in the tray's left-to-right order.
    // [taskbarShowTray] above stays the master switch for the whole block; these only decide what
    // the block contains once it is shown. All default ON so an upgrade looks exactly like the old tray
    // plus the indicators the device can actually report - except the clock (S2017: duplicates the top
    // bar's own clock once [replaceSystemStatusArea] is on by default).
    val trayShowClock: Boolean = false,
    val trayShowBluetooth: Boolean = true,
    val trayShowSim1: Boolean = true,
    val trayShowSim2: Boolean = true,
    val trayShowNetwork: Boolean = true,
    val trayShowBattery: Boolean = true,
    val trayShowSpeed: Boolean = false,
    // S0404: one-shot - true once the first-rotation hint has been shown, so it never repeats. No UI row
    // (invisible to the settings-doc gate); it is a remembered event, not a user-facing toggle.
    val rotationHintShown: Boolean = false,
    // S1090: guards entry into desktop edit mode. Off by default so the long-press gesture stays
    // discoverable; the Start-menu entry is deliberate and stays reachable regardless of this flag.
    val desktopLocked: Boolean = false,
    // S2249: applies a convenient lock toggle only to a double tap that starts on empty desktop space.
    val desktopDoubleTapLockEnabled: Boolean = true,
    val desktopSwipeUpAction: LauncherDesktopSwipeAction = LauncherDesktopSwipeAction.OpenAllApps,
    val desktopSwipeDownAction: LauncherDesktopSwipeAction =
        LauncherDesktopSwipeAction.EdgeGestureAction(ScreenshotGestureAction.OPEN_NOTIFICATION_SHADE),
    val desktopSwipeLeftAction: LauncherDesktopSwipeAction =
        LauncherDesktopSwipeAction.EdgeGestureAction(ScreenshotGestureAction.DO_NOT_USE),
    val desktopSwipeRightAction: LauncherDesktopSwipeAction =
        LauncherDesktopSwipeAction.EdgeGestureAction(ScreenshotGestureAction.DO_NOT_USE),
    // Launcher desktop directions can execute the same targeted actions as edge gestures. The payload
    // is a package name for OPEN_APP or a URL for OPEN_URL; other actions ignore it.
    val desktopSwipeUpPayload: String = "",
    val desktopSwipeDownPayload: String = "",
    val desktopSwipeLeftPayload: String = "",
    val desktopSwipeRightPayload: String = "",
    // S2304: the All apps panel keeps its own slot family - two of its routes act on the open panel and
    // mean nothing on the desktop, so the desktop family cannot carry them.
    val allAppsSwipeUpAction: LauncherAllAppsSwipeAction = LauncherAllAppsSwipeAction.ExpandAllApps,
    val allAppsSwipeDownAction: LauncherAllAppsSwipeAction = LauncherAllAppsSwipeAction.BackToDesktop,
    // Horizontal slots start unassigned: the owner named only the vertical pair, and the vertical axis is
    // the only one where a movement on this panel already means something.
    val allAppsSwipeLeftAction: LauncherAllAppsSwipeAction = LauncherAllAppsSwipeAction.Unassigned,
    val allAppsSwipeRightAction: LauncherAllAppsSwipeAction = LauncherAllAppsSwipeAction.Unassigned,
    val allAppsSwipeUpPayload: String = "",
    val allAppsSwipeDownPayload: String = "",
    val allAppsSwipeLeftPayload: String = "",
    val allAppsSwipeRightPayload: String = "",
    // S1101: desktop wallpaper mode, one of [AppSettings.LAUNCHER_WALLPAPER_MODES]. Stored as a token (like
    // [colorTheme]) so an unknown value from a newer build degrades to the branded default.
    val wallpaperMode: String = AppSettings.LAUNCHER_WALLPAPER_BRANDED,
    // S1101: absolute path of the user image copied into app-private storage; empty unless
    // [wallpaperMode] is [AppSettings.LAUNCHER_WALLPAPER_IMAGE].
    val wallpaperImagePath: String = "",
    // S2076: chosen camera lens id in [CameraLensEntry.id] form; empty unless [wallpaperMode]
    // is [AppSettings.LAUNCHER_WALLPAPER_CAMERA].
    val wallpaperCameraId: String = "",
    // S1401: the all-apps screen's chosen order, stored as an [InstalledAppSortOrder] name rather than
    // an ordinal so reordering the enum later cannot silently repoint a saved preference.
    val allAppsSortOrder: String = InstalledAppSortOrder.LABEL.name,
    val allAppsSortDescending: Boolean = false,
    // S1741: launcher-private screen blackout timeout in seconds (0 = Off).
    val screenBlackoutTimeoutSeconds: Int = 0,
    // S1748/S2253: launcher shared-surface opacity (0.0f = fully transparent, 1.0f = fully opaque).
    val widgetBackdropAlpha: Float = AppSettings.DEFAULT_LAUNCHER_WIDGET_BACKDROP_ALPHA,
    // S2213: the place last picked for a weather gadget, in `WeatherLocation.encode` form. It lives here
    // rather than only inside the desktop cell because clearing the desktop is exactly what a launcher
    // reset does, and the picked place must outlive it. Empty until the user picks one. No UI row
    // (invisible to the settings-doc gate); it is a remembered choice, not a user-facing toggle.
    val weatherLastLocation: String = "",
    // S2239: resetting the launcher steps widget stores the cumulative step count and timestamp of reset.
    val stepsResetCount: Long = 0L,
    val stepsResetTimestamp: Long = 0L,
    // S2223: animation color palette for procedural waves/particles (DYNAMIC, GREEN, PINK, BLUE).
    val animationPalette: String = AppSettings.ANIMATION_PALETTE_DYNAMIC,
)
