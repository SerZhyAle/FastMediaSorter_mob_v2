package com.sza.fastmediasorter.data.repository.settings

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sza.fastmediasorter.domain.model.AppSettings

/**
 * Owns persistence of the stopwatch tool's own options (S1411 ADR-7).
 *
 * The switch that makes the tool visible lives next door in [ProgramsSettingsStore] with the other
 * sub-programs; what the tool does once opened lives here. Both sit on the shared DataStore, which is
 * what strategic §3.2 requires so the choices survive an application update, and keeping them in a
 * sub-store is what keeps `SettingsRepositoryImpl` an assembly line rather than a place with logic.
 */
object StopwatchSettingsStore {

    private val KEY_PARTICIPANT_COUNT = intPreferencesKey("stopwatch_participant_count")
    private val KEY_MUSIC_ENABLED = booleanPreferencesKey("stopwatch_music_enabled")
    private val KEY_MUSIC_URI = stringPreferencesKey("stopwatch_music_uri")
    private val KEY_VOLUME_KEYS_CONTROL = booleanPreferencesKey("stopwatch_volume_keys_control")

    /** Stopwatch fields read from DataStore, ready for [AppSettings]. */
    data class Values(
        val participantCount: Int,
        val musicEnabled: Boolean,
        val musicUri: String,
        val volumeKeysControl: Boolean,
    )

    fun read(preferences: Preferences): Values = Values(
        participantCount = preferences[KEY_PARTICIPANT_COUNT] ?: AppSettings.STOPWATCH_DEFAULT_PARTICIPANTS,
        musicEnabled = preferences[KEY_MUSIC_ENABLED] ?: false,
        musicUri = preferences[KEY_MUSIC_URI] ?: "",
        // The one default here that is not the quiet option: strategic ADR-2 makes the volume keys
        // drive the measurement, and the switch exists to give them back, not to hand them over.
        volumeKeysControl = preferences[KEY_VOLUME_KEYS_CONTROL] ?: true,
    )

    /** Applies the group onto [settings] instead of having the repository restate every field name. */
    fun applyTo(settings: AppSettings, values: Values): AppSettings = settings.copy(
        stopwatchParticipantCount = values.participantCount,
        stopwatchMusicEnabled = values.musicEnabled,
        stopwatchMusicUri = values.musicUri,
        stopwatchVolumeKeysControl = values.volumeKeysControl,
    )

    fun write(preferences: MutablePreferences, settings: AppSettings) {
        preferences[KEY_PARTICIPANT_COUNT] = settings.stopwatchParticipantCount
        preferences[KEY_MUSIC_ENABLED] = settings.stopwatchMusicEnabled
        preferences[KEY_MUSIC_URI] = settings.stopwatchMusicUri
        preferences[KEY_VOLUME_KEYS_CONTROL] = settings.stopwatchVolumeKeysControl
    }
}
