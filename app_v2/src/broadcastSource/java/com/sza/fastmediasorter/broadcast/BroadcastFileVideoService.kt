package com.sza.fastmediasorter.broadcast

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.provider.OpenableColumns
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.sources.audio.AudioFileSource
import com.pedro.encoder.input.sources.audio.NoAudioSource
import com.pedro.rtspserver.RtspServerStream
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
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/**
 * S4234: broadcasts a stored video file as the phone's ordinary RTSP session. The file is decoded
 * and re-encoded into the same server a camera session uses, so the endpoint, the descriptor and
 * the relay tap are the ones every receiver already knows (LIVE-BROADCAST 0.21 item N: a source is
 * not a kind). The video track is decoded by [BroadcastFileVideoSource], not by the library's file
 * decoder, which drops most frames of a file with B-frames (S4300); the audio track goes through
 * the library's [AudioFileSource]. The end of the video track ends the broadcast.
 */
@Suppress("MagicNumber", "TooManyFunctions")
@AndroidEntryPoint
class BroadcastFileVideoService : Service(), ConnectChecker, ClientListener {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var credentialRegistry: BroadcastCredentialRegistry

    @Volatile
    private var rtspGate: RtspGatedPort? = null

    @Inject
    lateinit var relayFeed: ExchangeVideoRelayFeed

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var fileServer: RtspServerStream? = null

    @Volatile
    private var relayTap: RelayTapRecordController? = null

    private val isStreaming = AtomicBoolean(false)
    private val stateLock = Any()

    // Written before the session opens on the IO scope, read by the descriptor builder.
    @Volatile
    private var currentMode: BroadcastMode = BroadcastMode.VIDEO_AUDIO

    override fun onBind(intent: Intent?): IBinder? = null

