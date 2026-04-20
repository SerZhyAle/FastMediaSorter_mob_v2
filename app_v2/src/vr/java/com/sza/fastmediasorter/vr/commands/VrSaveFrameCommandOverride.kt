package com.sza.fastmediasorter.vr.commands

import com.sza.fastmediasorter.ui.player.PlayerActivity
import com.sza.fastmediasorter.ui.player.commands.SaveFrameCommandOverride
import com.sza.fastmediasorter.vr.VrPlayerActivity
import javax.inject.Inject

/**
 * Save Frame in VR must go through the OpenXR eye swapchains rather than the hidden PlayerView.
 */
class VrSaveFrameCommandOverride @Inject constructor() : SaveFrameCommandOverride {

    override fun execute(activity: PlayerActivity): Boolean {
        val vrActivity = activity as? VrPlayerActivity ?: return false
        return vrActivity.captureStereoSnapshotFromCommand()
    }
}