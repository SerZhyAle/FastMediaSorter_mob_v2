package com.sza.fastmediasorter.ui.player.helpers

import android.content.ContentUris
import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.usecase.FileOperation
import com.sza.fastmediasorter.domain.usecase.FileOperationResult
import com.sza.fastmediasorter.domain.usecase.FileOperationUseCase
import com.sza.fastmediasorter.ui.player.PlayerActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

private const val SNAPSHOT_DIR = "snapshots"
private const val PNG_QUALITY = 100
private const val JPEG_QUALITY = 85  // standard quality for JPEG frame saves

/**
 * Handles "Save Frame" in the video player:
 * 1. Captures the current frame from the TextureView inside PlayerView.
 * 2. Writes a PNG or JPEG to a temp file in cacheDir/snapshots/.
 * 3. Copies the file to the configured destination resource via [FileOperationUseCase],
 *    which supports ALL resource types: Local, SMB, SFTP, FTP, Google Drive, Dropbox, OneDrive.
 * 4. Falls back to MediaStore Downloads if no destination is configured or copy fails.
 * 5. Shows a Toast with the actual save location or an error.
 *
 * Format: PNG (lossless) or JPG (85% quality) — controlled by videoSnapshotFormat setting.
 * Filename: {video_name}_{HH}h{MM}m{SS}s.{ext} — based on current video track position.
 * Overwrite: existing files with the same name are always replaced.
 */
