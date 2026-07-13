package com.sza.fastmediasorter.ui.main.helpers

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.ui.applaunchpanel.AppLaunchPanelActivity
import com.sza.fastmediasorter.ui.calculator.CalculatorActivity
import com.sza.fastmediasorter.ui.streams.StreamsActivity

/**
 * S0774: single home for the main-window programs menu - item registration, count, click dispatch,
 * and the S0770 per-item "Open in new window" / "Remove" resolvers. Extracted verbatim from
 * MainActivity (which exceeded the 1500-LOC limit) so the screen-recording scenario has one place to
 * plug into. Behaviour-preserving: the per-scenario menu managers, the canonical item ids/orders, and
 * the gate predicates are unchanged - MainActivity now passes a resolved [ProgramsMenuGate] snapshot
 * each call instead of the coordinator reading its mutable flags.
 */
class MainProgramsMenuCoordinator(
    private val activity: AppCompatActivity,
    private val miniGameMenuManager: MainMiniGameMenuManager,
    private val streamsMenuManager: MainStreamsMenuManager,
    private val quickCaptureMenuManager: MainQuickCaptureMenuManager,
    private val linkDownloadMenuManager: MainLinkDownloadMenuManager,
    private val screenRecordingMenuManager: MainScreenRecordingMenuManager,
    private val hostActions: ProgramsHostActions,
) {

    /**
     * Host callbacks the coordinator delegates to, all resolved by MainActivity (via its panel-item
     * actions manager). Bundled into one holder so the constructor stays under the detekt
     * LongParameterList threshold as new program entries (S0962: VR Cinema) are added.
     */
    class ProgramsHostActions(
        val isNewWindowAvailable: () -> Boolean,
        val launchInNewWindow: (Intent) -> Unit,
        val confirmRemoveProgram: (Int, (AppSettings) -> AppSettings) -> Unit,
        // S0962 (VR Cinema, Pillar 1): tap handler - prompts for a resource, then opens the browser.
        val onVrCinemaSelected: () -> Unit,
    )

    /**
     * Resolved per-scenario visibility for one menu build - MainActivity folds in its runtime flags +
     * media capabilities.
     */
    data class ProgramsMenuGate(
        val streams: Boolean,
        val vrCinema: Boolean,
        val quickVoice: Boolean,
        val quickCamera: Boolean,
        val calculator: Boolean,
        val cameraOcr: Boolean,
        val linkDownload: Boolean,
        val miniGame: Boolean,
        val screenRecording: Boolean,
    )

    // S0757: the Quick Launch Panel entry is always present (no toggle), so the count starts at 1 and
    // the three-dots menu button stays visible even when every other program is disabled.
    fun itemCount(gate: ProgramsMenuGate): Int =
        1 + (if (gate.vrCinema) 1 else 0) + (if (gate.calculator) 1 else 0) +
            (if (gate.cameraOcr) 1 else 0) +
            miniGameMenuManager.itemCount(gate.miniGame) +
            quickCaptureMenuManager.itemCount(gate.quickVoice, gate.quickCamera) +
            linkDownloadMenuManager.itemCount(gate.linkDownload) +
            screenRecordingMenuManager.itemCount(gate.screenRecording)

    // S0756: excludeStreams drops the "Streams" item (the programs panel hides it when the streams
    // panel is visible, to avoid duplicating that entry point). The dropdown menu always passes false.
    fun populate(popup: PopupMenu, excludeStreams: Boolean, gate: ProgramsMenuGate): Int {
        popup.menu.clear()
        // S0758: each item's explicit order = its canonical position in the owner's programs menu order.
        // Sorted display order after S0962: Streams, VR Cinema [S0962], quick-launch panel [S0757],
        // quick-capture, calculator, camera-OCR, screen recording [S0913 - right after camera-OCR],
        // link download, mini-game. The panel mirrors this menu (single source of truth), so the order
        // carries there too.
        streamsMenuManager.populate(popup, !excludeStreams && gate.streams, MENU_ORDER_STREAMS)
        // S0962 (VR Cinema, Pillar 1): immersive-cinema program - shown only when XR is available and the
        // VR-3D master toggle is on (gate.vrCinema). Master-gated with no per-item toggle and not a window,
        // so newWindowActionFor/removeActionFor both leave it on their `else -> null` branch.
        if (gate.vrCinema) {
            popup.menu.add(
                0,
                MENU_ITEM_VR_CINEMA,
                MENU_ORDER_VR_CINEMA,
                R.string.vr_cinema_program_title,
            ).setIcon(R.drawable.ic_vr_headset)
        }
        // S0757: Quick Launch Panel - always present (no on/off; also reachable via tile/gesture/widget).
        popup.menu.add(
            0,
            MENU_ITEM_APP_LAUNCH_PANEL,
            MENU_ORDER_APP_LAUNCH_PANEL,
            R.string.app_launch_panel_title,
        ).setIcon(R.drawable.ic_view_grid)
        quickCaptureMenuManager.populate(
            popup,
            gate.quickVoice,
            gate.quickCamera,
            MENU_ORDER_QUICK_CAPTURE,
        )
        screenRecordingMenuManager.populate(popup, gate.screenRecording, MENU_ORDER_SCREEN_RECORDING)
        if (gate.calculator) {
            popup.menu.add(0, MENU_ITEM_CALCULATOR, MENU_ORDER_CALCULATOR, R.string.calculator_title)
                .setIcon(R.drawable.ic_calculator)
        }
        if (gate.cameraOcr) {
            popup.menu.add(
                0,
                MENU_ITEM_CAMERA_OCR,
                MENU_ORDER_CAMERA_OCR,
                R.string.setting_camera_ocr_translation_title,
            ).setIcon(R.drawable.ic_camera_ocr_translate)
        }
        linkDownloadMenuManager.populate(popup, gate.linkDownload, MENU_ORDER_LINK_DOWNLOAD)
        miniGameMenuManager.populate(popup, gate.miniGame, MENU_ORDER_MINI_GAME)
        return popup.menu.size()
    }

    /** S0755: shared click routing for both the dropdown popup and the programs panel buttons. */
    fun handleMenuItem(itemId: Int): Boolean {
        val handledByManager = miniGameMenuManager.handleMenuItem(itemId) ||
            streamsMenuManager.handleMenuItem(itemId) ||
            quickCaptureMenuManager.handleMenuItem(itemId) ||
            linkDownloadMenuManager.handleMenuItem(itemId) ||
            screenRecordingMenuManager.handleMenuItem(itemId)
        if (handledByManager) return true
        return when (itemId) {
            MENU_ITEM_CALCULATOR -> {
                activity.startActivity(CalculatorActivity.createIntent(activity))
                true
            }
            MENU_ITEM_CAMERA_OCR -> {
                activity.startActivity(
                    com.sza.fastmediasorter.ui.cameraocr.CameraOcrTranslateActivity.createIntent(activity)
                )
                true
            }
            MENU_ITEM_APP_LAUNCH_PANEL -> {
                activity.startActivity(Intent(activity, AppLaunchPanelActivity::class.java))
                true
            }
            MENU_ITEM_VR_CINEMA -> {
                hostActions.onVrCinemaSelected()
                true
            }
            else -> false
        }
    }

    /**
     * S0770: "Open in new window" action for a programs-panel item, or null when multi-window is off or
     * the item is not a standalone window (quick capture / link download act in-place, not as a window).
     */
    fun newWindowActionFor(itemId: Int): (() -> Unit)? {
        if (!hostActions.isNewWindowAvailable()) return null
        val intent = when (itemId) {
            MainStreamsMenuManager.MENU_ITEM_STREAMS -> Intent(activity, StreamsActivity::class.java)
            MENU_ITEM_APP_LAUNCH_PANEL -> Intent(activity, AppLaunchPanelActivity::class.java)
            MENU_ITEM_CALCULATOR -> CalculatorActivity.createIntent(activity)
            MENU_ITEM_CAMERA_OCR ->
                com.sza.fastmediasorter.ui.cameraocr.CameraOcrTranslateActivity.createIntent(activity)
            MainMiniGameMenuManager.MENU_ITEM_GAME ->
                com.sza.fastmediasorter.core.game.GameLaunchIntents.game(activity)
            else -> null
        }
        return intent?.let { resolved -> { hostActions.launchInNewWindow(resolved) } }
    }

    /**
     * S0770: "Remove" action for a programs-panel item = turn off its existing settings toggle, or null
     * when the item has no per-item toggle (Streams master feature + Quick Launch Panel stay un-removable).
     */
    fun removeActionFor(itemId: Int): (() -> Unit)? = when (itemId) {
        MENU_ITEM_CALCULATOR ->
            removeProgramAction(R.string.calculator_title) { it.copy(enableCalculator = false) }
        MENU_ITEM_CAMERA_OCR ->
            removeProgramAction(R.string.setting_camera_ocr_translation_title) {
                it.copy(cameraOcrTranslationEnabled = false)
            }
        MainQuickCaptureMenuManager.MENU_ITEM_QUICK_CAMERA ->
            removeProgramAction(R.string.quick_camera_menu_label) {
                it.copy(disableCameraCapture = true, disableVideoCapture = true)
            }
        MainQuickCaptureMenuManager.MENU_ITEM_QUICK_VOICE ->
            removeProgramAction(R.string.quick_voice_menu_label) { it.copy(micRecordingEnabled = false) }
        MainLinkDownloadMenuManager.MENU_ITEM_LINK_DOWNLOAD ->
            removeProgramAction(R.string.download_by_link_menu_label) {
                it.copy(linkAutoDownloadEnabled = false)
            }
        MainMiniGameMenuManager.MENU_ITEM_GAME ->
            removeProgramAction(R.string.game_menu_label) { it.copy(embeddedGameEnabled = false) }
        MainScreenRecordingMenuManager.MENU_ITEM_SCREEN_RECORDING ->
            removeProgramAction(R.string.screen_recording_menu_label) { it.copy(screenRecordingEnabled = false) }
        else -> null
    }

    private fun removeProgramAction(titleRes: Int, apply: (AppSettings) -> AppSettings): () -> Unit = {
        hostActions.confirmRemoveProgram(titleRes, apply)
    }

    companion object {
        const val MENU_ITEM_CALCULATOR = 1
        const val MENU_ITEM_CAMERA_OCR = 9
        const val MENU_ITEM_APP_LAUNCH_PANEL = 15
        const val MENU_ITEM_VR_CINEMA = 17

        private const val MENU_ORDER_STREAMS = 1
        private const val MENU_ORDER_VR_CINEMA = 2
        private const val MENU_ORDER_APP_LAUNCH_PANEL = 3
        private const val MENU_ORDER_QUICK_CAPTURE = 4
        private const val MENU_ORDER_CALCULATOR = 5
        private const val MENU_ORDER_CAMERA_OCR = 6
        private const val MENU_ORDER_SCREEN_RECORDING = 7
        private const val MENU_ORDER_LINK_DOWNLOAD = 8
        private const val MENU_ORDER_MINI_GAME = 9
    }
}
