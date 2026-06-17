package com.sza.fastmediasorter.ui.browse.managers

import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.core.content.getSystemService
import androidx.fragment.app.FragmentActivity
import com.google.android.material.snackbar.Snackbar
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.data.transfer.local.LocalDestinationClassifier
import com.sza.fastmediasorter.data.transfer.local.LocalDestinationWriter
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.stats.CaptureKind
import com.sza.fastmediasorter.domain.stats.StatsEvent
import com.sza.fastmediasorter.domain.stats.StatsSink
import com.sza.fastmediasorter.util.CaptureDestinationPolicy
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
    // S0367: resolves the user-configured mic-recording destination resource id (String in
    // settings) to a MediaResource so a recording can be redirected away from the browsed resource.
    private val resourceRepository: ResourceRepository,
    private val coroutineScope: CoroutineScope,
    private val onFileSaved: (fileName: String) -> Unit,
    private val onRecordingStateChanged: (isRecording: Boolean) -> Unit,
    private val onUploadFile: suspend (tempFile: File, name: String, resource: MediaResource) -> Boolean,
    // S0464: route on-device saves through the MediaStore-aware writer so public-collection
    // targets (Downloads fallback) do not fail with EACCES on API 29+ scoped storage.
    private val destinationClassifier: LocalDestinationClassifier,
    private val destinationWriter: LocalDestinationWriter,
    // S0473: usage-statistics sink. Fire-and-forget; no-ops when collection is disabled.
    private val statsSink: StatsSink,
) {

    private var pendingTempFile: File? = null
    private var pendingResource: MediaResource? = null
    private var mediaRecorder: MediaRecorder? = null
    private var isRecorderStarted = false
    private var lastStopThrew = false
    private var audioFocusListener: AudioManager.OnAudioFocusChangeListener? = null

    fun startRecording(resource: MediaResource) {
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

        coroutineScope.launch {
            val settings = settingsRepository.getSettings().first()
            val defaultName = tempFile.name
            if (settings.micRecordingAskFilename) {
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

    private fun showNameDialog(tempFile: File, defaultName: String, resource: MediaResource) {
        val input = EditText(activity).apply { setText(defaultName); selectAll() }
        AlertDialog.Builder(activity)
            .setTitle(R.string.mic_recording_filename_title)
            .setView(input)
            .setPositiveButton(R.string.ok) { _, _ ->
                val name = input.text.toString().trim().ifBlank { defaultName }
                coroutineScope.launch { save(tempFile, withExt(name, "m4a"), resource) }
            }
            .setNegativeButton(R.string.cancel) { _, _ -> clearPendingSession(deleteTempFile = true) }
            .setOnCancelListener { clearPendingSession(deleteTempFile = true) }
            .show()
    }

    private suspend fun save(tempFile: File, name: String, resource: MediaResource) {
        // S0367: choose the recording's destination. A non-blank, resolvable, usable configured
        // destination overrides the browsed resource; otherwise the browsed resource is kept
        // (parity). When neither is usable, fall back to public Downloads.
        val targetResource = resolveMicSaveResource(resource)
        var success = false
        try {
            success = if (targetResource != null) {
                when (targetResource.type) {
                    ResourceType.LOCAL ->
                        writeToDevice(tempFile, File(targetResource.path, name).absolutePath)
                    ResourceType.SMB, ResourceType.SFTP, ResourceType.FTP,
                    ResourceType.CLOUD -> onUploadFile(tempFile, name, targetResource)
                }
            } else {
                // Context-less / unusable browsed resource and no configured destination:
                // CaptureDestinationPolicy resolves the public Downloads folder.
                val downloads = CaptureDestinationPolicy.resolveMicDestination(null)
                writeToDevice(tempFile, File(downloads, name).absolutePath)
            }
        } catch (e: Exception) {
            Timber.e(e, "save FAILED name=$name")
        } finally {
            clearPendingSession(deleteTempFile = true)
        }
        withContext(Dispatchers.Main) {
            if (success) {
                // S0473: one voice note captured.
                statsSink.record(StatsEvent.Capture(CaptureKind.VOICE))
                showSnackbar(activity.getString(R.string.mic_recording_saved, name))
                onFileSaved(name)
            } else {
                showSnackbar(R.string.mic_recording_error_save)
            }
        }
    }

    /**
     * S0464: write the finished recording to an on-device path through the MediaStore-aware
     * destination writer. A public collection (e.g. the Downloads fallback) is published via
     * MediaStore on API 29+ - the previous direct `File.copyTo` failed with EACCES under scoped
     * storage / restrictive OEM policy. Non-public paths still go through a plain file stream.
     */
    private suspend fun writeToDevice(tempFile: File, absolutePath: String): Boolean =
        withContext(Dispatchers.IO) {
            val category = destinationClassifier.classify(absolutePath)
            val sink = destinationWriter.open(category, overwrite = true).getOrElse { e ->
                Timber.e(e, "mic save: writer.open failed for %s", absolutePath)
                return@withContext false
            }
            try {
                tempFile.inputStream().use { input -> input.copyTo(sink.outputStream) }
                sink.commit().isSuccess
            } catch (e: Exception) {
                Timber.e(e, "mic save: streaming failed for %s", absolutePath)
                sink.abort()
                false
            }
        }

    /**
     * S0367: pick the resource a finished recording should be saved into, or null to fall back to
     * public Downloads.
     *
     * Non-breaking override semantics:
     * - A non-blank `micRecordingDestinationResourceId` that resolves to a usable target
     *   (`CaptureDestinationPolicy.isUsableTarget`) wins - the recording is redirected there.
     * - Otherwise the browsed [browsedResource] is kept when it is itself a usable target, so a
     *   normal in-folder recording saves exactly where it did before (LOCAL copy or network upload).
     * - Only when there is neither a usable configured destination nor a usable browsed resource is
     *   null returned, routing the save to Downloads instead of the previous guaranteed-failure
     *   copy into a virtual/read-only path.
     */
    private suspend fun resolveMicSaveResource(browsedResource: MediaResource): MediaResource? {
        val configuredId = settingsRepository.getSettings().first()
            .micRecordingDestinationResourceId
            ?.toLongOrNull()
        if (configuredId != null) {
            val configured = resourceRepository.getResourceById(configuredId)
            if (CaptureDestinationPolicy.isUsableTarget(configured)) {
                Timber.i(
                    "MicRecording: redirecting recording to configured destination id=%d name=%s",
                    configured!!.id,
                    configured.name,
                )
                return configured
            }
        }
        return if (CaptureDestinationPolicy.isUsableTarget(browsedResource)) browsedResource else null
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
