package com.sza.fastmediasorter.ui.settings.helpers

import android.os.Build
import androidx.annotation.StringRes
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.ScreenshotGestureAction

/** S1038: presentation metadata for a single gesture action - its group, label and explanation. */
data class GestureActionMeta(
    val group: GestureActionGroup,
    @StringRes val labelRes: Int,
    @StringRes val explanationRes: Int,
)

/**
 * S1038: single source of truth mapping each [ScreenshotGestureAction] to its picker metadata
 * (group + label + explanation). Kept out of the enum so adding an action does not bloat the domain
 * model; label strings are reused from the pre-S1038 flat picker, explanations are new. New action
 * batches (phases 03-07) extend the [metaFor] when as they add enum values.
 */
object ScreenshotGestureActionCatalog {

    fun metaFor(action: ScreenshotGestureAction): GestureActionMeta = when (action) {
        ScreenshotGestureAction.SILENT_SCREENSHOT -> GestureActionMeta(
            GestureActionGroup.CAPTURE,
            R.string.screenshot_gesture_action_silent,
            R.string.gesture_action_explain_silent_screenshot,
        )
        ScreenshotGestureAction.OPEN_IN_PLAYER -> GestureActionMeta(
            GestureActionGroup.CAPTURE,
            R.string.screenshot_gesture_action_open_player,
            R.string.gesture_action_explain_open_in_player,
        )
        ScreenshotGestureAction.OPEN_IN_DRAW -> GestureActionMeta(
            GestureActionGroup.CAPTURE,
            R.string.screenshot_gesture_action_open_draw,
            R.string.gesture_action_explain_open_in_draw,
        )
        ScreenshotGestureAction.OCR_TRANSLATE -> GestureActionMeta(
            GestureActionGroup.CAPTURE,
            R.string.screenshot_gesture_action_ocr_translate,
            R.string.gesture_action_explain_ocr_translate,
        )
        ScreenshotGestureAction.SEND_TO_RECIPIENTS -> GestureActionMeta(
            GestureActionGroup.CAPTURE,
            R.string.screenshot_gesture_action_send_to,
            R.string.gesture_action_explain_send_to_recipients,
        )
        ScreenshotGestureAction.SHARE -> GestureActionMeta(
            GestureActionGroup.CAPTURE,
            R.string.screenshot_gesture_action_share,
            R.string.gesture_action_explain_share,
        )
        ScreenshotGestureAction.CROP_AND_SHARE -> GestureActionMeta(
            GestureActionGroup.CAPTURE,
            R.string.screenshot_gesture_action_crop_and_share,
            R.string.gesture_action_explain_crop_and_share,
        )
        ScreenshotGestureAction.OPEN_APP -> GestureActionMeta(
            GestureActionGroup.LAUNCH,
            R.string.screenshot_gesture_action_open_app,
            R.string.gesture_action_explain_open_app,
        )
        ScreenshotGestureAction.OPEN_PANEL -> GestureActionMeta(
            GestureActionGroup.LAUNCH,
            R.string.screenshot_gesture_action_open_panel,
            R.string.gesture_action_explain_open_panel,
        )
        ScreenshotGestureAction.LAUNCH_CAMERA -> GestureActionMeta(
            GestureActionGroup.CAMERA,
            R.string.screenshot_gesture_action_launch_camera,
            R.string.gesture_action_explain_launch_camera,
        )
        ScreenshotGestureAction.TAKE_PHOTO -> GestureActionMeta(
            GestureActionGroup.CAMERA,
            R.string.screenshot_gesture_action_take_photo,
            R.string.gesture_action_explain_take_photo,
        )
        ScreenshotGestureAction.TAKE_PHOTO_SEND_TO -> GestureActionMeta(
            GestureActionGroup.CAMERA,
            R.string.screenshot_gesture_action_take_photo_send_to,
            R.string.gesture_action_explain_take_photo_send_to,
        )
        ScreenshotGestureAction.TAKE_PHOTO_EDIT -> GestureActionMeta(
            GestureActionGroup.CAMERA,
            R.string.screenshot_gesture_action_take_photo_edit,
            R.string.gesture_action_explain_take_photo_edit,
        )
        ScreenshotGestureAction.TAKE_PHOTO_OCR_TRANSLATE -> GestureActionMeta(
            GestureActionGroup.CAMERA,
            R.string.screenshot_gesture_action_take_photo_ocr_translate,
            R.string.gesture_action_explain_take_photo_ocr_translate,
        )
        ScreenshotGestureAction.START_VIDEO_RECORDING -> GestureActionMeta(
            GestureActionGroup.CAMERA,
            R.string.screenshot_gesture_action_start_video_recording,
            R.string.gesture_action_explain_start_video_recording,
        )
        ScreenshotGestureAction.START_AUDIO_RECORDING -> GestureActionMeta(
            GestureActionGroup.UTILITY,
            R.string.screenshot_gesture_action_start_audio_recording,
            R.string.gesture_action_explain_start_audio_recording,
        )
        ScreenshotGestureAction.START_SCREEN_RECORDING -> GestureActionMeta(
            GestureActionGroup.UTILITY,
            R.string.screenshot_gesture_action_start_screen_recording,
            R.string.gesture_action_explain_start_screen_recording,
        )
        ScreenshotGestureAction.OPEN_NOTIFICATION_SHADE -> GestureActionMeta(
            GestureActionGroup.SYSTEM,
            R.string.screenshot_gesture_action_notification_shade,
            R.string.gesture_action_explain_open_notification_shade,
        )
        ScreenshotGestureAction.OPEN_QUICK_SETTINGS -> GestureActionMeta(
            GestureActionGroup.SYSTEM,
            R.string.screenshot_gesture_action_quick_settings,
            R.string.gesture_action_explain_open_quick_settings,
        )
        ScreenshotGestureAction.LOCK_SCREEN -> GestureActionMeta(
            GestureActionGroup.SYSTEM,
            R.string.screenshot_gesture_action_lock_screen,
            R.string.gesture_action_explain_lock_screen,
        )
        ScreenshotGestureAction.TOGGLE_SPLIT_SCREEN -> GestureActionMeta(
            GestureActionGroup.SYSTEM,
            R.string.screenshot_gesture_action_split_screen,
            R.string.gesture_action_explain_toggle_split_screen,
        )
        ScreenshotGestureAction.PREVIOUS_APP -> GestureActionMeta(
            GestureActionGroup.SYSTEM,
            R.string.screenshot_gesture_action_recent_apps,
            R.string.gesture_action_explain_previous_app,
        )
        ScreenshotGestureAction.OPEN_ASSISTANT -> GestureActionMeta(
            GestureActionGroup.LAUNCH,
            R.string.screenshot_gesture_action_open_assistant,
            R.string.gesture_action_explain_open_assistant,
        )
        ScreenshotGestureAction.OPEN_GEMINI -> GestureActionMeta(
            GestureActionGroup.LAUNCH,
            R.string.screenshot_gesture_action_open_gemini,
            R.string.gesture_action_explain_open_gemini,
        )
        ScreenshotGestureAction.CREATE_KEEP_NOTE -> GestureActionMeta(
            GestureActionGroup.LAUNCH,
            R.string.screenshot_gesture_action_create_keep_note,
            R.string.gesture_action_explain_create_keep_note,
        )
        ScreenshotGestureAction.OPEN_URL -> GestureActionMeta(
            GestureActionGroup.LAUNCH,
            R.string.screenshot_gesture_action_open_url,
            R.string.gesture_action_explain_open_url,
        )
        ScreenshotGestureAction.SET_ALARM -> GestureActionMeta(
            GestureActionGroup.LAUNCH,
            R.string.screenshot_gesture_action_set_alarm,
            R.string.gesture_action_explain_set_alarm,
        )
        ScreenshotGestureAction.SET_TIMER -> GestureActionMeta(
            GestureActionGroup.LAUNCH,
            R.string.screenshot_gesture_action_set_timer,
            R.string.gesture_action_explain_set_timer,
        )
        ScreenshotGestureAction.NEW_CALENDAR_EVENT -> GestureActionMeta(
            GestureActionGroup.LAUNCH,
            R.string.screenshot_gesture_action_new_calendar_event,
            R.string.gesture_action_explain_new_calendar_event,
        )
        ScreenshotGestureAction.TOGGLE_FLASHLIGHT -> GestureActionMeta(
            GestureActionGroup.DEVICE,
            R.string.screenshot_gesture_action_flashlight,
            R.string.gesture_action_explain_toggle_flashlight,
        )
        ScreenshotGestureAction.BRIGHTNESS_MAX -> GestureActionMeta(
            GestureActionGroup.DEVICE,
            R.string.screenshot_gesture_action_brightness_max,
            R.string.gesture_action_explain_brightness_max,
        )
        ScreenshotGestureAction.BRIGHTNESS_NORMAL -> GestureActionMeta(
            GestureActionGroup.DEVICE,
            R.string.screenshot_gesture_action_brightness_normal,
            R.string.gesture_action_explain_brightness_normal,
        )
        ScreenshotGestureAction.VOLUME_UP -> GestureActionMeta(
            GestureActionGroup.DEVICE,
            R.string.screenshot_gesture_action_volume_up,
            R.string.gesture_action_explain_volume_up,
        )
        ScreenshotGestureAction.VOLUME_DOWN -> GestureActionMeta(
            GestureActionGroup.DEVICE,
            R.string.screenshot_gesture_action_volume_down,
            R.string.gesture_action_explain_volume_down,
        )
        ScreenshotGestureAction.VOLUME_MUTE -> GestureActionMeta(
            GestureActionGroup.DEVICE,
            R.string.screenshot_gesture_action_volume_mute,
            R.string.gesture_action_explain_volume_mute,
        )
        ScreenshotGestureAction.MEDIA_PLAY_PAUSE -> GestureActionMeta(
            GestureActionGroup.DEVICE,
            R.string.screenshot_gesture_action_media_play_pause,
            R.string.gesture_action_explain_media_play_pause,
        )
        ScreenshotGestureAction.MEDIA_NEXT -> GestureActionMeta(
            GestureActionGroup.DEVICE,
            R.string.screenshot_gesture_action_media_next,
            R.string.gesture_action_explain_media_next,
        )
        ScreenshotGestureAction.MEDIA_PREV -> GestureActionMeta(
            GestureActionGroup.DEVICE,
            R.string.screenshot_gesture_action_media_prev,
            R.string.gesture_action_explain_media_prev,
        )
        ScreenshotGestureAction.DO_NOT_USE -> GestureActionMeta(
            GestureActionGroup.DISABLED,
            R.string.screenshot_gesture_action_none,
            R.string.gesture_action_explain_do_not_use,
        )
    }

    /**
     * S1038: whether [action] is available at the current API level. The accessibility global actions
     * for lock-screen (API 28+) and split-screen (API 24+) do not exist below those levels, so the
     * picker hides them there instead of offering a control that could never fire.
     */
    fun isAvailableOnApi(action: ScreenshotGestureAction): Boolean = when (action) {
        ScreenshotGestureAction.LOCK_SCREEN -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
        ScreenshotGestureAction.TOGGLE_SPLIT_SCREEN -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
        else -> true
    }

    fun groupOf(action: ScreenshotGestureAction): GestureActionGroup = metaFor(action).group

    @StringRes
    fun labelResFor(action: ScreenshotGestureAction): Int = metaFor(action).labelRes

    @StringRes
    fun explanationResFor(action: ScreenshotGestureAction): Int = metaFor(action).explanationRes
}
