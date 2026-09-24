package com.sza.fastmediasorter.broadcast

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BroadcastSourceControllerImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : BroadcastSourceController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override val isAvailable: Boolean = true

    /** A cross-service mode switch still waiting for the outgoing service to go off air. */
    private var switchJob: Job? = null

    override val cameraSurvivesBackground: Boolean by lazy {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            (
                VideoBroadcastService.declaredForegroundServiceTypes(context) and
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                ) != 0
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

    // S3349: only the audio path carries a guard - the video path hands its microphone to the encoder
    // library and is tracked by S3351.
    override val feedbackSuppressed: StateFlow<Boolean> = BroadcastCaptureService.feedbackSuppressed

    override fun start(mode: BroadcastMode, lensId: String?) {
        if (mode == BroadcastMode.AUDIO_ONLY) {
            BroadcastCaptureService.start(context, mode)
        } else {
            VideoBroadcastService.start(context, mode, lensId)
        }
    }

    override fun stop() {
        switchJob?.cancel()
        switchJob = null
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

    /**
     * Video to video stays inside one service: a stop followed by a start of the same service could
     * deliver the start between its stopBroadcast() and stopSelf(), and a service stopped under a
     * pending startForegroundService() costs the process. Audio and video are two services, so the
     * outgoing one goes off air - and releases the microphone - before the other starts.
     */
    override fun switchMode(mode: BroadcastMode, lensId: String?) {
        val liveMode = liveModeOrNull()
        if (liveMode == null || liveMode == mode) return
        Timber.d("S3518: controller switchMode live mode change")
        switchJob?.cancel()
        if (liveMode != BroadcastMode.AUDIO_ONLY && mode != BroadcastMode.AUDIO_ONLY) {
            VideoBroadcastService.switchMode(context, mode, lensId)
        } else {
            switchJob = scope.launch {
                if (liveMode == BroadcastMode.AUDIO_ONLY) {
                    BroadcastCaptureService.stop(context)
                    BroadcastCaptureService.state.first { it !is BroadcastState.Live }
                } else {
                    VideoBroadcastService.stop(context)
                    VideoBroadcastService.state.first { it !is BroadcastState.Live }
                }
                start(mode, lensId)
            }
        }
    }

    private fun liveModeOrNull(): BroadcastMode? {
        val live = state.value as? BroadcastState.Live ?: return null
        return BroadcastMode.entries.firstOrNull { it.name == live.descriptor.mode }
    }

    // Commands reach the service as startService intents, and an intent sent while no video session runs
    // would create an idle sticky service holding nothing.
    private fun isVideoLive(): Boolean = VideoBroadcastService.state.value is BroadcastState.Live
}
