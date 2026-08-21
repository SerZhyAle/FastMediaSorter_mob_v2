package com.sza.fastmediasorter.core.panel

import android.content.Context
import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.sza.fastmediasorter.R

/**
 * Static descriptor table of our own launchable features offered in the app-launch panel
 * (strategic S0663 §5.1.B). Each entry pairs a stable route key with the label/icon the matching
 * widget already uses and a reference to its intent builder in [AppLaunchPanelRouteIntents]. No
 * availability logic lives here - that is [com.sza.fastmediasorter.domain.usecase.panel.ResolvePanelRouteAvailabilityUseCase].
 */
object InternalRouteCatalog {

    /**
     * One feature route. [intent] reuses the widget entry point; [settingsIntent] (non-null only for
     * routes with a runtime on/off toggle, e.g. the game) is used when the feature is compiled in but
     * disabled by a setting, so the tile opens the relevant setting instead of dead-launching (§6.1).
     */
    data class Route(
        val key: String,
        @StringRes val labelRes: Int,
        @DrawableRes val iconRes: Int,
        val intent: (Context) -> Intent,
        val settingsIntent: ((Context) -> Intent)? = null,
    )

    // S1103: launch the quick-access panel overlay from a launcher cell.
    const val KEY_APP_LAUNCH_PANEL = "app_launch_panel"
    const val KEY_CALCULATOR = "calculator"
    const val KEY_NETWORK_MONITOR = "network_monitor"
    const val KEY_GAME = "game"
    const val KEY_OCR = "ocr"
    const val KEY_STREAMS = "streams"
    const val KEY_FAVORITES = "favorites"
    const val KEY_QUICK_CAMERA = "quick_camera"
    const val KEY_QUICK_VOICE = "quick_voice"
    const val KEY_SCREEN_RECORDING = "screen_recording"
    const val KEY_LINK_DOWNLOAD = "link_download"

    // S1796: the screen itself used as a lamp - a program like its neighbours, with its own toggle.
    const val KEY_FRONT_FLASHLIGHT = "front_flashlight"

    // S1733: system information as a program of its own, reachable without going into settings.
    const val KEY_SYSTEM_INFO = "system_info"
    const val KEY_WEAR_COMPANION = "wear_companion"

    // S0978: the camera/video gesture actions that already have a Context-generic trampoline, offered
    // as panel routes too (labels reused from the left-edge gesture picker so wording never drifts).
    const val KEY_TAKE_PHOTO_SEND_TO = "take_photo_send_to"
    const val KEY_TAKE_PHOTO_EDIT = "take_photo_edit"
    const val KEY_TAKE_PHOTO_OCR_TRANSLATE = "take_photo_ocr_translate"
    const val KEY_START_VIDEO_RECORDING = "start_video_recording"

    // S1170: the destinations the mechanical home-screen widgets fire that no route covered yet, so a
    // launcher desktop cell can express the same tap. Label and icon are taken from the matching
    // HomeWidgetCatalog entry rather than minted fresh - the two surfaces must never word it differently.
    const val KEY_CAMERA_PHOTOS = "camera_photos"
    const val KEY_CAMERA_LAUNCH = "camera_launch"
    const val KEY_CONTINUE_READING = "continue_reading"
    const val KEY_RANDOM_MUSIC = "random_music"
    const val KEY_SCHEDULED_TASKS = "scheduled_tasks"

