package com.sza.fastmediasorter.ui.main.helpers

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.fragment.app.FragmentActivity
import com.google.android.material.snackbar.Snackbar
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.transfer.local.LocalDestinationClassifier
import com.sza.fastmediasorter.data.transfer.local.LocalDestinationWriter
import com.sza.fastmediasorter.domain.stats.CaptureKind
import com.sza.fastmediasorter.domain.stats.StatsEvent
import com.sza.fastmediasorter.domain.stats.StatsSink
import com.sza.fastmediasorter.ui.common.permissions.permissionRationale
import com.sza.fastmediasorter.util.CaptureDestinationPolicy
import com.sza.fastmediasorter.util.CaptureFileNamer
import com.sza.fastmediasorter.util.RecordingElapsedTimer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

/**
 * S0523: host-neutral quick voice capture launched from the main-screen overflow menu. Records a
 * microphone note (mirroring the proven [com.sza.fastmediasorter.ui.browse.managers.BrowseMicRecordingManager]
 * recorder + audio-focus + too-short-artifact guard) and writes it straight to the phone's public
 * recordings folder via the MediaStore-aware writer - never into a sorting resource. The start/stop UX
 * is the shared, non-modal [RecordingIndicatorOverlayManager] (timer + pause/resume + stop + discard) -
 * also used by [MainScreenRecordingManager] for a consistent UX between both programs-panel recording
 * scenarios (S0774 rework 2026-07-03, replacing a full-screen modal dialog). Permission and the recorder
 * are released on host pause through [release].
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
    private var isPaused = false
    private var lastStopThrew = false
    private var audioFocusListener: AudioManager.OnAudioFocusChangeListener? = null

    private val indicator = RecordingIndicatorOverlayManager(activity)
    private var indicatorShown = false

    // No foreground service backs this recording (Activity-scoped, released on host pause), so the
    // elapsed timer accumulates in-memory rather than recomputing from a shared singleton's instants
    // (contrast MainScreenRecordingManager, which must survive Activity recreation).
    private val recordingElapsedTimer = RecordingElapsedTimer { formatted -> indicator.updateTimer(formatted) }

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
        if (granted) {
            actuallyStart()
        } else {
            showSnackbar(activity.permissionRationale(Manifest.permission.RECORD_AUDIO))
        }
    }

    private fun actuallyStart() {
        val tempFile = try {
            val dir = activity.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: activity.filesDir
            val fileName = CaptureFileNamer.shared.allocate(CaptureFileNamer.CaptureKind.AUDIO, ".m4a")
            File(dir, fileName)
                .also { it.createNewFile() }
        } catch (e: Exception) {
            Timber.e(e, "quick voice: failed to create temp file")
            showSnackbar(R.string.mic_recording_error_save)
            return
        }
        pendingTempFile = tempFile

        if (!requestAudioFocus()) {
            Timber.w("quick voice: audio focus not granted")
            // S0896: audioFocusListener was set inside requestAudioFocus() before the grant result
            // was known - clear it now. Without this, release()'s guard never reaches cancel()
            // afterward (pendingTempFile is null and the indicator was never shown at this point),
            // leaking the dangling listener reference.
            abandonAudioFocus()
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
            isPaused = false
            showRecordingIndicator()
        } catch (e: Exception) {
            Timber.e(e, "quick voice: failed to prepare/start recorder")
            cancel()
            showSnackbar(R.string.mic_recording_error_save)
        }
    }

    fun stop() {
        dismissRecordingIndicator()
        releaseRecorder()
        abandonAudioFocus()

        val tempFile = pendingTempFile ?: return
        // S0861: clear the field here - the async save below owns 'tempFile' as a local from this
        // point on. Leaving pendingTempFile set let a backgrounding onPause -> release() -> cancel()
        // delete the file out from under the in-flight IO copy (silent loss of a just-saved
        // recording), and let a second recording's own pendingTempFile assignment be clobbered by
        // this save's old finally block once it completed.
        pendingTempFile = null
        // A thrown stop() or a near-empty artifact is the signature of a too-short hold / focus-loss
        // race - discard rather than save a truncated file.
        val invalid = lastStopThrew || tempFile.length() < MIN_VALID_RECORDING_BYTES
        if (invalid) {
            tempFile.delete()
            showSnackbar(R.string.mic_recording_cancelled)
            return
        }
        coroutineScope.launch { save(tempFile, tempFile.name) }
    }

    fun cancel() {
        dismissRecordingIndicator()
        releaseRecorder()
        abandonAudioFocus()
        pendingTempFile?.delete()
        pendingTempFile = null
    }

    /** Host onPause hook: never leave the mic open after the screen is backgrounded. */
    fun release() {
        if (isRecorderStarted || pendingTempFile != null || indicatorShown) {
            cancel()
        }
    }

    /** Wired to the indicator's pause/resume button; guarded so a stray call while idle is a no-op. */
    fun pause() {
        if (!isRecorderStarted || isPaused) return
        try {
            mediaRecorder?.pause()
        } catch (e: IllegalStateException) {
            Timber.e(e, "quick voice: recorder.pause() failed")
            return
        }
        isPaused = true
        recordingElapsedTimer.pause()
        applyPausedState()
    }

    fun resume() {
        if (!isRecorderStarted || !isPaused) return
        try {
            mediaRecorder?.resume()
        } catch (e: IllegalStateException) {
            Timber.e(e, "quick voice: recorder.resume() failed")
            return
        }
        isPaused = false
        recordingElapsedTimer.resume()
        applyPausedState()
    }

    private suspend fun save(tempFile: File, name: String) {
        var success = false
        try {
            val dest = CaptureDestinationPolicy.resolveQuickVoiceDestination()
            success = writeToDevice(tempFile, File(dest, name).absolutePath)
        } catch (e: Exception) {
            Timber.e(e, "quick voice: save failed name=%s", name)
        } finally {
            // S0861: pendingTempFile ownership was transferred out of the field in stop() before
            // this coroutine launched - do not touch it here, it may already belong to a newer
            // in-flight recording.
            tempFile.delete()
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
        isPaused = false
        mediaRecorder?.release()
        mediaRecorder = null
    }

    private fun showRecordingIndicator() {
        if (indicatorShown) return
        indicatorShown = true
        indicator.show(
            accessibleLabel = activity.getString(R.string.quick_voice_recording_dialog_title),
            stopCd = activity.getString(R.string.quick_voice_recording_stop),
            onPauseResume = { if (isPaused) resume() else pause() },
            onStop = { stop() },
            onCancel = { cancel() },
            cancelCd = activity.getString(R.string.cancel),
        )
        applyPausedState()
        recordingElapsedTimer.start()
    }

    private fun dismissRecordingIndicator() {
        if (!indicatorShown) return
        indicatorShown = false
        recordingElapsedTimer.stop()
        indicator.dismiss()
    }

    private fun applyPausedState() {
        indicator.setPaused(
            isPaused,
            pauseCd = activity.getString(R.string.recording_pause),
            resumeCd = activity.getString(R.string.recording_resume),
        )
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
