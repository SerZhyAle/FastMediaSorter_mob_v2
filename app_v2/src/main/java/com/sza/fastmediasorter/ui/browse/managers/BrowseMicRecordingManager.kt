package com.sza.fastmediasorter.ui.browse.managers

import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.widget.EditText
import androidx.core.content.getSystemService
import androidx.fragment.app.FragmentActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.util.showBoundToHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BrowseMicRecordingManager(
    private val activity: FragmentActivity,
    private val settingsRepository: SettingsRepository,
    private val coroutineScope: CoroutineScope,
    // S0901: application-lifetime scope for the save pipeline. Activity teardown right after stop must
    // not cancel micRecordingSaver.save (which would drop the recording and orphan the temp file).
    private val appScope: CoroutineScope,
    private val onFileSaved: (fileName: String) -> Unit,
    private val onRecordingStateChanged: (isRecording: Boolean) -> Unit,
    private val onUploadFile: suspend (tempFile: File, name: String, resource: MediaResource) -> Boolean,
    // S0526: shared mic-save backend (destination resolution + write/upload + S0522 fallback).
    private val micRecordingSaver: com.sza.fastmediasorter.data.capture.MicRecordingSaver,
    // S0522: user notification when a recording is redirected to a local default folder because the
    // configured network destination is unavailable.
    private val saveFallbackNotifier: com.sza.fastmediasorter.core.save.SaveFallbackNotifier,
) {

    private var pendingTempFile: File? = null
    private var pendingResource: MediaResource? = null
    private var mediaRecorder: MediaRecorder? = null
    private var isRecorderStarted = false
    private var lastStopThrew = false
    private var audioFocusListener: AudioManager.OnAudioFocusChangeListener? = null

    fun startRecording(resource: MediaResource) {
        // S0861: startRecording() had no reentrancy guard - a second call (e.g. the RECORD_AUDIO
        // grant callback auto-starting while a prior held-with-no-finger session is still live)
        // would overwrite mediaRecorder/pendingTempFile/audioFocusListener, orphaning the first
        // recorder and permanently leaking its exclusive audio focus. Discard any live session first.
        if (isRecorderStarted || mediaRecorder != null || pendingTempFile != null) {
            Timber.w("startRecording - a session is already active, cancelling it first")
            cancelRecording()
        }
        pendingResource = resource

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val tempFile = try {
            val dir = activity.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: activity.filesDir
            File(dir, "REC_$timestamp.m4a").also { it.createNewFile() }
        } catch (e: Exception) {
            Timber.e(e, "startRecording failed to create temp file")
            pendingResource = null
            onRecordingStateChanged(false)
            return
        }
        pendingTempFile = tempFile

        val audioManager = activity.getSystemService<AudioManager>()!!
        val focusGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val listener = AudioManager.OnAudioFocusChangeListener { change ->
                if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                    cancelRecording()
                }
            }
            audioFocusListener = listener
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setOnAudioFocusChangeListener(listener)
                .build()
            audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            val listener = AudioManager.OnAudioFocusChangeListener { change ->
                if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                    cancelRecording()
                }
            }
            audioFocusListener = listener
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                listener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }

        if (!focusGranted) {
            Timber.w("startRecording ABORT - audio focus not granted")
            // S0896: audioFocusListener was set above before the grant result was known - clear it
            // now. Without this, stopRecording()'s pending-file guard returns before ever reaching
            // abandonAudioFocus(), leaking the dangling listener reference.
            abandonAudioFocus()
            tempFile.delete()
            pendingTempFile = null
            pendingResource = null
            onRecordingStateChanged(false)
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
            onRecordingStateChanged(true)
        } catch (e: Exception) {
            Timber.e(e, "startRecording failed to prepare/start recorder")
            cancelRecording()
        }
    }

    fun stopRecording() {
        if (pendingTempFile == null && pendingResource == null) {
            return
        }
        releaseRecorder()
        abandonAudioFocus()
        onRecordingStateChanged(false)

        val tempFile = pendingTempFile ?: run {
            Timber.w("stopRecording - no pending temp file")
            return
        }
        val resource = pendingResource ?: run {
            Timber.w("stopRecording - no pending resource")
            tempFile.delete()
            pendingTempFile = null
            return
        }

        // Classify the session BEFORE the save path. A thrown stop() or a near-empty artifact
        // is the signature of a too-short hold / focus-loss race - never copy/upload it.
        val invalid = lastStopThrew || tempFile.length() < MIN_VALID_RECORDING_BYTES
        if (invalid) {
            clearPendingSession(deleteTempFile = true)
            showSnackbar(R.string.mic_recording_cancelled)
            return
        }

        appScope.launch {
            val settings = settingsRepository.getSettings().first()
            val defaultName = tempFile.name
            // Only ask for a filename while the activity is alive; if it is gone, save with the default
            // name rather than lose the recording. The save itself runs on appScope so it always finishes.
            if (settings.micRecordingAskFilename && !activity.isFinishing && !activity.isDestroyed) {
                withContext(Dispatchers.Main) { showNameDialog(tempFile, defaultName, resource) }
            } else {
                save(tempFile, defaultName, resource)
            }
        }
    }

    fun cancelRecording() {
        releaseRecorder()
        abandonAudioFocus()
        clearPendingSession(deleteTempFile = true)
        onRecordingStateChanged(false)
    }

    /**
     * S0861: host teardown hook (onPause/onStop/onDestroy) - none of BrowseActivity's real
     * lifecycle edges touched this manager before, so a MediaRecorder + exclusive audio focus
     * started with no finger down (permission-grant auto-start) could survive activity destroy.
     * Discards rather than saves, matching [com.sza.fastmediasorter.ui.main.helpers.MainVoiceCaptureManager.release].
     */
    fun release() {
        if (isRecorderStarted || mediaRecorder != null || pendingTempFile != null) {
            cancelRecording()
        }
    }

    private fun showNameDialog(tempFile: File, defaultName: String, resource: MediaResource) {
        val input = EditText(activity).apply { setText(defaultName); selectAll() }
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.mic_recording_filename_title)
            .setView(input)
            .setPositiveButton(R.string.ok) { _, _ ->
                val name = input.text.toString().trim().ifBlank { defaultName }
                appScope.launch { save(tempFile, withExt(name, "m4a"), resource) }
            }
            .setNegativeButton(R.string.cancel) { _, _ -> clearPendingSession(deleteTempFile = true) }
            .setOnCancelListener { clearPendingSession(deleteTempFile = true) }
            .showBoundToHost(activity)
    }

    private suspend fun save(tempFile: File, name: String, resource: MediaResource) {
        // S0526: delegate destination resolution, write/upload and the S0522 fallback to the shared
        // mic-save backend; this manager keeps only the recorder lifecycle and the user-facing notice.
        val result = micRecordingSaver.save(
            tempFile = tempFile,
            name = name,
            browsedResource = resource,
            upload = onUploadFile,
        )
        clearPendingSession(deleteTempFile = true)
        withContext(Dispatchers.Main) {
            // The activity may have been torn down while the save ran on appScope - skip UI feedback then.
            if (activity.isDestroyed) return@withContext
            if (result.success) {
                showSnackbar(activity.getString(R.string.mic_recording_saved, name))
                result.fallbackReason?.let { reason ->
                    saveFallbackNotifier.notify(
                        reason = reason,
                        folderLabel = result.folderLabel.orEmpty(),
                        resourceName = result.resourceName.orEmpty(),
                        background = false,
                    )
                }
                onFileSaved(name)
            } else {
                showSnackbar(R.string.mic_recording_error_save)
            }
        }
    }

    private fun releaseRecorder() {
        // Reset per-release: stopRecording() reads this to decide if the artifact is usable.
        lastStopThrew = false
        if (isRecorderStarted) {
            try {
                mediaRecorder?.stop()
            } catch (e: Exception) {
                // Expected for too-short holds or an audio-focus-loss race: MediaRecorder.stop()
                // throws "stop failed" and the temp file is zero-byte/truncated. Not an error -
                // stopRecording() classifies the outcome and skips the save path.
                lastStopThrew = true
                Timber.d(e, "releaseRecorder: stop threw after start - too-short/focus-loss race")
            }
        }
        isRecorderStarted = false
        mediaRecorder?.release()
        mediaRecorder = null
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

    private fun clearPendingSession(deleteTempFile: Boolean) {
        if (deleteTempFile) {
            pendingTempFile?.delete()
        }
        pendingTempFile = null
        pendingResource = null
    }

    private fun showSnackbar(msgRes: Int) {
        Snackbar.make(activity.window.decorView.rootView, msgRes, Snackbar.LENGTH_LONG).show()
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(activity.window.decorView.rootView, message, Snackbar.LENGTH_LONG).show()
    }

    private fun withExt(name: String, ext: String): String {
        val dotExt = if (ext.startsWith(".")) ext else ".$ext"
        return if (name.endsWith(dotExt, ignoreCase = true)) name else "$name$dotExt"
    }

    private companion object {
        // A valid AAC/m4a recording is always larger than this; a zero/near-zero file is the
        // failure signature of a too-short hold or a stop() that threw on a focus-loss race.
        private const val MIN_VALID_RECORDING_BYTES = 1024L
    }
}