    private val routes: List<Route> = listOf(
        Route(
            key = KEY_APP_LAUNCH_PANEL,
            labelRes = R.string.app_launch_panel_route_launch_panel,
            iconRes = R.drawable.ic_view_grid,
            intent = AppLaunchPanelRouteIntents::appLaunchPanel,
        ),
        Route(
            key = KEY_CALCULATOR,
            labelRes = R.string.app_launch_panel_route_calculator,
            iconRes = R.drawable.ic_calculator,
            intent = AppLaunchPanelRouteIntents::calculator,
            settingsIntent = AppLaunchPanelRouteIntents::calculatorSettings,
        ),
        Route(
            key = KEY_NETWORK_MONITOR,
            labelRes = R.string.network_monitor_title,
            iconRes = R.drawable.ic_network_monitor,
            intent = AppLaunchPanelRouteIntents::networkMonitor,
            settingsIntent = AppLaunchPanelRouteIntents::networkMonitorSettings,
        ),
        Route(
            key = KEY_GAME,
            labelRes = R.string.app_launch_panel_route_game,
            iconRes = R.drawable.ic_game_kryvavitsa,
            intent = AppLaunchPanelRouteIntents::game,
            settingsIntent = { com.sza.fastmediasorter.core.game.GameLaunchIntents.settingsGameToggle(it) },
        ),
        // S1733: label reused from the settings dialog rather than minted fresh, the way the network
        // monitor reuses its own title - two wordings for one program drift apart.
        Route(
            key = KEY_SYSTEM_INFO,
            labelRes = R.string.settings_system_info_title,
            iconRes = R.drawable.ic_info,
            intent = AppLaunchPanelRouteIntents::systemInfo,
            settingsIntent = AppLaunchPanelRouteIntents::systemInfoSettings,
        ),
        // S1883: label reused from the button that has always opened the companion, for the same reason
        // system information reuses its settings string - two wordings for one program drift apart.
        Route(
            key = KEY_WEAR_COMPANION,
            labelRes = R.string.wear_companion,
            iconRes = R.drawable.ic_watch,
            intent = AppLaunchPanelRouteIntents::wearCompanion,
            settingsIntent = AppLaunchPanelRouteIntents::wearCompanionSettings,
        ),
        Route(
            key = KEY_OCR,
            labelRes = R.string.app_launch_panel_route_ocr,
            iconRes = R.drawable.ic_camera_ocr_translate,
            intent = AppLaunchPanelRouteIntents::ocr,
        ),
        Route(
            key = KEY_STREAMS,
            labelRes = R.string.app_launch_panel_route_streams,
            iconRes = R.drawable.ic_cast,
            intent = AppLaunchPanelRouteIntents::streams,
        ),
        Route(
            key = KEY_FAVORITES,
            labelRes = R.string.app_launch_panel_route_favorites,
            iconRes = R.drawable.ic_resource_favorites,
            intent = AppLaunchPanelRouteIntents::favorites,
        ),
        // S0912: the four routes below reuse the exact label/icon the Programs-and-Scenarios main-menu
        // entry already uses for the same feature, so the panel picker never drifts from that wording.
        Route(
            key = KEY_QUICK_CAMERA,
            labelRes = R.string.widget_camera_quick_capture_label,
            iconRes = R.drawable.ic_camera_capture,
            intent = AppLaunchPanelRouteIntents::quickCamera,
        ),
        Route(
            key = KEY_QUICK_VOICE,
            labelRes = R.string.quick_voice_menu_label,
            iconRes = R.drawable.ic_microphone,
            intent = AppLaunchPanelRouteIntents::quickVoice,
        ),
        Route(
            key = KEY_SCREEN_RECORDING,
            labelRes = R.string.screen_recording_menu_label,
            iconRes = R.drawable.ic_display,
            intent = AppLaunchPanelRouteIntents::screenRecording,
        ),
        Route(
            key = KEY_LINK_DOWNLOAD,
            labelRes = R.string.download_by_link_menu_label,
            iconRes = R.drawable.ic_cloud_download,
            intent = AppLaunchPanelRouteIntents::linkDownload,
        ),
        Route(
            key = KEY_FRONT_FLASHLIGHT,
            labelRes = R.string.front_flashlight_title,
            iconRes = R.drawable.ic_front_flashlight,
            intent = AppLaunchPanelRouteIntents::frontFlashlight,
            settingsIntent = AppLaunchPanelRouteIntents::frontFlashlightSettings,
        ),
        // S0978: camera/video gesture actions with an existing standalone trampoline. Labels reuse the
        // left-edge gesture picker's own strings; order follows the ScreenshotGestureAction enum order.
        Route(
            key = KEY_TAKE_PHOTO_SEND_TO,
            labelRes = R.string.screenshot_gesture_action_take_photo_send_to,
            iconRes = R.drawable.ic_camera_send_to,
            intent = AppLaunchPanelRouteIntents::takePhotoSendTo,
        ),
        Route(
            key = KEY_TAKE_PHOTO_EDIT,
            labelRes = R.string.screenshot_gesture_action_take_photo_edit,
            iconRes = R.drawable.ic_edit_20,
            intent = AppLaunchPanelRouteIntents::takePhotoEdit,
        ),
        Route(
            key = KEY_TAKE_PHOTO_OCR_TRANSLATE,
            labelRes = R.string.screenshot_gesture_action_take_photo_ocr_translate,
            iconRes = R.drawable.ic_camera_ocr_translate,
            intent = AppLaunchPanelRouteIntents::takePhotoOcrTranslate,
        ),
        Route(
            key = KEY_START_VIDEO_RECORDING,
            labelRes = R.string.screenshot_gesture_action_start_video_recording,
            iconRes = R.drawable.ic_video,
            intent = AppLaunchPanelRouteIntents::startVideoRecording,
        ),
        // S1170: label and icon are the widget picker's own, so the desktop cell, the picker row and the
        // Android-home widget all read the same. No new string keys are minted for these five.
        Route(
            key = KEY_CAMERA_PHOTOS,
            labelRes = R.string.widget_camera_photos_label,
            iconRes = R.drawable.ic_widget_camera_photos,
            intent = AppLaunchPanelRouteIntents::cameraPhotos,
        ),
        Route(
            key = KEY_CAMERA_LAUNCH,
            labelRes = R.string.widget_camera_launch_label,
            iconRes = R.drawable.ic_widget_camera_launch_accent,
            intent = AppLaunchPanelRouteIntents::cameraLaunch,
        ),
        Route(
            key = KEY_CONTINUE_READING,
            labelRes = R.string.widget_continue_reading_label,
            iconRes = R.drawable.ic_widget_continue_reading,
            intent = AppLaunchPanelRouteIntents::continueReading,
        ),
        Route(
            key = KEY_RANDOM_MUSIC,
            labelRes = R.string.widget_random_music_label,
            iconRes = R.drawable.ic_widget_random_music,
            intent = AppLaunchPanelRouteIntents::randomMusic,
        ),
        Route(
            key = KEY_SCHEDULED_TASKS,
            labelRes = R.string.widget_scheduled_tasks_label,
            iconRes = R.drawable.ic_widget_scheduled_tasks,
            intent = AppLaunchPanelRouteIntents::scheduledTasks,
        ),
    )

    fun all(): List<Route> = routes

    fun byKey(key: String): Route? = routes.firstOrNull { it.key == key }
}
