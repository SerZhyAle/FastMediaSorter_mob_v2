package com.sza.fastmediasorter.vr.commands

import android.widget.Toast
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.player.PlayerActivity
import com.sza.fastmediasorter.ui.player.PlayerViewModel
import com.sza.fastmediasorter.ui.player.commands.FullscreenCommandOverride
import com.sza.fastmediasorter.vr.VrPlayerActivity
import javax.inject.Inject

/**
 * VR rendering is already immersive, so the shared fullscreen button should not try to toggle
 * Android system bars or hide the command panel as if the user were on a phone screen.
 */
class VrFullscreenCommandOverride @Inject constructor() : FullscreenCommandOverride {

    override fun execute(activity: PlayerActivity, viewModel: PlayerViewModel): Boolean {
        if (activity !is VrPlayerActivity) return false

        Toast.makeText(activity, R.string.vr_fullscreen_already_active, Toast.LENGTH_SHORT).show()
        return true
    }
}