package com.sza.fastmediasorter.ui.settings.ticket.helpers

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import com.sza.fastmediasorter.util.getPackageInfoCompat
import timber.log.Timber
import java.io.File
import java.io.IOException

/**
 * S4554: records the ticket's voice note as AAC in MPEG-4 (`.m4a`, role `voice`) into the cache.
 *
 * The file is copied into the outbox when the ticket is sent, so the cache copy is disposable;
 * [release] discards an unfinished recording when the dialog goes away.
 */
class TicketVoiceRecordManager(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var output: File? = null

    val isRecording: Boolean get() = recorder != null

    /** Whether this build declares the microphone permission at all - `photos` does not. */
    fun isAvailable(): Boolean = runCatching {
        context.packageManager.getPackageInfoCompat(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions.orEmpty().contains(Manifest.permission.RECORD_AUDIO)
    }.getOrDefault(false)

    /** Start a new recording; false when the recorder could not start. */
    fun start(): Boolean {
        val dir = File(context.cacheDir, CACHE_DIR).apply { mkdirs() }
        val file = File(dir, "voice-note-${System.currentTimeMillis()}.m4a")
        val created = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else legacyRecorder()
        return try {
            created.setAudioSource(MediaRecorder.AudioSource.MIC)
            created.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            created.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            created.setOutputFile(file.absolutePath)
            created.prepare()
            created.start()
            recorder = created
            output = file
            true
        } catch (error: IOException) {
            Timber.w(error, "Ticket voice note could not start")
            created.release()
            false
        } catch (error: IllegalStateException) {
            Timber.w(error, "Ticket voice note could not start")
            created.release()
            false
        }
    }

    /** Stop and return the finished file, or null when nothing usable was recorded. */
    fun stop(): File? {
        val active = recorder ?: return null
        val file = output
        recorder = null
        output = null
        val finished = runCatching { active.stop() }
            .onFailure { Timber.w(it, "Ticket voice note stopped before any audio") }
            .isSuccess
        active.release()
        val usable = file?.takeIf { finished && it.length() > 0L }
        if (usable == null) file?.delete()
        return usable
    }

    fun release() {
        stop()?.delete()
    }

    @Suppress("DEPRECATION")
    private fun legacyRecorder(): MediaRecorder = MediaRecorder()

    private companion object {
        const val CACHE_DIR = "ticket_voice"
    }
}
