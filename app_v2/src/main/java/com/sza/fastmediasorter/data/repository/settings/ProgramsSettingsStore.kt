package com.sza.fastmediasorter.data.repository.settings

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import com.sza.fastmediasorter.domain.model.AppSettings

/**
 * Owns persistence of the sub-program switches: which of our own programs the user has turned on,
 * plus the two values the front flashlight remembers.
 *
 * Extracted from SettingsRepositoryImpl as a single named responsibility (S1797). Public
 * `AppSettings` shape and persisted key strings are unchanged - behaviour-preserving.
 */
object ProgramsSettingsStore {

    private val KEY_ENABLE_CALCULATOR = booleanPreferencesKey("enable_calculator")
    private val KEY_ENABLE_NETWORK_MONITOR = booleanPreferencesKey("enable_network_monitor")
    private val KEY_ENABLE_SYSTEM_INFO = booleanPreferencesKey("enable_system_info")
    private val KEY_ENABLE_WEAR_COMPANION = booleanPreferencesKey("enable_wear_companion")
    private val KEY_RECORD_GNSS_TRACK = booleanPreferencesKey("record_gnss_track")
    private val KEY_EMBEDDED_GAME_ENABLED = booleanPreferencesKey("embedded_game_enabled")
    private val KEY_FRONT_FLASHLIGHT_ENABLED = booleanPreferencesKey("front_flashlight_enabled")
    private val KEY_FRONT_FLASHLIGHT_COLOR = intPreferencesKey("front_flashlight_color")
    private val KEY_SHOW_PROGRAMS_PANEL = booleanPreferencesKey("show_programs_panel_main_window")
    private val KEY_ENABLE_FAVORITES = booleanPreferencesKey("enable_favorites")

    /** Sub-program fields read from DataStore, ready for [AppSettings]. */
    data class Values(
        val enableCalculator: Boolean,
        val enableNetworkMonitor: Boolean,
        val enableSystemInfo: Boolean,
        val enableWearCompanion: Boolean,
        val recordGnssTrack: Boolean,
        val embeddedGameEnabled: Boolean,
        val frontFlashlightEnabled: Boolean,
        val frontFlashlightColor: Int,
        val showProgramsPanelInMainWindow: Boolean,
        val enableFavorites: Boolean,
    )

    fun read(preferences: Preferences): Values = Values(
        enableCalculator = preferences[KEY_ENABLE_CALCULATOR] ?: false,
        enableNetworkMonitor = preferences[KEY_ENABLE_NETWORK_MONITOR] ?: false,
        enableSystemInfo = preferences[KEY_ENABLE_SYSTEM_INFO] ?: false,
        enableWearCompanion = preferences[KEY_ENABLE_WEAR_COMPANION] ?: false,
        // S1433: recording a satellite track is a separate choice from opening the Monitor.
        recordGnssTrack = preferences[KEY_RECORD_GNSS_TRACK] ?: false,
        embeddedGameEnabled = preferences[KEY_EMBEDDED_GAME_ENABLED] ?: false,
        frontFlashlightEnabled = preferences[KEY_FRONT_FLASHLIGHT_ENABLED] ?: false,
        frontFlashlightColor = preferences[KEY_FRONT_FLASHLIGHT_COLOR]
            ?: AppSettings.FRONT_FLASHLIGHT_DEFAULT_COLOR,
        showProgramsPanelInMainWindow = preferences[KEY_SHOW_PROGRAMS_PANEL] ?: false,
        // Favorites is the one program that starts on - it existed before the programs list did.
        enableFavorites = preferences[KEY_ENABLE_FAVORITES] ?: true,
    )

    fun write(preferences: MutablePreferences, settings: AppSettings) {
        preferences[KEY_ENABLE_CALCULATOR] = settings.enableCalculator
        preferences[KEY_ENABLE_NETWORK_MONITOR] = settings.enableNetworkMonitor
        preferences[KEY_ENABLE_SYSTEM_INFO] = settings.enableSystemInfo
        preferences[KEY_ENABLE_WEAR_COMPANION] = settings.enableWearCompanion
        preferences[KEY_RECORD_GNSS_TRACK] = settings.recordGnssTrack
        preferences[KEY_EMBEDDED_GAME_ENABLED] = settings.embeddedGameEnabled
        preferences[KEY_FRONT_FLASHLIGHT_ENABLED] = settings.frontFlashlightEnabled
        preferences[KEY_FRONT_FLASHLIGHT_COLOR] = settings.frontFlashlightColor
        preferences[KEY_SHOW_PROGRAMS_PANEL] = settings.showProgramsPanelInMainWindow
        preferences[KEY_ENABLE_FAVORITES] = settings.enableFavorites
    }

    /** Single-field write for the game switch, which the widget path toggles on its own. */
    fun writeEmbeddedGameEnabled(preferences: MutablePreferences, enabled: Boolean) {
        preferences[KEY_EMBEDDED_GAME_ENABLED] = enabled
    }
}
