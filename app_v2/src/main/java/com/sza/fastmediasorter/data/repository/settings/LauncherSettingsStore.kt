package com.sza.fastmediasorter.data.repository.settings

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.LauncherAllAppsSwipeAction
import com.sza.fastmediasorter.domain.model.LauncherDesktopSwipeAction
import com.sza.fastmediasorter.domain.model.ScreenshotGestureAction
import com.sza.fastmediasorter.domain.model.launcher.InstalledAppSortOrder
import com.sza.fastmediasorter.domain.model.launcher.LauncherSettings
import timber.log.Timber

/**
 * Owns persistence of launcher-mode desktop settings: grid density, taskbar composition and
 * placement, tray indicator visibility, wallpaper, desktop lock, all-apps sort, the
 * screen-blackout timeout and the last place picked for a weather gadget.
 *
 * Extracted from SettingsRepositoryImpl as a single named responsibility. Public
 * `AppSettings` shape and persisted key strings are unchanged - behaviour-preserving.
 */
object LauncherSettingsStore {

    private const val DEFAULT_LAUNCHER_SCREEN_COUNT = 2
    private const val MIN_LAUNCHER_SCREEN_COUNT = 1
    private const val MAX_LAUNCHER_SCREEN_COUNT = 5

    private val KEY_LAUNCHER_DENSITY_FACTOR = floatPreferencesKey("launcher_density_factor")
    private val KEY_LAUNCHER_SCREEN_COUNT = intPreferencesKey("launcher_screen_count")
    private val KEY_LAUNCHER_TASKBAR_SHOW_RECENTS = booleanPreferencesKey("launcher_taskbar_show_recents")
    private val KEY_LAUNCHER_TASKBAR_SHOW_PINNED = booleanPreferencesKey("launcher_taskbar_show_pinned")
    private val KEY_LAUNCHER_TASKBAR_SHOW_TRAY = booleanPreferencesKey("launcher_taskbar_show_tray")
    private val KEY_LAUNCHER_TRAY_SHOW_CLOCK = booleanPreferencesKey("launcher_tray_show_clock")
    private val KEY_LAUNCHER_TRAY_SHOW_BLUETOOTH = booleanPreferencesKey("launcher_tray_show_bluetooth")
    private val KEY_LAUNCHER_TRAY_SHOW_SIM1 = booleanPreferencesKey("launcher_tray_show_sim1")
    private val KEY_LAUNCHER_TRAY_SHOW_SIM2 = booleanPreferencesKey("launcher_tray_show_sim2")
    private val KEY_LAUNCHER_TRAY_SHOW_NETWORK = booleanPreferencesKey("launcher_tray_show_network")
    private val KEY_LAUNCHER_TRAY_SHOW_BATTERY = booleanPreferencesKey("launcher_tray_show_battery")
    private val KEY_LAUNCHER_TRAY_SHOW_SPEED = booleanPreferencesKey("launcher_tray_show_speed")
    private val KEY_LAUNCHER_REPLACE_SYSTEM_STATUS_AREA =
        booleanPreferencesKey("launcher_replace_system_status_area")
    private val KEY_LAUNCHER_TOP_STATUS_STRIP_MODE =
        booleanPreferencesKey("launcher_top_status_strip_mode")
    private val KEY_LAUNCHER_FOREIGN_NOTIFICATIONS =
        booleanPreferencesKey("launcher_foreign_notifications_enabled")
    private val KEY_LAUNCHER_TASKBAR_PLACEMENT = stringPreferencesKey("launcher_taskbar_placement")
    private val KEY_LAUNCHER_ROTATION_HINT_SHOWN = booleanPreferencesKey("launcher_rotation_hint_shown")
    private val KEY_LAUNCHER_DESKTOP_LOCKED = booleanPreferencesKey("launcher_desktop_locked")
    private val KEY_LAUNCHER_DESKTOP_DOUBLE_TAP_LOCK_ENABLED =
        booleanPreferencesKey("launcher_desktop_double_tap_lock_enabled")
    private val KEY_LAUNCHER_DESKTOP_SWIPE_UP_ACTION =
        stringPreferencesKey("launcher_desktop_swipe_up_action")
    private val KEY_LAUNCHER_DESKTOP_SWIPE_DOWN_ACTION =
        stringPreferencesKey("launcher_desktop_swipe_down_action")
    private val KEY_LAUNCHER_DESKTOP_SWIPE_LEFT_ACTION =
        stringPreferencesKey("launcher_desktop_swipe_left_action")
    private val KEY_LAUNCHER_DESKTOP_SWIPE_RIGHT_ACTION =
        stringPreferencesKey("launcher_desktop_swipe_right_action")
    private val KEY_LAUNCHER_DESKTOP_SWIPE_UP_PAYLOAD =
        stringPreferencesKey("launcher_desktop_swipe_up_payload")
    private val KEY_LAUNCHER_DESKTOP_SWIPE_DOWN_PAYLOAD =
        stringPreferencesKey("launcher_desktop_swipe_down_payload")
    private val KEY_LAUNCHER_DESKTOP_SWIPE_LEFT_PAYLOAD =
        stringPreferencesKey("launcher_desktop_swipe_left_payload")
    private val KEY_LAUNCHER_DESKTOP_SWIPE_RIGHT_PAYLOAD =
        stringPreferencesKey("launcher_desktop_swipe_right_payload")
    private val KEY_LAUNCHER_ALL_APPS_SWIPE_UP_ACTION =
        stringPreferencesKey("launcher_all_apps_swipe_up_action")
    private val KEY_LAUNCHER_ALL_APPS_SWIPE_DOWN_ACTION =
        stringPreferencesKey("launcher_all_apps_swipe_down_action")
    private val KEY_LAUNCHER_ALL_APPS_SWIPE_LEFT_ACTION =
        stringPreferencesKey("launcher_all_apps_swipe_left_action")
    private val KEY_LAUNCHER_ALL_APPS_SWIPE_RIGHT_ACTION =
        stringPreferencesKey("launcher_all_apps_swipe_right_action")
    private val KEY_LAUNCHER_ALL_APPS_SWIPE_UP_PAYLOAD =
        stringPreferencesKey("launcher_all_apps_swipe_up_payload")
    private val KEY_LAUNCHER_ALL_APPS_SWIPE_DOWN_PAYLOAD =
        stringPreferencesKey("launcher_all_apps_swipe_down_payload")
    private val KEY_LAUNCHER_ALL_APPS_SWIPE_LEFT_PAYLOAD =
        stringPreferencesKey("launcher_all_apps_swipe_left_payload")
    private val KEY_LAUNCHER_ALL_APPS_SWIPE_RIGHT_PAYLOAD =
        stringPreferencesKey("launcher_all_apps_swipe_right_payload")
    private val KEY_LAUNCHER_WALLPAPER_MODE = stringPreferencesKey("launcher_wallpaper_mode")
    private val KEY_LAUNCHER_WALLPAPER_IMAGE_PATH = stringPreferencesKey("launcher_wallpaper_image_path")
    private val KEY_LAUNCHER_WALLPAPER_CAMERA_ID = stringPreferencesKey("launcher_wallpaper_camera_id")
    private val KEY_ALL_APPS_SORT_ORDER = stringPreferencesKey("all_apps_sort_order")
    private val KEY_ALL_APPS_SORT_DESCENDING = booleanPreferencesKey("all_apps_sort_descending")
    private val KEY_LAUNCHER_SCREEN_BLACKOUT_TIMEOUT_SECONDS =
        intPreferencesKey("launcher_screen_blackout_timeout_seconds")
    private val KEY_LAUNCHER_WIDGET_BACKDROP_ALPHA = floatPreferencesKey("launcher_widget_backdrop_alpha")
    private val KEY_LAUNCHER_WEATHER_LAST_LOCATION =
        stringPreferencesKey("launcher_weather_last_location")
    private val KEY_LAUNCHER_STEPS_RESET_COUNT = longPreferencesKey("launcher_steps_reset_count")
    private val KEY_LAUNCHER_STEPS_RESET_TIMESTAMP = longPreferencesKey("launcher_steps_reset_timestamp")

