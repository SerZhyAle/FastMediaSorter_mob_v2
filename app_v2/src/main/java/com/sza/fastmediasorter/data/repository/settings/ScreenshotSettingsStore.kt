package com.sza.fastmediasorter.data.repository.settings

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.ScreenshotGestureAction

class ScreenshotSettingsStore private constructor() {

    companion object {
        private val KEY_GESTURE_OVERLAY_ENABLED =
            booleanPreferencesKey("gesture_overlay_enabled")
        private val KEY_GESTURE_STRIP_VISIBLE =
            booleanPreferencesKey("screenshot_gesture_strip_visible")
        private val KEY_SCREENSHOT_GESTURE_ACTION_DOWN =
            stringPreferencesKey("screenshot_gesture_action_down")
        private val KEY_SCREENSHOT_GESTURE_ACTION_RIGHT =
            stringPreferencesKey("screenshot_gesture_action_right")
        private val KEY_SCREENSHOT_GESTURE_ACTION_UP =
            stringPreferencesKey("screenshot_gesture_action_up")
        private val KEY_SCREENSHOT_DESTINATION_RESOURCE_ID =
            stringPreferencesKey("screenshot_destination_resource_id")
        private val KEY_COPY_SCREENSHOT_TO_CLIPBOARD =
            booleanPreferencesKey("copy_screenshot_to_clipboard")
        private val KEY_SCREEN_CAPTURE_DISCLOSURE_ACCEPTED =
            booleanPreferencesKey("screen_capture_disclosure_accepted")

        data class Values(
            val gestureOverlayEnabled: Boolean,
            val screenshotGestureStripVisible: Boolean,
            val screenshotGestureActionDown: ScreenshotGestureAction,
            val screenshotGestureActionRight: ScreenshotGestureAction,
            val screenshotGestureActionUp: ScreenshotGestureAction,
            val screenshotDestinationResourceId: String?,
            val copyScreenshotToClipboard: Boolean,
            val screenCaptureDisclosureAccepted: Boolean,
        )

        fun read(preferences: Preferences): Values = Values(
            gestureOverlayEnabled = preferences[KEY_GESTURE_OVERLAY_ENABLED] ?: false,
            screenshotGestureStripVisible = preferences[KEY_GESTURE_STRIP_VISIBLE] ?: false,
            screenshotGestureActionDown = ScreenshotGestureAction.fromName(
                preferences[KEY_SCREENSHOT_GESTURE_ACTION_DOWN],
                ScreenshotGestureAction.SILENT_SCREENSHOT
            ),
            screenshotGestureActionRight = ScreenshotGestureAction.fromName(
                preferences[KEY_SCREENSHOT_GESTURE_ACTION_RIGHT],
                ScreenshotGestureAction.DO_NOT_USE
            ),
            screenshotGestureActionUp = ScreenshotGestureAction.fromName(
                preferences[KEY_SCREENSHOT_GESTURE_ACTION_UP],
                ScreenshotGestureAction.DO_NOT_USE
            ),
            screenshotDestinationResourceId = preferences[KEY_SCREENSHOT_DESTINATION_RESOURCE_ID],
            copyScreenshotToClipboard = preferences[KEY_COPY_SCREENSHOT_TO_CLIPBOARD] ?: false,
            screenCaptureDisclosureAccepted = preferences[KEY_SCREEN_CAPTURE_DISCLOSURE_ACCEPTED] ?: false,
        )

        fun write(preferences: MutablePreferences, settings: AppSettings) {
            preferences[KEY_GESTURE_OVERLAY_ENABLED] = settings.gestureOverlayEnabled
            preferences[KEY_GESTURE_STRIP_VISIBLE] = settings.screenshotGestureStripVisible
            preferences[KEY_SCREENSHOT_GESTURE_ACTION_DOWN] = settings.screenshotGestureActionDown.name
            preferences[KEY_SCREENSHOT_GESTURE_ACTION_RIGHT] = settings.screenshotGestureActionRight.name
            preferences[KEY_SCREENSHOT_GESTURE_ACTION_UP] = settings.screenshotGestureActionUp.name
            preferences.setOrRemove(
                KEY_SCREENSHOT_DESTINATION_RESOURCE_ID,
                settings.screenshotDestinationResourceId
            )
            preferences[KEY_COPY_SCREENSHOT_TO_CLIPBOARD] = settings.copyScreenshotToClipboard
            preferences[KEY_SCREEN_CAPTURE_DISCLOSURE_ACCEPTED] = settings.screenCaptureDisclosureAccepted
        }
    }
}
