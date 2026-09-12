package com.sza.fastmediasorter.ui.main.helpers

import android.content.Context
import com.sza.fastmediasorter.ui.wear.WearCompanionActivity

/**
 * S1735: the Wear companion's own entry in the programs menu.
 *
 * A per-program helper rather than another branch inside the coordinator - strategic ADR-2.
 *
 * S2673: the entry is drawn by MainProgramsMenuCoordinator from the sub-program registry, and its
 * visibility is answered by the one route-availability chain, so this class still learns neither.
 */
class MainWearCompanionMenuManager(
    private val context: Context
) {

    fun handleMenuItem(itemId: Int): Boolean {
        if (itemId != MENU_ITEM_WEAR_COMPANION) return false
        context.startActivity(WearCompanionActivity.createIntent(context))
        return true
    }

    companion object {
        // Read by the coordinator to map this item to its new-window launch and its "Remove" write.
        const val MENU_ITEM_WEAR_COMPANION = 20
    }
}
