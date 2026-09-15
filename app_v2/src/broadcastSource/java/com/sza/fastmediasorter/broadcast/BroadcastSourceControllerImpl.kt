package com.sza.fastmediasorter.broadcast

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BroadcastSourceControllerImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : BroadcastSourceController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override val isAvailable: Boolean = true

    override val cameraSurvivesBackground: Boolean by lazy {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            (VideoBroadcastService.declaredForegroundServiceTypes(context) and ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA) != 0
    }

    override val state: StateFlow<BroadcastState> = combine(
        BroadcastCaptureService.state,
        VideoBroadcastService.state
    ) { audioState, videoState ->
        if (videoState !is BroadcastState.Idle) videoState else audioState
    }.stateIn(scope, SharingStarted.Eagerly, BroadcastState.Idle)

    override val listenerCount: StateFlow<Int> = combine(
        BroadcastCaptureService.listenerCount,
        VideoBroadcastService.listenerCount
    ) { audioCount, videoCount ->
        audioCount + videoCount
    }.stateIn(scope, SharingStarted.Eagerly, 0)

    override fun start(mode: BroadcastMode, lensId: String?) {
        if (mode == BroadcastMode.AUDIO_ONLY) {
            BroadcastCaptureService.start(context, mode)
        } else {
            VideoBroadcastService.start(context, mode, lensId)
        }
    }

    override fun stop() {
        BroadcastCaptureService.stop(context)
        VideoBroadcastService.stop(context)
    }

    override fun acknowledgeFailure() {
        BroadcastCaptureService.clearFailure()
        VideoBroadcastService.clearFailure()
    }

    override fun toggleCamera() {
        if (isVideoLive()) VideoBroadcastService.toggleCamera(context)
    }

    override fun toggleMicrophone() {
        if (isVideoLive()) VideoBroadcastService.toggleMicrophone(context)
    }

    override fun selectLens(lensId: String) {
        if (isVideoLive()) VideoBroadcastService.selectLens(context, lensId)
    }

    // Commands reach the service as startService intents, and an intent sent while no video session runs
    // would create an idle sticky service holding nothing.
    private fun isVideoLive(): Boolean = VideoBroadcastService.state.value is BroadcastState.Live
}
