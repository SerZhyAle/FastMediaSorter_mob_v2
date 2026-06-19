package com.sza.fastmediasorter.ui.main.helpers

import androidx.appcompat.widget.PopupMenu
import com.sza.fastmediasorter.R

/**
 * S0542: builds the main-menu "Download by link" entry and dispatches its tap. The entry is shown
 * only when the caller passes `enabled` - MainActivity resolves that as the existing link
 * auto-download setting, so no new toggle is introduced. The item id stays clear of the existing
 * menu ids (calculator 1, mini-game 2, camera-OCR 9, quick-capture 10/11/12).
 */
class MainLinkDownloadMenuManager(
    private val onLinkDownload: () -> Unit,
) {

    fun itemCount(enabled: Boolean): Int = if (enabled) 1 else 0

    fun populate(popup: PopupMenu, enabled: Boolean, startOrder: Int): Int {
        if (!enabled) return 0
        popup.menu.add(0, MENU_ITEM_LINK_DOWNLOAD, startOrder, R.string.download_by_link_menu_label)
            .setIcon(R.drawable.ic_cloud_download)
        return 1
    }

    fun handleMenuItem(itemId: Int): Boolean = when (itemId) {
        MENU_ITEM_LINK_DOWNLOAD -> { onLinkDownload(); true }
        else -> false
    }

    companion object {
        private const val MENU_ITEM_LINK_DOWNLOAD = 13
    }
}
