package com.sza.fastmediasorter.ui.browse.managers

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.cloud.CloudProvider
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.UndoOperation
import com.sza.fastmediasorter.ui.browse.BrowseEvent
import com.sza.fastmediasorter.ui.browse.BrowseViewModel
import com.sza.fastmediasorter.ui.player.PlayerActivity
import timber.log.Timber

/**
 * Handles BrowseEvent dispatch from ViewModel events flow.
 *
 * Extracted from BrowseActivity.observeData() (Wave 1.5 decomposition — IV.1).
 */
class BrowseEventHandler(
    private val activity: Activity,
    private val viewModel: BrowseViewModel,
    private val errorDisplayManager: BrowseErrorDisplayManager,
    private val archiveDialogManager: BrowseArchiveDialogManager,
    private val resourceOpsMenuManager: ResourceOpsMenuManager,
    private val permissionRequestLauncher: ActivityResultLauncher<IntentSenderRequest>,
    private val playerActivityLauncher: ActivityResultLauncher<Intent>,
    private val onShowCloudAuthDialog: (CloudProvider) -> Unit,
    private val skipAvailabilityCheck: Boolean
) {

    fun handleEvent(event: BrowseEvent) {
        when (event) {
            is BrowseEvent.ShowError -> {
                errorDisplayManager.showError(event.message, event.details, event.exception)
            }
            is BrowseEvent.ShowMessage -> {
                Toast.makeText(activity, event.message, Toast.LENGTH_SHORT).show()
            }
            is BrowseEvent.ShowUndoToast -> {
                val operation = viewModel.state.value.lastOperation
                if (operation != null) {
                    errorDisplayManager.showUndoSnackbar(operation)
                }
            }
            is BrowseEvent.NavigateToPlayer -> {
                viewModel.inlineStop()
                val resourceId = viewModel.state.value.resource?.id ?: 0L
                val playerIntent = PlayerActivity.createIntent(
                    activity,
                    resourceId,
                    event.fileIndex,
                    skipAvailabilityCheck,
                    event.filePath
                )
                playerActivityLauncher.launch(playerIntent)
                @Suppress("DEPRECATION")
                activity.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            }
            is BrowseEvent.ShowCloudAuthenticationRequired -> {
                onShowCloudAuthDialog(event.provider)
            }
            is BrowseEvent.CloudAuthRequired -> {
                Toast.makeText(activity, event.message, Toast.LENGTH_LONG).show()
            }
            is BrowseEvent.NoFilesFound -> {
                val msg = if (event.messageResId != null) {
                    activity.getString(event.messageResId)
                } else {
                    event.message ?: ""
                }
                Toast.makeText(activity, msg, Toast.LENGTH_LONG).show()
                activity.finish()
            }
            is BrowseEvent.PermissionRequired -> {
                launchPermissionRequest(event.pendingIntent)
            }
            is BrowseEvent.ShowDeleteBySizePreview -> {
                resourceOpsMenuManager.showDeleteBySizeConfirm(
                    viewModel,
                    event.count,
                    event.totalBytes,
                    event.matchedFiles
                )
            }
            is BrowseEvent.ArchiveProgress ->
                archiveDialogManager.updateArchiveProgress(event.current, event.total, event.fileName)
            is BrowseEvent.ArchiveSuccess -> {
                archiveDialogManager.onArchiveSuccess(event.archivePath, event.archivedCount)
            }
            is BrowseEvent.ArchiveError -> {
                archiveDialogManager.onArchiveError(event.message)
                errorDisplayManager.showError(
                    activity.getString(R.string.archive_error, event.message),
                    details = null,
                    exception = event.exception
                )
            }
            is BrowseEvent.ShowExtractConfirmDialog ->
                archiveDialogManager.showUnarchiveConfirmDialog(event.file, event.targetDirName)
            is BrowseEvent.ExtractionProgress ->
                archiveDialogManager.updateExtractProgress(event)
            is BrowseEvent.ExtractionSuccess ->
                archiveDialogManager.onExtractionSuccess(event.targetPath)
            is BrowseEvent.ExtractionFailed ->
                archiveDialogManager.onExtractionFailed(event.message)
            is BrowseEvent.ResourceAddedAsDestination -> {
                showAddedAsDestinationSnackbar()
            }
        }
    }

    private fun launchPermissionRequest(pendingIntent: PendingIntent) {
        Timber.i("BrowseActivity: ========================================")
        Timber.i("BrowseActivity: PERMISSION REQUIRED EVENT RECEIVED")
        Timber.i("BrowseActivity: PendingIntent: $pendingIntent")
        try {
            Timber.i("BrowseActivity: Building IntentSenderRequest...")
            val intentSenderRequest = IntentSenderRequest.Builder(pendingIntent).build()
            Timber.i("BrowseActivity: Launching permission request...")
            permissionRequestLauncher.launch(intentSenderRequest)
            Timber.i("BrowseActivity: Permission request launched successfully")
        } catch (e: Exception) {
            Timber.e(e, "BrowseActivity: FAILED to launch permission request")
            Toast.makeText(activity, R.string.failed_to_request_permission, Toast.LENGTH_SHORT).show()
        }
        Timber.i("BrowseActivity: ========================================")
    }

    private fun showAddedAsDestinationSnackbar() {
        com.google.android.material.snackbar.Snackbar
            .make(
                activity.findViewById(android.R.id.content),
                activity.getString(R.string.msg_added_as_receiver),
                com.google.android.material.snackbar.Snackbar.LENGTH_LONG
            )
            .setAction(activity.getString(R.string.btn_edit_receiver)) {
                activity.startActivity(
                    Intent(activity, com.sza.fastmediasorter.ui.settings.SettingsActivity::class.java).apply {
                        putExtra(com.sza.fastmediasorter.ui.settings.SettingsActivity.EXTRA_INITIAL_TAB, 3)
                    }
                )
            }
            .show()
    }
}
