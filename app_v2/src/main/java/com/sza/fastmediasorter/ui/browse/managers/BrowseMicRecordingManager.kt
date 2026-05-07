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
import com.sza.fastmediasorter.domain.repository.SettingsRepository
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
    private val onFileSaved: (fileName: String) -> Unit,
    private val onRecordingStateChanged: (isRecording: Boolean) -> Unit,
    private val onUploadFile: suspend (tempFile: File, name: String, resource: MediaResource) -> Boolean,
) {

    private var pendingTempFile: File? = null
    private var pendingResource: MediaResource? = null
    private var mediaRecorder: MediaRecorder? = null
    private var audioFocusListener: AudioManager.OnAudioFocusChangeListener? = null

    fun startRecording(resource: MediaResource) {
        pendingResource = resource

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val tempFile = try {
            val dir = activity.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: activity.filesDir
            File(dir, "REC_$timestamp.m4a").also { it.createNewFile() }
        } catch (e: Exception) {
            Timber.e(e, "S0100: startRecording failed to create temp file")
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
            Timber.w("S0100: startRecording ABORT — audio focus not granted")
            tempFile.delete()
            pendingTempFile = null
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
            onRecordingStateChanged(true)
        } catch (e: Exception) {
            Timber.e(e, "S0100: startRecording failed to prepare/start recorder")
            cancelRecording()
        }
    }

    fun stopRecording() {
        releaseRecorder()
        abandonAudioFocus()
        onRecordingStateChanged(false)

        val tempFile = pendingTempFile ?: run {
            Timber.w("S0100: stopRecording — no pending temp file")
            return
        }
        val resource = pendingResource ?: run {
            Timber.w("S0100: stopRecording — no pending resource")
            tempFile.delete()
            pendingTempFile = null
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
        pendingTempFile?.delete()
        pendingTempFile = null
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
            .setNegativeButton(R.string.cancel) { _, _ -> tempFile.delete() }
            .setOnCancelListener { tempFile.delete() }
            .show()
    }

    private suspend fun save(tempFile: File, name: String, resource: MediaResource) {
        var success = false
        try {
            success = when (resource.type) {
                ResourceType.LOCAL -> withContext(Dispatchers.IO) {
                    tempFile.copyTo(File(resource.path, name), overwrite = true)
                    true
                }
                ResourceType.SMB, ResourceType.SFTP, ResourceType.FTP,
                ResourceType.CLOUD -> onUploadFile(tempFile, name, resource)
            }
        } catch (e: Exception) {
            Timber.e(e, "S0100: save FAILED name=$name")
        } finally {
            tempFile.delete()
            pendingTempFile = null
        }
        withContext(Dispatchers.Main) {
            if (success) {
                showSnackbar(activity.getString(R.string.mic_recording_saved, name))
                onFileSaved(name)
            } else {
                showSnackbar(R.string.mic_recording_error_save)
            }
        }
    }

    private fun releaseRecorder() {
        try {
            mediaRecorder?.stop()
        } catch (e: Exception) {
            Timber.w(e, "S0100: releaseRecorder stop threw (may be normal if not started)")
        }
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
}