    // S0404: absent keys resolve to auto density + full taskbar (S2017: except the tray clock, off by
    // default), hidden system status bar and foreign-notification badges on (S2017 ADR-1).
    fun read(preferences: Preferences): LauncherSettings =
        withAllAppsSwipes(readCore(preferences), preferences)

    /**
     * S2304: the All apps panel slots are applied here rather than inline, because [readCore] sits at the
     * function-length ceiling and one more slot family would push it over.
     */
    private fun withAllAppsSwipes(base: LauncherSettings, preferences: Preferences): LauncherSettings =
        base.copy(
            allAppsSwipeUpAction = LauncherAllAppsSwipeAction.fromName(
                preferences[KEY_LAUNCHER_ALL_APPS_SWIPE_UP_ACTION],
                LauncherAllAppsSwipeAction.ExpandAllApps,
            ),
            allAppsSwipeDownAction = LauncherAllAppsSwipeAction.fromName(
                preferences[KEY_LAUNCHER_ALL_APPS_SWIPE_DOWN_ACTION],
                LauncherAllAppsSwipeAction.BackToDesktop,
            ),
            allAppsSwipeLeftAction = LauncherAllAppsSwipeAction.fromName(
                preferences[KEY_LAUNCHER_ALL_APPS_SWIPE_LEFT_ACTION],
                LauncherAllAppsSwipeAction.Unassigned,
            ),
            allAppsSwipeRightAction = LauncherAllAppsSwipeAction.fromName(
                preferences[KEY_LAUNCHER_ALL_APPS_SWIPE_RIGHT_ACTION],
                LauncherAllAppsSwipeAction.Unassigned,
            ),
            allAppsSwipeUpPayload = preferences.getOrDefault(KEY_LAUNCHER_ALL_APPS_SWIPE_UP_PAYLOAD, ""),
            allAppsSwipeDownPayload = preferences.getOrDefault(KEY_LAUNCHER_ALL_APPS_SWIPE_DOWN_PAYLOAD, ""),
            allAppsSwipeLeftPayload = preferences.getOrDefault(KEY_LAUNCHER_ALL_APPS_SWIPE_LEFT_PAYLOAD, ""),
            allAppsSwipeRightPayload = preferences.getOrDefault(KEY_LAUNCHER_ALL_APPS_SWIPE_RIGHT_PAYLOAD, ""),
        )

