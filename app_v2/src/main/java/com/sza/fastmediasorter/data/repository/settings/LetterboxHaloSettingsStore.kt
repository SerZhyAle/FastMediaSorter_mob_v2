package com.sza.fastmediasorter.data.repository.settings

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sza.fastmediasorter.core.letterbox.LetterboxFillMath
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.LetterboxHaloSettings

/**
 * S3702: DataStore keys of the photo bars switch and the LETTERBOX-HALO group under it; an unknown
 * stored speed reads as medium (halo rule 5).
 */
object LetterboxHaloSettingsStore {

    private val KEY_BARS_ENABLED = booleanPreferencesKey("dynamic_background_extension")
    private val KEY_ENABLED = booleanPreferencesKey("letterbox_halo_enabled")
    private val KEY_GROWTH = booleanPreferencesKey("letterbox_halo_growth")
    private val KEY_SPEED = stringPreferencesKey("letterbox_halo_speed")

    fun applyTo(settings: AppSettings, preferences: Preferences): AppSettings = settings.copy(
        dynamicBackgroundExtension = preferences[KEY_BARS_ENABLED] ?: false,
        letterboxHalo = LetterboxHaloSettings(
            enabled = preferences[KEY_ENABLED] ?: false,
            growth = preferences[KEY_GROWTH] ?: true,
            speed = LetterboxFillMath.normalizeSpeed(preferences[KEY_SPEED]),
        ),
    )

    fun write(preferences: MutablePreferences, settings: AppSettings) {
        preferences[KEY_BARS_ENABLED] = settings.dynamicBackgroundExtension
        val halo = settings.letterboxHalo
        preferences[KEY_ENABLED] = halo.enabled
        preferences[KEY_GROWTH] = halo.growth
        preferences[KEY_SPEED] = halo.speed
    }
}
