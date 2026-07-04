package com.sza.fastmediasorter.core.screencapture

import android.content.Context
import androidx.fragment.app.FragmentActivity

/**
 * S0774: store-safe, user-initiated screen video recording control. Implementations start the
 * MediaProjection consent flow plus the recording foreground service, and stop an active session.
 * Bound as an empty multibinding by default, so flavors without the capture engine resolve an empty
 * set and the programs-block scenario (and its settings rows) stay hidden.
 */
interface ScreenVideoRecordingController {
    fun launch(activity: FragmentActivity)
    fun requestStop(context: Context)

    // Default no-op bodies: keeps ScreenVideoRecordingControllerImpl (Phase 10) compiling between
    // this phase and its real pause/resume implementation - an abstract-only addition would break
    // the screenCapture source set's build the moment this interface change lands.
    fun requestPause(context: Context) {}
    fun requestResume(context: Context) {}
}
