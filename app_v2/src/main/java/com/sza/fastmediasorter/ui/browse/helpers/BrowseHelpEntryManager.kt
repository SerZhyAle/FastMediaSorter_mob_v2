package com.sza.fastmediasorter.ui.browse.helpers

import android.view.View
import androidx.fragment.app.FragmentActivity
import com.sza.fastmediasorter.ui.common.help.HelpOpenManager
import com.sza.fastmediasorter.ui.common.input.UiSurface
import com.sza.fastmediasorter.utils.setOnClickListenerDebounced

/**
 * The file browser's one way to open its help. F1 and the overflow "Help" item both call it, so they
 * give one answer (contract `CONTEXT-HELP` rule 3).
 */
object BrowseHelpEntryManager {

    const val SURFACE_ID = "BrowseActivity"

    fun open(activity: FragmentActivity) {
        HelpOpenManager.open(activity, SURFACE_ID, UiSurface.BROWSE)
    }

    /** S4328: the "?" fixed at the command bar's end; the overflow partition never moves it. */
    fun bindHeaderButton(activity: FragmentActivity, button: View) {
        button.setOnClickListenerDebounced { open(activity) }
    }
}
