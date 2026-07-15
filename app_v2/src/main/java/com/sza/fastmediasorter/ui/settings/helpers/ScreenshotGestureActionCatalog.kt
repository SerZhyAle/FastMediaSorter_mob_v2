package com.sza.fastmediasorter.ui.settings.helpers

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
        ScreenshotGestureAction.DO_NOT_USE -> GestureActionMeta(
            GestureActionGroup.DISABLED,
            R.string.screenshot_gesture_action_none,
            R.string.gesture_action_explain_do_not_use,
        )
    }

    fun groupOf(action: ScreenshotGestureAction): GestureActionGroup = metaFor(action).group

    @StringRes
    fun labelResFor(action: ScreenshotGestureAction): Int = metaFor(action).labelRes

    @StringRes
    fun explanationResFor(action: ScreenshotGestureAction): Int = metaFor(action).explanationRes
}
