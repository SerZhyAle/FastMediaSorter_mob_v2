package com.sza.fastmediasorter.ui.browse.managers

import android.app.Activity
import android.view.View
import android.widget.Toast
import com.google.android.material.snackbar.Snackbar
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.cloud.CloudProvider
import com.sza.fastmediasorter.domain.model.FileOperationType
import com.sza.fastmediasorter.domain.model.UndoOperation
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.core.error.ErrorSeverity
import com.sza.fastmediasorter.ui.common.copy.UiMessageFamily
import com.sza.fastmediasorter.ui.common.copy.UiMessageProjector
import com.sza.fastmediasorter.ui.common.copy.UiMessageSpec
import com.sza.fastmediasorter.ui.dialog.ScrollableTextDialog
import com.sza.fastmediasorter.util.AppErrorNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Manages error display, undo snackbar, and non-critical error filtering for BrowseActivity.
 *
 * Extracted from BrowseActivity (Wave 1.5 decomposition - IV.1).
 */
class BrowseErrorDisplayManager(
    private val activity: Activity,
    private val rootView: View,
    private val anchorView: View,
    private val settingsRepository: SettingsRepository,
    private val coroutineScope: CoroutineScope,
    private val onShowCloudAuthDialog: (CloudProvider) -> Unit,
    private val onUndoRequested: () -> Unit,
    private val getCurrentCloudProvider: () -> CloudProvider?
) {

    /**
     * Show error message respecting showDetailedErrors setting.
     * If showDetailedErrors=true: shows ErrorDialog with copyable text and detailed info.
     * If showDetailedErrors=false: shows Toast (short notification).
     */
    fun showError(message: String, details: String?, exception: Throwable? = null) {
        // Check if this is a Google Drive authentication error
        if (message.contains("Google Drive authentication required", ignoreCase = true) ||
            message.contains("Not authenticated", ignoreCase = true)
        ) {
            val provider = getCurrentCloudProvider() ?: CloudProvider.GOOGLE_DRIVE
            onShowCloudAuthDialog(provider)
            return
        }

        if (isNonCriticalNetworkImageError(message, details, exception)) {
            Timber.d("BrowseErrorDisplayManager: Suppressed non-critical network image error: $message")
            return
        }

        coroutineScope.launch {
            val settings = settingsRepository.getSettings().first()
            Timber.d("showError: showDetailedErrors=${settings.showDetailedErrors}, message=$message")

            if (settings.showDetailedErrors) {
                if (details != null) {
                    ScrollableTextDialog.show(
                        context = activity,
                        title = activity.getString(R.string.error),
                        message = message,
                        details = details
                    )
                } else {
                    // S0118: keep the richer dialog surface, but do not expose raw stack traces.
                    ScrollableTextDialog.show(
                        context = activity,
                        title = activity.getString(R.string.error),
                        message = message
                    )
                }
            } else {
                // S0118: route the short copy through UiMessageProjector so all error
                // surfaces share the same Snackbar formula and severity defaults.
                UiMessageProjector.showShort(
                    activity = activity,
                    spec = UiMessageSpec(
                        family = UiMessageFamily.ERROR,
                        shortMessage = message,
                    ),
                    severity = ErrorSeverity.CRITICAL,
                )
            }
        }
    }

    fun isNonCriticalNetworkImageError(
        message: String,
        details: String?,
        exception: Throwable?
    ): Boolean {
        val messages = mutableListOf<String>()
        messages.add(message)
        if (!details.isNullOrBlank()) {
            messages.add(details)
        }

        var current = exception
        var depth = 0
        while (current != null && depth < 12) {
            messages.add(current.message ?: "")
            current = current.cause
            depth++
        }

        val hasNetworkContext = messages.any { msg ->
            msg.contains("NetworkFileDataFetcher", ignoreCase = true) ||
                msg.contains("Failed to load network file:", ignoreCase = true) ||
                msg.contains("smb://", ignoreCase = true) ||
                msg.contains("sftp://", ignoreCase = true) ||
                msg.contains("ftp://", ignoreCase = true)
        }

        if (!hasNetworkContext) return false

        return messages.any { msg ->
            msg.contains("Invalid/corrupted image data", ignoreCase = true) ||
                msg.contains("Corrupted image data:", ignoreCase = true) ||
                msg.contains("Request cancelled", ignoreCase = true) ||
                msg.contains("Failed to decode", ignoreCase = true) ||
                msg.contains("DecodeException", ignoreCase = true) ||
                msg.contains("ImageDecoder", ignoreCase = true) ||
                msg.contains("HEIC", ignoreCase = true) ||
                msg.contains("HEIF", ignoreCase = true) ||
                msg.contains("AVIF", ignoreCase = true)
        }
    }

    /**
     * Show Snackbar with operation description and Undo button.
     */
    fun showUndoSnackbar(operation: UndoOperation) {
        if (activity.isFinishing || activity.isDestroyed) return

        val count = operation.sourceFiles.size
        val description = when (operation.type) {
            FileOperationType.DELETE -> {
                activity.getString(R.string.deleted_n_files, count)
            }
            FileOperationType.COPY -> {
                val destination = operation.destinationFolder?.substringAfterLast('/') ?: "destination"
                activity.getString(R.string.msg_copy_success_count, count, destination)
            }
            FileOperationType.MOVE -> {
                val destination = operation.destinationFolder?.substringAfterLast('/') ?: "destination"
                activity.getString(R.string.msg_move_success_count, count, destination)
            }
            FileOperationType.RENAME -> {
                activity.getString(R.string.renamed_n_files, count)
            }
            // ARCHIVE is a destination-picker-only operation type (no undo snackbar).
            FileOperationType.ARCHIVE -> return
        }

        Snackbar.make(rootView, description, Snackbar.LENGTH_LONG)
            .setAction(activity.getString(R.string.undo).uppercase()) {
                onUndoRequested()
            }
            .setAnchorView(anchorView)
            .show()
    }
}
