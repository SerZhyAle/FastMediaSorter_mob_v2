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

    const val KEY_CALCULATOR = "calculator"
    const val KEY_GAME = "game"
    const val KEY_OCR = "ocr"
    const val KEY_STREAMS = "streams"
    const val KEY_FAVORITES = "favorites"
    const val KEY_QUICK_CAMERA = "quick_camera"
    const val KEY_QUICK_VOICE = "quick_voice"
    const val KEY_SCREEN_RECORDING = "screen_recording"
    const val KEY_LINK_DOWNLOAD = "link_download"

    private val routes: List<Route> = listOf(
        Route(
            key = KEY_CALCULATOR,
            labelRes = R.string.app_launch_panel_route_calculator,
            iconRes = R.drawable.ic_calculator,
            intent = AppLaunchPanelRouteIntents::calculator,
        ),
        Route(
            key = KEY_GAME,
            labelRes = R.string.app_launch_panel_route_game,
            iconRes = R.drawable.ic_game_kryvavitsa,
            intent = AppLaunchPanelRouteIntents::game,
            settingsIntent = { com.sza.fastmediasorter.core.game.GameLaunchIntents.settingsGameToggle(it) },
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
            labelRes = R.string.quick_camera_menu_label,
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
    )

    fun all(): List<Route> = routes

    fun byKey(key: String): Route? = routes.firstOrNull { it.key == key }
}
