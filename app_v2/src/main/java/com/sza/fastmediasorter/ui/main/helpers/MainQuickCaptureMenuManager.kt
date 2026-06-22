package com.sza.fastmediasorter.ui.main.helpers

import androidx.appcompat.widget.PopupMenu
import com.sza.fastmediasorter.R

/**
 * S0523 / S0563: builds the main-menu quick-capture entries (voice / camera) and dispatches taps to
 * the supplied actions. The former separate photo and video items are merged into one "Camera" entry
 * (S0563) that lets the user switch mode in-screen; voice stays separate. Each entry is shown only
 * when the caller passes its enabled flag - MainActivity resolves that as (settings toggle AND the
 * matching media capability), so an unsupported flavor never surfaces the entry. Item ids stay clear
 * of the existing menu ids (calculator 1, mini-game 2, camera-OCR 9).
 */
class MainQuickCaptureMenuManager(
    private val onVoice: () -> Unit,
    private val onCamera: () -> Unit,
) {

    fun itemCount(voice: Boolean, camera: Boolean): Int =
        (if (voice) 1 else 0) + (if (camera) 1 else 0)

    fun populate(popup: PopupMenu, voice: Boolean, camera: Boolean, startOrder: Int): Int {
        var added = 0
        if (voice) {
            popup.menu.add(0, MENU_ITEM_QUICK_VOICE, startOrder + added, R.string.quick_voice_menu_label)
                .setIcon(R.drawable.ic_microphone)
            added++
        }
        if (camera) {
            popup.menu.add(0, MENU_ITEM_QUICK_CAMERA, startOrder + added, R.string.quick_camera_menu_label)
                .setIcon(R.drawable.ic_camera_capture)
            added++
        }
        return added
    }

    fun handleMenuItem(itemId: Int): Boolean = when (itemId) {
        MENU_ITEM_QUICK_VOICE -> { onVoice(); true }
        MENU_ITEM_QUICK_CAMERA -> { onCamera(); true }
        else -> false
    }

    companion object {
        private const val MENU_ITEM_QUICK_VOICE = 10
        private const val MENU_ITEM_QUICK_CAMERA = 12
    }
}
