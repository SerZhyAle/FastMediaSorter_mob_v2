package com.sza.fastmediasorter.data.repository.settings

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sza.fastmediasorter.domain.model.AppSettings

class ScreenshotSettingsStore private constructor() {

    companion object {
        private val KEY_GESTURE_OVERLAY_ENABLED =
            booleanPreferencesKey("gesture_overlay_enabled")
        private val KEY_SCREENSHOT_GESTURE_DOWN_ENABLED =
            booleanPreferencesKey("screenshot_gesture_down_enabled")
        private val KEY_SCREENSHOT_DESTINATION_RESOURCE_ID =
            stringPreferencesKey("screenshot_destination_resource_id")

        data class Values(
            val gestureOverlayEnabled: Boolean,
            val screenshotGestureDownEnabled: Boolean,
            val screenshotDestinationResourceId: String?,
        )

        fun read(preferences: Preferences): Values = Values(
            gestureOverlayEnabled = preferences[KEY_GESTURE_OVERLAY_ENABLED] ?: false,
            screenshotGestureDownEnabled = preferences[KEY_SCREENSHOT_GESTURE_DOWN_ENABLED] ?: true,
            screenshotDestinationResourceId = preferences[KEY_SCREENSHOT_DESTINATION_RESOURCE_ID],
        )

        fun write(preferences: MutablePreferences, settings: AppSettings) {
            preferences[KEY_GESTURE_OVERLAY_ENABLED] = settings.gestureOverlayEnabled
            preferences[KEY_SCREENSHOT_GESTURE_DOWN_ENABLED] = settings.screenshotGestureDownEnabled
            preferences.setOrRemove(
                KEY_SCREENSHOT_DESTINATION_RESOURCE_ID,
                settings.screenshotDestinationResourceId
            )
        }
    }
}
