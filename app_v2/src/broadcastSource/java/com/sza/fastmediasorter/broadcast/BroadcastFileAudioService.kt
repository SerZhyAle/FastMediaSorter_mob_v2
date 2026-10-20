package com.sza.fastmediasorter.broadcast

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.provider.OpenableColumns
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.notification.NotificationIds
import com.sza.fastmediasorter.data.broadcast.BroadcastDescriptorDto
import com.sza.fastmediasorter.data.broadcast.BroadcastEndpointDto
import com.sza.fastmediasorter.data.broadcast.auth.BroadcastCredentialRegistry
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
 * S4234: broadcasts a stored audio file as the phone's ordinary AUDIO_ONLY session. The file is
 * decoded to PCM and re-encoded by the same encoder into the same HTTP server the microphone uses,
 * so a receiver cannot tell a file broadcast from a captured one by its URL, headers or descriptor
 * (LIVE-BROADCAST 0.21 item N: a source is not a kind). The end of the file ends the broadcast.
 */
@Suppress("MagicNumber")
@AndroidEntryPoint
class BroadcastFileAudioService : Service() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var credentialRegistry: BroadcastCredentialRegistry

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Assigned on the session coroutine, read and cleared from the main-thread stop path.
    @Volatile
    private var httpServer: BroadcastHttpServer? = null

    private val isPlaying = AtomicBoolean(false)
    private val stateLock = Any()

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
            Timber.w("BroadcastFileAudioService: start refused, no file uri on the intent")
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
        // compareAndSet: the session opens asynchronously, so a second start command can arrive first.
        if (!isPlaying.compareAndSet(false, true)) return
        serviceScope.launch { openSession(uri) }
    }

    /**
     * Reads the preference snapshot and opens the session off the main thread: onStartCommand is a
     * main-thread callback and the settings read is DataStore I/O, which must not block it.
     */
    @Suppress("ReturnCount", "TooGenericExceptionCaught")
    private suspend fun openSession(uri: Uri) {
        val baseConfig = readSessionConfig()
        val decoder = BroadcastFileDecoder(this, uri)
        if (!decoder.open()) {
            abortSession(BroadcastFailure.FILE_UNREADABLE, "No decodable audio track") {
                decoder.release()
            }
            return
        }
        // The wire carries the file's own rate and channel count, and the title is the file: that is
        // what makes the session ordinary to a receiver and readable in a receiver's list.
        val config = baseConfig.copy(
            streamTitle = resolveDisplayName(uri) ?: baseConfig.streamTitle,
            sampleRateHz = decoder.sampleRateHz,
            channelCount = decoder.channelCount,
        )
        val server = BroadcastHttpServer(this, config, credentials = credentialRegistry::pairs)
        if (server.start() == -1) {
            abortSession(BroadcastFailure.NETWORK_UNAVAILABLE, "Port ${config.port} is occupied") {
                decoder.release()
            }
            return
        }
        // Published before the recheck: a stop landing after this line finds the server in the field,
        // and one landing before it is caught by the recheck, so the bound server cannot outlive the session.
        httpServer = server
        if (!isPlaying.get()) {
            server.stop()
            clearServer(server)
            decoder.release()
            return
        }
        val url = server.getBroadcastUrl()
        if (url == null) {
            abortSession(BroadcastFailure.NETWORK_UNAVAILABLE, "No reachable local IPv4 address") {
                server.stop()
                clearServer(server)
                decoder.release()
            }
            return
        }
        serviceScope.launch {
            server.listenerCount.collect { count ->
                _listenerCount.value = count
            }
        }
        val encoder = goLive(server, url, config, decoder) ?: return

        val ranToTheEnd = playUntilFileEnd(decoder)
        encoder.stop()
        decoder.release()
        // The stop path and the end of the file both own the teardown; getAndSet picks one.
        if (ranToTheEnd && isPlaying.getAndSet(false)) {
            stopBroadcast()
            leaveForegroundAndStop()
        }
    }

    /**
     * Builds the descriptor, starts the encoder and puts the session on air. Null when the encoder
     * or the file's codec refused - the caller only has to return, the session is already failed.
     */
    private fun goLive(
        server: BroadcastHttpServer,
        url: String,
        config: BroadcastSessionConfig,
        decoder: BroadcastFileDecoder,
    ): BroadcastAacEncoder? {
        val endpoint = BroadcastEndpointDto(
            url = url,
            transport = "HTTP",
            mode = BroadcastMode.AUDIO_ONLY.name,
            audioCodec = "aac",
            sampleRate = config.sampleRateHz,
            bitrate = config.bitRateBps,
            isLive = true,
            targetLatencyMs = 200L
        )
        val dto = BroadcastDescriptorDto(
            schemaVersion = 1,
            url = url,
            title = config.streamTitle,
            mode = BroadcastMode.AUDIO_ONLY.name,
            sourceId = config.sourceDeviceId,
            endpoints = listOf(endpoint),
            isLive = true,
            targetLatencyMs = 200L
        )
        // The server advertises audio/aac with icy-br derived from the same config, so the bytes on
        // the wire must be ADTS AAC at that rate - the file's own samples are re-encoded like the
        // microphone's, only their rate and channel count come from the file.
        val encoder = BroadcastAacEncoder(
            sampleRate = config.sampleRateHz,
            channelCount = config.channelCount,
            bitRate = config.bitRateBps,
        ) { frame, offset, length -> server.writeFrame(frame, offset, length) }
        if (!encoder.start() || !decoder.start { pcm, length, _ -> encoder.encode(pcm, length) }) {
            encoder.stop()
            decoder.release()
            abortSession(BroadcastFailure.ENCODER_UNAVAILABLE, "AAC codec unavailable") {}
            return null
        }
        val live = BroadcastState.Live(
            descriptor = dto,
            startedAtElapsedRealtimeMs = SystemClock.elapsedRealtime(),
            source = BROADCAST_SOURCE_FILE,
        )
        val published = synchronized(stateLock) {
            if (isPlaying.get()) {
                _state.value = live
                true
            } else {
                false
            }
        }
        if (!published) {
            encoder.stop()
            decoder.release()
            server.stop()
            clearServer(server)
        }
        return if (published) encoder else null
    }

    /**
     * The one failure ending: free what the caller names, report the typed reason and take the
     * session down. Every openSession refusal funnels here so none of them forgets a step.
     */
    private fun abortSession(failure: BroadcastFailure, detail: String, cleanup: () -> Unit) {
        cleanup()
        _state.value = BroadcastState.Failed(failure, detail)
        isPlaying.set(false)
        leaveForegroundAndStop()
    }

    /** False when a stop command ended the loop first; true only when the file itself ran out. */
    @Suppress("TooGenericExceptionCaught")
    private fun playUntilFileEnd(decoder: BroadcastFileDecoder): Boolean {
        return try {
            while (isPlaying.get()) {
                if (!decoder.readNext { isPlaying.get() }) break
            }
            isPlaying.get()
        } catch (e: Exception) {
            Timber.e(e, "BroadcastFileAudioService: file playback error")
            if (isPlaying.getAndSet(false)) {
                _state.value = BroadcastState.Failed(
                    BroadcastFailure.CAPTURE_ERROR,
                    e.message ?: "File playback error"
                )
                httpServer?.let { server ->
                    server.stop()
                    clearServer(server)
                }
                leaveForegroundAndStop()
            }
            false
        }
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
        )
    }

    /** The file's own name is what a receiver shows; an unreadable name falls back to the stream title. */
    @Suppress("TooGenericExceptionCaught", "ReturnCount")
    private fun resolveDisplayName(uri: Uri): String? {
        return try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (column >= 0 && cursor.moveToFirst()) cursor.getString(column) else null
                }
        } catch (e: Exception) {
            Timber.d("BroadcastFileAudioService: display name of %s unreadable - ${e.message}", uri)
            null
        }
    }

    private fun stopBroadcast() {
        // The playback loop sees the flag on its next paced wake and leaves the file alone.
        synchronized(stateLock) {
            isPlaying.set(false)
            _state.value = BroadcastState.Idle
        }
        httpServer?.stop()
        httpServer = null
        _listenerCount.value = 0
    }

    private fun clearServer(server: BroadcastHttpServer) {
        if (httpServer === server) httpServer = null
    }

    override fun onDestroy() {
        stopBroadcast()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.sza.fastmediasorter.broadcast.action.FILE_AUDIO_STOP"

        private val _state = MutableStateFlow<BroadcastState>(BroadcastState.Idle)
        val state: StateFlow<BroadcastState> = _state.asStateFlow()

        private val _listenerCount = MutableStateFlow(0)
        val listenerCount: StateFlow<Int> = _listenerCount.asStateFlow()

        /** A start the service cannot honour is refused before startForegroundService makes the promise. */
        fun start(context: Context, uri: Uri) {
            val intent = Intent(context, BroadcastFileAudioService::class.java).apply {
                setData(uri)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopIntent(context: Context): Intent =
            Intent(context, BroadcastFileAudioService::class.java).apply {
                action = ACTION_STOP
            }

        fun stop(context: Context) {
            context.startService(stopIntent(context))
        }

        /**
         * The failure state outlives the service that set it - it is static and the service has already
         * stopped itself - so only the UI that reported it can retire it.
         */
        fun clearFailure() {
            if (_state.value is BroadcastState.Failed) {
                _state.value = BroadcastState.Idle
            }
        }
    }
}
