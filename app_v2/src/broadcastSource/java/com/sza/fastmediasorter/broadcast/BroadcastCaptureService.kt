package com.sza.fastmediasorter.broadcast

import android.Manifest
import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.content.ContextCompat
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

@Suppress("MagicNumber")
@AndroidEntryPoint
class BroadcastCaptureService : Service() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Assigned on the session coroutine, read and cleared from the main-thread stop path.
    @Volatile
    private var httpServer: BroadcastHttpServer? = null

    @Volatile
    private var audioRecord: AudioRecord? = null
    private val isRecording = AtomicBoolean(false)

    override fun onBind(intent: Intent?): IBinder? = null

    @Suppress("ReturnCount")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopBroadcast()
            stopSelf()
            return START_NOT_STICKY
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Timber.w("BroadcastCaptureService: RECORD_AUDIO permission missing")
            _state.value = BroadcastState.Failed(
                BroadcastFailure.MICROPHONE_PERMISSION,
                "RECORD_AUDIO permission missing"
            )
            stopSelf()
            return START_NOT_STICKY
        }

        startBroadcast()
        return START_STICKY
    }

    private fun startBroadcast() {
        // compareAndSet, not get: the session now opens asynchronously, so a second onStartCommand can
        // arrive before the first one has bound its port.
        if (!isRecording.compareAndSet(false, true)) return

        val notification = BroadcastNotificationFactory.createNotification(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NotificationIds.PHONE_BROADCAST,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NotificationIds.PHONE_BROADCAST, notification)
        }

        serviceScope.launch { openSession() }
    }

    /**
     * Reads the preference snapshot and opens the session off the main thread: onStartCommand is a
     * main-thread callback and the settings read is DataStore I/O, which must not block it.
     */
    @Suppress("ReturnCount")
    private suspend fun openSession() {
        val config = readSessionConfig()
        val server = BroadcastHttpServer(this, config)
        val port = server.start()
        if (port == -1) {
            _state.value = BroadcastState.Failed(
                BroadcastFailure.NETWORK_UNAVAILABLE,
                "Port ${config.port} is occupied or unavailable"
            )
            isRecording.set(false)
            stopSelf()
            return
        }
        if (!isRecording.get()) {
            // Stopped while the port was being bound - the server would otherwise outlive the session.
            server.stop()
            return
        }
        val url = server.getBroadcastUrl() ?: run {
            Timber.d("S3054: rejecting broadcast without a reachable LAN IPv4 address")
            server.stop()
            _state.value = BroadcastState.Failed(
                BroadcastFailure.NETWORK_UNAVAILABLE,
                "No reachable local IPv4 address"
            )
            isRecording.set(false)
            stopSelf()
            return
        }
        httpServer = server
        serviceScope.launch {
            server.listenerCount.collect { count ->
                _listenerCount.value = count
            }
        }

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
        _state.value = BroadcastState.Live(dto, SystemClock.elapsedRealtime())

        captureAudioLoop(server, config)
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
        )
    }

    @Suppress("TooGenericExceptionCaught")
    // S3155: the service refuses to start at all without RECORD_AUDIO - onStartCommand checks it and
    // calls stopSelf() - so this loop is unreachable without the grant, and the AudioRecord below is
    // additionally inside a try/catch(Exception) that takes the SecurityException a mid-session
    // revocation would throw. Lint follows neither a guard in another method nor a catch around the
    // call rather than on it.
    @SuppressLint("MissingPermission")
    private fun captureAudioLoop(server: BroadcastHttpServer, config: BroadcastSessionConfig) {
        val channelConfig = if (config.channelCount >= 2) AudioFormat.CHANNEL_IN_STEREO else AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBufferSize = AudioRecord.getMinBufferSize(config.sampleRateHz, channelConfig, audioFormat)
        val bufferSize = maxOf(minBufferSize, 8192)

        // The server advertises audio/aac with icy-br derived from the same config, so the bytes on
        // the wire must be ADTS AAC at that rate - the recorder's raw PCM would make the response
        // header a lie no client can act on.
        val encoder = BroadcastAacEncoder(
            sampleRate = config.sampleRateHz,
            channelCount = config.channelCount,
            bitRate = config.bitRateBps,
        ) { frame, offset, length -> server.writeFrame(frame, offset, length) }
        if (!encoder.start()) {
            _state.value = BroadcastState.Failed(
                BroadcastFailure.ENCODER_UNAVAILABLE,
                "AAC encoder unavailable on this device"
            )
            isRecording.set(false)
            return
        }

        try {
            val recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                config.sampleRateHz,
                channelConfig,
                audioFormat,
                bufferSize
            )
            audioRecord = recorder
            recorder.startRecording()

            val buffer = ByteArray(bufferSize)
            val gainPercent = config.micGainPercent
            val gainMultiplier = gainPercent / 100.0f
            val applyGain = gainPercent != 100

            while (isRecording.get()) {
                val read = recorder.read(buffer, 0, bufferSize)
                if (read > 0) {
                    if (applyGain) {
                        applyPcmGain(buffer, read, gainMultiplier)
                    }
                    encoder.encode(buffer, read)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "BroadcastCaptureService: audio capture error")
            _state.value = BroadcastState.Failed(
                BroadcastFailure.CAPTURE_ERROR,
                e.message ?: "Capture error"
            )
        } finally {
            encoder.stop()
            releaseRecorder()
        }
    }

    private fun stopBroadcast() {
        isRecording.set(false)
        releaseRecorder()
        httpServer?.stop()
        httpServer = null
        _state.value = BroadcastState.Idle
        _listenerCount.value = 0
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

    @Suppress("SwallowedException", "TooGenericExceptionCaught")
    private fun releaseRecorder() {
        try {
            audioRecord?.stop()
        } catch (_: Exception) {}
        try {
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null
    }

    override fun onDestroy() {
        stopBroadcast()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.sza.fastmediasorter.broadcast.action.STOP"

        private val _state = MutableStateFlow<BroadcastState>(BroadcastState.Idle)
        val state: StateFlow<BroadcastState> = _state.asStateFlow()

        private val _listenerCount = MutableStateFlow(0)
        val listenerCount: StateFlow<Int> = _listenerCount.asStateFlow()

        fun start(context: Context, mode: BroadcastMode) {
            val intent = Intent(context, BroadcastCaptureService::class.java).apply {
                putExtra("mode", mode.name)
            }
            ContextCompat.startForegroundService(context, intent)
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

        fun stop(context: Context) {
            val intent = Intent(context, BroadcastCaptureService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