    private fun readCore(preferences: Preferences): LauncherSettings = LauncherSettings(
        // S2320: reads the canonical default rather than a literal - this line carried its own copy of
        // the old 1.0f and would have kept a fresh install on the previous density after it moved.
        densityFactor = preferences
            .getOrDefault(KEY_LAUNCHER_DENSITY_FACTOR, AppSettings.DEFAULT_LAUNCHER_DENSITY_FACTOR),
        screenCount = preferences.getOrDefault(
            KEY_LAUNCHER_SCREEN_COUNT,
            DEFAULT_LAUNCHER_SCREEN_COUNT
        ).coerceIn(MIN_LAUNCHER_SCREEN_COUNT, MAX_LAUNCHER_SCREEN_COUNT),
        taskbarShowRecents = preferences.getOrDefault(KEY_LAUNCHER_TASKBAR_SHOW_RECENTS, true),
        taskbarShowPinned = preferences.getOrDefault(KEY_LAUNCHER_TASKBAR_SHOW_PINNED, true),
        taskbarShowTray = preferences.getOrDefault(KEY_LAUNCHER_TASKBAR_SHOW_TRAY, true),
        // S2017: the one taskbar exception - duplicates the top bar's own clock once the status area is replaced.
        trayShowClock = preferences.getOrDefault(KEY_LAUNCHER_TRAY_SHOW_CLOCK, false),
        trayShowBluetooth = preferences.getOrDefault(KEY_LAUNCHER_TRAY_SHOW_BLUETOOTH, true),
        trayShowSim1 = preferences.getOrDefault(KEY_LAUNCHER_TRAY_SHOW_SIM1, true),
        trayShowSim2 = preferences.getOrDefault(KEY_LAUNCHER_TRAY_SHOW_SIM2, true),
        trayShowNetwork = preferences.getOrDefault(KEY_LAUNCHER_TRAY_SHOW_NETWORK, true),
        trayShowBattery = preferences.getOrDefault(KEY_LAUNCHER_TRAY_SHOW_BATTERY, true),
        trayShowSpeed = preferences.getOrDefault(KEY_LAUNCHER_TRAY_SHOW_SPEED, false),
        // S2017: hide the system status bar by default.
        replaceSystemStatusArea = preferences.getOrDefault(KEY_LAUNCHER_REPLACE_SYSTEM_STATUS_AREA, true),
        topStatusStripMode = preferences.getOrDefault(KEY_LAUNCHER_TOP_STATUS_STRIP_MODE, false),
        // S2017 ADR-1: default flipped to ON, superseding S1465 ADR-4's off-by-default rationale.
        foreignNotificationsEnabled = preferences.getOrDefault(KEY_LAUNCHER_FOREIGN_NOTIFICATIONS, true),
        // S1643: an unknown token (older/newer build, corrupted value) degrades to the bottom edge.
        taskbarPlacement = preferences[KEY_LAUNCHER_TASKBAR_PLACEMENT]
            ?.takeIf { it in AppSettings.LAUNCHER_TASKBAR_PLACEMENT_OPTIONS }
            ?: AppSettings.LAUNCHER_TASKBAR_PLACEMENT_BOTTOM,
        rotationHintShown = preferences.getOrDefault(KEY_LAUNCHER_ROTATION_HINT_SHOWN, false),
        desktopLocked = preferences.getOrDefault(KEY_LAUNCHER_DESKTOP_LOCKED, false),
        desktopDoubleTapLockEnabled = preferences.getOrDefault(
            KEY_LAUNCHER_DESKTOP_DOUBLE_TAP_LOCK_ENABLED,
            true,
        ),
        desktopSwipeUpAction = LauncherDesktopSwipeAction.fromName(
            preferences[KEY_LAUNCHER_DESKTOP_SWIPE_UP_ACTION],
            LauncherDesktopSwipeAction.OpenAllApps,
        ),
        desktopSwipeDownAction = LauncherDesktopSwipeAction.fromName(
            preferences[KEY_LAUNCHER_DESKTOP_SWIPE_DOWN_ACTION],
            LauncherDesktopSwipeAction.EdgeGestureAction(ScreenshotGestureAction.OPEN_NOTIFICATION_SHADE),
        ),
        desktopSwipeLeftAction = LauncherDesktopSwipeAction.fromName(
            preferences[KEY_LAUNCHER_DESKTOP_SWIPE_LEFT_ACTION],
            LauncherDesktopSwipeAction.EdgeGestureAction(ScreenshotGestureAction.DO_NOT_USE),
        ),
        desktopSwipeRightAction = LauncherDesktopSwipeAction.fromName(
            preferences[KEY_LAUNCHER_DESKTOP_SWIPE_RIGHT_ACTION],
            LauncherDesktopSwipeAction.EdgeGestureAction(ScreenshotGestureAction.DO_NOT_USE),
        ),
        desktopSwipeUpPayload = preferences.getOrDefault(KEY_LAUNCHER_DESKTOP_SWIPE_UP_PAYLOAD, ""),
        desktopSwipeDownPayload = preferences.getOrDefault(KEY_LAUNCHER_DESKTOP_SWIPE_DOWN_PAYLOAD, ""),
        desktopSwipeLeftPayload = preferences.getOrDefault(KEY_LAUNCHER_DESKTOP_SWIPE_LEFT_PAYLOAD, ""),
        desktopSwipeRightPayload = preferences.getOrDefault(KEY_LAUNCHER_DESKTOP_SWIPE_RIGHT_PAYLOAD, ""),
        // S1101: an unknown token (older/newer build, corrupted value) degrades to the branded default.
        wallpaperMode = preferences[KEY_LAUNCHER_WALLPAPER_MODE]
            ?.takeIf { it in AppSettings.LAUNCHER_WALLPAPER_MODES }
            ?: AppSettings.LAUNCHER_WALLPAPER_BRANDED,
        wallpaperImagePath = preferences.getOrDefault(KEY_LAUNCHER_WALLPAPER_IMAGE_PATH, ""),
        wallpaperCameraId = preferences.getOrDefault(KEY_LAUNCHER_WALLPAPER_CAMERA_ID, ""),
        // S1401: stored as an enum name; an unknown one degrades to the default order.
        allAppsSortOrder = InstalledAppSortOrder
            .fromNameOrDefault(preferences[KEY_ALL_APPS_SORT_ORDER]).name,
        allAppsSortDescending = preferences.getOrDefault(KEY_ALL_APPS_SORT_DESCENDING, false),
        // S1741: non-negative seconds (0 = Off)
        screenBlackoutTimeoutSeconds = preferences
            .getOrDefault(KEY_LAUNCHER_SCREEN_BLACKOUT_TIMEOUT_SECONDS, 0)
            .coerceAtLeast(0),
        // S1748: the widget backdrop opacity stored as a float, with the default matching the app's
        // launcher setting rows and the last chosen value surviving a restart.
        // S2320: snapped to the option list on read, so an install carrying an alpha S2320 removed
        // paints what its settings row shows instead of the two disagreeing.
        widgetBackdropAlpha = AppSettings.snapLauncherWidgetBackdropAlpha(
            preferences
                .getOrDefault(KEY_LAUNCHER_WIDGET_BACKDROP_ALPHA, AppSettings.DEFAULT_LAUNCHER_WIDGET_BACKDROP_ALPHA)
        ),
        // S2213: empty means the user has never picked a place, which is what a fresh install reads.
        weatherLastLocation = preferences.getOrDefault(KEY_LAUNCHER_WEATHER_LAST_LOCATION, ""),
        stepsResetCount = preferences.getOrDefault(KEY_LAUNCHER_STEPS_RESET_COUNT, 0L),
        stepsResetTimestamp = preferences.getOrDefault(KEY_LAUNCHER_STEPS_RESET_TIMESTAMP, 0L),
    )

