package com.sza.fastmediasorter.data.repository.settings

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.LauncherDesktopSwipeAction
import com.sza.fastmediasorter.domain.model.ScreenshotGestureAction
import com.sza.fastmediasorter.domain.model.launcher.InstalledAppSortOrder
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

    private val KEY_LAUNCHER_DENSITY_FACTOR = floatPreferencesKey("launcher_density_factor")
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

    /** Launcher desktop fields read from DataStore, ready for [AppSettings]. */
    data class Values(
        val launcherDensityFactor: Float,
        val launcherTaskbarShowRecents: Boolean,
        val launcherTaskbarShowPinned: Boolean,
        val launcherTaskbarShowTray: Boolean,
        val launcherTrayShowClock: Boolean,
        val launcherTrayShowBluetooth: Boolean,
        val launcherTrayShowSim1: Boolean,
        val launcherTrayShowSim2: Boolean,
        val launcherTrayShowNetwork: Boolean,
        val launcherTrayShowBattery: Boolean,
        val launcherTrayShowSpeed: Boolean,
        val launcherReplaceSystemStatusArea: Boolean,
        val launcherTopStatusStripMode: Boolean,
        val launcherForeignNotificationsEnabled: Boolean,
        val launcherTaskbarPlacement: String,
        val launcherRotationHintShown: Boolean,
        val launcherDesktopLocked: Boolean,
        val launcherDesktopSwipeUpAction: LauncherDesktopSwipeAction,
        val launcherDesktopSwipeDownAction: LauncherDesktopSwipeAction,
        val launcherDesktopSwipeLeftAction: LauncherDesktopSwipeAction,
        val launcherDesktopSwipeRightAction: LauncherDesktopSwipeAction,
        val launcherDesktopSwipeUpPayload: String,
        val launcherDesktopSwipeDownPayload: String,
        val launcherDesktopSwipeLeftPayload: String,
        val launcherDesktopSwipeRightPayload: String,
        val launcherWallpaperMode: String,
        val launcherWallpaperImagePath: String,
        val launcherWallpaperCameraId: String,
        val allAppsSortOrder: String,
        val allAppsSortDescending: Boolean,
        val launcherScreenBlackoutTimeoutSeconds: Int,
        val launcherWidgetBackdropAlpha: Float,
        val launcherWeatherLastLocation: String,
        val launcherStepsResetCount: Long,
        val launcherStepsResetTimestamp: Long,
    )

    // S0404: absent keys resolve to auto density + full taskbar (S2017: except the tray clock, off by
    // default), hidden system status bar and foreign-notification badges on (S2017 ADR-1).
    fun read(preferences: Preferences): Values = Values(
        launcherDensityFactor = preferences.getOrDefault(KEY_LAUNCHER_DENSITY_FACTOR, 1.0f),
        launcherTaskbarShowRecents = preferences.getOrDefault(KEY_LAUNCHER_TASKBAR_SHOW_RECENTS, true),
        launcherTaskbarShowPinned = preferences.getOrDefault(KEY_LAUNCHER_TASKBAR_SHOW_PINNED, true),
        launcherTaskbarShowTray = preferences.getOrDefault(KEY_LAUNCHER_TASKBAR_SHOW_TRAY, true),
        // S2017: the one taskbar exception - duplicates the top bar's own clock once the status area is replaced.
        launcherTrayShowClock = preferences.getOrDefault(KEY_LAUNCHER_TRAY_SHOW_CLOCK, false),
        launcherTrayShowBluetooth = preferences.getOrDefault(KEY_LAUNCHER_TRAY_SHOW_BLUETOOTH, true),
        launcherTrayShowSim1 = preferences.getOrDefault(KEY_LAUNCHER_TRAY_SHOW_SIM1, true),
        launcherTrayShowSim2 = preferences.getOrDefault(KEY_LAUNCHER_TRAY_SHOW_SIM2, true),
        launcherTrayShowNetwork = preferences.getOrDefault(KEY_LAUNCHER_TRAY_SHOW_NETWORK, true),
        launcherTrayShowBattery = preferences.getOrDefault(KEY_LAUNCHER_TRAY_SHOW_BATTERY, true),
        launcherTrayShowSpeed = preferences.getOrDefault(KEY_LAUNCHER_TRAY_SHOW_SPEED, false),
        // S2017: hide the system status bar by default.
        launcherReplaceSystemStatusArea = preferences.getOrDefault(KEY_LAUNCHER_REPLACE_SYSTEM_STATUS_AREA, true),
        launcherTopStatusStripMode = preferences.getOrDefault(KEY_LAUNCHER_TOP_STATUS_STRIP_MODE, false),
        // S2017 ADR-1: default flipped to ON, superseding S1465 ADR-4's off-by-default rationale.
        launcherForeignNotificationsEnabled = preferences.getOrDefault(KEY_LAUNCHER_FOREIGN_NOTIFICATIONS, true),
        // S1643: an unknown token (older/newer build, corrupted value) degrades to the bottom edge.
        launcherTaskbarPlacement = preferences[KEY_LAUNCHER_TASKBAR_PLACEMENT]
            ?.takeIf { it in AppSettings.LAUNCHER_TASKBAR_PLACEMENT_OPTIONS }
            ?: AppSettings.LAUNCHER_TASKBAR_PLACEMENT_BOTTOM,
        launcherRotationHintShown = preferences.getOrDefault(KEY_LAUNCHER_ROTATION_HINT_SHOWN, false),
        launcherDesktopLocked = preferences.getOrDefault(KEY_LAUNCHER_DESKTOP_LOCKED, false),
        launcherDesktopSwipeUpAction = LauncherDesktopSwipeAction.fromName(
            preferences[KEY_LAUNCHER_DESKTOP_SWIPE_UP_ACTION],
            LauncherDesktopSwipeAction.OpenAllApps,
        ),
        launcherDesktopSwipeDownAction = LauncherDesktopSwipeAction.fromName(
            preferences[KEY_LAUNCHER_DESKTOP_SWIPE_DOWN_ACTION],
            LauncherDesktopSwipeAction.EdgeGestureAction(ScreenshotGestureAction.OPEN_NOTIFICATION_SHADE),
        ),
        launcherDesktopSwipeLeftAction = LauncherDesktopSwipeAction.fromName(
            preferences[KEY_LAUNCHER_DESKTOP_SWIPE_LEFT_ACTION],
            LauncherDesktopSwipeAction.EdgeGestureAction(ScreenshotGestureAction.DO_NOT_USE),
        ),
        launcherDesktopSwipeRightAction = LauncherDesktopSwipeAction.fromName(
            preferences[KEY_LAUNCHER_DESKTOP_SWIPE_RIGHT_ACTION],
            LauncherDesktopSwipeAction.EdgeGestureAction(ScreenshotGestureAction.DO_NOT_USE),
        ),
        launcherDesktopSwipeUpPayload = preferences.getOrDefault(KEY_LAUNCHER_DESKTOP_SWIPE_UP_PAYLOAD, ""),
        launcherDesktopSwipeDownPayload = preferences.getOrDefault(KEY_LAUNCHER_DESKTOP_SWIPE_DOWN_PAYLOAD, ""),
        launcherDesktopSwipeLeftPayload = preferences.getOrDefault(KEY_LAUNCHER_DESKTOP_SWIPE_LEFT_PAYLOAD, ""),
        launcherDesktopSwipeRightPayload = preferences.getOrDefault(KEY_LAUNCHER_DESKTOP_SWIPE_RIGHT_PAYLOAD, ""),
        // S1101: an unknown token (older/newer build, corrupted value) degrades to the branded default.
        launcherWallpaperMode = preferences[KEY_LAUNCHER_WALLPAPER_MODE]
            ?.takeIf { it in AppSettings.LAUNCHER_WALLPAPER_MODES }
            ?: AppSettings.LAUNCHER_WALLPAPER_BRANDED,
        launcherWallpaperImagePath = preferences.getOrDefault(KEY_LAUNCHER_WALLPAPER_IMAGE_PATH, ""),
        launcherWallpaperCameraId = preferences.getOrDefault(KEY_LAUNCHER_WALLPAPER_CAMERA_ID, ""),
        // S1401: stored as an enum name; an unknown one degrades to the default order.
        allAppsSortOrder = InstalledAppSortOrder
            .fromNameOrDefault(preferences[KEY_ALL_APPS_SORT_ORDER]).name,
        allAppsSortDescending = preferences.getOrDefault(KEY_ALL_APPS_SORT_DESCENDING, false),
        // S1741: non-negative seconds (0 = Off)
        launcherScreenBlackoutTimeoutSeconds = preferences
            .getOrDefault(KEY_LAUNCHER_SCREEN_BLACKOUT_TIMEOUT_SECONDS, 0)
            .coerceAtLeast(0),
        // S1748: the widget backdrop opacity stored as a float, with the default matching the app's
        // launcher setting rows and the last chosen value surviving a restart.
        launcherWidgetBackdropAlpha = preferences
            .getOrDefault(KEY_LAUNCHER_WIDGET_BACKDROP_ALPHA, AppSettings.DEFAULT_LAUNCHER_WIDGET_BACKDROP_ALPHA),
        // S2213: empty means the user has never picked a place, which is what a fresh install reads.
        launcherWeatherLastLocation = preferences.getOrDefault(KEY_LAUNCHER_WEATHER_LAST_LOCATION, ""),
        launcherStepsResetCount = preferences.getOrDefault(KEY_LAUNCHER_STEPS_RESET_COUNT, 0L),
        launcherStepsResetTimestamp = preferences.getOrDefault(KEY_LAUNCHER_STEPS_RESET_TIMESTAMP, 0L),
    )

    /**
     * S2213: copies the launcher group onto [settings].
     *
     * The assembly lives here rather than in the repository's `AppSettings(..)` call because this object
     * already owns the group - the repository only knew the field names, and every launcher setting added
     * since made that call longer without making it more informative. Keeping it here also means one more
     * launcher field costs one line in this file instead of two across two.
     */
    fun applyTo(settings: AppSettings, values: Values): AppSettings = settings.copy(
        launcherDensityFactor = values.launcherDensityFactor,
        launcherTaskbarShowRecents = values.launcherTaskbarShowRecents,
        launcherTaskbarShowPinned = values.launcherTaskbarShowPinned,
        launcherTaskbarShowTray = values.launcherTaskbarShowTray,
        launcherTrayShowClock = values.launcherTrayShowClock,
        launcherTrayShowBluetooth = values.launcherTrayShowBluetooth,
        launcherTrayShowSim1 = values.launcherTrayShowSim1,
        launcherTrayShowSim2 = values.launcherTrayShowSim2,
        launcherTrayShowNetwork = values.launcherTrayShowNetwork,
        launcherTrayShowBattery = values.launcherTrayShowBattery,
        launcherTrayShowSpeed = values.launcherTrayShowSpeed,
        launcherReplaceSystemStatusArea = values.launcherReplaceSystemStatusArea,
        launcherTopStatusStripMode = values.launcherTopStatusStripMode,
        launcherForeignNotificationsEnabled = values.launcherForeignNotificationsEnabled,
        launcherTaskbarPlacement = values.launcherTaskbarPlacement,
        launcherRotationHintShown = values.launcherRotationHintShown,
        launcherDesktopLocked = values.launcherDesktopLocked,
        launcherDesktopSwipeUpAction = values.launcherDesktopSwipeUpAction,
        launcherDesktopSwipeDownAction = values.launcherDesktopSwipeDownAction,
        launcherDesktopSwipeLeftAction = values.launcherDesktopSwipeLeftAction,
        launcherDesktopSwipeRightAction = values.launcherDesktopSwipeRightAction,
        launcherDesktopSwipeUpPayload = values.launcherDesktopSwipeUpPayload,
        launcherDesktopSwipeDownPayload = values.launcherDesktopSwipeDownPayload,
        launcherDesktopSwipeLeftPayload = values.launcherDesktopSwipeLeftPayload,
        launcherDesktopSwipeRightPayload = values.launcherDesktopSwipeRightPayload,
        launcherWallpaperMode = values.launcherWallpaperMode,
        launcherWallpaperImagePath = values.launcherWallpaperImagePath,
        launcherWallpaperCameraId = values.launcherWallpaperCameraId,
        allAppsSortOrder = values.allAppsSortOrder,
        allAppsSortDescending = values.allAppsSortDescending,
        launcherScreenBlackoutTimeoutSeconds = values.launcherScreenBlackoutTimeoutSeconds,
        launcherWidgetBackdropAlpha = values.launcherWidgetBackdropAlpha,
        launcherWeatherLastLocation = values.launcherWeatherLastLocation,
        launcherStepsResetCount = values.launcherStepsResetCount,
        launcherStepsResetTimestamp = values.launcherStepsResetTimestamp,
    )

    fun write(preferences: MutablePreferences, settings: AppSettings) {
        preferences[KEY_LAUNCHER_DENSITY_FACTOR] = settings.launcherDensityFactor
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
        preferences[KEY_LAUNCHER_DESKTOP_SWIPE_UP_ACTION] = settings.launcherDesktopSwipeUpAction.persistedName
        preferences[KEY_LAUNCHER_DESKTOP_SWIPE_DOWN_ACTION] = settings.launcherDesktopSwipeDownAction.persistedName
        preferences[KEY_LAUNCHER_DESKTOP_SWIPE_LEFT_ACTION] = settings.launcherDesktopSwipeLeftAction.persistedName
        preferences[KEY_LAUNCHER_DESKTOP_SWIPE_RIGHT_ACTION] = settings.launcherDesktopSwipeRightAction.persistedName
        preferences[KEY_LAUNCHER_DESKTOP_SWIPE_UP_PAYLOAD] = settings.launcherDesktopSwipeUpPayload
        preferences[KEY_LAUNCHER_DESKTOP_SWIPE_DOWN_PAYLOAD] = settings.launcherDesktopSwipeDownPayload
        preferences[KEY_LAUNCHER_DESKTOP_SWIPE_LEFT_PAYLOAD] = settings.launcherDesktopSwipeLeftPayload
        preferences[KEY_LAUNCHER_DESKTOP_SWIPE_RIGHT_PAYLOAD] = settings.launcherDesktopSwipeRightPayload
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
