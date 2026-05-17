package com.sza.fastmediasorter.ui.browse.managers

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.LifecycleOwner
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.cloud.CloudProvider
import com.sza.fastmediasorter.domain.model.FileFilter
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.SortMode
import com.sza.fastmediasorter.domain.model.UndoOperation
import com.sza.fastmediasorter.domain.usecase.FileOperationUseCase
import com.sza.fastmediasorter.ui.browse.BrowseViewModel
import java.io.File

/**
 * Implements [BrowseDialogHelper.DialogCallbacks] by delegating to [viewModel] and
 * the provided action lambdas.
 *
 * Extracted from BrowseActivity.setupViews() (Wave 1.5 decomposition — IV.1).
 */
class BrowseDialogCallbacksImpl(
    private val viewModel: BrowseViewModel,
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val onLaunchGoogleSignIn: () -> Unit,
    private val getCloudAuthManager: () -> BrowseCloudAuthManager
) : BrowseDialogHelper.DialogCallbacks {

    override fun onFilterApplied(filter: FileFilter?) {
        viewModel.setFilter(filter)
        val resource = viewModel.state.value.resource
        val isUserFilter = filter != null && !filter.isEmpty() && (
            !filter.nameContains.isNullOrBlank() ||
            filter.minDate != null ||
            filter.maxDate != null ||
            filter.minSizeMb != null ||
            filter.maxSizeMb != null ||
            (filter.mediaTypes != null && filter.mediaTypes != resource?.supportedMediaTypes)
        )
        if (isUserFilter) {
            Toast.makeText(context, R.string.toast_filter_active, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSortModeSelected(sortMode: SortMode) {
        viewModel.setSortMode(sortMode)
    }

    override fun onRandomReshuffle() {
        viewModel.reshuffleRandom()
    }

    override fun onRenameConfirmed(oldName: String, newName: String) {
        // Not used - handled by RenameDialog directly
    }

    override fun onRenameMultipleConfirmed(files: List<Pair<String, String>>) {
        // Not used - handled internally in showRenameMultipleDialog
    }

    override fun onDirectoryRenameConfirmed(oldPath: String, newName: String) {
        viewModel.renameDirectory(oldPath, newName)
    }

    override fun onCopyDestinationSelected(destinationPath: String) {
        // Not used - handled by CopyToDialog
    }

    override fun onMoveDestinationSelected(destinationPath: String) {
        // Not used - handled by MoveToDialog
    }

    override fun onDeleteConfirmed(overridePaths: Set<String>?) {
        viewModel.deleteSelectedFiles(overridePaths)
    }

    override fun onCloudSignInRequested(provider: CloudProvider) {
        when (provider) {
            CloudProvider.GOOGLE_DRIVE -> onLaunchGoogleSignIn()
            CloudProvider.DROPBOX -> getCloudAuthManager().launchDropboxSignIn()
            CloudProvider.ONEDRIVE -> getCloudAuthManager().launchOneDriveSignIn()
        }
    }

    override fun saveUndoOperation(undoOp: UndoOperation) {
        viewModel.saveUndoOperation(undoOp)
    }

    override fun reloadFiles() {
        viewModel.reloadFiles()
    }

    override fun updateFile(oldPath: String, newFile: MediaFile) {
        viewModel.updateFile(oldPath, newFile)
    }

    override fun setIgnoringFileChanges(ignoring: Boolean) {
        viewModel.setIgnoringFileChanges(ignoring)
    }

    override fun createMediaFileFromFile(file: File): MediaFile {
        return viewModel.createMediaFileFromFile(file)
    }

    override fun getFileOperationUseCase(): FileOperationUseCase {
        return viewModel.fileOperationUseCase
    }

    override fun getResourceName(): String? {
        return viewModel.state.value.resource?.name
    }

    override fun getLifecycleOwner(): LifecycleOwner {
        return lifecycleOwner
    }
}