    @Suppress("ReturnCount")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val uri = intent?.data
        if (intent?.action == ACTION_STOP) {
            stopBroadcast()
            stopSelf()
            return START_NOT_STICKY
        }
        // Before the reentrancy guard and before the asynchronous session work: a start promised by
        // startForegroundService() must be kept even when the command itself proves broken (S3267).
        enterForeground()
        if (uri == null) {
            Timber.w("BroadcastFileVideoService: start refused, no file uri on the intent")
            _state.value = BroadcastState.Failed(
                BroadcastFailure.FILE_UNREADABLE,
                "Start intent carries no file uri"
            )
            stopSelf()
            return START_NOT_STICKY
        }
        startBroadcast(uri)
        return START_NOT_STICKY
    }

    private fun enterForeground() {
        val notification = BroadcastNotificationFactory.createNotification(
            context = this,
            stopIntent = stopIntent(this),
            titleRes = R.string.broadcast_notification_file_title,
            textRes = R.string.broadcast_notification_file_text,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NotificationIds.PHONE_BROADCAST,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NotificationIds.PHONE_BROADCAST, notification)
        }
    }

    private fun leaveForegroundAndStop() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startBroadcast(uri: Uri) {
        if (!isStreaming.compareAndSet(false, true)) return
        serviceScope.launch { openSession(uri) }
    }

    @Suppress("ReturnCount", "TooGenericExceptionCaught")
    private suspend fun openSession(uri: Uri) {
        Timber.d("S4234: video file session opening uri=$uri")
        val config = readSessionConfig()
        if (!isStreaming.get()) return
        val tracks = probeTracks(uri)
        currentMode = if (tracks.hasAudio) BroadcastMode.VIDEO_AUDIO else BroadcastMode.VIDEO_ONLY
        val lanHost = resolveLanHostOrFail() ?: return

        try {
            // Always headless: a file session has no preview surface anywhere.
            val gated = RtspGatedPort(config.rtspPort, credentialRegistry)
            rtspGate?.close()
            rtspGate = gated
            val video = BroadcastFileVideoSource(this, uri) { onFileDecoded() }
            if (!video.open()) {
                video.release()
                failOpen("The file's video track did not prepare")
                return
            }
            val audio = if (currentMode == BroadcastMode.VIDEO_ONLY) NoAudioSource() else AudioFileSource(this, uri)
            val server = RtspServerStream(this, gated.innerPort, this, video, audio)
            val streamClient = server.getStreamClient()
            gated.closeLibrary(streamClient)
            gated.open()
            // The server announces an audio track in the SDP unless told otherwise.
            streamClient.setOnlyVideo(currentMode == BroadcastMode.VIDEO_ONLY)
            // One address family on the wire, for the same reason the camera session forces IPv4.
            streamClient.forceIpType(IpType.IPv4)
            streamClient.setClientListener(this)
            // Set before the encoders start, so the tap sees the formats as they arrive.
            val tap = RelayTapRecordController(relayFeed, hasAudio = currentMode != BroadcastMode.VIDEO_ONLY)
            server.setRecordController(tap)
            relayTap = tap
            // Published before the prepares: a failure below releases the sources through it, a stop
            // landing after this line finds the server in the field, one before it the recheck below.
            fileServer = server

            val prepareFailure = prepareEncoders(server, video, tracks, config)
            if (prepareFailure != null) {
                failOpen(prepareFailure)
                return
            }
            if (!isStreaming.get()) {
                discardStoppedServer(server)
                return
            }
            server.startStream()

            val endpoint = publishableEndpoint(streamClient.getEndPointConnection(), lanHost)
            val live = BroadcastState.Live(
                descriptor = liveDescriptor(endpoint, resolveDisplayName(uri), config),
                startedAtElapsedRealtimeMs = SystemClock.elapsedRealtime(),
                cameraEnabled = false,
                microphoneEnabled = false,
                activeLensId = null,
                source = BROADCAST_SOURCE_FILE,
            )
            val published = synchronized(stateLock) {
                if (isStreaming.get()) {
                    _state.value = live
                    true
                } else {
                    false
                }
            }
            if (!published) {
                discardStoppedServer(server)
                return
            }
            Timber.d("BroadcastFileVideoService: RTSP file streaming started at %s", endpoint)
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            Timber.e(e, "BroadcastFileVideoService: failed to start RTSP file server")
            if (isStreaming.getAndSet(false)) {
                _state.value = BroadcastState.Failed(
                    BroadcastFailure.NETWORK_UNAVAILABLE,
                    e.message ?: "Failed to start RTSP file server"
                )
                releaseServer()
                leaveForegroundAndStop()
            }
        }
    }

    /**
     * Sizes the encoders from the file's own tracks. Returns why a track did not prepare, or null.
     * The audio source refuses a rate or channel layout other than the file's by throwing.
     * A file with no audio track still prepares the audio encoder, from the session config: the library
     * starts it unconditionally and throws when it was never prepared. [NoAudioSource] feeds it nothing.
     */
    private fun prepareEncoders(
        server: RtspServerStream,
        video: BroadcastFileVideoSource,
        tracks: FileTracks,
        config: BroadcastSessionConfig,
    ): String? {
        val videoReady = preparesWithoutRefusal {
            server.prepareVideo(video.trackWidth, video.trackHeight, FILE_VIDEO_BITRATE_BPS, video.trackFps)
        }
        val audioReady = videoReady && preparesWithoutRefusal {
            val silent = currentMode == BroadcastMode.VIDEO_ONLY
            val sampleRateHz = if (silent) config.sampleRateHz else tracks.sampleRateHz
            val channelCount = if (silent) config.channelCount else tracks.channelCount
            server.prepareAudio(sampleRateHz, channelCount > 1, config.bitRateBps)
        }
        return when {
            !videoReady -> "The file's video track did not prepare"
            !audioReady -> "The file's audio track did not prepare"
            else -> null
        }
    }

    private inline fun preparesWithoutRefusal(prepare: () -> Boolean): Boolean = try {
        prepare()
    } catch (e: IllegalArgumentException) {
        Timber.w(e, "BroadcastFileVideoService: encoder refused the file's track")
        false
    }

    /** The video source reports the track played out on its pump thread; the ending mirrors a natural stop. */
    private fun onFileDecoded() {
        Timber.d("S4234: video file decoded to its end, broadcast over")
        serviceScope.launch {
            if (isStreaming.getAndSet(false)) {
                stopBroadcast()
                leaveForegroundAndStop()
            }
        }
    }

    private fun failOpen(detail: String) {
        Timber.w("BroadcastFileVideoService: %s", detail)
        _state.value = BroadcastState.Failed(BroadcastFailure.FILE_UNREADABLE, detail)
        isStreaming.set(false)
        releaseServer()
        leaveForegroundAndStop()
    }

    private fun resolveLanHostOrFail(): String? {
        val lanHost = LanAddressResolver(this).resolve()
        if (lanHost == null) {
            Timber.w("BroadcastFileVideoService: no LAN address, session not started")
            _state.value = BroadcastState.Failed(BroadcastFailure.NETWORK_UNAVAILABLE, "No LAN address")
            isStreaming.set(false)
            leaveForegroundAndStop()
        }
        return lanHost
    }

    private fun publishableEndpoint(libraryEndpoint: String, lanHost: String): String =
        checkNotNull(RtspEndpointAddress.onLanHost(libraryEndpoint, lanHost, rtspGate?.publicPort)) {
            "RTSP endpoint $libraryEndpoint cannot be published on $lanHost"
        }

    /** The endpoint the file session is reached by - the same RTSP shape a camera session publishes. */
    private fun liveDescriptor(
        endpoint: String,
        displayName: String?,
        config: BroadcastSessionConfig,
    ): BroadcastDescriptorDto {
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
        return BroadcastDescriptorDto(
            schemaVersion = 1,
            url = endpoint,
            title = displayName ?: config.streamTitle,
            mode = currentMode.name,
            sourceId = config.sourceDeviceId,
            endpoints = listOf(endpointDto),
            isLive = true,
            targetLatencyMs = 200L
        )
    }

    /** The kinds of track the file carries, read before the mode and the SDP are decided. */
    @Suppress("TooGenericExceptionCaught")
    private fun probeTracks(uri: Uri): FileTracks {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(this, uri, null)
            var tracks = FileTracks(hasVideo = false, hasAudio = false)
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                when (format.getString(MediaFormat.KEY_MIME)?.substringBefore('/')) {
                    "video" -> tracks = tracks.copy(hasVideo = true)
                    "audio" -> if (!tracks.hasAudio) tracks = tracks.withAudio(format)
                }
            }
            return tracks
        } catch (e: Exception) {
            Timber.w(e, "BroadcastFileVideoService: tracks of %s unreadable", uri)
            return FileTracks(hasVideo = false, hasAudio = false)
        } finally {
            extractor.release()
        }
    }

    private data class FileTracks(
        val hasVideo: Boolean,
        val hasAudio: Boolean,
        val sampleRateHz: Int = 0,
        val channelCount: Int = 0,
    ) {
        // The first audio track is the one the library's audio source decodes.
        fun withAudio(format: MediaFormat) = copy(
            hasAudio = true,
            sampleRateHz = format.getInteger(MediaFormat.KEY_SAMPLE_RATE),
            channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT),
        )
    }

    private suspend fun readSessionConfig(): BroadcastSessionConfig {
        val settings = settingsRepository.getSettings().first()
        val broadcast = settings.broadcast
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
            videoBitrateBps = broadcast.videoBitrateBps,
        )
    }

    @Suppress("TooGenericExceptionCaught", "ReturnCount")
    private fun resolveDisplayName(uri: Uri): String? {
        return try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (column >= 0 && cursor.moveToFirst()) cursor.getString(column) else null
                }
        } catch (e: Exception) {
            Timber.d("BroadcastFileVideoService: display name of %s unreadable - ${e.message}", uri)
            null
        }
    }

    private fun releaseServer() {
        isStreaming.set(false)
        val server = fileServer
        if (server != null) {
            if (server.isStreaming) server.stopStream()
            // Ends the video pump and the audio decoder, and closes both extractors.
            server.release()
        }
        fileServer = null
        relayTap?.release()
        relayTap = null
    }

    /** A server the stop path already took is left alone: the field may by now hold a newer session's. */
    private fun discardStoppedServer(server: RtspServerStream) {
        if (fileServer === server) releaseServer()
    }

    private fun stopBroadcast() {
        releaseServer()
        rtspGate?.close()
        rtspGate = null
        synchronized(stateLock) {
            _state.value = BroadcastState.Idle
        }
        _listenerCount.value = 0
    }

    override fun onDestroy() {
        stopBroadcast()
        serviceScope.cancel()
        super.onDestroy()
    }

    // ConnectChecker implementations
    override fun onConnectionStarted(url: String) {
        Timber.d("BroadcastFileVideoService: client connecting: %s", url)
    }

    override fun onConnectionSuccess() {
        Timber.d("BroadcastFileVideoService: connection success")
    }

    override fun onConnectionFailed(reason: String) {
        Timber.w("BroadcastFileVideoService: connection failed: %s", reason)
    }

    override fun onDisconnect() {
        Timber.d("BroadcastFileVideoService: disconnect")
    }

    // A fresh listener joins the live edge: the re-encoded stream carries keyframes on its schedule,
    // and requestKeyFrame is what turns the wait into one keyframe interval at most.
    override fun onClientConnected(client: ServerClient) {
        fileServer?.requestKeyframe()
        publishListenerCount()
    }

    override fun onClientDisconnected(client: ServerClient) {
        publishListenerCount()
    }

    override fun onClientNewBitrate(bitrate: Long, client: ServerClient) = Unit

    private fun publishListenerCount() {
        _listenerCount.value = fileServer?.getStreamClient()?.getNumClients() ?: 0
    }

    override fun onAuthError() {
        Timber.w("BroadcastFileVideoService: auth error")
    }

    override fun onAuthSuccess() {
        Timber.d("BroadcastFileVideoService: auth success")
    }

    companion object {
        const val ACTION_STOP = "com.sza.fastmediasorter.broadcast.action.FILE_VIDEO_STOP"

        /** The bitrate the library's file server defaulted to, kept when S4300 moved the session off it. */
        private const val FILE_VIDEO_BITRATE_BPS = 1_228_800

        private val _state = MutableStateFlow<BroadcastState>(BroadcastState.Idle)
        val state: StateFlow<BroadcastState> = _state.asStateFlow()

        private val _listenerCount = MutableStateFlow(0)
        val listenerCount: StateFlow<Int> = _listenerCount.asStateFlow()

        fun start(context: Context, uri: Uri) {
            val intent = Intent(context, BroadcastFileVideoService::class.java).apply {
                setData(uri)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopIntent(context: Context): Intent =
            Intent(context, BroadcastFileVideoService::class.java).apply {
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
