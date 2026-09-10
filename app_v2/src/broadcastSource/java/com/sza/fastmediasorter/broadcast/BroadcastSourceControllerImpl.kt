package com.sza.fastmediasorter.broadcast

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BroadcastSourceControllerImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : BroadcastSourceController {

    override val isAvailable: Boolean = true

    override val state: StateFlow<BroadcastState>
        get() = BroadcastCaptureService.state

    override fun start(mode: BroadcastMode) {
        if (mode != BroadcastMode.AUDIO_ONLY) return
        BroadcastCaptureService.start(context, mode)
    }

    override fun stop() {
        BroadcastCaptureService.stop(context)
    }

    override fun acknowledgeFailure() {
        BroadcastCaptureService.clearFailure()
    }
}
