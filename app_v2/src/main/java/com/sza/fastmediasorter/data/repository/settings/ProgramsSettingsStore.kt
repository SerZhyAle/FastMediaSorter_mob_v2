package com.sza.fastmediasorter.data.repository.settings

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import com.sza.fastmediasorter.domain.model.AppSettings

/**
 * Owns persistence of the sub-program switches: which of our own programs the user has turned on,
 * plus the values the front flashlight and the mirror remember.
 *
 * Extracted from SettingsRepositoryImpl as a single named responsibility (S1797). Public
 * `AppSettings` shape and persisted key strings are unchanged - behaviour-preserving.
 */
object ProgramsSettingsStore {

    private val KEY_ENABLE_CALCULATOR = booleanPreferencesKey("enable_calculator")
    private val KEY_ENABLE_STOPWATCH = booleanPreferencesKey("enable_stopwatch")
    private val KEY_ENABLE_NETWORK_MONITOR = booleanPreferencesKey("enable_network_monitor")
    private val KEY_ENABLE_SYSTEM_INFO = booleanPreferencesKey("enable_system_info")

    // S2050: this field stays in AppSettings/DataStore rather than the watch-mirror store because it
    // is read as a reactive phone-behaviour toggle by code outside the companion sheet (e.g. the
    // programs panel, share targets). See WearSettingsMirrorStore's KDoc for the symmetric case - a
    // field that only the companion sheet itself ever reads back.
    private val KEY_ENABLE_WEAR_COMPANION = booleanPreferencesKey("enable_wear_companion")

    // S2810: phone-only behaviour toggle, so it persists with the programs group like enableWearCompanion.
    private val KEY_SUPPRESS_WEAR_MEDIA_TAKEOVER =
        booleanPreferencesKey("suppress_wear_media_takeover")
    private val KEY_RECORD_GNSS_TRACK = booleanPreferencesKey("record_gnss_track")
    private val KEY_EMBEDDED_GAME_ENABLED = booleanPreferencesKey("embedded_game_enabled")
    private val KEY_FRONT_FLASHLIGHT_ENABLED = booleanPreferencesKey("front_flashlight_enabled")
    private val KEY_FRONT_FLASHLIGHT_COLOR = intPreferencesKey("front_flashlight_color")
    private val KEY_WATER_FLASHLIGHT_ENABLED = booleanPreferencesKey("water_flashlight_enabled")
    private val KEY_FLASHLIGHT_SHORTCUT_NOTIFICATION =
        booleanPreferencesKey("flashlight_shortcut_notification_enabled")
    private val KEY_MIRROR_ENABLED = booleanPreferencesKey("mirror_enabled")
    private val KEY_MIRROR_ZOOM_RATIO = floatPreferencesKey("mirror_zoom_ratio")
    private val KEY_MIRROR_HORIZONTALLY_FLIPPED = booleanPreferencesKey("mirror_horizontally_flipped")
    private val KEY_MIRROR_BACKLIGHT_ON = booleanPreferencesKey("mirror_backlight_on")
    private val KEY_SHOW_PROGRAMS_PANEL = booleanPreferencesKey("show_programs_panel_main_window")
    private val KEY_ENABLE_FAVORITES = booleanPreferencesKey("enable_favorites")

    /** Sub-program fields read from DataStore, ready for [AppSettings]. */
    data class Values(
        val enableCalculator: Boolean,
        val enableStopwatch: Boolean,
        val enableNetworkMonitor: Boolean,
        val enableSystemInfo: Boolean,
        val enableWearCompanion: Boolean,
        val suppressWearMediaTakeover: Boolean,
        val recordGnssTrack: Boolean,
        val embeddedGameEnabled: Boolean,
        val frontFlashlightEnabled: Boolean,
        val frontFlashlightColor: Int,
        val waterFlashlightEnabled: Boolean,
        val flashlightShortcutNotificationEnabled: Boolean,
        val mirrorEnabled: Boolean,
        val mirrorZoomRatio: Float,
        val mirrorHorizontallyFlipped: Boolean,
        val mirrorBacklightOn: Boolean,
        val showProgramsPanelInMainWindow: Boolean,
        val enableFavorites: Boolean,
    )

