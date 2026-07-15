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
        // S1008: legacy single strip-visible key, read-only for LEFT_TOP migration (no longer written).
        private val KEY_GESTURE_STRIP_VISIBLE =
            booleanPreferencesKey("screenshot_gesture_strip_visible")
        // S1008: four per-zone strip-visible toggles (grey guide on each band's edge).
        private val KEY_ZONE_LEFT_TOP_STRIP_VISIBLE = booleanPreferencesKey("gesture_zone_left_top_strip_visible")
        private val KEY_ZONE_LEFT_BOTTOM_STRIP_VISIBLE = booleanPreferencesKey("gesture_zone_left_bottom_strip_visible")
        private val KEY_ZONE_RIGHT_TOP_STRIP_VISIBLE = booleanPreferencesKey("gesture_zone_right_top_strip_visible")
        private val KEY_ZONE_RIGHT_BOTTOM_STRIP_VISIBLE = booleanPreferencesKey("gesture_zone_right_bottom_strip_visible")
        // S0847: legacy single-strip keys, read-only for LEFT_TOP migration (no longer written).
        private val KEY_SCREENSHOT_GESTURE_ACTION_DOWN =
            stringPreferencesKey("screenshot_gesture_action_down")
        private val KEY_SCREENSHOT_GESTURE_ACTION_RIGHT =
            stringPreferencesKey("screenshot_gesture_action_right")
        private val KEY_SCREENSHOT_GESTURE_ACTION_UP =
            stringPreferencesKey("screenshot_gesture_action_up")
        // S0847: four edge-band enable toggles.
        private val KEY_ZONE_LEFT_TOP_ENABLED = booleanPreferencesKey("gesture_zone_left_top_enabled")
        private val KEY_ZONE_LEFT_BOTTOM_ENABLED = booleanPreferencesKey("gesture_zone_left_bottom_enabled")
        private val KEY_ZONE_RIGHT_TOP_ENABLED = booleanPreferencesKey("gesture_zone_right_top_enabled")
        private val KEY_ZONE_RIGHT_BOTTOM_ENABLED = booleanPreferencesKey("gesture_zone_right_bottom_enabled")
        // S0847: 12 zone-scoped action slots (zone x direction).
        private val KEY_LEFT_TOP_DOWN = stringPreferencesKey("gesture_left_top_down")
        private val KEY_LEFT_TOP_RIGHT = stringPreferencesKey("gesture_left_top_right")
        private val KEY_LEFT_TOP_UP = stringPreferencesKey("gesture_left_top_up")
        private val KEY_LEFT_BOTTOM_DOWN = stringPreferencesKey("gesture_left_bottom_down")
        private val KEY_LEFT_BOTTOM_RIGHT = stringPreferencesKey("gesture_left_bottom_right")
        private val KEY_LEFT_BOTTOM_UP = stringPreferencesKey("gesture_left_bottom_up")
        private val KEY_RIGHT_TOP_DOWN = stringPreferencesKey("gesture_right_top_down")
        private val KEY_RIGHT_TOP_RIGHT = stringPreferencesKey("gesture_right_top_right")
        private val KEY_RIGHT_TOP_UP = stringPreferencesKey("gesture_right_top_up")
        private val KEY_RIGHT_BOTTOM_DOWN = stringPreferencesKey("gesture_right_bottom_down")
        private val KEY_RIGHT_BOTTOM_RIGHT = stringPreferencesKey("gesture_right_bottom_right")
        private val KEY_RIGHT_BOTTOM_UP = stringPreferencesKey("gesture_right_bottom_up")
        // S1038: 12 generic per-slot string payloads (zone x direction), value-agnostic per ADR-3
        // (URL for "open URL", app package for S1036). Absent key = empty payload.
        private val KEY_PAYLOAD_LEFT_TOP_DOWN = stringPreferencesKey("gesture_payload_left_top_down")
        private val KEY_PAYLOAD_LEFT_TOP_RIGHT = stringPreferencesKey("gesture_payload_left_top_right")
        private val KEY_PAYLOAD_LEFT_TOP_UP = stringPreferencesKey("gesture_payload_left_top_up")
        private val KEY_PAYLOAD_LEFT_BOTTOM_DOWN = stringPreferencesKey("gesture_payload_left_bottom_down")
        private val KEY_PAYLOAD_LEFT_BOTTOM_RIGHT = stringPreferencesKey("gesture_payload_left_bottom_right")
        private val KEY_PAYLOAD_LEFT_BOTTOM_UP = stringPreferencesKey("gesture_payload_left_bottom_up")
        private val KEY_PAYLOAD_RIGHT_TOP_DOWN = stringPreferencesKey("gesture_payload_right_top_down")
        private val KEY_PAYLOAD_RIGHT_TOP_RIGHT = stringPreferencesKey("gesture_payload_right_top_right")
        private val KEY_PAYLOAD_RIGHT_TOP_UP = stringPreferencesKey("gesture_payload_right_top_up")
        private val KEY_PAYLOAD_RIGHT_BOTTOM_DOWN = stringPreferencesKey("gesture_payload_right_bottom_down")
        private val KEY_PAYLOAD_RIGHT_BOTTOM_RIGHT = stringPreferencesKey("gesture_payload_right_bottom_right")
        private val KEY_PAYLOAD_RIGHT_BOTTOM_UP = stringPreferencesKey("gesture_payload_right_bottom_up")
        private val KEY_SCREENSHOT_DESTINATION_RESOURCE_ID =
            stringPreferencesKey("screenshot_destination_resource_id")
        private val KEY_COPY_SCREENSHOT_TO_CLIPBOARD =
            booleanPreferencesKey("copy_screenshot_to_clipboard")
        private val KEY_SCREEN_CAPTURE_DISCLOSURE_ACCEPTED =
            booleanPreferencesKey("screen_capture_disclosure_accepted")

        private val NONE = ScreenshotGestureAction.DO_NOT_USE

        // S1038: empty payload persists as an absent key rather than a stored empty string.
        private fun String.orNull(): String? = takeIf { it.isNotEmpty() }

        data class Values(
            val gestureOverlayEnabled: Boolean,
            val zoneLeftTopEnabled: Boolean,
            val zoneLeftBottomEnabled: Boolean,
            val zoneRightTopEnabled: Boolean,
            val zoneRightBottomEnabled: Boolean,
            val zoneLeftTopStripVisible: Boolean,
            val zoneLeftBottomStripVisible: Boolean,
            val zoneRightTopStripVisible: Boolean,
            val zoneRightBottomStripVisible: Boolean,
            val leftTopDown: ScreenshotGestureAction,
            val leftTopRight: ScreenshotGestureAction,
            val leftTopUp: ScreenshotGestureAction,
            val leftBottomDown: ScreenshotGestureAction,
            val leftBottomRight: ScreenshotGestureAction,
            val leftBottomUp: ScreenshotGestureAction,
            val rightTopDown: ScreenshotGestureAction,
            val rightTopRight: ScreenshotGestureAction,
            val rightTopUp: ScreenshotGestureAction,
            val rightBottomDown: ScreenshotGestureAction,
            val rightBottomRight: ScreenshotGestureAction,
            val rightBottomUp: ScreenshotGestureAction,
            val payloadLeftTopDown: String,
            val payloadLeftTopRight: String,
            val payloadLeftTopUp: String,
            val payloadLeftBottomDown: String,
            val payloadLeftBottomRight: String,
            val payloadLeftBottomUp: String,
            val payloadRightTopDown: String,
            val payloadRightTopRight: String,
            val payloadRightTopUp: String,
            val payloadRightBottomDown: String,
            val payloadRightBottomRight: String,
            val payloadRightBottomUp: String,
            val screenshotDestinationResourceId: String?,
            val copyScreenshotToClipboard: Boolean,
            val screenCaptureDisclosureAccepted: Boolean,
        )

        fun read(preferences: Preferences): Values = Values(
            gestureOverlayEnabled = preferences[KEY_GESTURE_OVERLAY_ENABLED] ?: false,
            zoneLeftTopEnabled = preferences[KEY_ZONE_LEFT_TOP_ENABLED] ?: true,
            zoneLeftBottomEnabled = preferences[KEY_ZONE_LEFT_BOTTOM_ENABLED] ?: false,
            zoneRightTopEnabled = preferences[KEY_ZONE_RIGHT_TOP_ENABLED] ?: false,
            zoneRightBottomEnabled = preferences[KEY_ZONE_RIGHT_BOTTOM_ENABLED] ?: false,
            // LEFT_TOP strip visibility falls back to the legacy single strip-visible key for existing users.
            zoneLeftTopStripVisible =
                preferences[KEY_ZONE_LEFT_TOP_STRIP_VISIBLE] ?: preferences[KEY_GESTURE_STRIP_VISIBLE] ?: false,
            zoneLeftBottomStripVisible = preferences[KEY_ZONE_LEFT_BOTTOM_STRIP_VISIBLE] ?: false,
            zoneRightTopStripVisible = preferences[KEY_ZONE_RIGHT_TOP_STRIP_VISIBLE] ?: false,
            zoneRightBottomStripVisible = preferences[KEY_ZONE_RIGHT_BOTTOM_STRIP_VISIBLE] ?: false,
            // LEFT_TOP falls back to the legacy single-strip keys so existing users keep their bindings.
            leftTopDown = ScreenshotGestureAction.fromName(
                preferences[KEY_LEFT_TOP_DOWN] ?: preferences[KEY_SCREENSHOT_GESTURE_ACTION_DOWN],
                ScreenshotGestureAction.SILENT_SCREENSHOT
            ),
            leftTopRight = ScreenshotGestureAction.fromName(
                preferences[KEY_LEFT_TOP_RIGHT] ?: preferences[KEY_SCREENSHOT_GESTURE_ACTION_RIGHT], NONE
            ),
            leftTopUp = ScreenshotGestureAction.fromName(
                preferences[KEY_LEFT_TOP_UP] ?: preferences[KEY_SCREENSHOT_GESTURE_ACTION_UP], NONE
            ),
            leftBottomDown = ScreenshotGestureAction.fromName(preferences[KEY_LEFT_BOTTOM_DOWN], NONE),
            leftBottomRight = ScreenshotGestureAction.fromName(preferences[KEY_LEFT_BOTTOM_RIGHT], NONE),
            leftBottomUp = ScreenshotGestureAction.fromName(preferences[KEY_LEFT_BOTTOM_UP], NONE),
            rightTopDown = ScreenshotGestureAction.fromName(preferences[KEY_RIGHT_TOP_DOWN], NONE),
            rightTopRight = ScreenshotGestureAction.fromName(preferences[KEY_RIGHT_TOP_RIGHT], NONE),
            rightTopUp = ScreenshotGestureAction.fromName(preferences[KEY_RIGHT_TOP_UP], NONE),
            rightBottomDown = ScreenshotGestureAction.fromName(preferences[KEY_RIGHT_BOTTOM_DOWN], NONE),
            rightBottomRight = ScreenshotGestureAction.fromName(preferences[KEY_RIGHT_BOTTOM_RIGHT], NONE),
            rightBottomUp = ScreenshotGestureAction.fromName(preferences[KEY_RIGHT_BOTTOM_UP], NONE),
            payloadLeftTopDown = preferences[KEY_PAYLOAD_LEFT_TOP_DOWN] ?: "",
            payloadLeftTopRight = preferences[KEY_PAYLOAD_LEFT_TOP_RIGHT] ?: "",
            payloadLeftTopUp = preferences[KEY_PAYLOAD_LEFT_TOP_UP] ?: "",
            payloadLeftBottomDown = preferences[KEY_PAYLOAD_LEFT_BOTTOM_DOWN] ?: "",
            payloadLeftBottomRight = preferences[KEY_PAYLOAD_LEFT_BOTTOM_RIGHT] ?: "",
            payloadLeftBottomUp = preferences[KEY_PAYLOAD_LEFT_BOTTOM_UP] ?: "",
            payloadRightTopDown = preferences[KEY_PAYLOAD_RIGHT_TOP_DOWN] ?: "",
            payloadRightTopRight = preferences[KEY_PAYLOAD_RIGHT_TOP_RIGHT] ?: "",
            payloadRightTopUp = preferences[KEY_PAYLOAD_RIGHT_TOP_UP] ?: "",
            payloadRightBottomDown = preferences[KEY_PAYLOAD_RIGHT_BOTTOM_DOWN] ?: "",
            payloadRightBottomRight = preferences[KEY_PAYLOAD_RIGHT_BOTTOM_RIGHT] ?: "",
            payloadRightBottomUp = preferences[KEY_PAYLOAD_RIGHT_BOTTOM_UP] ?: "",
            screenshotDestinationResourceId = preferences[KEY_SCREENSHOT_DESTINATION_RESOURCE_ID],
            copyScreenshotToClipboard = preferences[KEY_COPY_SCREENSHOT_TO_CLIPBOARD] ?: false,
            screenCaptureDisclosureAccepted = preferences[KEY_SCREEN_CAPTURE_DISCLOSURE_ACCEPTED] ?: false,
        )

        fun write(preferences: MutablePreferences, settings: AppSettings) {
            preferences[KEY_GESTURE_OVERLAY_ENABLED] = settings.gestureOverlayEnabled
            preferences[KEY_ZONE_LEFT_TOP_ENABLED] = settings.screenshotGestureZoneLeftTopEnabled
            preferences[KEY_ZONE_LEFT_BOTTOM_ENABLED] = settings.screenshotGestureZoneLeftBottomEnabled
            preferences[KEY_ZONE_RIGHT_TOP_ENABLED] = settings.screenshotGestureZoneRightTopEnabled
            preferences[KEY_ZONE_RIGHT_BOTTOM_ENABLED] = settings.screenshotGestureZoneRightBottomEnabled
            preferences[KEY_ZONE_LEFT_TOP_STRIP_VISIBLE] = settings.screenshotGestureZoneLeftTopStripVisible
            preferences[KEY_ZONE_LEFT_BOTTOM_STRIP_VISIBLE] = settings.screenshotGestureZoneLeftBottomStripVisible
            preferences[KEY_ZONE_RIGHT_TOP_STRIP_VISIBLE] = settings.screenshotGestureZoneRightTopStripVisible
            preferences[KEY_ZONE_RIGHT_BOTTOM_STRIP_VISIBLE] = settings.screenshotGestureZoneRightBottomStripVisible
            preferences[KEY_LEFT_TOP_DOWN] = settings.screenshotGestureLeftTopDown.name
            preferences[KEY_LEFT_TOP_RIGHT] = settings.screenshotGestureLeftTopRight.name
            preferences[KEY_LEFT_TOP_UP] = settings.screenshotGestureLeftTopUp.name
            preferences[KEY_LEFT_BOTTOM_DOWN] = settings.screenshotGestureLeftBottomDown.name
            preferences[KEY_LEFT_BOTTOM_RIGHT] = settings.screenshotGestureLeftBottomRight.name
            preferences[KEY_LEFT_BOTTOM_UP] = settings.screenshotGestureLeftBottomUp.name
            preferences[KEY_RIGHT_TOP_DOWN] = settings.screenshotGestureRightTopDown.name
            preferences[KEY_RIGHT_TOP_RIGHT] = settings.screenshotGestureRightTopRight.name
            preferences[KEY_RIGHT_TOP_UP] = settings.screenshotGestureRightTopUp.name
            preferences[KEY_RIGHT_BOTTOM_DOWN] = settings.screenshotGestureRightBottomDown.name
            preferences[KEY_RIGHT_BOTTOM_RIGHT] = settings.screenshotGestureRightBottomRight.name
            preferences[KEY_RIGHT_BOTTOM_UP] = settings.screenshotGestureRightBottomUp.name
            // S1038: store the payload only when set; an empty value removes the key (default is empty).
            preferences.setOrRemove(KEY_PAYLOAD_LEFT_TOP_DOWN, settings.screenshotGesturePayloadLeftTopDown.orNull())
            preferences.setOrRemove(KEY_PAYLOAD_LEFT_TOP_RIGHT, settings.screenshotGesturePayloadLeftTopRight.orNull())
            preferences.setOrRemove(KEY_PAYLOAD_LEFT_TOP_UP, settings.screenshotGesturePayloadLeftTopUp.orNull())
            preferences.setOrRemove(KEY_PAYLOAD_LEFT_BOTTOM_DOWN, settings.screenshotGesturePayloadLeftBottomDown.orNull())
            preferences.setOrRemove(
                KEY_PAYLOAD_LEFT_BOTTOM_RIGHT,
                settings.screenshotGesturePayloadLeftBottomRight.orNull()
            )
            preferences.setOrRemove(KEY_PAYLOAD_LEFT_BOTTOM_UP, settings.screenshotGesturePayloadLeftBottomUp.orNull())
            preferences.setOrRemove(KEY_PAYLOAD_RIGHT_TOP_DOWN, settings.screenshotGesturePayloadRightTopDown.orNull())
            preferences.setOrRemove(KEY_PAYLOAD_RIGHT_TOP_RIGHT, settings.screenshotGesturePayloadRightTopRight.orNull())
            preferences.setOrRemove(KEY_PAYLOAD_RIGHT_TOP_UP, settings.screenshotGesturePayloadRightTopUp.orNull())
            preferences.setOrRemove(
                KEY_PAYLOAD_RIGHT_BOTTOM_DOWN,
                settings.screenshotGesturePayloadRightBottomDown.orNull()
            )
            preferences.setOrRemove(
                KEY_PAYLOAD_RIGHT_BOTTOM_RIGHT,
                settings.screenshotGesturePayloadRightBottomRight.orNull()
            )
            preferences.setOrRemove(KEY_PAYLOAD_RIGHT_BOTTOM_UP, settings.screenshotGesturePayloadRightBottomUp.orNull())
            preferences.setOrRemove(
                KEY_SCREENSHOT_DESTINATION_RESOURCE_ID,
                settings.screenshotDestinationResourceId
            )
            preferences[KEY_COPY_SCREENSHOT_TO_CLIPBOARD] = settings.copyScreenshotToClipboard
            preferences[KEY_SCREEN_CAPTURE_DISCLOSURE_ACCEPTED] = settings.screenCaptureDisclosureAccepted
        }
    }
}
