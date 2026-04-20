package com.sza.fastmediasorter.vr.capture

import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.vr.VrPlayerActivity
import com.sza.fastmediasorter.vr.openxr.OpenXrSessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private const val SNAPSHOT_SUBDIRECTORY = "FastMediaSorter_VR"
private const val SNAPSHOT_TIMEOUT_MS = 1_500L
private const val SNAPSHOT_POLL_INTERVAL_MS = 16L

/**
 * Captures a one-shot SBS PNG from the live OpenXR eye swapchains.
 *
 * The native bridge performs glReadPixels on the render thread, while this manager stays on the
 * app side for bitmap composition, MediaStore writes, and the existing toast/snackbar UX.
 */
class VrStereoSnapshotManager(
    private val activity: VrPlayerActivity,
    private val sessionProvider: () -> OpenXrSessionManager?,
) {

    fun captureCurrentFrame() {
        val sessionManager = sessionProvider()
        if (sessionManager == null || !sessionManager.requestStereoSnapshot()) {
            Toast.makeText(activity, R.string.vr_save_frame_unavailable, Toast.LENGTH_SHORT).show()
            return
        }

        activity.lifecycleScope.launch {
            val snapshot = waitForSnapshot(sessionManager)
            if (snapshot == null) {
                Toast.makeText(activity, R.string.vr_save_frame_timeout, Toast.LENGTH_SHORT).show()
                return@launch
            }

            try {
                val fileName = VrStereoSnapshotFileNameBuilder.build(
                    mediaFile = activity.viewModel.state.value.currentFile,
                    capturedAt = LocalDateTime.now(),
                )
                val bitmap = composeSbsBitmap(snapshot)
                val savedUri = saveBitmap(bitmap, fileName)
                bitmap.recycle()

                val message = activity.getString(R.string.vr_save_frame_saved_to_pictures)
                Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
                Snackbar.make(activity.activityBinding.root, message, Snackbar.LENGTH_LONG)
                    .setAction(activity.getString(R.string.action_open)) {
                        openSavedSnapshot(savedUri)
                    }
                    .show()
            } catch (t: Throwable) {
                Timber.e(t, "VrStereoSnapshotManager: failed to save stereo snapshot")
                Toast.makeText(activity, R.string.save_frame_error, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun waitForSnapshot(sessionManager: OpenXrSessionManager): OpenXrSessionManager.StereoSnapshotPixels? {
        val deadline = System.currentTimeMillis() + SNAPSHOT_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            sessionManager.pollStereoSnapshot()?.let { return it }
            delay(SNAPSHOT_POLL_INTERVAL_MS)
        }
        return null
    }

    private suspend fun composeSbsBitmap(
        snapshot: OpenXrSessionManager.StereoSnapshotPixels,
    ): Bitmap = withContext(Dispatchers.Default) {
        val output = Bitmap.createBitmap(
            snapshot.width * 2,
            snapshot.height,
            Bitmap.Config.ARGB_8888,
        )
        output.setPixels(snapshot.leftEyePixels, 0, snapshot.width, 0, 0, snapshot.width, snapshot.height)
        output.setPixels(snapshot.rightEyePixels, 0, snapshot.width, snapshot.width, 0, snapshot.width, snapshot.height)
        output
    }

    private suspend fun saveBitmap(bitmap: Bitmap, fileName: String): Uri = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$SNAPSHOT_SUBDIRECTORY")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = activity.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("MediaStore insert returned null")
            resolver.openOutputStream(uri)?.use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    "PNG compression failed"
                }
            } ?: error("Could not open output stream for $uri")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return@withContext uri
        }

        @Suppress("DEPRECATION")
        val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val outputDir = File(picturesDir, SNAPSHOT_SUBDIRECTORY).apply { mkdirs() }
        val outputFile = File(outputDir, fileName)
        FileOutputStream(outputFile).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                "PNG compression failed"
            }
        }
        MediaScannerConnection.scanFile(activity, arrayOf(outputFile.absolutePath), arrayOf("image/png"), null)
        FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", outputFile)
    }

    private fun openSavedSnapshot(uri: Uri) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "image/png")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            activity.startActivity(Intent.createChooser(intent, activity.getString(R.string.action_open)))
        } catch (e: ActivityNotFoundException) {
            Timber.w(e, "VrStereoSnapshotManager: no app can open saved snapshot")
            Toast.makeText(activity, R.string.vr_save_frame_open_failed, Toast.LENGTH_SHORT).show()
        }
    }
}

internal object VrStereoSnapshotFileNameBuilder {
    private val timestampFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")

    fun build(mediaFile: MediaFile?, capturedAt: LocalDateTime): String {
        val baseName = mediaFile?.name
            ?.substringBeforeLast('.')
            ?.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            ?.trim('_')
            ?.take(48)
            ?.ifBlank { "vr_snapshot" }
            ?: "vr_snapshot"
        return "${baseName}_${capturedAt.format(timestampFormatter)}_SBS.png"
    }
}