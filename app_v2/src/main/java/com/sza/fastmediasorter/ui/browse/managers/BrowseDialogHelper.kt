package com.sza.fastmediasorter.ui.browse.managers

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LifecycleOwner
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.data.cloud.CloudProvider
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.FileFilter
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.SortMode
import com.sza.fastmediasorter.domain.model.UndoOperation
import com.sza.fastmediasorter.domain.usecase.FileOperationUseCase
import java.io.File

/**
 * Thin facade over browse-screen dialog managers.
 */
class BrowseDialogHelper(
    activity: AppCompatActivity,
    callbacks: DialogCallbacks,
    mediaCapabilities: MediaCapabilities
) {
    interface DialogCallbacks {
        fun onFilterApplied(filter: FileFilter?)
        fun onSortModeSelected(sortMode: SortMode)
        fun onRandomReshuffle()
        fun onRenameConfirmed(oldName: String, newName: String)
        fun onRenameMultipleConfirmed(files: List<Pair<String, String>>)
        fun onDirectoryRenameConfirmed(oldPath: String, newName: String)
        fun onCopyDestinationSelected(destinationPath: String)
        fun onMoveDestinationSelected(destinationPath: String)

        /**
         * Called when the user confirms a delete-files dialog.
         *
         * @param overridePaths When non-null, exact set of paths the dialog was shown
         *   for (per-file overflow menu) - implementation must delete these and leave
         *   the global multiselect untouched. When null, the dialog was shown for the
         *   current global multiselect - implementation should read it as usual and
         *   may clear it after deletion. Always-reading-global-selection here breaks
         *   overflow-menu deletes when the global selection is empty (regression
         *   observed 2026-05-17 - toast `no_files_selected`).
         */
        fun onDeleteConfirmed(overridePaths: Set<String>?)
        fun onCloudSignInRequested(provider: CloudProvider)
        fun saveUndoOperation(undoOp: UndoOperation)
        fun updateFile(oldPath: String, newFile: MediaFile)
        fun setIgnoringFileChanges(ignoring: Boolean)
        fun createMediaFileFromFile(file: File): MediaFile
        fun getFileOperationUseCase(): FileOperationUseCase
        fun getResourceName(): String?
        fun getLifecycleOwner(): LifecycleOwner
    }

    private val filterDialogManager = BrowseFilterDialogManager(activity, callbacks, mediaCapabilities)
    private val sortDialogManager = BrowseSortDialogManager(activity, callbacks)
    private val deleteDialogManager = BrowseDeleteDialogManager(activity, callbacks)
    private val feedbackDialogManager = BrowseFeedbackDialogManager(activity, callbacks)
    private val renameDialogManager = BrowseRenameDialogManager(activity, callbacks)

    fun initialize() {
        // No initialization needed
    }

    fun cleanup() {
        // Dismiss any open dialogs if needed
    }

    fun showFilterDialog(currentFilter: FileFilter?, allowedMediaTypes: Set<MediaType>? = null) {
        filterDialogManager.showFilterDialog(currentFilter, allowedMediaTypes)
    }

    fun formatDate(timestamp: Long): String = filterDialogManager.formatDate(timestamp)

    fun showSortDialog(currentSortMode: SortMode) {
        sortDialogManager.showSortDialog(currentSortMode)
    }

    fun showDeleteConfirmation(
        files: List<MediaFile>,
        resource: MediaResource?,
        settings: AppSettings,
        overridePaths: Set<String>? = null
    ) {
        deleteDialogManager.showDeleteConfirmation(files, resource, settings, overridePaths)
    }

    fun showErrorDialog(message: String, details: String?) {
        feedbackDialogManager.showErrorDialog(message, details)
    }

    fun showCloudAuthenticationDialog(
        provider: CloudProvider,
        resourceName: String,
        onRemoveResource: () -> Unit = {}
    ) {
        feedbackDialogManager.showCloudAuthenticationDialog(provider, resourceName, onRemoveResource)
    }

    fun showRenameDialog(selectedFiles: List<MediaFile>) {
        renameDialogManager.showRenameDialog(selectedFiles)
    }
}
