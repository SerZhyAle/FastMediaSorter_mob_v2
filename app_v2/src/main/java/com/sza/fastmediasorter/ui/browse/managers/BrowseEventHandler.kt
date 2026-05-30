package com.sza.fastmediasorter.ui.browse.managers

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import androidx.appcompat.app.AlertDialog
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.util.PermissionHelper
import com.sza.fastmediasorter.data.cloud.CloudProvider
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.StereoMode
import com.sza.fastmediasorter.domain.model.UndoOperation
import com.sza.fastmediasorter.ui.browse.BrowseActivity
import com.sza.fastmediasorter.ui.browse.BrowseEvent
import com.sza.fastmediasorter.ui.browse.BrowseViewModel
import com.sza.fastmediasorter.ui.player.PlayerActivity
import com.sza.fastmediasorter.ui.player.StereoDetector
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Handles BrowseEvent dispatch from ViewModel events flow.
 *
 * Extracted from BrowseActivity.observeData() (Wave 1.5 decomposition - IV.1).
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
    private val skipAvailabilityCheck: Boolean,
    private val onScrollToFile: ((String) -> Unit)? = null
) {
    private val stereoDetector = StereoDetector()

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
                val activityHost = activity as? ComponentActivity
                if (activityHost == null) {
                    Timber.e("BrowseEventHandler: activity is not a ComponentActivity, cannot launch player")
                    return
                }

                activityHost.lifecycleScope.launch {
                    val resourceId = viewModel.state.value.resource?.id ?: 0L
                    val file = viewModel.state.value.mediaFiles.getOrNull(event.fileIndex)
                        ?.takeIf { it.path == event.filePath }
                        ?: viewModel.state.value.mediaFiles.firstOrNull { it.path == event.filePath }


                    // S0241 Phase 02: Browse no longer launches the immersive task directly.
                    // We still forward the detected stereo hint so the flat player starts with
                    // the same context that previously primed the VR host.
                    val detectedStereoMode = file?.let { detectStereoForLaunch(it) }
                        ?.takeUnless { it == StereoMode.UNKNOWN }
                    val playerIntent = createStandardPlayerIntent(
                        resourceId = resourceId,
                        fileIndex = event.fileIndex,
                        filePath = event.filePath,
                        detectedStereoMode = detectedStereoMode,
                    )

                    Timber.d(
                        "VR_AUDIT/5: BrowseEventHandler startActivity target=PANEL component=%s extras_detected=%s file=%s",
                        playerIntent.component?.className,
                        playerIntent.getStringExtra(PlayerActivity.EXTRA_DETECTED_STEREO_MODE),
                        event.filePath,
                    )
                    playerActivityLauncher.launch(playerIntent)
                    @Suppress("DEPRECATION")
                    activity.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                }
            }
            is BrowseEvent.NavigateToTextEditor -> {
                // S0189: open text file in edit mode - bypasses normal file-index resolution.
                // Use createPanelIntent to target PlayerActivity directly.
                val activityHost = activity as? ComponentActivity ?: run {
                    Timber.e("BrowseEventHandler: activity is not ComponentActivity, cannot launch text editor")
                    return
                }
                val playerIntent = PlayerActivity.createPanelIntent(
                    activity,
                    event.resourceId,
                    initialFilePath = event.filePath,
                ).putExtra(PlayerActivity.EXTRA_TEXT_EDIT_MODE_ON_OPEN, true)
                playerActivityLauncher.launch(playerIntent)
                @Suppress("DEPRECATION")
                activity.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            }
            is BrowseEvent.NavigateToDrawingEditor -> {
                val activityHost = activity as? ComponentActivity ?: run {
                    Timber.e("BrowseEventHandler: activity is not ComponentActivity, cannot launch drawing editor")
                    return
                }
                val playerIntent = PlayerActivity.createPanelIntent(
                    activity,
                    event.resourceId,
                    initialFilePath = event.filePath,
                ).putExtra(PlayerActivity.EXTRA_ACTIVATE_DRAW_MODE, true)
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
            is BrowseEvent.ShowArchivePasswordDialog ->
                archiveDialogManager.showArchivePasswordDialog(event.file, event.targetDirName)
            is BrowseEvent.ExtractionProgress ->
                archiveDialogManager.updateExtractProgress(event)
            is BrowseEvent.ExtractionSuccess ->
                archiveDialogManager.onExtractionSuccess(event.targetPath)
            is BrowseEvent.ExtractionFailed ->
                archiveDialogManager.onExtractionFailed(event.message)
            is BrowseEvent.ResourceAddedAsDestination -> {
                showAddedAsDestinationSnackbar()
            }
            is BrowseEvent.ScrollToFile -> {
                onScrollToFile?.invoke(event.fileName)
            }
            is BrowseEvent.ShowLocalNetworkPermissionRequired -> {
                showLocalNetworkPermissionRationale()
            }
        }
    }

    private fun showLocalNetworkPermissionRationale() {
        AlertDialog.Builder(activity)
            .setTitle(R.string.local_network_permission_rationale_title)
            .setMessage(R.string.local_network_permission_rationale_message)
            .setPositiveButton(R.string.local_network_permission_open_settings) { _, _ ->
                if (PermissionHelper.isLocalNetworkRuntimePermissionExpected()) {
                    PermissionHelper.requestLocalNetworkPermission(activity)
                } else {
                    PermissionHelper.routeToLocalNetworkSettings(activity)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
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

    private fun createStandardPlayerIntent(
        resourceId: Long,
        fileIndex: Int,
        filePath: String,
        detectedStereoMode: StereoMode? = null,
    ): Intent = PlayerActivity.createPanelIntent(
        context = activity,
        resourceId = resourceId,
        initialIndex = fileIndex,
        skipAvailabilityCheck = skipAvailabilityCheck,
        initialFilePath = filePath,
        detectedStereoMode = detectedStereoMode,
    )

    /**
     * S0026: same detection priority as [shouldLaunchStandardPlayer], but returns the actual
     * StereoMode for forwarding through intent-extras (not a boolean). Filename match wins over
     * dimension match; UNKNOWN if nothing matches.
     */
    private fun detectStereoForLaunch(file: MediaFile): StereoMode {
        if (file.type != MediaType.VIDEO) return StereoMode.UNKNOWN
        val byFilename = stereoDetector.detectFromFilename(file.path)
        if (byFilename != StereoMode.UNKNOWN) return byFilename
        if (file.width != null && file.height != null) {
            return stereoDetector.detectFromDimensions(file.width, file.height)
        }
        return StereoMode.UNKNOWN
    }

    // S0028: open BrowseActivity for a given resource in a new multi-window slot
    fun openBrowseInNewWindow(resourceId: Long) {
        val windowId = java.util.UUID.randomUUID().toString()
        val intent = Intent(activity, BrowseActivity::class.java).apply {
            putExtra(BrowseActivity.EXTRA_RESOURCE_ID, resourceId)
            putExtra(BrowseActivity.EXTRA_WINDOW_ID, windowId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        }
        activity.startActivity(intent)
    }

    // S0184: open the selected file's player in a new multi-window slot from Browse.
    fun openPlayerInNewWindow(file: MediaFile) {
        viewModel.inlineStop()
        val currentState = viewModel.state.value
        val resourceId = currentState.resource?.id ?: file.resourceId ?: 0L
        val fileIndex = currentState.mediaFiles.indexOfFirst { it.path == file.path }
            .takeIf { it >= 0 } ?: 0
        val detectedStereoMode = detectStereoForLaunch(file)
            .takeUnless { it == StereoMode.UNKNOWN }
        val windowId = java.util.UUID.randomUUID().toString()
        val intent = createStandardPlayerIntent(
            resourceId = resourceId,
            fileIndex = fileIndex,
            filePath = file.path,
            detectedStereoMode = detectedStereoMode,
        ).apply {
            putExtra(PlayerActivity.EXTRA_WINDOW_ID, windowId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        }
        activity.startActivity(intent)
    }

    // S0028: tear off current Browse to a new window; current activity finishes (returns to home)
    fun tearOffBrowse(resourceId: Long, currentFilePath: String?, scrollPosition: Int) {
        val windowId = java.util.UUID.randomUUID().toString()
        val intent = Intent(activity, BrowseActivity::class.java).apply {
            putExtra(BrowseActivity.EXTRA_WINDOW_ID, windowId)
            putExtra(BrowseActivity.EXTRA_RESOURCE_ID, resourceId)
            currentFilePath?.let { putExtra(BrowseActivity.EXTRA_INITIAL_FILE_PATH, it) }
            putExtra(BrowseActivity.EXTRA_SCROLL_POSITION, scrollPosition)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        }
        activity.startActivity(intent)
        activity.finish()
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
