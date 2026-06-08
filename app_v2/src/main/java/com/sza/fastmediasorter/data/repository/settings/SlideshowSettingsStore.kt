package com.sza.fastmediasorter.data.repository.settings

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sza.fastmediasorter.domain.model.AppSettings
import timber.log.Timber

/**
 * Owns persistence of slideshow settings: interval, optional background-music URI
 * and resource id, and the background-music toggle.
 *
 * Extracted from SettingsRepositoryImpl as a single named responsibility. Public
 * `AppSettings` shape, persisted key strings, and the existing diagnostic logging
 * are unchanged - behaviour-preserving.
 */
object SlideshowSettingsStore {

    private val KEY_SLIDESHOW_INTERVAL = intPreferencesKey("slideshow_interval")
    private val KEY_SLIDESHOW_MUSIC_URI = stringPreferencesKey("slideshow_music_uri")
    private val KEY_ENABLE_SLIDESHOW_BACKGROUND_MUSIC = booleanPreferencesKey("enable_slideshow_background_music")
    private val KEY_SLIDESHOW_MUSIC_RESOURCE_ID = longPreferencesKey("slideshow_music_resource_id")

    /** Slideshow fields read from DataStore, ready for [AppSettings]. */
    data class Values(
        val slideshowInterval: Int,
        val slideshowMusicUri: String?,
        val enableSlideshowBackgroundMusic: Boolean,
        val slideshowMusicResourceId: Long?,
    )

    fun read(preferences: Preferences): Values = Values(
        slideshowInterval = preferences[KEY_SLIDESHOW_INTERVAL] ?: 10,
        slideshowMusicUri = preferences[KEY_SLIDESHOW_MUSIC_URI],
        enableSlideshowBackgroundMusic = preferences[KEY_ENABLE_SLIDESHOW_BACKGROUND_MUSIC] ?: false,
        slideshowMusicResourceId = preferences[KEY_SLIDESHOW_MUSIC_RESOURCE_ID],
    )

    fun write(preferences: MutablePreferences, settings: AppSettings) {
        preferences[KEY_SLIDESHOW_INTERVAL] = settings.slideshowInterval
        preferences.setOrRemove(KEY_SLIDESHOW_MUSIC_URI, settings.slideshowMusicUri)
        preferences[KEY_ENABLE_SLIDESHOW_BACKGROUND_MUSIC] = settings.enableSlideshowBackgroundMusic
        if (settings.slideshowMusicResourceId != null) {
            preferences[KEY_SLIDESHOW_MUSIC_RESOURCE_ID] = settings.slideshowMusicResourceId
            Timber.d("SettingsRepo: Saved slideshowMusicResourceId=${settings.slideshowMusicResourceId} to DataStore")
        } else {
            preferences.remove(KEY_SLIDESHOW_MUSIC_RESOURCE_ID)
            Timber.d("SettingsRepo: Removed slideshowMusicResourceId from DataStore")
        }
    }
}
