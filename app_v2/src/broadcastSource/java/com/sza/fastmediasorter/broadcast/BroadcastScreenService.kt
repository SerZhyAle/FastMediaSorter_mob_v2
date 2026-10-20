package com.sza.fastmediasorter.broadcast

import android.app.Activity
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.pedro.common.ConnectChecker
import com.pedro.rtspserver.RtspServerDisplay
import com.pedro.rtspserver.server.ClientListener
import com.pedro.rtspserver.server.IpType
import com.pedro.rtspserver.server.ServerClient
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.network.LanAddressResolver
import com.sza.fastmediasorter.core.notification.NotificationIds
import com.sza.fastmediasorter.core.util.rethrowIfCancellation
import com.sza.fastmediasorter.data.broadcast.BroadcastDescriptorDto
import com.sza.fastmediasorter.data.broadcast.BroadcastEndpointDto
import com.sza.fastmediasorter.data.broadcast.auth.BroadcastCredentialRegistry
import com.sza.fastmediasorter.data.broadcast.exchange.ExchangeVideoRelayFeed
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
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlin.math.max

/**
 * S4233: broadcasts the phone's own screen as its ordinary RTSP session, in the picture-without-sound
 * shape. The library's [RtspServerDisplay] turns a MediaProjection result into the same server a camera
 * session uses, so the endpoint, the descriptor and the relay tap are the ones every receiver already
 * knows (LIVE-BROADCAST 0.21 item N: a source is not a kind).
 *
 * The consent behind the projection is good for this one session: when the platform stops it - the
 * status-bar chip, a policy, another capture taking over - the broadcast ends with it, because a
 * receiver left on an endpoint that shows a frozen frame is worse than a closed one.
 */
@Suppress("TooManyFunctions")
@AndroidEntryPoint
class BroadcastScreenService : Service(), ConnectChecker, ClientListener {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var credentialRegistry: BroadcastCredentialRegistry

    private var rtspGate: RtspGatedPort? = null

    @Inject
    lateinit var relayFeed: ExchangeVideoRelayFeed

    // Main, not IO: the library registers its projection callback on the calling thread's looper when
    // the stream starts, and a worker thread has none.
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile
    private var displayServer: RtspServerDisplay? = null

    @Volatile
    private var relayTap: RelayTapRecordController? = null

