package com.sza.fastmediasorter.broadcast

import android.content.Context
import android.content.pm.ServiceInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import com.sza.fastmediasorter.data.broadcast.exchange.ExchangeBroadcastAnnouncer
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
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BroadcastSourceControllerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exchangeAnnouncer: ExchangeBroadcastAnnouncer,
) : BroadcastSourceController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override val isAvailable: Boolean = true

    /** A cross-service mode switch still waiting for the outgoing service to go off air. */
    private var switchJob: Job? = null

    override val cameraSurvivesBackground: Boolean by lazy {
        val survives = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            (
                VideoBroadcastService.declaredForegroundServiceTypes(context) and
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                ) != 0
        Timber.d("S4235: cameraSurvivesBackground=%b", survives)
        survives
    }

    override val state: StateFlow<BroadcastState> = combine(
        BroadcastCaptureService.state,
        VideoBroadcastService.state,
        BroadcastFileAudioService.state,
        BroadcastFileVideoService.state,
    ) { audioState, videoState, fileAudioState, fileVideoState ->
        when {
            videoState !is BroadcastState.Idle -> videoState
            fileVideoState !is BroadcastState.Idle -> fileVideoState
            fileAudioState !is BroadcastState.Idle -> fileAudioState
            else -> audioState
        }
    }.stateIn(scope, SharingStarted.Eagerly, BroadcastState.Idle)

    init {
        // S4116: a live broadcast is announced through the outgoing exchange entry, when the user has one.
        exchangeAnnouncer.follow(state)
    }

    override val listenerCount: StateFlow<Int> = combine(
        BroadcastCaptureService.listenerCount,
        VideoBroadcastService.listenerCount,
        BroadcastFileAudioService.listenerCount,
        BroadcastFileVideoService.listenerCount,
    ) { audioCount, videoCount, fileAudioCount, fileVideoCount ->
        audioCount + videoCount + fileAudioCount + fileVideoCount
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

    // The services are exclusive, and the shared ports make that more than a rule: a session started
    // while the outgoing one still holds its port would fail to bind it, so every crossing waits for
    // the outgoing service to leave the air before the next one starts.
    override fun startFile(uri: Uri) {
        switchJob?.cancel()
        switchJob = scope.launch {
            stopCapturedSessionsAndWait()
            stopFileSessionsAndWait()
            // The track kinds decide the shape: a film takes the RTSP service, a music file the
            // HTTP AAC one, and a file with neither lands on the audio service, whose open fails
            // with the one typed refusal both shapes share.
            val tracks = withContext(Dispatchers.IO) { probeFileTracks(context, uri) }
            if (tracks.hasVideo) {
                BroadcastFileVideoService.start(context, uri)
            } else {
                BroadcastFileAudioService.start(context, uri)
            }
        }
    }

    override fun stop() {
        switchJob?.cancel()
        switchJob = null
        BroadcastCaptureService.stop(context)
        VideoBroadcastService.stop(context)
        BroadcastFileAudioService.stop(context)
        BroadcastFileVideoService.stop(context)
    }

    override fun acknowledgeFailure() {
        BroadcastCaptureService.clearFailure()
        VideoBroadcastService.clearFailure()
        BroadcastFileAudioService.clearFailure()
        BroadcastFileVideoService.clearFailure()
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
        switchJob?.cancel()
        if (isFileLive()) {
            // A file session re-opening as a captured one crosses services like audio-video does:
            // the file service goes off air before the capture service starts.
            switchJob = scope.launch {
                stopFileSessionsAndWait()
                start(mode, lensId)
            }
        } else if (liveMode != BroadcastMode.AUDIO_ONLY && mode != BroadcastMode.AUDIO_ONLY) {
            VideoBroadcastService.switchMode(context, mode, lensId)
        } else {
            switchJob = scope.launch {
                stopCapturedSessionsAndWait()
                start(mode, lensId)
            }
        }
    }

    /** Stops both capture services and returns once neither is on the air any more. */
    private suspend fun stopCapturedSessionsAndWait() {
        BroadcastCaptureService.stop(context)
        VideoBroadcastService.stop(context)
        BroadcastCaptureService.state.first { it !is BroadcastState.Live }
        VideoBroadcastService.state.first { it !is BroadcastState.Live }
    }

    /** Stops both file services and returns once neither is on the air any more. */
    private suspend fun stopFileSessionsAndWait() {
        BroadcastFileAudioService.stop(context)
        BroadcastFileVideoService.stop(context)
        BroadcastFileAudioService.state.first { it !is BroadcastState.Live }
        BroadcastFileVideoService.state.first { it !is BroadcastState.Live }
    }

    private fun liveModeOrNull(): BroadcastMode? {
        val live = state.value as? BroadcastState.Live ?: return null
        return BroadcastMode.entries.firstOrNull { it.name == live.descriptor.mode }
    }

    // Commands reach the service as startService intents, and an intent sent while no video session runs
    // would create an idle sticky service holding nothing.
    private fun isVideoLive(): Boolean = VideoBroadcastService.state.value is BroadcastState.Live

    private fun isFileLive(): Boolean =
        BroadcastFileAudioService.state.value is BroadcastState.Live ||
            BroadcastFileVideoService.state.value is BroadcastState.Live

    /** The kinds of track a picked file carries, read off the media I/O thread before a service is chosen. */
    private data class FileTracks(val hasVideo: Boolean, val hasAudio: Boolean)

    @Suppress("TooGenericExceptionCaught")
    private fun probeFileTracks(context: Context, uri: Uri): FileTracks {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, uri, null)
            var hasVideo = false
            var hasAudio = false
            for (index in 0 until extractor.trackCount) {
                when (extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.substringBefore('/')) {
                    "video" -> hasVideo = true
                    "audio" -> hasAudio = true
                }
            }
            FileTracks(hasVideo, hasAudio)
        } catch (e: Exception) {
            Timber.w(e, "BroadcastSourceController: tracks of %s unreadable", uri)
            FileTracks(hasVideo = false, hasAudio = false)
        } finally {
            extractor.release()
        }
    }
}
