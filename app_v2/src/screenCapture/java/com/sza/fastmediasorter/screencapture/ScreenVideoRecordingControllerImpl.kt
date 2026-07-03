package com.sza.fastmediasorter.screencapture

import android.content.Context
import android.content.Intent
import androidx.fragment.app.FragmentActivity
import com.sza.fastmediasorter.core.screencapture.ScreenVideoRecordingController
import javax.inject.Inject

/**
 * S0774: screenCapture-flavor implementation. Launching starts the consent gate; stopping forwards an
 * ACTION_STOP command to the recording service (also reachable from its notification action).
 */
class ScreenVideoRecordingControllerImpl @Inject constructor() : ScreenVideoRecordingController {

    override fun launch(activity: FragmentActivity) {
        activity.startActivity(Intent(activity, ScreenVideoRecordingConsentActivity::class.java))
    }

    override fun requestStop(context: Context) {
        ScreenVideoRecordingService.stop(context)
    }
}
