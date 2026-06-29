package com.sza.fastmediasorter.ui.main.helpers

import androidx.appcompat.widget.PopupMenu
import com.sza.fastmediasorter.R

/**
 * S0774: builds the programs-block "Screen video recording" entry and dispatches its tap. The caller
 * passes enabled = (settings toggle AND a bound ScreenVideoRecordingController), so flavors without the
 * capture engine never surface it. Id 16 stays clear of the existing menu ids
 * (1 calculator, 2 game, 9 camera-OCR, 10 quick-voice, 12 quick-camera, 13 link, 14 streams, 15 panel).
 */
class MainScreenRecordingMenuManager(
    private val onScreenRecording: () -> Unit,
) {

    fun itemCount(enabled: Boolean): Int = if (enabled) 1 else 0

    fun populate(popup: PopupMenu, enabled: Boolean, order: Int): Int {
        if (!enabled) return 0
        popup.menu.add(0, MENU_ITEM_SCREEN_RECORDING, order, R.string.screen_recording_menu_label)
            .setIcon(R.drawable.ic_video)
        return 1
    }

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
