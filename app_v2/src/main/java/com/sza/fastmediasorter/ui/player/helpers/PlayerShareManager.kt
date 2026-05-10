package com.sza.fastmediasorter.ui.player.helpers

import android.content.ClipData
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.player.PlayerActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File

/**
 * Manages file sharing and external player launch actions in the player.
 * Handles opening files in external apps, sharing to Google Lens.
 * Extracted from PlayerActivity to reduce its size.
 */
class PlayerShareManager(
    private val activity: PlayerActivity
) {
    /**
     * Open a local file in an external player via ACTION_VIEW intent.
     */
    fun openInExternalPlayer(filePath: String) {
        try {
            val file = File(filePath)
            if (!file.exists()) {
                Toast.makeText(activity, R.string.file_not_found, Toast.LENGTH_SHORT).show()
                return
            }

            val uri = FileProvider.getUriForFile(
                activity,
                "${activity.packageName}.fileprovider",
                file
            )

            val mimeType = when (file.extension.lowercase()) {
                "flv" -> "video/x-flv"
                "avi" -> "video/x-msvideo"
                "mid", "midi" -> "audio/midi"
                "wmv" -> "video/x-ms-wmv"
                "rm", "rmvb" -> "video/vnd.rn-realvideo"
                "vob" -> "video/dvd"
                "ogv" -> "video/ogg"
                "mp4" -> "video/mp4"
                "mkv" -> "video/x-matroska"
                "mp3" -> "audio/mpeg"
                "aac" -> "audio/aac"
                else -> "*/*"
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            if (intent.resolveActivity(activity.packageManager) != null) {
                activity.startActivity(Intent.createChooser(intent, activity.getString(R.string.open_with)))
            } else {
                Toast.makeText(
                    activity,
                    activity.getString(R.string.no_app_to_handle_format),
                    Toast.LENGTH_LONG
                ).show()
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to open file in external player")
            Toast.makeText(
                activity,
                activity.getString(R.string.error_opening_external_player),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * Share the current media file to Google Lens.
     * For network files, downloads/caches the file first.
     */
    fun shareCurrentFileToGoogleLens() {
        val currentFile = activity.viewModel.state.value.currentFile ?: return

        if (!currentFile.path.contains("://")) {
            shareFileToGoogleLens(File(currentFile.path))
        } else {
            activity.lifecycleScope.launch {
                try {
                    val file = activity.networkFileManager.prepareFileForRead(currentFile)
                    shareFileToGoogleLens(file)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to prepare file for Google Lens")
                    Toast.makeText(activity, R.string.toast_failed_to_prepare_file, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * Share a local [File] to Google Lens (standalone app → Google App → generic chooser).
     */
    fun shareFileToGoogleLens(file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                activity,
                "${activity.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newRawUri(null, uri)
            }

            val lensPackage = "com.google.ar.lens"
            val googlePackage = "com.google.android.googlequicksearchbox"
            val pm = activity.packageManager

            intent.setPackage(lensPackage)
            if (intent.resolveActivity(pm) != null) {
                activity.startActivity(intent)
                return
            }

            intent.setPackage(googlePackage)
            if (intent.resolveActivity(pm) != null) {
                activity.startActivity(intent)
                return
            }

            intent.setPackage(null)
            activity.startActivity(Intent.createChooser(intent, activity.getString(R.string.enable_google_lens)))
        } catch (e: Exception) {
            Timber.e(e, "Failed to share to Google Lens")
            Toast.makeText(activity, R.string.toast_error_google_lens, Toast.LENGTH_SHORT).show()
        }
    }
}
