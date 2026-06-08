package com.sza.fastmediasorter.data.repository.settings

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import com.sza.fastmediasorter.domain.model.AppSettings

/**
 * Owns persistence of the media file-size filter bounds (image and video min/max).
 *
 * Extracted from SettingsRepositoryImpl as a single named responsibility. Public
 * `AppSettings` shape and persisted key strings are unchanged - behaviour-preserving.
 */
object MediaSizeFilterSettingsStore {

    private val KEY_IMAGE_SIZE_MIN = longPreferencesKey("image_size_min")
    private val KEY_IMAGE_SIZE_MAX = longPreferencesKey("image_size_max")
    private val KEY_VIDEO_SIZE_MIN = longPreferencesKey("video_size_min")
    private val KEY_VIDEO_SIZE_MAX = longPreferencesKey("video_size_max")

    /** Media size-filter fields read from DataStore, ready for [AppSettings]. */
    data class Values(
        val imageSizeMin: Long,
        val imageSizeMax: Long,
        val videoSizeMin: Long,
        val videoSizeMax: Long,
    )

    fun read(preferences: Preferences): Values = Values(
        imageSizeMin = preferences[KEY_IMAGE_SIZE_MIN] ?: 1024L,
        imageSizeMax = preferences[KEY_IMAGE_SIZE_MAX] ?: 10485760L,
        videoSizeMin = preferences[KEY_VIDEO_SIZE_MIN] ?: 102400L, // 100KB in bytes
        videoSizeMax = preferences[KEY_VIDEO_SIZE_MAX] ?: 107374182400L,
    )

    fun write(preferences: MutablePreferences, settings: AppSettings) {
        preferences[KEY_IMAGE_SIZE_MIN] = settings.imageSizeMin
        preferences[KEY_IMAGE_SIZE_MAX] = settings.imageSizeMax
        preferences[KEY_VIDEO_SIZE_MIN] = settings.videoSizeMin
        preferences[KEY_VIDEO_SIZE_MAX] = settings.videoSizeMax
    }
}
