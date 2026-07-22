package com.sza.fastmediasorter.ui.player.helpers

import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.transfer.local.LocalDestinationClassifier
import com.sza.fastmediasorter.data.transfer.local.LocalDestinationWriter
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.SaveFallbackReason
import com.sza.fastmediasorter.domain.usecase.FileOperation
import com.sza.fastmediasorter.domain.usecase.FileOperationResult
import com.sza.fastmediasorter.domain.usecase.FileOperationUseCase
import com.sza.fastmediasorter.ui.player.PlayerActivity
import com.sza.fastmediasorter.utils.MediaStoreNotifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

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
 * Format: PNG (lossless) or JPG (85% quality) - controlled by videoSnapshotFormat setting.
 * Filename: {video_name}_{HH}h{MM}m{SS}s.{ext} - based on current video track position.
 * Overwrite: existing files with the same name are always replaced.
 */
class SaveVideoFrameManager(
    private val activity: PlayerActivity,
    private val fileOperationUseCase: FileOperationUseCase,
    private val imageClipboardWriter: com.sza.fastmediasorter.core.clipboard.ImageClipboardWriter,
    private val localDestinationClassifier: LocalDestinationClassifier,
    private val localDestinationWriter: LocalDestinationWriter
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

        // Capture playback position on main thread before launching coroutine -
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

                // S0522: track why a frame was redirected to Downloads so the user can be notified
                // when the configured network destination was unavailable (not when none was set).
                var fallbackReason: SaveFallbackReason? = null
                var configuredResourceName = ""
                val savedLocation = if (resourceId != null) {
                    // Try to save to configured destination resource
                    val destinations = activity.viewModel.getDestinationsUseCase.invoke().first()
                    val resource = destinations.find { it.id == resourceId }
                    if (resource != null) {
                        configuredResourceName = resource.name
                        if (resource.type.isNetworkResource &&
                            !activity.networkStateMonitor.canReach(resource.type)
                        ) {
                            // S0522: transport down - skip the doomed copy and fall back to Downloads.
                            Timber.w("SaveVideoFrameManager: resource '${resource.name}' unreachable, falling back to Downloads")
                            fallbackReason = SaveFallbackReason.ResourceUnavailable
                            null
                        } else {
                            val saved = trySaveToResource(tempFile, resource, fileName)
                            if (saved == null && resource.type.isNetworkResource) {
                                fallbackReason = SaveFallbackReason.ResourceWriteFailed
                            }
                            saved
                        }
                    } else {
                        // Configured resource no longer exists - fall back to Downloads
                        Timber.w("SaveVideoFrameManager: configured resource id=$resourceId not found, falling back to Downloads")
                        null
                    }
                } else null

                val finalMessage = if (savedLocation != null) {
                    activity.getString(R.string.save_frame_saved_to_resource, savedLocation)
                } else {
                    // Fallback: save to Downloads via the shared local destination writer
                    saveToDownloads(tempFile, fileName)
                    fallbackReason?.let { reason ->
                        activity.saveFallbackNotifier.notify(
                            reason = reason,
                            folderLabel = Environment.DIRECTORY_DOWNLOADS,
                            resourceName = configuredResourceName,
                            background = false,
                        )
                    }
                    activity.getString(R.string.save_frame_saved_to_downloads)
                }

                // S0470: when enabled, also place the saved frame on the system clipboard.
                // Copy the encoded tempFile verbatim (no re-encode) so the pasted image matches the
                // saved frame's format/quality. Runs before delete and never replaces the save above.
                val copiedToClipboard = if (settings.videoFrameCopyToClipboard) {
                    imageClipboardWriter.copyImageFile(tempFile)
                } else false

                tempFile.delete()
                showToast(finalMessage)
                if (copiedToClipboard) {
                    showToast(activity.getString(R.string.video_frame_copied_to_clipboard))
                }

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

    /** Captures the current TextureView-backed frame on the main thread. */
    private fun captureFrame(): Bitmap? {
        val playerView = activity.activityBinding.playerView
        val bitmap = PlayerTextureFrameCapture.capture(playerView) { failure ->
            Timber.e(failure, "SaveVideoFrameManager: getBitmap() failed")
            val t = failure
            if (t is OutOfMemoryError) {
                showToast(activity.getString(R.string.save_frame_error), Toast.LENGTH_LONG)
            }
        }
        if (bitmap == null) {
            Timber.w("SaveVideoFrameManager: TextureView frame unavailable")
        }
        return bitmap
    }

    /** Saves the bitmap to a temp file in cacheDir/snapshots/ in the requested format. */
    private suspend fun writeTempFile(bitmap: Bitmap, fileName: String, useJpeg: Boolean): File = withContext(Dispatchers.IO) {
        val dir = File(activity.cacheDir, SNAPSHOT_DIR).also { it.mkdirs() }
        val tempFile = File(dir, fileName)
        FileOutputStream(tempFile).use { out ->
            if (useJpeg) {
                // 85% quality - good balance of file size and visual fidelity for JPEG
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
                    // Cloud token expired - user must re-authenticate; don't silently redirect to Downloads
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
     * Saves [tempFile] to the system Downloads folder through the shared local destination
     * writer in overwrite mode, so collision behaviour is identical across API levels and the
     * MediaStore write path is not duplicated. Throws on failure so the caller's catch reports it.
     */
    private suspend fun saveToDownloads(tempFile: File, fileName: String) = withContext(Dispatchers.IO) {
        val targetPath = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            fileName
        ).absolutePath
        val category = localDestinationClassifier.classify(targetPath)
        val sink = localDestinationWriter.open(category, overwrite = true).getOrElse { e ->
            Timber.e(e, "SaveVideoFrameManager: failed to open Downloads sink")
            throw e
        }
        try {
            FileInputStream(tempFile).use { input -> input.copyTo(sink.outputStream) }
        } catch (e: Exception) {
            sink.abort()
            Timber.e(e, "SaveVideoFrameManager: failed to write frame to Downloads")
            throw e
        }
        val savedPath = sink.commit().getOrElse { e ->
            Timber.e(e, "SaveVideoFrameManager: failed to commit Downloads sink")
            throw e
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // Pre-Q the writer lands a plain file the system has not indexed; the manual scan
            // makes it visible. On Q+ the MediaStore publish already indexes it.
            MediaStoreNotifier.notifyFile(activity, savedPath, "save-frame")
        }
        Timber.i("SaveVideoFrameManager: frame saved to Downloads as $fileName")
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

    // Toast renders in a system overlay, immune to view hierarchy and edge-to-edge insets -
    // critical for full-screen video player where Snackbar anchor is off-screen.
    private fun showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(activity, message, duration).show()
    }
}
