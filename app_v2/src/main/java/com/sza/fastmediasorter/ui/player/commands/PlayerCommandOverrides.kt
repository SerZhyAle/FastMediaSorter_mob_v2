package com.sza.fastmediasorter.ui.player.commands

import com.sza.fastmediasorter.ui.player.PlayerActivity
import com.sza.fastmediasorter.ui.player.PlayerViewModel

/**
 * Flavor-specific hook for commands whose default phone/tablet behavior is invalid in VR.
 *
 * Main flavors do not bind these overrides, so shared player callbacks keep their existing logic.
 */
interface FullscreenCommandOverride {
    fun execute(activity: PlayerActivity, viewModel: PlayerViewModel): Boolean
}

interface SaveFrameCommandOverride {
    fun execute(activity: PlayerActivity): Boolean
}

interface SystemUiCommandOverride {
    fun execute(activity: PlayerActivity): Boolean
}