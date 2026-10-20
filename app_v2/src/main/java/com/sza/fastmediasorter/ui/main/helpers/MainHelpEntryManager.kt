package com.sza.fastmediasorter.ui.main.helpers

import android.view.View
import androidx.fragment.app.FragmentActivity
import com.sza.fastmediasorter.ui.common.help.HelpOpenManager
import com.sza.fastmediasorter.ui.common.help.HelpTooltipManager
import com.sza.fastmediasorter.ui.common.input.UiSurface
import com.sza.fastmediasorter.utils.setOnClickListenerDebounced

/**
 * Wires the main screen's header "?" button. It opens the same surface id as the F1 key and the
 * overflow "Help" item in MainActivity, so all three give one answer (contract `CONTEXT-HELP` rule 3).
 */
object MainHelpEntryManager {

    const val SURFACE_ID = "MainActivity"

    fun bind(activity: FragmentActivity, helpButton: View) {
        HelpTooltipManager.apply(helpButton)
        helpButton.setOnClickListenerDebounced {
            HelpOpenManager.open(activity, SURFACE_ID, UiSurface.MAIN)
        }
    }
}
