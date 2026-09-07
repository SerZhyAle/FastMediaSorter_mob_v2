package com.sza.fastmediasorter.ui.main.helpers

/**
 * S0523 / S0563: dispatches the main-menu quick-capture taps (voice / camera) to the supplied
 * actions. The former separate photo and video items are merged into one "Camera" entry (S0563) that
 * lets the user switch mode in-screen; voice stays separate.
 *
 * S2673: the two entries are drawn by MainProgramsMenuCoordinator from the sub-program registry. Both
 * act inside the main window, which is why the tap stays here rather than following the route's
 * intent: the route opens a trampoline activity, and that is not what either item has ever done.
 */
class MainQuickCaptureMenuManager(
    private val onVoice: () -> Unit,
    private val onCamera: () -> Unit,
) {

    fun handleMenuItem(itemId: Int): Boolean = when (itemId) {
        MENU_ITEM_QUICK_VOICE -> {
            onVoice()
            true
        }
        MENU_ITEM_QUICK_CAMERA -> {
            onCamera()
            true
        }
        else -> false
    }

    companion object {
        // S0770: read by MainActivity to map these items to their "Remove" (disable) settings writes.
        const val MENU_ITEM_QUICK_VOICE = 10
        const val MENU_ITEM_QUICK_CAMERA = 12
    }
}