    private val isStreaming = AtomicBoolean(false)

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            // The library stops the projection itself on our own stop, so this also fires after a user stop.
            if (isStreaming.getAndSet(false)) {
                stopBroadcast()
                leaveForegroundAndStop()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            isStreaming.set(false)
            stopBroadcast()
            // By id: a start queued behind this stop keeps the instance alive and keeps its foreground promise.
            stopSelf(startId)
        } else {
            startFromConsent(intent)
        }
        return START_NOT_STICKY
    }

    /**
     * The foreground promise of `startForegroundService()` is kept first, before the extras are judged:
     * a start that cannot be honoured is refused after the service is foreground, never instead of it.
     */
    private fun startFromConsent(intent: Intent?) {
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
            ?: Activity.RESULT_CANCELED
        val resultData = intent?.readResultData()
        if (!enterForeground()) {
            refuseStart("Foreground type mediaProjection was refused")
        } else if (resultCode != Activity.RESULT_OK || resultData == null) {
            refuseStart("Start intent carries no screen-capture consent")
        } else if (isStreaming.compareAndSet(false, true)) {
            serviceScope.launch { openSession(resultCode, resultData) }
        }
    }

    /** False when the platform refuses the typed foreground start - there is then nothing to keep. */
    private fun enterForeground(): Boolean {
        val notification = BroadcastNotificationFactory.createNotification(
            context = this,
            stopIntent = stopIntent(this),
            titleRes = R.string.broadcast_notification_screen_title,
            textRes = R.string.broadcast_notification_screen_text,
        )
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NotificationIds.PHONE_BROADCAST,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
                )
            } else {
                startForeground(NotificationIds.PHONE_BROADCAST, notification)
            }
            true
        } catch (e: SecurityException) {
            Timber.w(e, "BroadcastScreenService: typed foreground start refused")
            false
        }
    }

    private fun leaveForegroundAndStop() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun refuseStart(detail: String) {
        failSession(BroadcastFailure.CAPTURE_ERROR, detail)
    }

    private fun failSession(failure: BroadcastFailure, detail: String) {
        Timber.w("BroadcastScreenService: %s", detail)
        _state.value = BroadcastState.Failed(failure, detail)
        isStreaming.set(false)
        releaseServer()
        leaveForegroundAndStop()
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun openSession(resultCode: Int, resultData: Intent) {
        val config = readSessionConfig()
        // LIVE-BROADCAST producer rule 1: no LAN address, no session - the screen is never captured
        // for a descriptor nobody could reach.
        val lanHost = withContext(Dispatchers.IO) { LanAddressResolver(this@BroadcastScreenService).resolve() }
        // The settings read and the address lookup suspend, and a stop landing during them must not be
        // followed by a capture open.
        if (!isStreaming.get()) return
        if (lanHost == null) {
            failSession(BroadcastFailure.NETWORK_UNAVAILABLE, "No LAN address")
            return
        }
        try {
            goLive(config, lanHost, resultCode, resultData)
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            Timber.e(e, "BroadcastScreenService: failed to start the screen session")
            failSession(BroadcastFailure.CAPTURE_ERROR, e.message ?: "Failed to start the screen session")
        }
    }

    private fun goLive(config: BroadcastSessionConfig, lanHost: String, resultCode: Int, resultData: Intent) {
        val gated = RtspGatedPort(config.rtspPort, credentialRegistry)
        rtspGate?.close()
        rtspGate = gated
        val server = RtspServerDisplay(this, false, this, gated.innerPort)
        val streamClient = server.streamClient
        gated.closeLibrary(streamClient)
        gated.open()
        // The server announces an audio track in the SDP unless told otherwise, and a screen prepares none.
        streamClient.setOnlyVideo(true)
        // One address family on the wire, for the same reason the camera session forces IPv4.
        streamClient.forceIpType(IpType.IPv4)
        streamClient.setClientListener(this)
        // Set before the encoders start, so the tap sees the format that carries the SPS and PPS.
        val tap = RelayTapRecordController(relayFeed, hasAudio = false)
        server.setRecordController(tap)
        relayTap = tap
        server.setMediaProjectionCallback(projectionCallback)
        server.setIntentResult(resultCode, resultData)

        val size = ScreenStreamGeometry.fit(
            screenWidth = resources.displayMetrics.widthPixels,
            screenHeight = resources.displayMetrics.heightPixels,
            maxLongSide = max(config.videoWidth, config.videoHeight),
        )
        val prepared = server.prepareVideo(
            size.width,
            size.height,
            config.videoFps,
            config.videoBitrateBps,
            NO_ROTATION,
            resources.displayMetrics.densityDpi,
        )
        if (!prepared) {
            failSession(BroadcastFailure.ENCODER_UNAVAILABLE, "The screen encoder did not prepare")
            return
        }
        // Published before startStream: a stop landing after this line finds the server in the field,
        // and one landing before it is caught by the recheck below.
        displayServer = server
        if (!isStreaming.get()) {
            discardStoppedServer(server)
            return
        }
        server.startStream()

        val endpoint = publishableEndpoint(streamClient.getEndPointConnection(), lanHost)
        _state.value = BroadcastState.Live(
            descriptor = liveDescriptor(endpoint, config),
            startedAtElapsedRealtimeMs = SystemClock.elapsedRealtime(),
            cameraEnabled = false,
            microphoneEnabled = false,
            activeLensId = null,
            source = BROADCAST_SOURCE_SCREEN,
        )
        Timber.d("BroadcastScreenService: RTSP screen streaming started at %s", endpoint)
    }

    private fun publishableEndpoint(libraryEndpoint: String, lanHost: String): String =
        checkNotNull(RtspEndpointAddress.onLanHost(libraryEndpoint, lanHost, rtspGate?.publicPort)) {
            "RTSP endpoint $libraryEndpoint cannot be published on $lanHost"
        }

    /** The endpoint the screen session is reached by - the same RTSP shape a camera session publishes. */
    private fun liveDescriptor(endpoint: String, config: BroadcastSessionConfig): BroadcastDescriptorDto {
        val mode = BroadcastMode.VIDEO_ONLY.name
        val endpointDto = BroadcastEndpointDto(
            url = endpoint,
            transport = "RTSP",
            mode = mode,
            videoCodec = "h264",
            audioCodec = null,
            sampleRate = null,
            bitrate = config.videoBitrateBps,
            isLive = true,
            targetLatencyMs = TARGET_LATENCY_MS,
        )
        return BroadcastDescriptorDto(
            schemaVersion = 1,
            url = endpoint,
            title = config.streamTitle,
            mode = mode,
            sourceId = config.sourceDeviceId,
            endpoints = listOf(endpointDto),
            isLive = true,
            targetLatencyMs = TARGET_LATENCY_MS,
        )
    }

    private suspend fun readSessionConfig(): BroadcastSessionConfig {
        val broadcast = settingsRepository.getSettings().first().broadcast
        val sourceDeviceId = broadcast.sourceDeviceId ?: run {
            // Re-checked under the transform lock: a concurrent session may have minted the id first.
            var id = UUID.randomUUID().toString()
            settingsRepository.updateSettings { current ->
                val existing = current.broadcast.sourceDeviceId
                if (existing != null) {
                    id = existing
                    current
                } else {
                    current.copy(broadcast = current.broadcast.copy(sourceDeviceId = id))
                }
            }
            id
        }
        return BroadcastSessionConfig(
            streamTitle = broadcast.streamTitle.ifBlank { BroadcastSessionConfig.DEFAULT.streamTitle },
            bitRateBps = broadcast.bitRateBps,
            port = broadcast.port,
            sampleRateHz = broadcast.sampleRateHz,
            channelCount = broadcast.channelCount,
            sourceDeviceId = sourceDeviceId,
            videoWidth = broadcast.videoWidth,
            videoHeight = broadcast.videoHeight,
            videoFps = broadcast.videoFps,
            videoBitrateBps = broadcast.videoBitrateBps,
        )
    }

    private fun releaseServer() {
        val server = displayServer
        if (server != null && server.isStreaming) {
            server.stopStream()
        }
        displayServer = null
        relayTap?.release()
        relayTap = null
    }

    /** A server the stop path already took is left alone: the field may by now hold a newer session's. */
    private fun discardStoppedServer(server: RtspServerDisplay) {
        if (displayServer === server) releaseServer()
    }

    private fun stopBroadcast() {
        releaseServer()
        rtspGate?.close()
        rtspGate = null
        _state.value = BroadcastState.Idle
        _listenerCount.value = 0
    }

    override fun onDestroy() {
        stopBroadcast()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun Intent.readResultData(): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(EXTRA_RESULT_DATA)
        }

    // ConnectChecker implementations
    override fun onConnectionStarted(url: String) {
        Timber.d("BroadcastScreenService: client connecting: %s", url)
    }

    override fun onConnectionSuccess() {
        Timber.d("BroadcastScreenService: connection success")
    }

    override fun onConnectionFailed(reason: String) {
        Timber.w("BroadcastScreenService: connection failed: %s", reason)
    }

    override fun onDisconnect() {
        Timber.d("BroadcastScreenService: disconnect")
    }

    override fun onAuthError() {
        Timber.w("BroadcastScreenService: auth error")
    }

    override fun onAuthSuccess() {
        Timber.d("BroadcastScreenService: auth success")
    }

    // A fresh listener joins the live edge: the encoder carries keyframes on its schedule, and
    // requestKeyFrame is what turns the wait into one keyframe interval at most.
    override fun onClientConnected(client: ServerClient) {
        displayServer?.requestKeyFrame()
        publishListenerCount()
    }

    override fun onClientDisconnected(client: ServerClient) {
        publishListenerCount()
    }

    override fun onClientNewBitrate(bitrate: Long, client: ServerClient) = Unit

    private fun publishListenerCount() {
        _listenerCount.value = displayServer?.streamClient?.getNumClients() ?: 0
    }

    companion object {
        const val ACTION_STOP = "com.sza.fastmediasorter.broadcast.action.SCREEN_STOP"
        const val EXTRA_RESULT_CODE = "broadcast_screen_result_code"
        const val EXTRA_RESULT_DATA = "broadcast_screen_result_data"

        private const val NO_ROTATION = 0
        private const val TARGET_LATENCY_MS = 200L

        private val _state = MutableStateFlow<BroadcastState>(BroadcastState.Idle)
        val state: StateFlow<BroadcastState> = _state.asStateFlow()

        private val _listenerCount = MutableStateFlow(0)
        val listenerCount: StateFlow<Int> = _listenerCount.asStateFlow()

        fun start(context: Context, resultCode: Int, resultData: Intent) {
            val intent = Intent(context, BroadcastScreenService::class.java).apply {
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, resultData)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopIntent(context: Context): Intent =
            Intent(context, BroadcastScreenService::class.java).apply {
                action = ACTION_STOP
            }

        fun stop(context: Context) {
            context.startService(stopIntent(context))
        }

        fun clearFailure() {
            if (_state.value is BroadcastState.Failed) {
                _state.value = BroadcastState.Idle
            }
        }
    }
}
