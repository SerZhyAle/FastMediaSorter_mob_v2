package com.sza.fastmediasorter.broadcast

import android.Manifest
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.annotation.RequiresApi
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.audio.CustomAudioEffect
import com.pedro.encoder.input.video.CameraOpenException
import com.pedro.rtspserver.RtspServerCamera2
import com.pedro.rtspserver.server.ClientListener
import com.pedro.rtspserver.server.IpType
import com.pedro.rtspserver.server.ServerClient
import com.pedro.rtspserver.util.RtspServerStreamClient
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.network.LanAddressResolver
import com.sza.fastmediasorter.core.notification.NotificationIds
import com.sza.fastmediasorter.data.broadcast.BroadcastDescriptorDto
import com.sza.fastmediasorter.data.broadcast.BroadcastEndpointDto
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

@Suppress("MagicNumber", "TooManyFunctions")
@AndroidEntryPoint
class VideoBroadcastService : Service(), ConnectChecker, ClientListener {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var previewProvider: BroadcastPreviewProvider

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var cameraServer: RtspServerCamera2? = null

    private val isStreaming = AtomicBoolean(false)
    private var currentMode: BroadcastMode = BroadcastMode.VIDEO_AUDIO
    private var currentMicEnabled = true
    private var pendingLensId: String? = null
    private var activePhysicalId: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    @Suppress("ReturnCount")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopBroadcast()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE_MIC -> {
                toggleMicInternal()
                return START_STICKY
            }
            ACTION_SELECT_LENS -> {
                intent.getStringExtra(EXTRA_LENS_ID)?.let(::selectLensInternal)
                return START_STICKY
            }
            ACTION_TOGGLE_CAMERA -> {
                toggleCameraInternal()
                return START_STICKY
            }
        }

        val modeName = intent?.getStringExtra(EXTRA_MODE)
        currentMode = modeName?.let { runCatching { BroadcastMode.valueOf(it) }.getOrNull() }
            ?: BroadcastMode.VIDEO_AUDIO
        pendingLensId = intent?.getStringExtra(EXTRA_LENS_ID)

        val missingCamera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED
        // VIDEO_ONLY never opens the microphone, and the entry screen does not ask for it in that mode.
        val missingAudio = currentMode != BroadcastMode.VIDEO_ONLY &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED

        // Reachable only if a grant was revoked between the companion's check and this callback: a typed
        // foreground start without its permission is refused by Android 14, so this branch cannot keep
        // the startForegroundService() promise and the promise is refused before it is made (S3267).
        if (missingCamera || missingAudio) {
            Timber.w(
                "VideoBroadcastService: permissions missing (camera: %b, audio: %b)",
                missingCamera,
                missingAudio,
            )
            _state.value = BroadcastState.Failed(
                BroadcastFailure.MICROPHONE_PERMISSION,
                "Camera or Microphone permission missing"
            )
            stopSelf()
            return START_NOT_STICKY
        }

        // Before the reentrancy guard and before the asynchronous session work (S3267).
        if (!enterForeground()) {
            _state.value = BroadcastState.Failed(
                BroadcastFailure.MICROPHONE_PERMISSION,
                "Microphone permission missing for a video-only session"
            )
            stopSelf()
            return START_NOT_STICKY
        }

        startBroadcast()
        return START_STICKY
    }

    /**
     * False only when the platform offers this session no legal foreground type at all - the caller
     * then has nothing to keep and stops, which is the one shape that can still cost the process.
     * Step 01.2's guard keeps that case out of reach by refusing the start before it is made.
     */
    private fun enterForeground(): Boolean {
        // The stop action must reach this service: the shared factory defaults to the audio service,
        // which left a video session with no way to stop outside the control screen (S3058 run 1).
        val notification = BroadcastNotificationFactory.createNotification(
            context = this,
            stopIntent = stopIntent(this),
            titleRes = R.string.broadcast_notification_video_title,
            textRes = R.string.broadcast_notification_video_text,
        )
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            startForeground(NotificationIds.PHONE_BROADCAST, notification)
            return true
        }
        val declared = declaredForegroundServiceTypes(this)
        val type = resolveForegroundType(declared)
            ?: (declared and ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
        val entered = type != 0
        if (entered) {
            startForeground(NotificationIds.PHONE_BROADCAST, notification, type)
        } else {
            Timber.w("VideoBroadcastService: no foreground type available for this session")
        }
        return entered
    }

    private fun leaveForegroundAndStop() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startBroadcast() {
        if (!isStreaming.compareAndSet(false, true)) return

        serviceScope.launch { openSession() }
    }

    @Suppress("ReturnCount", "TooGenericExceptionCaught")
    private suspend fun openSession() {
        val config = readSessionConfig()
        val port = config.rtspPort
        val lanHost = resolveLanHostOrFail() ?: return

        try {
            // Always headless: the control screen attaches its preview later through BroadcastPreviewProvider,
            // once its surface exists.
            val camera = RtspServerCamera2(this, this, port)

            val defaultRotation = 0
            val videoPrepared = camera.prepareVideo(
                config.videoWidth,
                config.videoHeight,
                config.videoFps,
                config.videoBitrateBps,
                defaultRotation
            )
            val audioPrepared: Boolean = if (currentMode != BroadcastMode.VIDEO_ONLY) {
                camera.prepareAudio(config.bitRateBps, config.sampleRateHz, config.channelCount >= 2)
            } else {
                true
            }

            attachAudioEffect(camera, config)

            if (!videoPrepared || !audioPrepared) {
                Timber.w(
                    "VideoBroadcastService: RtspServerCamera2 prepare failed (video: %b, audio: %b)",
                    videoPrepared,
                    audioPrepared
                )
                _state.value = BroadcastState.Failed(
                    BroadcastFailure.ENCODER_UNAVAILABLE,
                    "RtspServerCamera2 prepare failed"
                )
                isStreaming.set(false)
                leaveForegroundAndStop()
                return
            }

            val streamClient = configureStreamClient(camera)
            val lensId = startStreamOnLens(camera)

            val endpoint = publishableEndpoint(streamClient.getEndPointConnection(), lanHost)
            val endpointDto = BroadcastEndpointDto(
                url = endpoint,
                transport = "RTSP",
                mode = currentMode.name,
                videoCodec = "h264",
                audioCodec = if (currentMode != BroadcastMode.VIDEO_ONLY) "aac" else null,
                sampleRate = if (currentMode != BroadcastMode.VIDEO_ONLY) config.sampleRateHz else null,
                bitrate = config.videoBitrateBps,
                isLive = true,
                targetLatencyMs = 200L
            )
            val dto = BroadcastDescriptorDto(
                schemaVersion = 1,
                url = endpoint,
                title = config.streamTitle,
                mode = currentMode.name,
                sourceId = config.sourceDeviceId,
                endpoints = listOf(endpointDto),
                isLive = true,
                targetLatencyMs = 200L
            )

            currentMicEnabled = currentMode != BroadcastMode.VIDEO_ONLY
            _state.value = BroadcastState.Live(
                descriptor = dto,
                startedAtElapsedRealtimeMs = SystemClock.elapsedRealtime(),
                cameraEnabled = true,
                microphoneEnabled = currentMicEnabled,
                activeLensId = lensId ?: camera.currentCameraId,
            )
            Timber.d("VideoBroadcastService: RTSP streaming started at %s", endpoint)
        } catch (e: Exception) {
            Timber.e(e, "VideoBroadcastService: failed to start RTSP server")
            _state.value = BroadcastState.Failed(
                BroadcastFailure.NETWORK_UNAVAILABLE,
                e.message ?: "Failed to start RTSP server"
            )
            isStreaming.set(false)
            leaveForegroundAndStop()
        }
    }

    /**
     * LIVE-BROADCAST producer rule 1: with no LAN address the session never starts, so neither the camera
     * nor the microphone opens for a descriptor nobody could reach.
     */
    private fun resolveLanHostOrFail(): String? {
        val lanHost = LanAddressResolver(this).resolve()
        if (lanHost == null) {
            Timber.w("VideoBroadcastService: no LAN address, session not started")
            _state.value = BroadcastState.Failed(BroadcastFailure.NETWORK_UNAVAILABLE, "No LAN address")
            isStreaming.set(false)
            leaveForegroundAndStop()
        }
        return lanHost
    }

    private fun publishableEndpoint(libraryEndpoint: String, lanHost: String): String =
        checkNotNull(RtspEndpointAddress.onLanHost(libraryEndpoint, lanHost)) {
            "RTSP endpoint $libraryEndpoint cannot be published on $lanHost"
        }

    private fun configureStreamClient(camera: RtspServerCamera2): RtspServerStreamClient {
        val streamClient = camera.streamClient
        // The server announces an audio track in the SDP unless told otherwise, and Camera only prepares none.
        streamClient.setOnlyVideo(currentMode == BroadcastMode.VIDEO_ONLY)
        // Left on All, the library publishes the first address it meets - an IPv6 one on the test device -
        // while the audio link of the same phone is IPv4, so one phone handed out two address families.
        streamClient.forceIpType(IpType.IPv4)
        // ConnectChecker's success and disconnect callbacks do not fire per RTSP client; the counter stayed at
        // zero with a listener receiving bytes (device run 2026-09-13).
        streamClient.setClientListener(this)
        return streamClient
    }

    /** Starts the stream on the lens the user picked and returns that lens id, or null for the default camera. */
    private fun startStreamOnLens(camera: RtspServerCamera2): String? {
        val lensId = pendingLensId
        if (lensId != null) {
            // Headless and not yet streaming, startPreview only records which camera startStream opens.
            camera.startPreview(BroadcastLensOption.logicalIdOf(lensId))
        }
        camera.startStream()
        cameraServer = camera
        previewProvider.attachCamera(camera)
        lensId?.let { BroadcastLensOption.physicalIdOf(it) }?.let { openPhysicalLens(camera, it) }
        return lensId
    }

    private suspend fun readSessionConfig(): BroadcastSessionConfig {
        val settings = settingsRepository.getSettings().first()
        val broadcast = settings.broadcast
        val sourceDeviceId = broadcast.sourceDeviceId ?: run {
            val id = UUID.randomUUID().toString()
            settingsRepository.updateSettings(
                settings.copy(broadcast = broadcast.copy(sourceDeviceId = id))
            )
            id
        }
        return BroadcastSessionConfig(
            streamTitle = broadcast.streamTitle.ifBlank { BroadcastSessionConfig.DEFAULT.streamTitle },
            bitRateBps = broadcast.bitRateBps,
            port = broadcast.port,
            sampleRateHz = broadcast.sampleRateHz,
            channelCount = broadcast.channelCount,
            sourceDeviceId = sourceDeviceId,
            micGainPercent = broadcast.micGainPercent,
            videoWidth = broadcast.videoWidth,
            videoHeight = broadcast.videoHeight,
            videoFps = broadcast.videoFps,
            videoBitrateBps = broadcast.videoBitrateBps,
        )
    }

    private fun toggleMicInternal() {
        val camera = cameraServer ?: return
        if (camera.isAudioMuted) {
            camera.enableAudio()
            currentMicEnabled = true
        } else {
            camera.disableAudio()
            currentMicEnabled = false
        }
        val currentLive = _state.value as? BroadcastState.Live
        if (currentLive != null) {
            _state.value = currentLive.copy(microphoneEnabled = currentMicEnabled)
        }
    }

    /**
     * Camera2 opens a logical camera and narrows it to a physical sub-lens afterwards, and the library
     * keeps the physical id across re-opens, so a sub-lens is cleared before another logical camera opens -
     * the old physical id is not a lens of the new camera.
     */
    private fun selectLensInternal(lensId: String) {
        val camera = cameraServer ?: return
        val liveState = _state.value as? BroadcastState.Live ?: return
        val logicalId = BroadcastLensOption.logicalIdOf(lensId)
        try {
            if (activePhysicalId != null) {
                camera.openPhysicalCamera(null)
                activePhysicalId = null
            }
            if (logicalId != camera.currentCameraId) {
                camera.switchCamera(logicalId)
            }
            BroadcastLensOption.physicalIdOf(lensId)?.let { openPhysicalLens(camera, it) }
            _state.value = liveState.copy(activeLensId = lensId)
        } catch (e: CameraOpenException) {
            Timber.w(
                e,
                "VideoBroadcastService: lens %s did not open, the previous lens stays on air",
                lensId,
            )
        }
    }

    /** A sub-lens that refuses to open leaves its logical camera on air rather than ending the session. */
    private fun openPhysicalLens(camera: RtspServerCamera2, physicalId: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        try {
            camera.openPhysicalCamera(physicalId)
            activePhysicalId = physicalId
        } catch (e: CameraOpenException) {
            Timber.w(e, "VideoBroadcastService: physical lens %s did not open", physicalId)
        } catch (e: IllegalArgumentException) {
            Timber.w(e, "VideoBroadcastService: physical lens %s is unknown to Camera2", physicalId)
        }
    }

    /**
     * Camera off is a video mute: the RTSP session, its URL and its listeners stay, and the stream
     * carries black frames. Stopping the stream dropped every listener and invalidated the link that
     * had been handed out (S3058 run 1, S3050).
     */
    private fun toggleCameraInternal() {
        val liveState = _state.value as? BroadcastState.Live ?: return
        val cameraOn = !liveState.cameraEnabled
        previewProvider.setVideoMuted(!cameraOn)
        _state.value = liveState.copy(cameraEnabled = cameraOn)
    }

    private fun stopBroadcast() {
        isStreaming.set(false)
        previewProvider.detachCamera()
        val camera = cameraServer
        if (camera != null) {
            if (camera.isStreaming) {
                camera.stopStream()
            }
            if (camera.isOnPreview) {
                camera.stopPreview()
            }
        }
        cameraServer = null
        activePhysicalId = null
        _state.value = BroadcastState.Idle
        _listenerCount.value = 0
    }

    override fun onDestroy() {
        stopBroadcast()
        serviceScope.cancel()
        super.onDestroy()
    }

    // ConnectChecker implementations
    override fun onConnectionStarted(url: String) {
        Timber.d("VideoBroadcastService: client connecting: %s", url)
    }

    override fun onConnectionSuccess() {
        Timber.d("VideoBroadcastService: connection success")
    }

    override fun onConnectionFailed(reason: String) {
        Timber.w("VideoBroadcastService: connection failed: %s", reason)
    }

    override fun onDisconnect() {
        Timber.d("VideoBroadcastService: disconnect")
    }

    // The server removes a client from its list before it reports the disconnect, so its own count is the
    // truth on both edges and a missed or doubled callback cannot drift the number.
    override fun onClientConnected(client: ServerClient) {
        publishListenerCount()
    }

    override fun onClientDisconnected(client: ServerClient) {
        publishListenerCount()
    }

    override fun onClientNewBitrate(bitrate: Long, client: ServerClient) = Unit

    private fun publishListenerCount() {
        _listenerCount.value = cameraServer?.streamClient?.getNumClients() ?: 0
    }

    override fun onAuthError() {
        Timber.w("VideoBroadcastService: auth error")
    }

    override fun onAuthSuccess() {
        Timber.d("VideoBroadcastService: auth success")
    }

    /**
     * Picks the foreground types this session may claim from what the flavor manifest declares. A build
     * without the camera type still needs a declared type for a video-only session, and the microphone is
     * the only one it has - Android 14 refuses that type until the permission is granted, hence null.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun resolveForegroundType(declared: Int): Int? {
        var type = declared and ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        val needsMicrophoneType = currentMode != BroadcastMode.VIDEO_ONLY || type == 0
        if (needsMicrophoneType && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val micGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            if (!micGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return null
            type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        }
        return type
    }

    private fun attachAudioEffect(camera: RtspServerCamera2, config: BroadcastSessionConfig) {
        val gainPercent = config.micGainPercent
        val gainMultiplier = gainPercent / 100.0f
        val applyGain = gainPercent != 100

        if (currentMode != BroadcastMode.VIDEO_ONLY && applyGain) {
            camera.setCustomAudioEffect(object : CustomAudioEffect() {
                override fun process(pcmBuffer: ByteArray): ByteArray {
                    applyPcmGain(pcmBuffer, pcmBuffer.size, gainMultiplier)
                    return pcmBuffer
                }
            })
        }
        Timber.d("S3351: video broadcast audio custom effect attached (gain: %d%%)", config.micGainPercent)
    }

    private fun applyPcmGain(buffer: ByteArray, length: Int, gainMultiplier: Float) {
        var i = 0
        while (i + 1 < length) {
            val sample = (buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)
            val shortSample = sample.toShort()
            val scaled = (shortSample * gainMultiplier).toInt().coerceIn(-32768, 32767)
            buffer[i] = (scaled and 0xFF).toByte()
            buffer[i + 1] = ((scaled shr 8) and 0xFF).toByte()
            i += 2
        }
    }

    companion object {
        const val ACTION_STOP = "com.sza.fastmediasorter.broadcast.action.VIDEO_STOP"
        const val ACTION_TOGGLE_MIC = "com.sza.fastmediasorter.broadcast.action.TOGGLE_MIC"
        const val ACTION_SELECT_LENS = "com.sza.fastmediasorter.broadcast.action.SELECT_LENS"
        const val ACTION_TOGGLE_CAMERA = "com.sza.fastmediasorter.broadcast.action.TOGGLE_CAMERA"

        private const val EXTRA_MODE = "mode"
        private const val EXTRA_LENS_ID = "lens_id"

        private val _state = MutableStateFlow<BroadcastState>(BroadcastState.Idle)
        val state: StateFlow<BroadcastState> = _state.asStateFlow()

        private val _listenerCount = MutableStateFlow(0)
        val listenerCount: StateFlow<Int> = _listenerCount.asStateFlow()

        /**
         * A start the service cannot honour is refused here rather than inside it: a promise made by
         * startForegroundService() and not kept costs the whole process, while a refusal made before
         * it is only a message (S3267).
         */
        fun start(context: Context, mode: BroadcastMode, lensId: String?) {
            val missingCamera = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) !=
                PackageManager.PERMISSION_GRANTED
            val missingAudio = mode != BroadcastMode.VIDEO_ONLY &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
                PackageManager.PERMISSION_GRANTED
            if (missingCamera || missingAudio) {
                Timber.w(
                    "VideoBroadcastService: start refused (camera: %b, audio: %b)",
                    missingCamera,
                    missingAudio,
                )
                _state.value = BroadcastState.Failed(
                    BroadcastFailure.MICROPHONE_PERMISSION,
                    "Camera or Microphone permission missing"
                )
                return
            }
            val intent = Intent(context, VideoBroadcastService::class.java).apply {
                putExtra(EXTRA_MODE, mode.name)
                putExtra(EXTRA_LENS_ID, lensId)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopIntent(context: Context): Intent =
            Intent(context, VideoBroadcastService::class.java).apply {
                action = ACTION_STOP
            }

        fun stop(context: Context) {
            context.startService(stopIntent(context))
        }

        fun toggleMicrophone(context: Context) {
            val intent = Intent(context, VideoBroadcastService::class.java).apply {
                action = ACTION_TOGGLE_MIC
            }
            context.startService(intent)
        }

        fun selectLens(context: Context, lensId: String) {
            val intent = Intent(context, VideoBroadcastService::class.java).apply {
                action = ACTION_SELECT_LENS
                putExtra(EXTRA_LENS_ID, lensId)
            }
            context.startService(intent)
        }

        fun toggleCamera(context: Context) {
            val intent = Intent(context, VideoBroadcastService::class.java).apply {
                action = ACTION_TOGGLE_CAMERA
            }
            context.startService(intent)
        }

        fun clearFailure() {
            if (_state.value is BroadcastState.Failed) {
                _state.value = BroadcastState.Idle
            }
        }

        /** The service's `foregroundServiceType` as the merged manifest of this flavor declares it (S3154). */
        @RequiresApi(Build.VERSION_CODES.Q)
        fun declaredForegroundServiceTypes(context: Context): Int {
            val component = ComponentName(context, VideoBroadcastService::class.java)
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getServiceInfo(component, PackageManager.ComponentInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getServiceInfo(component, 0)
            }
            return info.foregroundServiceType
        }
    }
}
