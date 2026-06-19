package com.sza.fastmediasorter.ui.main.helpers

import androidx.appcompat.widget.PopupMenu
import com.sza.fastmediasorter.R

/**
 * S0523: builds the three main-menu quick-capture entries (voice / video / photo) and dispatches
 * taps to the supplied capture actions. Each entry is shown only when the caller passes its enabled
 * flag - MainActivity resolves that as (settings toggle AND the matching media capability), so an
 * unsupported flavor never surfaces the entry. Item ids stay clear of the existing menu ids
 * (calculator 1, mini-game 2, camera-OCR 9).
 */
class MainQuickCaptureMenuManager(
    private val onVoice: () -> Unit,
    private val onVideo: () -> Unit,
    private val onPhoto: () -> Unit,
) {

    fun itemCount(voice: Boolean, video: Boolean, photo: Boolean): Int =
        (if (voice) 1 else 0) + (if (video) 1 else 0) + (if (photo) 1 else 0)

    fun populate(popup: PopupMenu, voice: Boolean, video: Boolean, photo: Boolean, startOrder: Int): Int {
        var added = 0
        if (voice) {
            popup.menu.add(0, MENU_ITEM_QUICK_VOICE, startOrder + added, R.string.quick_voice_menu_label)
                .setIcon(R.drawable.ic_microphone)
            added++
        }
        if (video) {
            popup.menu.add(0, MENU_ITEM_QUICK_VIDEO, startOrder + added, R.string.quick_video_menu_label)
                .setIcon(R.drawable.ic_video)
            added++
        }
        if (photo) {
            popup.menu.add(0, MENU_ITEM_QUICK_PHOTO, startOrder + added, R.string.quick_photo_menu_label)
                .setIcon(R.drawable.ic_camera_capture)
            added++
        }
        return added
    }

    fun handleMenuItem(itemId: Int): Boolean = when (itemId) {
        MENU_ITEM_QUICK_VOICE -> { onVoice(); true }
        MENU_ITEM_QUICK_VIDEO -> { onVideo(); true }
        MENU_ITEM_QUICK_PHOTO -> { onPhoto(); true }
        else -> false
    }

    companion object {
        private const val MENU_ITEM_QUICK_VOICE = 10
        private const val MENU_ITEM_QUICK_VIDEO = 11
        private const val MENU_ITEM_QUICK_PHOTO = 12
    }
}
