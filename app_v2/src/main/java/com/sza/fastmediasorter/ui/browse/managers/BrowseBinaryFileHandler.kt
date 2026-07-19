package com.sza.fastmediasorter.ui.browse.managers

import android.app.Activity
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.share.ShareableContent
import com.sza.fastmediasorter.core.share.handlers.OpenInShareTargetHandler
import com.sza.fastmediasorter.core.util.MimeTypeResolver
import com.sza.fastmediasorter.data.common.MediaTypeUtils
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.ui.share.SendToMenuManager
import com.sza.fastmediasorter.util.BinaryFileTypeDetector
import timber.log.Timber
import java.io.File

/**
 * Handles binary file actions for BrowseActivity: bottom sheet menu, open-with, share, MIME type.
 *
 * Extracted from BrowseActivity (Wave 1.5 decomposition - IV.1).
 *
 * S0459 Phase 07: Share routes through the unified [SendToMenuManager]; "Open in.." routes through
 * the shared [OpenInShareTargetHandler] - no surface-local ACTION_SEND / ACTION_VIEW chooser.
 */
class BrowseBinaryFileHandler(
    private val activity: Activity,
    private val sendToMenuManager: SendToMenuManager,
    private val openInHandler: OpenInShareTargetHandler,
    private val getSettings: () -> AppSettings?,
    private val onSelectFile: (String) -> Unit,
    private val onPrepareExtraction: (MediaFile) -> Unit,
    private val onShowCopyDialog: () -> Unit,
    private val onShowMoveDialog: () -> Unit,
    private val onShowRenameDialog: () -> Unit,
    private val onShowDeleteConfirmation: () -> Unit,
    private val binaryFileMenuActions: Set<@JvmSuppressWildcards BrowseBinaryFileMenuAction> = emptySet(),
) {

    fun showBinaryFileMenu(mediaFile: MediaFile) {
        if (mediaFile.type == MediaType.BINARY_ARCHIVE &&
            mediaFile.name.substringAfterLast('.', "").equals("zip", ignoreCase = true)
        ) {
            onPrepareExtraction(mediaFile)
            return
        }

        val bottomSheet = com.google.android.material.bottomsheet.BottomSheetDialog(activity)
        val root = activity.findViewById<android.view.ViewGroup>(android.R.id.content)
        val view = activity.layoutInflater.inflate(R.layout.bottom_sheet_binary_file, root, false)

        view.findViewById<android.widget.TextView>(R.id.tvFileName)?.text = mediaFile.name

        view.findViewById<android.view.View>(R.id.btnShare)?.setOnClickListener {
            shareFile(mediaFile)
            bottomSheet.dismiss()
        }

        view.findViewById<android.view.View>(R.id.btnOpenWith)?.setOnClickListener {
            openWithDefaultApp(mediaFile)
            bottomSheet.dismiss()
        }

        view.findViewById<android.view.View>(R.id.btnCopy)?.setOnClickListener {
            onSelectFile(mediaFile.path)
            onShowCopyDialog()
            bottomSheet.dismiss()
        }

        view.findViewById<android.view.View>(R.id.btnMove)?.setOnClickListener {
            onSelectFile(mediaFile.path)
            onShowMoveDialog()
            bottomSheet.dismiss()
        }

        view.findViewById<android.view.View>(R.id.btnRename)?.setOnClickListener {
            onSelectFile(mediaFile.path)
            onShowRenameDialog()
            bottomSheet.dismiss()
        }

        view.findViewById<android.view.View>(R.id.btnDelete)?.setOnClickListener {
            onSelectFile(mediaFile.path)
            onShowDeleteConfirmation()
            bottomSheet.dismiss()
        }

        binaryFileMenuActions.forEach { action ->
            action.bind(view, mediaFile) { bottomSheet.dismiss() }
        }

        bottomSheet.setContentView(view)
        bottomSheet.show()
    }

    fun openWithDefaultApp(mediaFile: MediaFile) {
        val content = buildBinaryContent(mediaFile)
        if (content == null) {
            Toast.makeText(activity, R.string.error_opening_file_simple, Toast.LENGTH_SHORT).show()
            return
        }
        if (!openInHandler.send(activity, content)) {
            Toast.makeText(activity, R.string.no_app_to_open, Toast.LENGTH_SHORT).show()
        }
    }

    fun shareFile(mediaFile: MediaFile) {
        val host = activity as? FragmentActivity ?: run {
            Timber.w("BrowseBinaryFileHandler: host is not a FragmentActivity, cannot show Send-to menu")
            return
        }
        val settings = getSettings() ?: return
        val content = buildBinaryContent(mediaFile)
        if (content == null) {
            Toast.makeText(activity, R.string.error_sharing_file, Toast.LENGTH_SHORT).show()
            return
        }
        sendToMenuManager.show(host, content, settings)
    }

    // Build a shareable Uri for a binary MediaFile: FileProvider for a local file path, otherwise
    // the path is already a content/scheme Uri (e.g. SAF) and is parsed as-is.
    private fun buildBinaryContent(mediaFile: MediaFile): ShareableContent? {
        return try {
            val uri = if (mediaFile.path.contains("://")) {
                Uri.parse(mediaFile.path)
            } else {
                FileProvider.getUriForFile(
                    activity,
                    "${activity.packageName}.fileprovider",
                    File(mediaFile.path)
                )
            }
            ShareableContent(
                uris = listOf(uri),
                mime = getMimeTypeForFile(mediaFile),
                mediaType = mediaFile.type,
                displayName = mediaFile.name,
                mediaFile = mediaFile,
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to build shareable content for binary file: %s", mediaFile.path)
            null
        }
    }

    fun getMimeTypeForFile(mediaFile: MediaFile): String {
        val extension = mediaFile.name.substringAfterLast('.', "").lowercase()
        return when (mediaFile.type) {
            // S1058: registry type first (pinned, identical on API 23..35), then the platform map,
            // which already ends at application/octet-stream. Never `application/$extension` - a
            // coined type matches only `*/*` filters, so archivers declaring a concrete type lose.
            MediaType.BINARY_ARCHIVE, MediaType.BINARY_DISK ->
                BinaryFileTypeDetector.mimeTypeForExtension(extension)
                    ?: MimeTypeResolver.resolve(extension)
            MediaType.BINARY_EXECUTABLE -> when (extension) {
                "apk" -> "application/vnd.android.package-archive"
                "exe", "dll" -> "application/x-msdownload"
                else -> "application/octet-stream"
            }
            MediaType.OFFICE_DOCUMENT -> MediaTypeUtils.officeMimeTypeForFileName(mediaFile.name)
                ?: "application/octet-stream"
            else -> "application/octet-stream"
        }
    }
}
