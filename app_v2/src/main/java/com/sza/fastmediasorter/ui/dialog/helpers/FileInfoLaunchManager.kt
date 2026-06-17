package com.sza.fastmediasorter.ui.dialog.helpers

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.share.ShareableContent
import com.sza.fastmediasorter.core.share.handlers.OpenInShareTargetHandler
import com.sza.fastmediasorter.data.common.MediaTypeUtils
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.usecase.DownloadNetworkFileUseCase
import com.sza.fastmediasorter.ui.dialog.MaterialProgressDialog
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File

/**
 * Owns the "open in external player" and "download then open" flows for FileInfoDialog.
 * Extracted to keep FileInfoDialog under the line-budget ceiling.
 */
class FileInfoLaunchManager(
    private val context: Context,
    private val mediaFile: MediaFile,
    private val downloadNetworkFileUseCase: DownloadNetworkFileUseCase?,
    private val scope: CoroutineScope,
    private val onDismissRequested: () -> Unit
) {

    fun openInExternalPlayer() {
        try {
            val mimeType = mimeTypeForMediaFile(mediaFile.name)

            val uri = when {
                mediaFile.path.startsWith("content://") -> Uri.parse(mediaFile.path)
                else -> {
                    val file = File(mediaFile.path)
                    if (!file.exists()) {
                        Timber.w("FileInfoLaunchManager.openInExternalPlayer: file does not exist: ${file.absolutePath}")
                        Toast.makeText(
                            context,
                            context.getString(R.string.file_not_found),
                            Toast.LENGTH_SHORT
                        ).show()
                        return
                    }
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                }
            }

            if (!openWithSharedHandler(uri, mimeType)) {
                Toast.makeText(
                    context,
                    context.getString(
                        R.string.error_opening_file,
                        context.getString(R.string.friendly_copy_error_generic)
                    ),
                    Toast.LENGTH_SHORT
                ).show()
                return
            }
            onDismissRequested()
        } catch (e: Exception) {
            Timber.e(e, "Failed to open file in external player")
            Toast.makeText(
                context,
                context.getString(
                    R.string.error_opening_file,
                    context.getString(R.string.friendly_copy_error_generic)
                ),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun downloadAndOpenFile(
        onProgressDialogReady: (MaterialProgressDialog) -> Unit,
        onFinished: () -> Unit
    ) {
        if (downloadNetworkFileUseCase == null) {
            Timber.e("DownloadNetworkFileUseCase not available")
            Toast.makeText(
                context,
                context.getString(
                    R.string.download_failed,
                    context.getString(R.string.friendly_copy_error_generic)
                ),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val progressDialog = MaterialProgressDialog(context).apply {
            setTitle(context.getString(R.string.downloading_file))
            setMessage(mediaFile.name)
            setProgressStyle(MaterialProgressDialog.STYLE_HORIZONTAL)
            max = 100
            setCancelable(false)
            show()
        }
        onProgressDialogReady(progressDialog)

        scope.launch(Dispatchers.IO) {
            try {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS
                )
                val targetFile = File(downloadsDir, mediaFile.name)

                Timber.d("Downloading ${mediaFile.path} to ${targetFile.absolutePath}")

                val success = downloadNetworkFileUseCase.execute(
                    remotePath = mediaFile.path,
                    targetFile = targetFile,
                    progressCallback = { progress ->
                        scope.launch(Dispatchers.Main) {
                            progressDialog.progress = progress
                        }
                    }
                )

                launch(Dispatchers.Main) {
                    progressDialog.dismiss()

                    if (success) {
                        Timber.d("Download successful: ${targetFile.absolutePath}")
                        Toast.makeText(
                            context,
                            context.getString(R.string.downloaded_successfully),
                            Toast.LENGTH_SHORT
                        ).show()

                        openDownloadedFile(targetFile)
                        onFinished()
                    } else {
                        Timber.e("Download failed")
                        Toast.makeText(
                            context,
                            context.getString(
                                R.string.download_failed,
                                context.getString(R.string.friendly_copy_error_generic)
                            ),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error downloading file")
                launch(Dispatchers.Main) {
                    progressDialog.dismiss()
                    Toast.makeText(
                        context,
                        context.getString(
                            R.string.download_failed,
                            context.getString(R.string.friendly_copy_error_generic)
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun openDownloadedFile(file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val mimeType = mimeTypeForMediaFile(file.name)
            if (!openWithSharedHandler(uri, mimeType)) {
                Toast.makeText(
                    context,
                    context.getString(
                        R.string.error_opening_file,
                        context.getString(R.string.friendly_copy_error_generic)
                    ),
                    Toast.LENGTH_SHORT
                ).show()
            }
        } catch (e: Exception) {
            Timber.e(e, "Error opening downloaded file")
            Toast.makeText(
                context,
                context.getString(
                    R.string.error_opening_file,
                    context.getString(R.string.friendly_copy_error_generic)
                ),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * Delegate the open-with chooser to the shared [OpenInShareTargetHandler] (S0459 Phase 06) so
     * the file-info one-tap open uses the same ACTION_VIEW path as the unified menu's "Open in.."
     * receiver. Returns false when the host context is not an Activity or no viewer is available.
     */
    private fun openWithSharedHandler(uri: Uri, mimeType: String): Boolean {
        val activity = context as? Activity ?: run {
            Timber.w("file-info open-with host is not an Activity - cannot open external viewer")
            return false
        }
        val handler = EntryPointAccessors
            .fromApplication(context.applicationContext, OpenInEntryPoint::class.java)
            .openInShareTargetHandler()
        val content = ShareableContent(
            uris = listOf(uri),
            mime = mimeType,
            mediaType = mediaFile.type,
            displayName = mediaFile.name,
            mediaFile = mediaFile,
        )
        return handler.send(activity, content)
    }

    private fun mimeTypeForMediaFile(displayName: String): String {
        val extension = displayName.substringAfterLast('.', "").lowercase()
        return when (mediaFile.type) {
            MediaType.VIDEO -> when (extension) {
                "mp4" -> "video/mp4"
                "mkv" -> "video/x-matroska"
                "avi" -> "video/x-msvideo"
                "mov" -> "video/quicktime"
                "webm" -> "video/webm"
                "3gp" -> "video/3gpp"
                else -> "video/*"
            }
            MediaType.AUDIO -> when (extension) {
                "mp3" -> "audio/mpeg"
                "m4a" -> "audio/mp4"
                "wav" -> "audio/wav"
                "ogg" -> "audio/ogg"
                "flac" -> "audio/flac"
                else -> "audio/*"
            }
            MediaType.IMAGE -> when (extension) {
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "webp" -> "image/webp"
                "bmp" -> "image/bmp"
                else -> "image/*"
            }
            MediaType.GIF -> "image/gif"
            MediaType.TEXT -> "text/plain"
            MediaType.PDF -> "application/pdf"
            MediaType.EPUB -> "application/epub+zip"
            MediaType.OFFICE_DOCUMENT -> MediaTypeUtils.officeMimeTypeForFileName(displayName) ?: "application/octet-stream"
            MediaType.BINARY_ARCHIVE, MediaType.BINARY_DISK,
            MediaType.BINARY_EXECUTABLE, MediaType.BINARY_OTHER -> "application/octet-stream"
        }
    }

    /** S0459: app-scoped accessor for the shared [OpenInShareTargetHandler] from this manually-built helper. */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    internal interface OpenInEntryPoint {
        fun openInShareTargetHandler(): OpenInShareTargetHandler
    }
}
