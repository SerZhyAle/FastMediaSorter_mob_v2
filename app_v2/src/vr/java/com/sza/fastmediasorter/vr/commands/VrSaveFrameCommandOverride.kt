package com.sza.fastmediasorter.vr.commands

import com.sza.fastmediasorter.ui.player.PlayerActivity
import com.sza.fastmediasorter.ui.player.commands.SaveFrameCommandOverride
import com.sza.fastmediasorter.vr.VrPlayerActivity
import timber.log.Timber
import javax.inject.Inject

/**
 * Save Frame in VR must go through the OpenXR eye swapchains rather than the hidden PlayerView.
 */
class VrSaveFrameCommandOverride @Inject constructor() : SaveFrameCommandOverride {

    override fun execute(activity: PlayerActivity): Boolean {
        Timber.i(
            "VrPhotoCapture: stage=command source=command-override activity=%s",
            activity::class.simpleName,
        )
        val vrActivity = activity as? VrPlayerActivity
        if (vrActivity == null) {
            Timber.w("VrPhotoCapture: stage=command result=skipped reason=non-vr-activity")
            return false
        }
        val result = vrActivity.captureStereoSnapshotFromCommand()
        Timber.i("VrPhotoCapture: stage=command result=%s", result)
        return result
    }
}