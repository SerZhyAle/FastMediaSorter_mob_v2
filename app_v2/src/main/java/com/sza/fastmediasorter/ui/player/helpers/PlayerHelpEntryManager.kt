package com.sza.fastmediasorter.ui.player.helpers

import androidx.fragment.app.FragmentActivity
import com.sza.fastmediasorter.ui.common.help.HelpOpenManager
import com.sza.fastmediasorter.ui.common.input.UiSurface

/**
 * The player family's one way to open its help. F1 and the overflow "Help" item of the in-app
 * player and of every standalone player call it, so they give one answer (contract `CONTEXT-HELP`
 * rule 3).
 *
 * The surface id is the host activity's class name: the in-app player owns the `player` page and each
 * standalone player is an `inherit` row of the help map that resolves to it. Manifest activities keep
 * their class names under R8, which is what makes the simple name a stable map key.
 */
object PlayerHelpEntryManager {

    fun open(activity: FragmentActivity) {
        HelpOpenManager.open(activity, activity.javaClass.simpleName, UiSurface.PLAYER)
    }
}
