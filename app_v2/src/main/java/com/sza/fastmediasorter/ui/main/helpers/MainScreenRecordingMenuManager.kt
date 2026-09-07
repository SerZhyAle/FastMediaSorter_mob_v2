package com.sza.fastmediasorter.ui.main.helpers

/**
 * S0774: dispatches the programs-block "Screen video recording" tap.
 *
 * S2673: the entry is drawn by MainProgramsMenuCoordinator from the sub-program registry, which is
 * also where S0913's ic_display now lives - the same drawable the settings toggle row uses, so the
 * menu entry reads as the same feature as its setting. The tap stays here: recording starts in the
 * current window and asks for the capture consent, it does not open an activity.
 */
class MainScreenRecordingMenuManager(
    private val onScreenRecording: () -> Unit,
) {

    fun handleMenuItem(itemId: Int): Boolean = when (itemId) {
        MENU_ITEM_SCREEN_RECORDING -> {
            onScreenRecording()
            true
        }
        else -> false
    }

    companion object {
        const val MENU_ITEM_SCREEN_RECORDING = 16
    }
}
