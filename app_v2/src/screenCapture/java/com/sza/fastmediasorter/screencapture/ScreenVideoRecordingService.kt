package com.sza.fastmediasorter.screencapture

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.notification.NotificationIcons
import com.sza.fastmediasorter.core.notification.NotificationIds
import com.sza.fastmediasorter.core.screencapture.ScreenRecordingStateController
import com.sza.fastmediasorter.data.capture.LocalCaptureDestinationWriter
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.util.CaptureDestinationPolicy
import dagger.Lazy
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * S0774: foreground service (type mediaProjection) that records the whole screen continuously to an
 * MP4 with microphone audio, via MediaProjection -> VirtualDisplay -> MediaRecorder (surface mode,
 * hardware H.264 + AAC). Recording continues while the app is backgrounded; it stops on the
 * notification Stop action, on [ScreenVideoRecordingController.requestStop], or when MediaProjection is
 * revoked. On stop the temp file is copied to the configured destination resource (empty -> Downloads)
 * and all capture resources are released immediately.
 */
@AndroidEntryPoint
class ScreenVideoRecordingService : Service() {

    @Inject
    lateinit var settingsRepository: Lazy<SettingsRepository>

    @Inject
    lateinit var resourceRepository: Lazy<ResourceRepository>

    @Inject
    lateinit var localCaptureDestinationWriter: Lazy<LocalCaptureDestinationWriter>

    @Inject
    lateinit var stateController: ScreenRecordingStateController

    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var mediaRecorder: MediaRecorder? = null
    private var pendingTempFile: File? = null
    private var isRecording = false
    private var isPaused = false
    private var isFinalizing = false

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            if (!isRecording) return
            Timber.w("ScreenVideoRecordingService: MediaProjection stopped externally")
            stopAndSave()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Every branch returns START_NOT_STICKY - a single exit point keeps this within detekt's
        // ReturnCount limit as pause/resume actions join the existing stop/start dispatch.
        when (intent?.action) {
            ACTION_STOP -> stopAndSave()
            ACTION_PAUSE -> pauseRecording()
            ACTION_RESUME -> resumeRecording()
            else -> if (!isRecording) startRecording(intent)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (isRecording) {
            // Process teardown mid-recording: stop the encoder and drop the partial file.
            runCatching { mediaRecorder?.stop() }
            isRecording = false
            pendingTempFile?.delete()
            pendingTempFile = null
        }
        isPaused = false
        releaseRecordingResources()
        stateController.markStopped()
        serviceScope.cancel()
        super.onDestroy()
    }

    // Guard-clause early returns for each consent/file/recorder setup failure - clearer than nesting
    // the whole method body under successive non-null checks.
    @Suppress("ReturnCount")
    private fun startRecording(intent: Intent?) {
        // Clear the finalize latch: a fresh recording must be stoppable even if it starts during/after a
        // prior recording's finalization (else stopAndSave's `if (isFinalizing) return` strands it).
        isFinalizing = false
        isPaused = false
        startForegroundCompat()

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
            ?: Activity.RESULT_CANCELED
        val resultData = intent?.readResultData()
        if (resultCode != Activity.RESULT_OK || resultData == null) {
            Timber.w("ScreenVideoRecordingService: missing MediaProjection consent extras")
            finishWithError()
            return
        }

        val projectionManager = getSystemService(MediaProjectionManager::class.java)
        val projection = projectionManager?.getMediaProjection(resultCode, resultData)
        if (projection == null) {
            Timber.w("ScreenVideoRecordingService: getMediaProjection returned null")
            finishWithError()
            return
        }
        mediaProjection = projection
        projection.registerCallback(projectionCallback, mainHandler)

        val spec = captureSpec()
        val tempFile = createTempFile()
        if (tempFile == null) {
            finishWithError()
            return
        }
        pendingTempFile = tempFile

        val recorder = buildRecorder(tempFile, spec)
        if (recorder == null) {
            finishWithError()
            return
        }
        mediaRecorder = recorder

        virtualDisplay = projection.createVirtualDisplay(
            VIRTUAL_DISPLAY_NAME,
            spec.width,
            spec.height,
            spec.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            recorder.surface,
            null,
            mainHandler
        )

        try {
            recorder.start()
            isRecording = true
            stateController.markStarted()
        } catch (e: IllegalStateException) {
            Timber.e(e, "ScreenVideoRecordingService: recorder.start() failed")
            finishWithError()
        }
    }

