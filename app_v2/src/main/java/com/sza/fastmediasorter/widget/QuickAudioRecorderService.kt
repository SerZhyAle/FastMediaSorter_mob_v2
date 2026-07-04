package com.sza.fastmediasorter.widget

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.save.SaveFallbackNotifier
import com.sza.fastmediasorter.data.capture.MicRecordingSaver
import com.sza.fastmediasorter.data.transfer.strategies.LocalToFtpStrategy
import com.sza.fastmediasorter.data.transfer.strategies.LocalToSftpStrategy
import com.sza.fastmediasorter.data.transfer.strategies.LocalToSmbStrategy
import com.sza.fastmediasorter.data.transfer.strategy.CloudOperationStrategy
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.util.RecordingElapsedTimer
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * S0349 - microphone foreground service backing the Quick Audio Recorder widget.
 *
 * Owns a single [MediaRecorder] for the lifetime of one recording. Started/stopped via the
 * [start]/[stop] helpers (driven by [QuickAudioRecorderActivity]); pushes widget icon updates
 * through [QuickAudioRecorderWidgetProvider.updateAllWidgets] on each state transition.
 *
 * Recorder configuration mirrors the proven `BrowseMicRecordingManager` (MIC -> MPEG_4/AAC,
 * mono, 44.1 kHz, 128 kbps, `.m4a`). Files land in the app's external `Music/` directory
 * (no storage permission required; works down to API 23 - see S0349 §4.3).
 */
@AndroidEntryPoint
class QuickAudioRecorderService : Service() {

    // S0526: route the finished recording through the shared mic-save backend (selected destination,
    // network upload with local fallback) instead of leaving it in the app's private Music dir.
    @Inject lateinit var micRecordingSaver: MicRecordingSaver
    @Inject lateinit var saveFallbackNotifier: SaveFallbackNotifier
    @Inject lateinit var localToFtpStrategy: LocalToFtpStrategy
    @Inject lateinit var localToSmbStrategy: LocalToSmbStrategy
    @Inject lateinit var localToSftpStrategy: LocalToSftpStrategy
    @Inject lateinit var cloudOperationStrategy: CloudOperationStrategy
    // S0930: empty on flavors without the draw-over-apps permission - degrades to the existing
    // notification Stop action and the S0796 repeat-gesture toggle when no controller is bound.
    @Inject lateinit var indicatorControllers: Set<@JvmSuppressWildcards QuickRecorderIndicatorController>

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var mediaRecorder: MediaRecorder? = null
    private var recorderStarted = false
    private var outputFile: File? = null
    private var audioFocusListener: AudioManager.OnAudioFocusChangeListener? = null
    private var activeIndicator: QuickRecorderIndicatorController? = null
    private val elapsedTimer = RecordingElapsedTimer { formatted -> activeIndicator?.updateElapsed(formatted) }

