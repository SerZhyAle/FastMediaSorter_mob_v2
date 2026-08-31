package com.sza.fastmediasorter.ui.settings.helpers

import android.os.Build
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.ScreenshotGestureAction

/**
 * S1038: presentation metadata for a single gesture action - its group, label and explanation.
 * S1166: plus the picker icon, so a list of ~40 actions is scanned by glance instead of by reading.
 */
data class GestureActionMeta(
    val group: GestureActionGroup,
    @StringRes val labelRes: Int,
    @StringRes val explanationRes: Int,
    @DrawableRes val iconRes: Int,
)

/**
 * S1038: single source of truth mapping each [ScreenshotGestureAction] to its picker metadata
 * (group + label + explanation + icon). Kept out of the enum so adding an action does not bloat the
 * domain model; label strings are reused from the pre-S1038 flat picker, explanations are new. New
 * action batches (phases 03-07) extend the [metaFor] when as they add enum values.
 */
object ScreenshotGestureActionCatalog {

    fun metaFor(action: ScreenshotGestureAction): GestureActionMeta = when (action) {
        ScreenshotGestureAction.SILENT_SCREENSHOT -> GestureActionMeta(
            GestureActionGroup.CAPTURE,
            R.string.screenshot_gesture_action_silent,
            R.string.gesture_action_explain_silent_screenshot,
            R.drawable.ic_gesture_action_screenshot,
        )
        ScreenshotGestureAction.OPEN_IN_PLAYER -> GestureActionMeta(
            GestureActionGroup.CAPTURE,
            R.string.screenshot_gesture_action_open_player,
            R.string.gesture_action_explain_open_in_player,
            R.drawable.ic_play,
        )
        ScreenshotGestureAction.OPEN_IN_DRAW -> GestureActionMeta(
            GestureActionGroup.CAPTURE,
            R.string.screenshot_gesture_action_open_draw,
            R.string.gesture_action_explain_open_in_draw,
            R.drawable.ic_draw_overlay,
        )
        ScreenshotGestureAction.OCR_TRANSLATE -> GestureActionMeta(
            GestureActionGroup.CAPTURE,
            R.string.screenshot_gesture_action_ocr_translate,
            R.string.gesture_action_explain_ocr_translate,
            R.drawable.ic_ocr,
        )
        ScreenshotGestureAction.SEND_TO_RECIPIENTS -> GestureActionMeta(
            GestureActionGroup.CAPTURE,
            R.string.screenshot_gesture_action_send_to,
            R.string.gesture_action_explain_send_to_recipients,
            R.drawable.ic_send_plane,
        )
        ScreenshotGestureAction.SHARE -> GestureActionMeta(
            GestureActionGroup.CAPTURE,
            R.string.screenshot_gesture_action_share,
            R.string.gesture_action_explain_share,
            R.drawable.ic_share,
        )
        ScreenshotGestureAction.CROP_AND_SHARE -> GestureActionMeta(
            GestureActionGroup.CAPTURE,
            R.string.screenshot_gesture_action_crop_and_share,
            R.string.gesture_action_explain_crop_and_share,
            R.drawable.ic_crop,
        )
        ScreenshotGestureAction.OPEN_APP -> GestureActionMeta(
            GestureActionGroup.LAUNCH,
            R.string.screenshot_gesture_action_open_app,
            R.string.gesture_action_explain_open_app,
            R.drawable.ic_exit_to_app,
        )
        ScreenshotGestureAction.OPEN_PANEL -> GestureActionMeta(
            GestureActionGroup.LAUNCH,
            R.string.screenshot_gesture_action_open_panel,
            R.string.gesture_action_explain_open_panel,
            R.drawable.ic_apps,
        )
        ScreenshotGestureAction.LAUNCH_CAMERA -> GestureActionMeta(
            GestureActionGroup.CAMERA,
            R.string.screenshot_gesture_action_launch_camera,
            R.string.gesture_action_explain_launch_camera,
            R.drawable.ic_camera_capture,
        )
        ScreenshotGestureAction.TAKE_PHOTO -> GestureActionMeta(
            GestureActionGroup.CAMERA,
            R.string.screenshot_gesture_action_take_photo,
            R.string.gesture_action_explain_take_photo,
            R.drawable.ic_gesture_action_shutter,
        )
        ScreenshotGestureAction.TAKE_PHOTO_SEND_TO -> GestureActionMeta(
            GestureActionGroup.CAMERA,
            R.string.screenshot_gesture_action_take_photo_send_to,
            R.string.gesture_action_explain_take_photo_send_to,
            R.drawable.ic_camera_send_to,
        )
        ScreenshotGestureAction.TAKE_PHOTO_EDIT -> GestureActionMeta(
            GestureActionGroup.CAMERA,
            R.string.screenshot_gesture_action_take_photo_edit,
            R.string.gesture_action_explain_take_photo_edit,
            R.drawable.ic_edit_20,
        )
        ScreenshotGestureAction.TAKE_PHOTO_OCR_TRANSLATE -> GestureActionMeta(
            GestureActionGroup.CAMERA,
            R.string.screenshot_gesture_action_take_photo_ocr_translate,
            R.string.gesture_action_explain_take_photo_ocr_translate,
            R.drawable.ic_camera_ocr_translate,
        )
        ScreenshotGestureAction.START_VIDEO_RECORDING -> GestureActionMeta(
            GestureActionGroup.CAMERA,
            R.string.screenshot_gesture_action_start_video_recording,
            R.string.gesture_action_explain_start_video_recording,
            R.drawable.ic_video,
        )
        ScreenshotGestureAction.START_AUDIO_RECORDING -> GestureActionMeta(
            GestureActionGroup.UTILITY,
            R.string.screenshot_gesture_action_start_audio_recording,
            R.string.gesture_action_explain_start_audio_recording,
            R.drawable.ic_microphone,
        )
        ScreenshotGestureAction.START_SCREEN_RECORDING -> GestureActionMeta(
            GestureActionGroup.UTILITY,
            R.string.screenshot_gesture_action_start_screen_recording,
            R.string.gesture_action_explain_start_screen_recording,
            R.drawable.ic_gesture_action_screen_record,
        )
        ScreenshotGestureAction.OPEN_NOTIFICATION_SHADE -> GestureActionMeta(
            GestureActionGroup.SYSTEM,
            R.string.screenshot_gesture_action_notification_shade,
            R.string.gesture_action_explain_open_notification_shade,
            R.drawable.ic_gesture_action_notification_shade,
        )
        ScreenshotGestureAction.OPEN_QUICK_SETTINGS -> GestureActionMeta(
            GestureActionGroup.SYSTEM,
            R.string.screenshot_gesture_action_quick_settings,
            R.string.gesture_action_explain_open_quick_settings,
            R.drawable.ic_tune,
        )
        ScreenshotGestureAction.LOCK_SCREEN -> GestureActionMeta(
            GestureActionGroup.SYSTEM,
            R.string.screenshot_gesture_action_lock_screen,
            R.string.gesture_action_explain_lock_screen,
            R.drawable.ic_lock,
        )
        ScreenshotGestureAction.TOGGLE_SPLIT_SCREEN -> GestureActionMeta(
            GestureActionGroup.SYSTEM,
            R.string.screenshot_gesture_action_split_screen,
            R.string.gesture_action_explain_toggle_split_screen,
            R.drawable.ic_gesture_action_split_screen,
        )
        ScreenshotGestureAction.PREVIOUS_APP -> GestureActionMeta(
            GestureActionGroup.SYSTEM,
            R.string.screenshot_gesture_action_recent_apps,
            R.string.gesture_action_explain_previous_app,
            R.drawable.ic_virtual_recent,
        )
        ScreenshotGestureAction.OPEN_ASSISTANT -> GestureActionMeta(
            GestureActionGroup.LAUNCH,
            R.string.screenshot_gesture_action_open_assistant,
            R.string.gesture_action_explain_open_assistant,
            R.drawable.ic_gesture_action_assistant,
        )
        ScreenshotGestureAction.OPEN_GEMINI -> GestureActionMeta(
            GestureActionGroup.LAUNCH,
            R.string.screenshot_gesture_action_open_gemini,
            R.string.gesture_action_explain_open_gemini,
            R.drawable.ic_gesture_action_gemini,
        )
        ScreenshotGestureAction.CREATE_KEEP_NOTE -> GestureActionMeta(
            GestureActionGroup.LAUNCH,
            R.string.screenshot_gesture_action_create_keep_note,
            R.string.gesture_action_explain_create_keep_note,
            R.drawable.ic_text_send_keep,
        )
        ScreenshotGestureAction.OPEN_URL -> GestureActionMeta(
            GestureActionGroup.LAUNCH,
            R.string.screenshot_gesture_action_open_url,
            R.string.gesture_action_explain_open_url,
            R.drawable.ic_open_in_browse,
        )
        ScreenshotGestureAction.SET_ALARM -> GestureActionMeta(
            GestureActionGroup.LAUNCH,
            R.string.screenshot_gesture_action_set_alarm,
            R.string.gesture_action_explain_set_alarm,
            R.drawable.ic_gesture_action_alarm,
        )
        ScreenshotGestureAction.SET_TIMER -> GestureActionMeta(
            GestureActionGroup.LAUNCH,
            R.string.screenshot_gesture_action_set_timer,
            R.string.gesture_action_explain_set_timer,
            R.drawable.ic_schedule,
        )
        ScreenshotGestureAction.NEW_CALENDAR_EVENT -> GestureActionMeta(
            GestureActionGroup.LAUNCH,
            R.string.screenshot_gesture_action_new_calendar_event,
            R.string.gesture_action_explain_new_calendar_event,
            R.drawable.ic_gesture_action_calendar,
        )
        ScreenshotGestureAction.TOGGLE_FLASHLIGHT -> GestureActionMeta(
            GestureActionGroup.DEVICE,
            R.string.screenshot_gesture_action_flashlight,
            R.string.gesture_action_explain_toggle_flashlight,
            R.drawable.ic_camera_flash_on,
        )
        ScreenshotGestureAction.BRIGHTNESS_MAX -> GestureActionMeta(
            GestureActionGroup.DEVICE,
            R.string.screenshot_gesture_action_brightness_max,
            R.string.gesture_action_explain_brightness_max,
            R.drawable.ic_gesture_action_brightness_high,
        )
        ScreenshotGestureAction.BRIGHTNESS_NORMAL -> GestureActionMeta(
            GestureActionGroup.DEVICE,
            R.string.screenshot_gesture_action_brightness_normal,
            R.string.gesture_action_explain_brightness_normal,
            R.drawable.ic_gesture_action_brightness_medium,
        )
        ScreenshotGestureAction.VOLUME_UP -> GestureActionMeta(
            GestureActionGroup.DEVICE,
            R.string.screenshot_gesture_action_volume_up,
            R.string.gesture_action_explain_volume_up,
            R.drawable.ic_volume_up,
        )
        ScreenshotGestureAction.VOLUME_DOWN -> GestureActionMeta(
            GestureActionGroup.DEVICE,
            R.string.screenshot_gesture_action_volume_down,
            R.string.gesture_action_explain_volume_down,
            R.drawable.ic_gesture_action_volume_down,
        )
        ScreenshotGestureAction.VOLUME_MUTE -> GestureActionMeta(
            GestureActionGroup.DEVICE,
            R.string.screenshot_gesture_action_volume_mute,
            R.string.gesture_action_explain_volume_mute,
            R.drawable.ic_gesture_action_volume_mute,
        )
        ScreenshotGestureAction.MEDIA_PLAY_PAUSE -> GestureActionMeta(
            GestureActionGroup.DEVICE,
            R.string.screenshot_gesture_action_media_play_pause,
            R.string.gesture_action_explain_media_play_pause,
            R.drawable.ic_gesture_action_play_pause,
        )
        ScreenshotGestureAction.MEDIA_NEXT -> GestureActionMeta(
            GestureActionGroup.DEVICE,
            R.string.screenshot_gesture_action_media_next,
            R.string.gesture_action_explain_media_next,
            R.drawable.ic_skip_next,
        )
        ScreenshotGestureAction.MEDIA_PREV -> GestureActionMeta(
            GestureActionGroup.DEVICE,
            R.string.screenshot_gesture_action_media_prev,
            R.string.gesture_action_explain_media_prev,
            R.drawable.ic_skip_previous,
        )
        // S2256: shares the launcher panel's own "All apps" label so both catalogs name it identically.
        ScreenshotGestureAction.OPEN_ALL_APPS -> GestureActionMeta(
            GestureActionGroup.LAUNCH,
            R.string.launcher_action_all_apps,
            R.string.gesture_action_explain_open_all_apps,
            R.drawable.ic_view_grid,
        )
        ScreenshotGestureAction.DO_NOT_USE -> GestureActionMeta(
            GestureActionGroup.DISABLED,
            R.string.screenshot_gesture_action_none,
            R.string.gesture_action_explain_do_not_use,
            R.drawable.ic_gesture_action_none,
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

    @DrawableRes
    fun iconResFor(action: ScreenshotGestureAction): Int = metaFor(action).iconRes
}
