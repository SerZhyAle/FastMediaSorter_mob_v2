package com.sza.fastmediasorter.vr.commands

import com.sza.fastmediasorter.ui.player.PlayerActivity
import com.sza.fastmediasorter.ui.player.commands.SystemUiCommandOverride
import com.sza.fastmediasorter.vr.VrPlayerActivity
import javax.inject.Inject

/**
 * Inside VR there are no Android system bars to hide, so controller/menu input toggles the
 * headset control overlay instead.
 */
class VrSystemUiCommandOverride @Inject constructor() : SystemUiCommandOverride {

    override fun execute(activity: PlayerActivity): Boolean {
        val vrActivity = activity as? VrPlayerActivity ?: return false
        return vrActivity.toggleVrControlOverlayFromCommand()
    }
}