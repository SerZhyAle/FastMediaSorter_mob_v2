package com.sza.fastmediasorter.ui.browse.managers

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.common.MediaTypeUtils
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import timber.log.Timber

/**
 * Handles binary file actions for BrowseActivity: bottom sheet menu, open-with, share, MIME type.
 *
 * Extracted from BrowseActivity (Wave 1.5 decomposition - IV.1).
 */
class BrowseBinaryFileHandler(
    private val activity: Activity,
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
        try {
            val uri = Uri.parse(mediaFile.path)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, getMimeTypeForFile(mediaFile))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            if (intent.resolveActivity(activity.packageManager) != null) {
                activity.startActivity(intent)
            } else {
                Toast.makeText(activity, R.string.no_app_to_open, Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to open file with default app")
            Toast.makeText(activity, R.string.error_opening_file_simple, Toast.LENGTH_SHORT).show()
        }
    }

    fun shareFile(mediaFile: MediaFile) {
        try {
            val uri = Uri.parse(mediaFile.path)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = getMimeTypeForFile(mediaFile)
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            activity.startActivity(Intent.createChooser(intent, activity.getString(R.string.share)))
        } catch (e: Exception) {
            Timber.e(e, "Failed to share file")
            Toast.makeText(activity, R.string.error_sharing_file, Toast.LENGTH_SHORT).show()
        }
    }

    fun getMimeTypeForFile(mediaFile: MediaFile): String {
        val extension = mediaFile.name.substringAfterLast('.', "").lowercase()
        return when (mediaFile.type) {
            MediaType.BINARY_ARCHIVE -> "application/$extension"
            MediaType.BINARY_EXECUTABLE -> when (extension) {
                "apk" -> "application/vnd.android.package-archive"
                "exe", "dll" -> "application/x-msdownload"
                else -> "application/octet-stream"
            }
            MediaType.BINARY_DISK -> "application/$extension"
            MediaType.OFFICE_DOCUMENT -> MediaTypeUtils.officeMimeTypeForFileName(mediaFile.name)
                ?: "application/octet-stream"
            else -> "application/octet-stream"
        }
    }
}