    // MediaRecorder.stop()'s too-short-recording failure is a bare, unqualified RuntimeException in
    // the platform implementation (no narrower documented subtype exists to catch instead).
    @Suppress("TooGenericExceptionCaught")
    private fun stopAndSave() {
        if (isFinalizing) return
        isFinalizing = true

        val tempFile = pendingTempFile
        var stopThrew = false
        if (isRecording) {
            try {
                mediaRecorder?.stop()
            } catch (e: RuntimeException) {
                // A too-short recording throws plain RuntimeException here (documented MediaRecorder
                // behavior, not IllegalStateException); an encoder error is IllegalStateException,
                // which RuntimeException also covers. Leaves a truncated file either way; classify as
                // invalid and discard rather than save a corrupt MP4.
                stopThrew = true
                Timber.w(e, "ScreenVideoRecordingService: recorder.stop() threw - discarding recording")
            }
        }
        isRecording = false
        isPaused = false
        releaseRecordingResources()
        stateController.markStopped()

        if (tempFile == null || stopThrew || tempFile.length() < MIN_VALID_BYTES) {
            tempFile?.delete()
            pendingTempFile = null
            toast(getString(R.string.screen_recording_error), Toast.LENGTH_LONG)
            finishService()
            return
        }
        serviceScope.launch { saveRecording(tempFile) }
    }

    private fun pauseRecording() {
        if (!isRecording || isPaused) return
        try {
            mediaRecorder?.pause()
        } catch (e: IllegalStateException) {
            // Wrong encoder state (e.g. already stopping) - leave the session running unpaused
            // rather than desync the UI from the actual recorder state.
            Timber.e(e, "ScreenVideoRecordingService: recorder.pause() failed")
            return
        }
        isPaused = true
        stateController.markPaused()
        refreshNotification(paused = true)
    }

    private fun resumeRecording() {
        if (!isRecording || !isPaused) return
        try {
            mediaRecorder?.resume()
        } catch (e: IllegalStateException) {
            Timber.e(e, "ScreenVideoRecordingService: recorder.resume() failed")
            return
        }
        isPaused = false
        stateController.markResumed()
        refreshNotification(paused = false)
    }

