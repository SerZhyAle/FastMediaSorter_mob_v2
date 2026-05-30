package com.sza.fastmediasorter.ui.dialog.helpers

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.common.MediaTypeUtils
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.usecase.DownloadNetworkFileUseCase
import com.sza.fastmediasorter.ui.dialog.MaterialProgressDialog
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
        Timber.d("FileInfoLaunchManager.openInExternalPlayer: Opening file ${mediaFile.name} (path=${mediaFile.path})")

        try {
            val intent = Intent(Intent.ACTION_VIEW)
            val mimeType = mimeTypeForMediaFile(mediaFile.name)
            Timber.d("FileInfoLaunchManager.openInExternalPlayer: MIME type = $mimeType")

            val uri = when {
                mediaFile.path.startsWith("content://") -> {
                    Timber.d("FileInfoLaunchManager.openInExternalPlayer: Using content:// URI directly")
                    Uri.parse(mediaFile.path)
                }
                else -> {
                    val file = File(mediaFile.path)
                    Timber.d("FileInfoLaunchManager.openInExternalPlayer: File path = ${file.absolutePath}, exists = ${file.exists()}")

                    if (!file.exists()) {
                        Timber.w("FileInfoLaunchManager.openInExternalPlayer: File does not exist!")
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

            Timber.d("FileInfoLaunchManager.openInExternalPlayer: URI = $uri")

            intent.setDataAndType(uri, mimeType)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

            val chooserIntent = Intent.createChooser(intent, context.getString(R.string.open_with))
            chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            Timber.d("FileInfoLaunchManager.openInExternalPlayer: Starting chooser activity")
            context.startActivity(chooserIntent)
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

            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(uri, mimeType)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

            val chooserIntent = Intent.createChooser(intent, context.getString(R.string.open_with))
            chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            context.startActivity(chooserIntent)
        } catch (e: ActivityNotFoundException) {
            Timber.w(e, "No app to open downloaded file")
            Toast.makeText(
                context,
                context.getString(
                    R.string.error_opening_file,
                    context.getString(R.string.friendly_copy_error_generic)
                ),
                Toast.LENGTH_SHORT
            ).show()
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
}
