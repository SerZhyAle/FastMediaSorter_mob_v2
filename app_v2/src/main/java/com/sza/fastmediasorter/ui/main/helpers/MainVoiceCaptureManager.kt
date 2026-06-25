package com.sza.fastmediasorter.ui.main.helpers

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.fragment.app.FragmentActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.transfer.local.LocalDestinationClassifier
import com.sza.fastmediasorter.data.transfer.local.LocalDestinationWriter
import com.sza.fastmediasorter.domain.stats.CaptureKind
import com.sza.fastmediasorter.domain.stats.StatsEvent
import com.sza.fastmediasorter.domain.stats.StatsSink
import com.sza.fastmediasorter.util.CaptureDestinationPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * S0523: host-neutral quick voice capture launched from the main-screen overflow menu. Records a
 * microphone note (mirroring the proven [com.sza.fastmediasorter.ui.browse.managers.BrowseMicRecordingManager]
 * recorder + audio-focus + too-short-artifact guard) and writes it straight to the phone's public
 * recordings folder via the MediaStore-aware writer - never into a sorting resource. The start/stop
 * UX is a modal recording dialog (owner-confirmed); permission and the recorder are released on host
 * pause through [release].
 */
class MainVoiceCaptureManager(
    private val activity: FragmentActivity,
    private val coroutineScope: CoroutineScope,
    private val destinationClassifier: LocalDestinationClassifier,
    private val destinationWriter: LocalDestinationWriter,
    private val statsSink: StatsSink,
    // RECORD_AUDIO launcher is owned by the host Activity (must be registered before STARTED);
    // start() invokes this when permission is missing, and the host calls back into onRecordAudioResult.
    private val requestRecordAudioPermission: () -> Unit,
) {

    private var pendingTempFile: File? = null
    private var mediaRecorder: MediaRecorder? = null
    private var isRecorderStarted = false
    private var lastStopThrew = false
    private var audioFocusListener: AudioManager.OnAudioFocusChangeListener? = null

    private var recordingDialog: AlertDialog? = null
    private val timerHandler = Handler(Looper.getMainLooper())
    private var recordingStartedAtMs = 0L
    private val timerTick = object : Runnable {
        override fun run() {
            val dialog = recordingDialog ?: return
            val totalSec = ((SystemClock.elapsedRealtime() - recordingStartedAtMs) / 1000).toInt()
            dialog.setMessage(String.format(Locale.US, "%02d:%02d", totalSec / 60, totalSec % 60))
            timerHandler.postDelayed(this, 1000)
        }
    }

    fun start() {
        if (isRecorderStarted) return
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            actuallyStart()
        } else {
            requestRecordAudioPermission()
        }
    }

    /** Host RECORD_AUDIO launcher callback: start on grant, surface denial otherwise. */
    fun onRecordAudioResult(granted: Boolean) {
        if (granted) actuallyStart() else showSnackbar(R.string.mic_recording_permission_denied)
    }

    private fun actuallyStart() {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val tempFile = try {
            val dir = activity.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: activity.filesDir
            File(dir, "REC_$timestamp.m4a").also { it.createNewFile() }
        } catch (e: Exception) {
            Timber.e(e, "quick voice: failed to create temp file")
            showSnackbar(R.string.mic_recording_error_save)
            return
        }
        pendingTempFile = tempFile

        if (!requestAudioFocus()) {
            Timber.w("quick voice: audio focus not granted")
            tempFile.delete()
            pendingTempFile = null
            return
        }

        @Suppress("DEPRECATION")
        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(activity)
        } else {
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
            recorder.setOutputFile(tempFile.absolutePath)
            recorder.prepare()
            recorder.start()
            isRecorderStarted = true
            showRecordingDialog()
        } catch (e: Exception) {
            Timber.e(e, "quick voice: failed to prepare/start recorder")
            cancel()
            showSnackbar(R.string.mic_recording_error_save)
        }
    }

    fun stop() {
        dismissRecordingDialog()
        releaseRecorder()
        abandonAudioFocus()

        val tempFile = pendingTempFile ?: return
        // A thrown stop() or a near-empty artifact is the signature of a too-short hold / focus-loss
        // race - discard rather than save a truncated file.
        val invalid = lastStopThrew || tempFile.length() < MIN_VALID_RECORDING_BYTES
        if (invalid) {
            tempFile.delete()
            pendingTempFile = null
            showSnackbar(R.string.mic_recording_cancelled)
            return
        }
        coroutineScope.launch { save(tempFile, tempFile.name) }
    }

    fun cancel() {
        dismissRecordingDialog()
        releaseRecorder()
        abandonAudioFocus()
        pendingTempFile?.delete()
        pendingTempFile = null
    }

    /** Host onPause hook: never leave the mic open after the screen is backgrounded. */
    fun release() {
        if (isRecorderStarted || pendingTempFile != null || recordingDialog != null) {
            cancel()
        }
    }

    private suspend fun save(tempFile: File, name: String) {
        var success = false
        try {
            val dest = CaptureDestinationPolicy.resolveQuickVoiceDestination()
            success = writeToDevice(tempFile, File(dest, name).absolutePath)
        } catch (e: Exception) {
            Timber.e(e, "quick voice: save failed name=%s", name)
        } finally {
            tempFile.delete()
            pendingTempFile = null
        }
        withContext(Dispatchers.Main) {
            if (success) {
                statsSink.record(StatsEvent.Capture(CaptureKind.VOICE))
                showSnackbar(activity.getString(R.string.mic_recording_saved, name))
            } else {
                showSnackbar(R.string.mic_recording_error_save)
            }
        }
    }

    private suspend fun writeToDevice(tempFile: File, absolutePath: String): Boolean =
        withContext(Dispatchers.IO) {
            val category = destinationClassifier.classify(absolutePath)
            val sink = destinationWriter.open(category, overwrite = true).getOrElse { e ->
                Timber.e(e, "quick voice save: writer.open failed for %s", absolutePath)
                return@withContext false
            }
            try {
                tempFile.inputStream().use { input -> input.copyTo(sink.outputStream) }
                sink.commit().isSuccess
            } catch (e: Exception) {
                Timber.e(e, "quick voice save: streaming failed for %s", absolutePath)
                sink.abort()
                false
            }
        }

    private fun requestAudioFocus(): Boolean {
        val audioManager = activity.getSystemService<AudioManager>() ?: return false
        val listener = AudioManager.OnAudioFocusChangeListener { change ->
            if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                cancel()
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
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE,
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        val audioManager = activity.getSystemService<AudioManager>() ?: return
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

    private fun releaseRecorder() {
        lastStopThrew = false
        if (isRecorderStarted) {
            try {
                mediaRecorder?.stop()
            } catch (e: Exception) {
                // Expected for too-short holds or a focus-loss race: stop() throws and the temp file
                // is truncated. stop() classifies the outcome and skips the save path.
                lastStopThrew = true
                Timber.d(e, "quick voice: stop threw after start - too-short/focus-loss race")
            }
        }
        isRecorderStarted = false
        mediaRecorder?.release()
        mediaRecorder = null
    }

    private fun showRecordingDialog() {
        recordingStartedAtMs = SystemClock.elapsedRealtime()
        recordingDialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.quick_voice_recording_dialog_title)
            .setMessage("00:00")
            .setCancelable(false)
            .setPositiveButton(R.string.quick_voice_recording_stop) { _, _ -> stop() }
            .setNegativeButton(R.string.cancel) { _, _ -> cancel() }
            .show()
        timerHandler.post(timerTick)
    }

    private fun dismissRecordingDialog() {
        timerHandler.removeCallbacks(timerTick)
        recordingDialog?.dismiss()
        recordingDialog = null
    }

    private fun showSnackbar(msgRes: Int) {
        Snackbar.make(activity.window.decorView.rootView, msgRes, Snackbar.LENGTH_LONG).show()
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(activity.window.decorView.rootView, message, Snackbar.LENGTH_LONG).show()
    }

    private companion object {
        private const val MIN_VALID_RECORDING_BYTES = 1024L
    }
}
