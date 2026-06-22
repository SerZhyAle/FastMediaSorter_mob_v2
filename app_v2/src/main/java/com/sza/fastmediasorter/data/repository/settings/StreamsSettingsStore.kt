package com.sza.fastmediasorter.data.repository.settings

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.sza.fastmediasorter.domain.model.AppSettings

/**
 * Owns persistence of the Streams feature master switch (S0575). Extracted as its own store so the
 * Streams capability is a single named responsibility, mirroring [TextRecognitionSettingsStore].
 */
object StreamsSettingsStore {

    private val KEY_ENABLE_STREAMS = booleanPreferencesKey("enable_streams")

    fun read(preferences: Preferences): Boolean = preferences[KEY_ENABLE_STREAMS] ?: false

    fun write(preferences: MutablePreferences, settings: AppSettings) {
        preferences[KEY_ENABLE_STREAMS] = settings.enableStreams
    }
}