    fun read(preferences: Preferences): Values = Values(
        enableCalculator = preferences[KEY_ENABLE_CALCULATOR] ?: false,
        enableStopwatch = preferences[KEY_ENABLE_STOPWATCH] ?: false,
        enableNetworkMonitor = preferences[KEY_ENABLE_NETWORK_MONITOR] ?: false,
        enableSystemInfo = preferences[KEY_ENABLE_SYSTEM_INFO] ?: false,
        enableWearCompanion = preferences[KEY_ENABLE_WEAR_COMPANION] ?: false,
        suppressWearMediaTakeover = preferences[KEY_SUPPRESS_WEAR_MEDIA_TAKEOVER] ?: false,
        // S1433: recording a satellite track is a separate choice from opening the Monitor.
        recordGnssTrack = preferences[KEY_RECORD_GNSS_TRACK] ?: false,
        embeddedGameEnabled = preferences[KEY_EMBEDDED_GAME_ENABLED] ?: false,
        frontFlashlightEnabled = preferences[KEY_FRONT_FLASHLIGHT_ENABLED] ?: false,
        frontFlashlightColor = preferences[KEY_FRONT_FLASHLIGHT_COLOR]
            ?: AppSettings.FRONT_FLASHLIGHT_DEFAULT_COLOR,
        waterFlashlightEnabled = preferences[KEY_WATER_FLASHLIGHT_ENABLED] ?: false,
        flashlightShortcutNotificationEnabled =
        preferences[KEY_FLASHLIGHT_SHORTCUT_NOTIFICATION] ?: false,
        // S1924 ADR-3: the mirror starts on where the flashlight starts off, so the fallback here is
        // true rather than the false every switch above it defaults to.
        mirrorEnabled = preferences[KEY_MIRROR_ENABLED] ?: true,
        mirrorZoomRatio = preferences[KEY_MIRROR_ZOOM_RATIO] ?: AppSettings.MIRROR_DEFAULT_ZOOM_RATIO,
        mirrorHorizontallyFlipped = preferences[KEY_MIRROR_HORIZONTALLY_FLIPPED] ?: true,
        mirrorBacklightOn = preferences[KEY_MIRROR_BACKLIGHT_ON] ?: true,
        showProgramsPanelInMainWindow = preferences[KEY_SHOW_PROGRAMS_PANEL] ?: false,
        // Favorites is the one program that starts on - it existed before the programs list did.
        enableFavorites = preferences[KEY_ENABLE_FAVORITES] ?: true,
    )

    /**
     * Applies the sub-program group onto [settings] rather than having the repository restate every
     * field name a second time - the move S2213 already made for the launcher group.
     */
    fun applyTo(settings: AppSettings, values: Values): AppSettings = settings.copy(
        enableCalculator = values.enableCalculator,
        enableStopwatch = values.enableStopwatch,
        enableNetworkMonitor = values.enableNetworkMonitor,
        enableSystemInfo = values.enableSystemInfo,
        enableWearCompanion = values.enableWearCompanion,
        suppressWearMediaTakeover = values.suppressWearMediaTakeover,
        recordGnssTrack = values.recordGnssTrack,
        embeddedGameEnabled = values.embeddedGameEnabled,
        frontFlashlightEnabled = values.frontFlashlightEnabled,
        frontFlashlightColor = values.frontFlashlightColor,
        waterFlashlightEnabled = values.waterFlashlightEnabled,
        flashlightShortcutNotificationEnabled = values.flashlightShortcutNotificationEnabled,
        mirrorEnabled = values.mirrorEnabled,
        mirrorZoomRatio = values.mirrorZoomRatio,
        mirrorHorizontallyFlipped = values.mirrorHorizontallyFlipped,
        mirrorBacklightOn = values.mirrorBacklightOn,
        showProgramsPanelInMainWindow = values.showProgramsPanelInMainWindow,
        enableFavorites = values.enableFavorites,
    )

    fun write(preferences: MutablePreferences, settings: AppSettings) {
        preferences[KEY_ENABLE_CALCULATOR] = settings.enableCalculator
        preferences[KEY_ENABLE_STOPWATCH] = settings.enableStopwatch
        preferences[KEY_ENABLE_NETWORK_MONITOR] = settings.enableNetworkMonitor
        preferences[KEY_ENABLE_SYSTEM_INFO] = settings.enableSystemInfo
        preferences[KEY_ENABLE_WEAR_COMPANION] = settings.enableWearCompanion
        preferences[KEY_SUPPRESS_WEAR_MEDIA_TAKEOVER] = settings.suppressWearMediaTakeover
        preferences[KEY_RECORD_GNSS_TRACK] = settings.recordGnssTrack
        preferences[KEY_EMBEDDED_GAME_ENABLED] = settings.embeddedGameEnabled
        preferences[KEY_FRONT_FLASHLIGHT_ENABLED] = settings.frontFlashlightEnabled
        preferences[KEY_FRONT_FLASHLIGHT_COLOR] = settings.frontFlashlightColor
        preferences[KEY_WATER_FLASHLIGHT_ENABLED] = settings.waterFlashlightEnabled
        preferences[KEY_FLASHLIGHT_SHORTCUT_NOTIFICATION] =
            settings.flashlightShortcutNotificationEnabled
        preferences[KEY_MIRROR_ENABLED] = settings.mirrorEnabled
        preferences[KEY_MIRROR_ZOOM_RATIO] = settings.mirrorZoomRatio
        preferences[KEY_MIRROR_HORIZONTALLY_FLIPPED] = settings.mirrorHorizontallyFlipped
        preferences[KEY_MIRROR_BACKLIGHT_ON] = settings.mirrorBacklightOn
        preferences[KEY_SHOW_PROGRAMS_PANEL] = settings.showProgramsPanelInMainWindow
        preferences[KEY_ENABLE_FAVORITES] = settings.enableFavorites
    }

    /** Single-field write for the game switch, which the widget path toggles on its own. */
    fun writeEmbeddedGameEnabled(preferences: MutablePreferences, enabled: Boolean) {
        preferences[KEY_EMBEDDED_GAME_ENABLED] = enabled
    }
}