    /**
     * S2213: copies the launcher group onto [settings].
     *
     * S2300: one field now - the group is a nested [LauncherSettings], so a new launcher setting costs a
     * line in that class and nothing here.
     */
    fun applyTo(settings: AppSettings, values: LauncherSettings): AppSettings = settings.copy(launcher = values)

    fun write(preferences: MutablePreferences, settings: AppSettings) {
        preferences[KEY_LAUNCHER_DENSITY_FACTOR] = settings.launcherDensityFactor
        preferences[KEY_LAUNCHER_SCREEN_COUNT] = settings.launcherScreenCount
        preferences[KEY_LAUNCHER_TASKBAR_SHOW_RECENTS] = settings.launcherTaskbarShowRecents
        preferences[KEY_LAUNCHER_TASKBAR_SHOW_PINNED] = settings.launcherTaskbarShowPinned
        preferences[KEY_LAUNCHER_TASKBAR_SHOW_TRAY] = settings.launcherTaskbarShowTray
        preferences[KEY_LAUNCHER_TRAY_SHOW_CLOCK] = settings.launcherTrayShowClock
        preferences[KEY_LAUNCHER_TRAY_SHOW_BLUETOOTH] = settings.launcherTrayShowBluetooth
        preferences[KEY_LAUNCHER_TRAY_SHOW_SIM1] = settings.launcherTrayShowSim1
        preferences[KEY_LAUNCHER_TRAY_SHOW_SIM2] = settings.launcherTrayShowSim2
        preferences[KEY_LAUNCHER_TRAY_SHOW_NETWORK] = settings.launcherTrayShowNetwork
        preferences[KEY_LAUNCHER_TRAY_SHOW_BATTERY] = settings.launcherTrayShowBattery
        preferences[KEY_LAUNCHER_TRAY_SHOW_SPEED] = settings.launcherTrayShowSpeed
        preferences[KEY_LAUNCHER_REPLACE_SYSTEM_STATUS_AREA] = settings.launcherReplaceSystemStatusArea
        preferences[KEY_LAUNCHER_TOP_STATUS_STRIP_MODE] = settings.launcherTopStatusStripMode
        preferences[KEY_LAUNCHER_FOREIGN_NOTIFICATIONS] = settings.launcherForeignNotificationsEnabled
        preferences[KEY_LAUNCHER_TASKBAR_PLACEMENT] = settings.launcherTaskbarPlacement
        preferences[KEY_LAUNCHER_ROTATION_HINT_SHOWN] = settings.launcherRotationHintShown
        preferences[KEY_LAUNCHER_DESKTOP_LOCKED] = settings.launcherDesktopLocked
        preferences[KEY_LAUNCHER_DESKTOP_DOUBLE_TAP_LOCK_ENABLED] =
            settings.launcherDesktopDoubleTapLockEnabled
        preferences[KEY_LAUNCHER_DESKTOP_SWIPE_UP_ACTION] = settings.launcherDesktopSwipeUpAction.persistedName
        preferences[KEY_LAUNCHER_DESKTOP_SWIPE_DOWN_ACTION] = settings.launcherDesktopSwipeDownAction.persistedName
        preferences[KEY_LAUNCHER_DESKTOP_SWIPE_LEFT_ACTION] = settings.launcherDesktopSwipeLeftAction.persistedName
        preferences[KEY_LAUNCHER_DESKTOP_SWIPE_RIGHT_ACTION] = settings.launcherDesktopSwipeRightAction.persistedName
        preferences[KEY_LAUNCHER_DESKTOP_SWIPE_UP_PAYLOAD] = settings.launcherDesktopSwipeUpPayload
        preferences[KEY_LAUNCHER_DESKTOP_SWIPE_DOWN_PAYLOAD] = settings.launcherDesktopSwipeDownPayload
        preferences[KEY_LAUNCHER_DESKTOP_SWIPE_LEFT_PAYLOAD] = settings.launcherDesktopSwipeLeftPayload
        preferences[KEY_LAUNCHER_DESKTOP_SWIPE_RIGHT_PAYLOAD] = settings.launcherDesktopSwipeRightPayload
        preferences[KEY_LAUNCHER_ALL_APPS_SWIPE_UP_ACTION] =
            settings.launcherAllAppsSwipeUpAction.persistedName
        preferences[KEY_LAUNCHER_ALL_APPS_SWIPE_DOWN_ACTION] =
            settings.launcherAllAppsSwipeDownAction.persistedName
        preferences[KEY_LAUNCHER_ALL_APPS_SWIPE_LEFT_ACTION] =
            settings.launcherAllAppsSwipeLeftAction.persistedName
        preferences[KEY_LAUNCHER_ALL_APPS_SWIPE_RIGHT_ACTION] =
            settings.launcherAllAppsSwipeRightAction.persistedName
        preferences[KEY_LAUNCHER_ALL_APPS_SWIPE_UP_PAYLOAD] = settings.launcherAllAppsSwipeUpPayload
        preferences[KEY_LAUNCHER_ALL_APPS_SWIPE_DOWN_PAYLOAD] = settings.launcherAllAppsSwipeDownPayload
        preferences[KEY_LAUNCHER_ALL_APPS_SWIPE_LEFT_PAYLOAD] = settings.launcherAllAppsSwipeLeftPayload
        preferences[KEY_LAUNCHER_ALL_APPS_SWIPE_RIGHT_PAYLOAD] = settings.launcherAllAppsSwipeRightPayload
        preferences[KEY_LAUNCHER_WALLPAPER_MODE] = settings.launcherWallpaperMode
        preferences[KEY_LAUNCHER_WALLPAPER_IMAGE_PATH] = settings.launcherWallpaperImagePath
        preferences[KEY_LAUNCHER_WALLPAPER_CAMERA_ID] = settings.launcherWallpaperCameraId
        preferences[KEY_ALL_APPS_SORT_ORDER] = settings.allAppsSortOrder
        preferences[KEY_ALL_APPS_SORT_DESCENDING] = settings.allAppsSortDescending
        preferences[KEY_LAUNCHER_SCREEN_BLACKOUT_TIMEOUT_SECONDS] =
            settings.launcherScreenBlackoutTimeoutSeconds
        preferences[KEY_LAUNCHER_WIDGET_BACKDROP_ALPHA] = settings.launcherWidgetBackdropAlpha
        preferences[KEY_LAUNCHER_WEATHER_LAST_LOCATION] = settings.launcherWeatherLastLocation
        preferences[KEY_LAUNCHER_STEPS_RESET_COUNT] = settings.launcherStepsResetCount
        preferences[KEY_LAUNCHER_STEPS_RESET_TIMESTAMP] = settings.launcherStepsResetTimestamp
        Timber.d(
            "S2243: persisted reset count=%d timestamp=%d",
            settings.launcherStepsResetCount,
            settings.launcherStepsResetTimestamp
        )
    }
}
