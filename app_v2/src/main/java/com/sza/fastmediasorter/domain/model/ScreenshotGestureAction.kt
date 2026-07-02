package com.sza.fastmediasorter.domain.model

/**
 * Action assignable to an edge gesture direction. The screenshot actions capture the screen first and
 * then run a post-capture route; the camera/recording actions ([LAUNCH_CAMERA], [TAKE_PHOTO] and the
 * TAKE_PHOTO_* variants, [START_VIDEO_RECORDING], [START_AUDIO_RECORDING], [START_SCREEN_RECORDING])
 * plus [OPEN_APP], [OPEN_PANEL] and [DO_NOT_USE] are pre-capture actions that skip the screenshot path.
 */
enum class ScreenshotGestureAction {
    SILENT_SCREENSHOT,
    OPEN_IN_PLAYER,
    OPEN_IN_DRAW,
    OCR_TRANSLATE,
    SEND_TO_RECIPIENTS,
    SHARE,
    CROP_AND_SHARE,
    OPEN_APP,
    OPEN_PANEL,

    // S0788: pre-capture action - open the in-app photo/video camera instead of capturing the screen.
    LAUNCH_CAMERA,

    // S0790-S0794: pre-capture - auto-capture a photo, then save+toast / send-to / edit / OCR-translate.
    TAKE_PHOTO,
    TAKE_PHOTO_SEND_TO,
    TAKE_PHOTO_EDIT,
    TAKE_PHOTO_OCR_TRANSLATE,

    // S0795: pre-capture - open the in-app camera fixed in video mode, primed to record.
    START_VIDEO_RECORDING,

    // S0796: pre-capture - toggle the quick voice recorder (starts, or stops an active session).
    START_AUDIO_RECORDING,

    // S0797: pre-capture - toggle screen video recording (consent + foreground service). Offered only
    // where the capture engine is compiled in (standard fms.screenCapture=on + noLegal).
    START_SCREEN_RECORDING,
    DO_NOT_USE;

    companion object {
        /** Tolerant DataStore parser: returns the matching constant or [default] for unknown/null values. */
        fun fromName(name: String?, default: ScreenshotGestureAction): ScreenshotGestureAction =
            entries.firstOrNull { it.name == name } ?: default
    }
}
