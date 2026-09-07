package com.sza.fastmediasorter.ui.main.helpers

/**
 * S0542: dispatches the main-menu "Download by link" tap.
 *
 * S2673: the entry is drawn by MainProgramsMenuCoordinator from the sub-program registry. The tap
 * stays here because the download runs inside the main window rather than opening the route.
 */
class MainLinkDownloadMenuManager(
    private val onLinkDownload: () -> Unit,
) {

    fun handleMenuItem(itemId: Int): Boolean = when (itemId) {
        MENU_ITEM_LINK_DOWNLOAD -> {
            onLinkDownload()
            true
        }
        else -> false
    }

    companion object {
        // S0770: read by MainActivity to map this item to its "Remove" (disable) settings write.
        const val MENU_ITEM_LINK_DOWNLOAD = 13
    }
}