    // S0858: the finished clip's save (network upload with local fallback) runs async while the
    // service stays foreground - guards the reentrancy window where a widget tap could start a
    // second recorder, or a duplicate Stop tap could cancel the in-flight save via onDestroy.
    private var isSaving = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopAndSave()
            else -> handleStart()
        }
        return START_NOT_STICKY
    }

    private fun handleStart() {
        // S0858: also block while a previous clip's save is still in flight - starting a new
        // recorder here would orphan it when the stale save's stopSelf() later tears the service down.
        if (isRecording || isSaving) return

        createChannel()
        startForegroundCompat()

        if (!requestFocus()) {
            Timber.w("QuickAudioRecorder: audio focus not granted - aborting")
            failAndStop()
            return
        }

        val dir = (getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: filesDir).apply { mkdirs() }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dir, "REC_$timestamp.m4a")
        outputFile = file

        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        mediaRecorder = recorder

        try {
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioChannels(1)
            recorder.setAudioSamplingRate(44100)
            recorder.setAudioEncodingBitRate(128_000)
            recorder.setOutputFile(file.absolutePath)
            recorder.prepare()
            recorder.start()
            recorderStarted = true
            isRecording = true
            QuickAudioRecorderWidgetProvider.updateAllWidgets(this, true)
            val controller = indicatorControllers.firstOrNull { it.isAvailable(this) }
            if (controller != null) {
                Timber.d("S0930: showing floating stop indicator for quick audio recording")
                controller.show(this) { stopAndSave() }
                activeIndicator = controller
                elapsedTimer.start()
            }
        } catch (e: Exception) {
            Timber.e(e, "QuickAudioRecorder: failed to prepare/start recorder")
            failAndStop()
        }
    }

    private fun stopAndSave() {
        // S0858: a duplicate Stop tap while the previous clip is still saving must be a no-op -
        // recorderStarted is already false at this point, so the old code fell into the "nothing
        // captured" branch and called stopSelf(), whose onDestroy() -> serviceScope.cancel()
        // cancelled the in-flight save and dropped the captured clip. The original save's own
        // completion still tears the service down once it finishes.
        if (isSaving) return

        val file = outputFile
        var captured = false
        if (recorderStarted) {
            try {
                mediaRecorder?.stop()
                captured = file != null && file.exists() && file.length() > 0
            } catch (e: Exception) {
                // stop() throws when stopped before any frame is captured - the clip is unusable.
                Timber.w(e, "QuickAudioRecorder: stop() threw - discarding empty recording")
                file?.delete()
            }
        }
        releaseRecorder()
        abandonFocus()
        isRecording = false
        QuickAudioRecorderWidgetProvider.updateAllWidgets(this, false)
        elapsedTimer.stop()
        activeIndicator?.hide()
        activeIndicator = null

        if (captured && file != null) {
            // S0526: hand the finished clip to the shared saver. Stay in the foreground until the
            // suspend save (which may upload over the network) completes, then report and stop.
            // S0858: isSaving gates handleStart()/stopAndSave() reentrancy for the whole window.
            isSaving = true
            serviceScope.launch {
                try {
                    val result = micRecordingSaver.save(
                        tempFile = file,
                        name = file.name,
                        browsedResource = null,
                        upload = { tempFile, name, resource -> uploadToResource(tempFile, name, resource) },
                    )
                    file.delete()
                    if (result.success) {
                        val location = result.savedPath ?: result.resourceName ?: file.name
                        toast(getString(R.string.quick_recorder_saved_to, location))
                        result.fallbackReason?.let { reason ->
                            saveFallbackNotifier.notify(
                                reason = reason,
                                folderLabel = result.folderLabel.orEmpty(),
                                resourceName = result.resourceName.orEmpty(),
                                background = true,
                            )
                        }
                    } else {
                        toast(getString(R.string.quick_recorder_error))
                    }
                } finally {
                    isSaving = false
                    // Only this save's own file - a belt-and-braces guard against clobbering a
                    // newer session's outputFile even though isSaving already blocks that path.
                    if (outputFile === file) outputFile = null
                    stopForegroundCompat()
                    stopSelf()
                }
            }
        } else {
            toast(getString(R.string.quick_recorder_error))
            outputFile = null
            stopForegroundCompat()
            stopSelf()
        }
    }

    /** Mirrors the Browse mic upload routing: copy the clip to a network/cloud resource. */
    private suspend fun uploadToResource(tempFile: File, name: String, resource: MediaResource): Boolean {
        val sourceUri = Uri.fromFile(tempFile)
        val destUri = Uri.parse(resource.path.trimEnd('/') + '/' + Uri.encode(name))
        return when (resource.type) {
            ResourceType.FTP -> localToFtpStrategy.copy(sourceUri, destUri, true, null, null)
            ResourceType.SMB -> localToSmbStrategy.copy(sourceUri, destUri, true, null, null)
            ResourceType.SFTP -> localToSftpStrategy.copy(sourceUri, destUri, true, null, null)
            ResourceType.CLOUD -> cloudOperationStrategy.copyFile(
                tempFile.absolutePath,
                resource.path.trimEnd('/') + '/' + name,
                true,
                null,
            ).isSuccess
            else -> false
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        // S0858: last-resort teardown - if a recorder session is still live here (system-initiated
        // stop, or any path that bypassed stopAndSave()/failAndStop()), release the mic and abandon
        // focus rather than leaving both held past service teardown.
        if (mediaRecorder != null || recorderStarted) {
            releaseRecorder()
            abandonFocus()
            isRecording = false
            elapsedTimer.stop()
            activeIndicator?.hide()
            activeIndicator = null
        }
        super.onDestroy()
    }

    private fun failAndStop() {
        releaseRecorder()
        abandonFocus()
        outputFile?.delete()
        outputFile = null
        isRecording = false
        QuickAudioRecorderWidgetProvider.updateAllWidgets(this, false)
        elapsedTimer.stop()
        activeIndicator?.hide()
        activeIndicator = null
        toast(getString(R.string.quick_recorder_error))
        stopForegroundCompat()
        stopSelf()
    }

    private fun releaseRecorder() {
        recorderStarted = false
        try {
            mediaRecorder?.reset()
            mediaRecorder?.release()
        } catch (e: Exception) {
            Timber.w(e, "QuickAudioRecorder: recorder release failed")
        }
        mediaRecorder = null
    }

    private fun requestFocus(): Boolean {
        val audioManager = getSystemService<AudioManager>() ?: return false
        val listener = AudioManager.OnAudioFocusChangeListener { change ->
            if (change == AudioManager.AUDIOFOCUS_LOSS ||
                change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
            ) {
                // Something took the mic - keep what was captured so far.
                stopAndSave()
            }
        }
        audioFocusListener = listener
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setOnAudioFocusChangeListener(listener)
                .build()
            audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                listener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonFocus() {
        val audioManager = getSystemService<AudioManager>() ?: return
        val listener = audioFocusListener ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setOnAudioFocusChangeListener(listener)
                .build()
            audioManager.abandonAudioFocusRequest(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(listener)
        }
        audioFocusListener = null
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService<NotificationManager>() ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.quick_recorder_notification_channel),
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)
    }

    private fun startForegroundCompat() {
        val stopIntent = Intent(this, QuickAudioRecorderService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_widget_quick_audio_recorder_idle)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.quick_recorder_notification_recording))
            .setOngoing(true)
            .setSilent(true)
            .addAction(
                0,
                getString(R.string.quick_recorder_action_stop),
                stopPending
            )
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    companion object {
        /** Read by the widget provider and the trampoline to decide start vs stop. */
        @Volatile
        var isRecording: Boolean = false
            private set

        const val ACTION_START = "com.sza.fastmediasorter.action.QUICK_RECORDER_START"
        const val ACTION_STOP = "com.sza.fastmediasorter.action.QUICK_RECORDER_STOP"

        private const val CHANNEL_ID = "quick_audio_recorder"
        private const val NOTIFICATION_ID = 0xA349

        fun start(context: Context) {
            val intent = Intent(context, QuickAudioRecorderService::class.java).apply {
                action = ACTION_START
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, QuickAudioRecorderService::class.java).apply {
                action = ACTION_STOP
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