    /**
     * S1195: repaints the ongoing-recording notification after a pause/resume. POST_NOTIFICATIONS is
     * requested by the launcher before recording starts, but the user can revoke it mid-session; the
     * recording itself is unaffected, so a stale pause badge is the correct degradation rather than a
     * SecurityException killing a foreground service.
     */
    private fun refreshNotification(paused: Boolean) {
        try {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification(paused))
        } catch (e: SecurityException) {
            Timber.i(e, "ScreenVideoRecordingService: notification refresh skipped - POST_NOTIFICATIONS revoked")
        }
    }

    // Spans settings read + Room resource lookup + device write - each throws a different exception
    // family (Room/SQLite, Flow), so a single specific catch would miss a real failure mode. This is
    // the operation's own top-level boundary (background save, no caller to propagate to); it always
    // recovers to a "not saved" state with a correct-level log, so a broad catch here is intentional.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun saveRecording(tempFile: File) {
        Timber.d("S1354: screen recording local destination save")
        var savedName: String? = null
        try {
            val settings = settingsRepository.get().getSettings().first()
            val selected = withContext(Dispatchers.IO) {
                val id = settings.screenRecordingDestinationResourceId ?: return@withContext null
                resourceRepository.get().getAllResourcesSync().firstOrNull { it.id.toString() == id }
            }
            val destinationPath = selected
                ?.takeIf(CaptureDestinationPolicy::isUsableTarget)
                ?.path
                ?: CaptureDestinationPolicy.resolveScreenRecordingDestination(null).absolutePath
            if (localCaptureDestinationWriter.get().write(tempFile, destinationPath, tempFile.name).isSuccess) {
                savedName = tempFile.name
            }
        } catch (e: Exception) {
            Timber.e(e, "ScreenVideoRecordingService: save failed name=%s", tempFile.name)
        } finally {
            tempFile.delete()
            pendingTempFile = null
        }
        withContext(Dispatchers.Main) {
            if (savedName != null) {
                toast(getString(R.string.screen_recording_saved, savedName))
            } else {
                toast(getString(R.string.screen_recording_error), Toast.LENGTH_LONG)
            }
            finishService()
        }
    }

    private fun createTempFile(): File? = try {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val dir = getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: filesDir
        File(dir, "SCR_$timestamp.mp4").also { it.createNewFile() }
    } catch (e: IOException) {
        Timber.e(e, "ScreenVideoRecordingService: temp file creation failed")
        null
    }

    // The setters throw IllegalStateException, prepare() throws IOException as well - two unrelated
    // exception families with no narrower common supertype, so a broad catch is the precise option
    // here, not a shortcut.
    @Suppress("TooGenericExceptionCaught")
    private fun buildRecorder(output: File, spec: CaptureSpec): MediaRecorder? {
        @Suppress("DEPRECATION")
        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            MediaRecorder()
        }
        return try {
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setVideoSize(spec.width, spec.height)
            recorder.setVideoFrameRate(VIDEO_FRAME_RATE)
            recorder.setVideoEncodingBitRate(videoBitRate(spec.width, spec.height))
            recorder.setAudioChannels(1)
            recorder.setAudioSamplingRate(AUDIO_SAMPLE_RATE_HZ)
            recorder.setAudioEncodingBitRate(AUDIO_BIT_RATE)
            recorder.setOutputFile(output.absolutePath)
            recorder.prepare()
            recorder
        } catch (e: Exception) {
            Timber.e(e, "ScreenVideoRecordingService: failed to prepare recorder")
            recorder.release()
            null
        }
    }

    private fun releaseRecordingResources() {
        virtualDisplay?.release()
        virtualDisplay = null

        mediaRecorder?.let { recorder ->
            runCatching { recorder.reset() }
            recorder.release()
        }
        mediaRecorder = null

        mediaProjection?.unregisterCallback(projectionCallback)
        mediaProjection?.stop()
        mediaProjection = null
    }

    private fun captureSpec(): CaptureSpec {
        val metrics = resources.displayMetrics
        val windowManager = getSystemService(WindowManager::class.java)
        val bounds = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager?.maximumWindowMetrics?.bounds
        } else {
            null
        }
        val width = bounds?.width() ?: metrics.widthPixels
        val height = bounds?.height() ?: metrics.heightPixels
        // H.264 requires even dimensions; round down so the encoder accepts the surface size.
        return CaptureSpec(
            width = width - (width % 2),
            height = height - (height % 2),
            densityDpi = metrics.densityDpi
        )
    }

    private fun videoBitRate(width: Int, height: Int): Int {
        val raw = width.toDouble() * height.toDouble() * VIDEO_FRAME_RATE * BITRATE_MOTION_FACTOR
        return raw.toLong().coerceIn(MIN_BITRATE, MAX_BITRATE).toInt()
    }

    private fun finishService() {
        stopForegroundCompat()
        stopSelf()
    }

    private fun finishWithError() {
        releaseRecordingResources()
        pendingTempFile?.delete()
        pendingTempFile = null
        isRecording = false
        isPaused = false
        stateController.markStopped()
        toast(getString(R.string.screen_recording_error), Toast.LENGTH_LONG)
        finishService()
    }

    private fun startForegroundCompat() {
        createChannel()
        val notification = buildNotification(paused = false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    /** Rebuilt (not re-started) on every pause/resume toggle so the action label tracks live state. */
    private fun buildNotification(paused: Boolean): Notification {
        Timber.d("S1399: screen video-recording notification built with the branded status-bar icon")
        val stopPending = servicePendingIntent(ACTION_STOP, requestCode = 0)
        val pauseResumeAction = if (paused) {
            NotificationCompat.Action(
                0,
                getString(R.string.recording_resume),
                servicePendingIntent(ACTION_RESUME, requestCode = 1)
            )
        } else {
            NotificationCompat.Action(
                0,
                getString(R.string.recording_pause),
                servicePendingIntent(ACTION_PAUSE, requestCode = 2)
            )
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(NotificationIcons.STATUS_BAR)
            .setContentTitle(getString(R.string.screen_recording_notification_title))
            .setContentText(getString(R.string.screen_recording_notification_text))
            .setOngoing(true)
            .setSilent(true)
            .addAction(pauseResumeAction)
            .addAction(0, getString(R.string.screen_recording_notification_stop), stopPending)
            .build()
    }

    private fun servicePendingIntent(serviceAction: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, ScreenVideoRecordingService::class.java).apply { action = serviceAction }
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    // Guard-clause early returns (unsupported API level / manager unavailable / channel already
    // exists) - clearer than nesting the channel-creation body under successive checks.
    @Suppress("ReturnCount")
    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.screen_recording_notification_channel),
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)
    }

    private fun toast(message: String, duration: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(this, message, duration).show()
    }

    private fun Intent.readResultData(): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(EXTRA_RESULT_DATA)
        }

    private data class CaptureSpec(
        val width: Int,
        val height: Int,
        val densityDpi: Int
    )

    companion object {
        const val EXTRA_RESULT_CODE = "screen_recording_result_code"
        const val EXTRA_RESULT_DATA = "screen_recording_result_data"
        const val ACTION_STOP = "com.sza.fastmediasorter.screencapture.action.STOP_SCREEN_RECORDING"
        const val ACTION_PAUSE = "com.sza.fastmediasorter.screencapture.action.PAUSE_SCREEN_RECORDING"
        const val ACTION_RESUME = "com.sza.fastmediasorter.screencapture.action.RESUME_SCREEN_RECORDING"

        private const val CHANNEL_ID = "screen_recording_service"

        // S1292: was 0x4054, the same id OverlayHostService re-posts on every process foreground -
        // which replaced this recording notification (losing Pause/Stop) while recording continued.
        private const val NOTIFICATION_ID = NotificationIds.SCREEN_VIDEO_RECORDING
        private const val VIRTUAL_DISPLAY_NAME = "screen_video_recording_service"
        private const val VIDEO_FRAME_RATE = 30
        private const val BITRATE_MOTION_FACTOR = 0.15
        private const val MIN_BITRATE = 2_000_000L
        private const val MAX_BITRATE = 16_000_000L
        private const val MIN_VALID_BYTES = 1024L
        private const val AUDIO_SAMPLE_RATE_HZ = 44_100
        private const val AUDIO_BIT_RATE = 128_000

        fun start(context: Context, resultCode: Int, resultData: Intent) {
            val intent = Intent(context, ScreenVideoRecordingService::class.java).apply {
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, resultData)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, ScreenVideoRecordingService::class.java).apply { action = ACTION_STOP }
            ContextCompat.startForegroundService(context, intent)
        }

        fun pause(context: Context) {
            val intent = Intent(context, ScreenVideoRecordingService::class.java).apply { action = ACTION_PAUSE }
            ContextCompat.startForegroundService(context, intent)
        }

        fun resume(context: Context) {
            val intent = Intent(context, ScreenVideoRecordingService::class.java).apply { action = ACTION_RESUME }
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
