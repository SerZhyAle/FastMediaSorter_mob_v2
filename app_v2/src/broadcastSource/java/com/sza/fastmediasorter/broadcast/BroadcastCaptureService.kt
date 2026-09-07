package com.sza.fastmediasorter.broadcast

import android.Manifest
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
import androidx.core.content.ContextCompat
import com.sza.fastmediasorter.core.notification.NotificationIds
import com.sza.fastmediasorter.data.broadcast.BroadcastDescriptorDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

@Suppress("MagicNumber")
class BroadcastCaptureService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var httpServer: BroadcastHttpServer? = null
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
            _state.value = BroadcastState.Failed("RECORD_AUDIO permission missing")
            stopSelf()
            return START_NOT_STICKY
        }

        startBroadcast()
        return START_STICKY
    }

    private fun startBroadcast() {
        if (isRecording.get()) return

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

        val server = BroadcastHttpServer(this)
        val port = server.start()
        if (port == -1) {
            _state.value = BroadcastState.Failed("Failed to start HTTP server")
            stopSelf()
            return
        }
        httpServer = server

        val url = server.getBroadcastUrl()
        val dto = BroadcastDescriptorDto(
            schemaVersion = 1,
            url = url,
            title = "Phone Audio Stream",
            mode = BroadcastMode.AUDIO_ONLY.name
        )
        _state.value = BroadcastState.Live(dto)

        isRecording.set(true)
        serviceScope.launch {
            captureAudioLoop(server)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun captureAudioLoop(server: BroadcastHttpServer) {
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE_HZ, channelConfig, audioFormat)
        val bufferSize = maxOf(minBufferSize, 8192)

        // The server advertises audio/aac with icy-br 128, so the bytes on the wire must be ADTS AAC
        // at that rate - the recorder's raw PCM would make the response header a lie no client can act on.
        val encoder = BroadcastAacEncoder(
            sampleRate = SAMPLE_RATE_HZ,
            channelCount = CHANNEL_COUNT,
            bitRate = BITRATE_BPS,
        ) { frame, offset, length -> server.writeFrame(frame, offset, length) }
        if (!encoder.start()) {
            _state.value = BroadcastState.Failed("AAC encoder unavailable on this device")
            isRecording.set(false)
            return
        }

        Timber.d("S2508: broadcast audio loop is encoding to ADTS AAC")
        try {
            val recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE_HZ,
                channelConfig,
                audioFormat,
                bufferSize
            )
            audioRecord = recorder
            recorder.startRecording()

            val buffer = ByteArray(bufferSize)
            while (isRecording.get()) {
                val read = recorder.read(buffer, 0, bufferSize)
                if (read > 0) {
                    encoder.encode(buffer, read)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "BroadcastCaptureService: audio capture error")
            _state.value = BroadcastState.Failed(e.message ?: "Capture error")
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

        private const val SAMPLE_RATE_HZ = 44100
        private const val CHANNEL_COUNT = 1

        /** Matches the `icy-br` header BroadcastHttpServer sends, so the two cannot drift apart. */
        private const val BITRATE_BPS = 128_000

        private val _state = MutableStateFlow<BroadcastState>(BroadcastState.Idle)
        val state: StateFlow<BroadcastState> = _state.asStateFlow()

        fun start(context: Context, mode: BroadcastMode) {
            val intent = Intent(context, BroadcastCaptureService::class.java).apply {
                putExtra("mode", mode.name)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, BroadcastCaptureService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
