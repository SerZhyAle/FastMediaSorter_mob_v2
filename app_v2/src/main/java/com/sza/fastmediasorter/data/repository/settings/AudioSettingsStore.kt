package com.sza.fastmediasorter.data.repository.settings

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sza.fastmediasorter.domain.model.AppSettings

/**
 * Owns persistence of audio-browsing settings: size bounds, the optional
 * background-photos resource for the audio player, and the empty-state mode.
 *
 * Extracted from SettingsRepositoryImpl as a single named responsibility. Public
 * `AppSettings` shape and persisted key strings are unchanged - behaviour-preserving.
 */
object AudioSettingsStore {

    private val KEY_AUDIO_SIZE_MIN = longPreferencesKey("audio_size_min")
    private val KEY_AUDIO_SIZE_MAX = longPreferencesKey("audio_size_max")
    private val KEY_AUDIO_BACKGROUND_PHOTOS_RESOURCE_ID = stringPreferencesKey("audio_background_photos_resource_id")
    private val KEY_AUDIO_EMPTY_STATE_MODE = stringPreferencesKey("audio_empty_state_mode")

    /** Audio fields read from DataStore, ready for [AppSettings]. */
    data class Values(
        val audioSizeMin: Long,
        val audioSizeMax: Long,
        val audioBackgroundPhotosResourceId: String?,
        val audioEmptyStateMode: String,
    )

    fun read(preferences: Preferences): Values = Values(
        audioSizeMin = preferences[KEY_AUDIO_SIZE_MIN] ?: 0L,
        audioSizeMax = preferences[KEY_AUDIO_SIZE_MAX] ?: 1073741824L, // 1GB
        audioBackgroundPhotosResourceId = preferences[KEY_AUDIO_BACKGROUND_PHOTOS_RESOURCE_ID],
        audioEmptyStateMode = preferences[KEY_AUDIO_EMPTY_STATE_MODE] ?: "CANVAS_WAVES",
    )

    fun write(preferences: MutablePreferences, settings: AppSettings) {
        preferences[KEY_AUDIO_SIZE_MIN] = settings.audioSizeMin
        preferences[KEY_AUDIO_SIZE_MAX] = settings.audioSizeMax
        preferences.setOrRemove(KEY_AUDIO_BACKGROUND_PHOTOS_RESOURCE_ID, settings.audioBackgroundPhotosResourceId)
        preferences[KEY_AUDIO_EMPTY_STATE_MODE] = settings.audioEmptyStateMode
    }
}
