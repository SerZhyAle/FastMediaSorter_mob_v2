package com.sza.fastmediasorter.domain.model

/**
 * Action assignable to an edge gesture direction. The screenshot actions capture the screen first and
 * then run a post-capture route; the camera/recording actions ([LAUNCH_CAMERA], [TAKE_PHOTO] and the
 * TAKE_PHOTO_* variants, [START_VIDEO_RECORDING], [START_AUDIO_RECORDING], [START_SCREEN_RECORDING])
 * plus [OPEN_APP], [OPEN_PANEL], [OPEN_ALL_APPS] and [DO_NOT_USE] are pre-capture actions that skip
 * the screenshot path.
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

    // S1038: device-control actions (all gesture flavors), pre-capture. Flashlight + brightness run
    // through DeviceActionHandler; volume + media transport through MediaActionHandler. All surface under
    // the single DEVICE picker group per the strategic spec's "device control" category.
    TOGGLE_FLASHLIGHT,
    BRIGHTNESS_MAX,
    BRIGHTNESS_NORMAL,
    VOLUME_UP,
    VOLUME_DOWN,
    VOLUME_MUTE,
    MEDIA_PLAY_PAUSE,
    MEDIA_NEXT,
    MEDIA_PREV,

    // S1038: launch/intent actions (all gesture flavors), pre-capture, dispatched by LaunchActionHandler.
    // Each is a guarded startActivity that degrades to a safe no-op when the target app is absent.
    // OPEN_URL reads the per-slot payload (S1038 phase 02) for its target address. Group LAUNCH.
    OPEN_ASSISTANT,
    OPEN_GEMINI,
    CREATE_KEEP_NOTE,
    OPEN_URL,
    SET_ALARM,
    SET_TIMER,
    NEW_CALENDAR_EVENT,

    // S1038: system actions performed through the accessibility service's global actions. The enum lives
    // in src/main (shared) so the values exist on every flavor, but only the noLegal accessibility path
    // can perform them - the picker surfaces the SYSTEM group solely where that capability is compiled.
    OPEN_NOTIFICATION_SHADE,
    OPEN_QUICK_SETTINGS,
    LOCK_SCREEN,
    TOGGLE_SPLIT_SCREEN,
    PREVIOUS_APP,

    // S2256: launcher route - bring the home surface forward with its All apps panel open. Executable
    // only where the launcher-mode seam is compiled in; the picker hides it on the other flavors.
    OPEN_ALL_APPS,
    DO_NOT_USE;

    companion object {
        /** Tolerant DataStore parser: returns the matching constant or [default] for unknown/null values. */
        fun fromName(name: String?, default: ScreenshotGestureAction): ScreenshotGestureAction =
            entries.firstOrNull { it.name == name } ?: default
    }
}