class SaveVideoFrameManager(
    private val activity: PlayerActivity,
    private val fileOperationUseCase: FileOperationUseCase
) {

    /**
     * Entry point: capture the current video frame and save it to the configured destination.
     * Must be called from the main thread (UI coroutine scope).
     */
    fun saveCurrentFrame() {
        // Capture bitmap synchronously on main thread (TextureView requires it)
        val bitmap = captureFrame()
        if (bitmap == null) {
            showToast(activity.getString(R.string.save_frame_no_video), Toast.LENGTH_LONG)
            return
        }

        // Capture playback position on main thread before launching coroutine —
        // player.currentPosition is only safe to read on main thread
        val positionMs = activity._videoPlayerManager?.getPlayer()?.currentPosition ?: 0L
        val sourceFileName = activity.viewModel.state.value.currentFile?.name

        activity.lifecycleScope.launch {
            try {
                // Load settings in coroutine (suspends on IO, avoids blocking UI thread)
                val settings = activity.settingsRepository.getSettings().first()
                val useJpeg = settings.videoSnapshotFormat == "JPG"
                val fileName = buildFileName(sourceFileName, positionMs, useJpeg)
                val tempFile = writeTempFile(bitmap, fileName, useJpeg)

                val resourceId = settings.videoSnapshotResourceId

                val savedLocation = if (resourceId != null) {
                    // Try to save to configured destination resource
                    val destinations = activity.viewModel.getDestinationsUseCase.invoke().first()
                    val resource = destinations.find { it.id == resourceId }
                    if (resource != null) {
                        trySaveToResource(tempFile, resource, fileName)
                    } else {
                        // Configured resource no longer exists — fall back to Downloads
                        Timber.w("SaveVideoFrameManager: configured resource id=$resourceId not found, falling back to Downloads")
                        null
                    }
                } else null

                val finalMessage = if (savedLocation != null) {
                    activity.getString(R.string.save_frame_saved_to_resource, savedLocation)
                } else {
                    // Fallback: save to MediaStore Downloads
                    saveToDownloads(tempFile, fileName, useJpeg)
                    activity.getString(R.string.save_frame_saved_to_downloads)
                }

                tempFile.delete()
                showToast(finalMessage)

            } catch (t: Throwable) {
                if (t is OutOfMemoryError) {
                    Timber.e(t, "SaveVideoFrameManager: OutOfMemoryError during save")
                    showToast(activity.getString(R.string.save_frame_error), Toast.LENGTH_LONG)
                } else {
                    Timber.e(t, "SaveVideoFrameManager: unexpected error saving frame")
                    showToast(activity.getString(R.string.save_frame_error), Toast.LENGTH_LONG)
                }
            }
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Finds the TextureView inside the given PlayerView hierarchy and calls getBitmap().
     * Must be called on the main thread.
     * Returns null if no TextureView is found or the Surface is not yet available.
     */
    private fun captureFrame(): Bitmap? {
        val playerView = activity.activityBinding.playerView
        val textureView = findTextureView(playerView) ?: run {
            Timber.w("SaveVideoFrameManager: TextureView not found in PlayerView hierarchy")
            return null
        }
        return try {
            textureView.getBitmap()
        } catch (t: Throwable) {
            Timber.e(t, "SaveVideoFrameManager: getBitmap() failed")
            if (t is OutOfMemoryError) {
                showToast(activity.getString(R.string.save_frame_error), Toast.LENGTH_LONG)
            }
            null
        }
    }

    /** Recursively searches the view hierarchy for a TextureView. */
    private fun findTextureView(view: View): TextureView? {
        if (view is TextureView) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val found = findTextureView(view.getChildAt(i))
                if (found != null) return found
            }
        }
        return null
    }

    /** Saves the bitmap to a temp file in cacheDir/snapshots/ in the requested format. */
    private suspend fun writeTempFile(bitmap: Bitmap, fileName: String, useJpeg: Boolean): File = withContext(Dispatchers.IO) {
        val dir = File(activity.cacheDir, SNAPSHOT_DIR).also { it.mkdirs() }
        val tempFile = File(dir, fileName)
        FileOutputStream(tempFile).use { out ->
            if (useJpeg) {
                // 85% quality — good balance of file size and visual fidelity for JPEG
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            } else {
                bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, out)
            }
        }
        tempFile
    }

    /**
     * Copies [tempFile] to [resource] using [FileOperationUseCase], which handles all
     * resource types: Local, SMB, SFTP, FTP, Google Drive, Dropbox, OneDrive.
     * Returns the resource display name on success, null on any failure (triggers Downloads fallback).
     */
    private suspend fun trySaveToResource(
        tempFile: File,
        resource: MediaResource,
        fileName: String
    ): String? = withContext(Dispatchers.IO) {
        try {
            // FileOperationUseCase detects the destination scheme (smb/sftp/ftp/cloud/local)
            // and routes to the correct protocol handler automatically.
            // Source is always the local temp file; destination is the resource folder path.
            val operation = FileOperation.Copy(
                sources = listOf(tempFile),
                destination = File(resource.path),
                overwrite = true
            )
            when (val result = fileOperationUseCase.execute(operation)) {
                is FileOperationResult.Success -> {
                    Timber.i("SaveVideoFrameManager: frame saved to resource '${resource.name}'")
                    resource.name
                }
                is FileOperationResult.AuthenticationRequired -> {
                    // Cloud token expired — user must re-authenticate; don't silently redirect to Downloads
                    Timber.w("SaveVideoFrameManager: auth required for '${resource.name}' (${result.provider})")
                    null
                }
                is FileOperationResult.Failure -> {
                    Timber.e("SaveVideoFrameManager: copy failed for '${resource.name}': ${result.error}")
                    null
                }
                else -> {
                    Timber.w("SaveVideoFrameManager: unexpected result ${result::class.simpleName} for '${resource.name}'")
                    null
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "SaveVideoFrameManager: exception copying to '${resource.name}'")
            null
        }
    }

    /**
     * Saves [tempFile] to the system Downloads folder via MediaStore.
     * Deletes any existing entry with the same display name before inserting (overwrite semantics).
     * Works on Android Q+ (API 29+) without WRITE_EXTERNAL_STORAGE permission.
     */
    private suspend fun saveToDownloads(tempFile: File, fileName: String, useJpeg: Boolean) = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = activity.contentResolver
                val mimeType = if (useJpeg) "image/jpeg" else "image/png"

                // Remove any existing MediaStore entry for this filename to implement overwrite
                val queryUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI
                val projection = arrayOf(MediaStore.Downloads._ID)
                val selection = "${MediaStore.Downloads.DISPLAY_NAME} = ?"
                resolver.query(queryUri, projection, selection, arrayOf(fileName), null)?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID))
                        resolver.delete(ContentUris.withAppendedId(queryUri, id), null, null)
                    }
                }

                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = resolver.insert(queryUri, values)
                    ?: throw IOException("MediaStore insert returned null")
                resolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(tempFile).use { input -> input.copyTo(out) }
                }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } else {
                // API 26-28: write directly to Downloads directory
                @Suppress("DEPRECATION")
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                downloadsDir.mkdirs()
                // overwrite = true: existing frame with same name is replaced
                tempFile.copyTo(File(downloadsDir, fileName), overwrite = true)
            }
            Timber.i("SaveVideoFrameManager: frame saved to Downloads as $fileName")
        } catch (e: Exception) {
            Timber.e(e, "SaveVideoFrameManager: failed to save to Downloads")
            throw e
        }
    }

    /**
     * Builds a filename from the source video name and current video track position.
     * Format: {video_name}_{HH}h{MM}m{SS}s.{ext}
     * Example: my_clip_00h01m23s.jpg
     */
    private fun buildFileName(sourceFileName: String?, positionMs: Long, useJpeg: Boolean): String {
        val base = sourceFileName
            ?.substringBeforeLast(".")
            ?.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
            ?.take(40)
            ?: "frame"
        val totalSeconds = positionMs / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        val pos = "%02dh%02dm%02ds".format(hours, minutes, seconds)
        val ext = if (useJpeg) "jpg" else "png"
        return "${base}_${pos}.${ext}"
    }

    // Toast renders in a system overlay, immune to view hierarchy and edge-to-edge insets —
    // critical for full-screen video player where Snackbar anchor is off-screen.
    private fun showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(activity, message, duration).show()
    }
}
