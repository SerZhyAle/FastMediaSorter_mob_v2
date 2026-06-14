package com.sza.fastmediasorter.data.repository.settings

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sza.fastmediasorter.domain.model.AppSettings

/**
 * Owns persistence of link auto-download settings: enable/destination/open-in-player
 * plus download preferences (max resolution, audio-only, login-wall heuristic).
 *
 * Extracted from SettingsRepositoryImpl as a single named responsibility. Public
 * `AppSettings` shape and persisted key strings are unchanged - behaviour-preserving.
 */
object LinkSettingsStore {

    private val KEY_LINK_AUTO_DOWNLOAD_ENABLED = booleanPreferencesKey("link_auto_download_enabled")
    private val KEY_LINK_AUTO_DOWNLOAD_RESOURCE_ID = longPreferencesKey("link_auto_download_resource_id")
    private val KEY_LINK_AUTO_DOWNLOAD_OPEN_IN_PLAYER = booleanPreferencesKey("link_auto_download_open_in_player")
    private val KEY_LINK_DOWNLOAD_MAX_RESOLUTION = stringPreferencesKey("link_download_max_resolution")
    private val KEY_LINK_DOWNLOAD_AUDIO_ONLY = booleanPreferencesKey("link_download_audio_only")
    private val KEY_LINK_DOWNLOAD_LOGIN_WALL_HEURISTIC_ENABLED = booleanPreferencesKey("link_download_login_wall_heuristic_enabled")

    /** Link auto-download fields read from DataStore, ready for [AppSettings]. */
    data class Values(
        val linkAutoDownloadEnabled: Boolean,
        val linkAutoDownloadResourceId: Long?,
        val linkAutoDownloadOpenInPlayer: Boolean,
        val linkDownloadMaxResolution: String,
        val linkDownloadAudioOnly: Boolean,
        val linkDownloadLoginWallHeuristicEnabled: Boolean,
    )

    fun read(preferences: Preferences): Values = Values(
        linkAutoDownloadEnabled = preferences[KEY_LINK_AUTO_DOWNLOAD_ENABLED] ?: true,
        linkAutoDownloadResourceId = preferences[KEY_LINK_AUTO_DOWNLOAD_RESOURCE_ID],
        linkAutoDownloadOpenInPlayer = preferences[KEY_LINK_AUTO_DOWNLOAD_OPEN_IN_PLAYER] ?: true,
        // S0116 §5.1 pillar J: whitelist guard mirrors the videoSnapshotFormat pattern.
        linkDownloadMaxResolution = preferences[KEY_LINK_DOWNLOAD_MAX_RESOLUTION]
            ?.takeIf { it in setOf("480p", "720p", "1080p", "best") } ?: "1080p",
        linkDownloadAudioOnly = preferences[KEY_LINK_DOWNLOAD_AUDIO_ONLY] ?: false,
        linkDownloadLoginWallHeuristicEnabled = preferences[KEY_LINK_DOWNLOAD_LOGIN_WALL_HEURISTIC_ENABLED] ?: true,
    )

    fun write(preferences: MutablePreferences, settings: AppSettings) {
        preferences[KEY_LINK_AUTO_DOWNLOAD_ENABLED] = settings.linkAutoDownloadEnabled
        preferences.setOrRemove(KEY_LINK_AUTO_DOWNLOAD_RESOURCE_ID, settings.linkAutoDownloadResourceId)
        preferences[KEY_LINK_AUTO_DOWNLOAD_OPEN_IN_PLAYER] = settings.linkAutoDownloadOpenInPlayer
        preferences[KEY_LINK_DOWNLOAD_MAX_RESOLUTION] = settings.linkDownloadMaxResolution
        preferences[KEY_LINK_DOWNLOAD_AUDIO_ONLY] = settings.linkDownloadAudioOnly
        preferences[KEY_LINK_DOWNLOAD_LOGIN_WALL_HEURISTIC_ENABLED] = settings.linkDownloadLoginWallHeuristicEnabled
    }
}
