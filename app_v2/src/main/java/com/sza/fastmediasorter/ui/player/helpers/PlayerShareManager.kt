package com.sza.fastmediasorter.ui.player.helpers

import android.content.ClipData
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.share.ShareableContent
import com.sza.fastmediasorter.ui.player.PlayerActivity
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File

/**
 * Manages external-player launch and the in-app Office-document open/share paths for the player.
 * The image Google Lens floating-button path also lives here; the command-bar/overflow «share» is
 * the unified «Send to..» menu (S0459). Extracted from PlayerActivity to reduce its size.
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

    fun openOfficeDocument(mediaFile: MediaFile) {
        activity.lifecycleScope.launch {
            try {
                val preparedFile = activity.networkFileManager.prepareFileForRead(mediaFile)
                val opened = OfficeDocumentOpenManager.openPreparedFile(
                    activity = activity,
                    file = preparedFile,
                    displayName = mediaFile.name
                )
                if (!opened) {
                    Toast.makeText(activity, R.string.no_app_to_open, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to open Office document in external viewer")
                Toast.makeText(activity, R.string.error_opening_file_simple, Toast.LENGTH_SHORT).show()
            } finally {
                activity.finish()
            }
        }
    }

    /**
     * Route an Office document through the flavor-safe viewer provider (S0301 Phase 02).
     *
     * The provider decides whether the file is opened externally (S0299 market behavior),
     * shown via the explicit fallback dialog, or rendered internally (noLegal, Phase 03).
     * Until the engine and fallback dialog land, every outcome preserves the S0299
     * external-handoff behavior so this seam introduces no user-facing change.
     */
    fun routeOfficeDocument(mediaFile: MediaFile) {
        val session = activity.officeDocumentViewerProvider.resolve(mediaFile)
        when (session.outcome) {
            OfficeDocumentViewerOutcome.DISPLAY_INTERNALLY ->
                displayOfficeDocumentInternally(mediaFile)
            // S0301 Phase 05: the provider explicitly asked for the user-facing fallback dialog
            // (external / share / cancel) instead of a silent external handoff.
            OfficeDocumentViewerOutcome.SHOW_FALLBACK_DIALOG ->
                showOfficeFallbackDialog(mediaFile)
            OfficeDocumentViewerOutcome.DELEGATE_EXTERNAL ->
                openOfficeDocument(mediaFile)
        }
    }

    /**
     * Explicit Office fallback dialog (S0301 Phase 05).
     *
     * Shown when the embedded viewer cannot render a document. Offers opening in another app or
     * sharing the file; Cancel keeps the player open (no handoff, no finish) per the approved UI
     * decision. Strings stay factual and make no fidelity promises (COMMUNICATION_POLICY §6).
     */
    fun showOfficeFallbackDialog(mediaFile: MediaFile) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.office_viewer_fallback_title)
            .setMessage(R.string.office_viewer_fallback_message)
            .setPositiveButton(R.string.office_viewer_fallback_open_external) { _, _ ->
                openOfficeDocument(mediaFile)
            }
            .setNeutralButton(R.string.office_viewer_fallback_share) { _, _ ->
                shareOfficeDocument(mediaFile)
            }
            .setNegativeButton(R.string.office_viewer_fallback_cancel, null)
            .show()
    }

    /**
     * Share the prepared Office [mediaFile] through the unified «Send to..» menu (S0459; was a
     * standalone ACTION_SEND chooser in S0301 Phase 05). prepareFileForRead keeps network fetches
     * off the main thread; the resulting FileProvider Uri is handed to the menu.
     */
    private fun shareOfficeDocument(mediaFile: MediaFile) {
        val settings = activity.currentSettings ?: return
        activity.lifecycleScope.launch {
            try {
                val preparedFile = activity.networkFileManager.prepareFileForRead(mediaFile)
                val uri = FileProvider.getUriForFile(
                    activity,
                    "${activity.packageName}.fileprovider",
                    preparedFile
                )
                val content = ShareableContent(
                    uris = listOf(uri),
                    mime = "application/octet-stream",
                    mediaType = MediaType.OFFICE_DOCUMENT,
                    displayName = mediaFile.name,
                    mediaFile = mediaFile,
                )
                activity.sendToMenuManager.show(activity, content, settings)
            } catch (e: Exception) {
                Timber.e(e, "Failed to share Office document")
                Toast.makeText(activity, R.string.error_opening_file_simple, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Render an Office document inside the in-app viewer (S0301 Phase 03, noLegal).
     *
     * Materializes the file with the shared S0299 path, then hands it to the embedded
     * read-only viewer. If the host cannot accept the prepared file, the explicit Office
     * fallback dialog preserves the approved external / share / cancel choice.
     * Unlike [openOfficeDocument], the internal path does not finish the activity.
     */
    private fun displayOfficeDocumentInternally(mediaFile: MediaFile) {
        activity.lifecycleScope.launch {
            try {
                val preparedFile = activity.networkFileManager.prepareFileForRead(mediaFile)
                val started = activity.officeDocumentViewerManager.open(mediaFile, preparedFile)
                if (!started) showOfficeFallbackDialog(mediaFile)
            } catch (e: Exception) {
                Timber.e(e, "Failed to render Office document internally")
                showOfficeFallbackDialog(mediaFile)
            }
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
