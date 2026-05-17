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
import com.sza.fastmediasorter.ui.player.VrForcedFormatResolver
import com.sza.fastmediasorter.ui.player.entry.VrTaskTransition
import kotlinx.coroutines.launch
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

                    val playerIntent = if (file != null && shouldLaunchStandardPlayer(file)) {
                        createStandardPlayerIntent(resourceId, event.fileIndex, event.filePath)
                    } else {
                        // S0026: forward detected stereo mode so VrPlayerActivity primes the
                        // coordinator before settings apply (otherwise inner route decision sees MONO).
                        val detectedForVr = file?.let { detectStereoForLaunch(it) }
                        PlayerActivity.createIntent(
                            activity,
                            resourceId,
                            event.fileIndex,
                            skipAvailabilityCheck,
                            event.filePath,
                            detectedStereoMode = detectedForVr,
                        )
                    }

                    if (VrTaskTransition.shouldEnterImmersiveTask(playerIntent)) {
                        // Hybrid-app entry: browse task is destroyed on enterImmersive, so no
                        // activity result can be delivered back. Standard-player intents (above
                        // if-branch) still go through playerActivityLauncher to preserve the
                        // EXTRA_MODIFIED_FILES result contract used by the browse list.
                        Timber.d(
                            "VR_AUDIT/5: BrowseEventHandler startActivity target=VR component=%s extras_detected=%s file=%s",
                            playerIntent.component?.className,
                            playerIntent.getStringExtra(PlayerActivity.EXTRA_DETECTED_STEREO_MODE),
                            event.filePath,
                        )
                        VrTaskTransition.enterImmersive(activity, playerIntent)
                    } else {
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
            }
            is BrowseEvent.NavigateToTextEditor -> {
                // S0189: open text file in edit mode — bypasses normal file-index resolution.
                // Use createPanelIntent to target the 2D PlayerActivity directly: TEXT is never a
                // VR-eligible surface, so we must not route through the per-flavor
                // BuildConfig.PLAYER_ACTIVITY_CLASS override (which on noLegal/vr resolves to
                // VrPlayerActivity and triggers the «VR Headset Required» fallback on phones).
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

    private suspend fun shouldLaunchStandardPlayer(file: MediaFile): Boolean {
        if (file.type != MediaType.VIDEO) {
            // Keep still images and document viewers in the standard player for now.
            // The vr flavor currently lacks an explicit "enter immersive" step, so routing
            // non-video content through VrPlayerActivity would drop the user into XR immediately.
            Timber.i(
                "BrowseEventHandler: force standard player for non-video file=%s type=%s",
                file.path,
                file.type,
            )
            return true
        }

        val settings = viewModel.getSettings()

        // Global VR kill-switch: when enabled, all 3D/VR detection is bypassed and content
        // follows the standard 2D player path regardless of format.
        if (settings.disable3dVr) {
            Timber.i(
                "BrowseEventHandler: disable3dVr=true — forcing standard player for file=%s",
                file.path,
            )
            return true
        }

        val detectedByFilename = stereoDetector.detectFromFilename(file.path)
        val detectedMode = if (detectedByFilename != StereoMode.UNKNOWN) {
            detectedByFilename
        } else if (file.width != null && file.height != null) {
            stereoDetector.detectFromDimensions(file.width, file.height)
        } else {
            StereoMode.UNKNOWN
        }

        val effectiveMode = if (!settings.vrAutoDetectFormat && !detectedMode.isSpherical()) {
            // Flat content must stay on the standard path when VR auto-detection is disabled,
            // otherwise the user sees the VR host flash before fallback.
            StereoMode.MONO
        } else {
            VrForcedFormatResolver.resolve(
                detected = detectedMode,
                perFileOverride = null,
                forcedPlat = VrForcedFormatResolver.mapPlatSetting(settings.vrForcedPlatFormat),
                forcedSpherical = VrForcedFormatResolver.mapSphericalSetting(settings.vrForcedSphericalFormat),
            )
        }

        val route = BrowseRoutingDecision.decide(file, effectiveMode, settings)
        val shouldUseStandard = route == BrowseRoutingDecision.Route.STANDARD_PLAYER
        Timber.i(
            "BrowseEventHandler: route file=%s type=%s detected=%s effective=%s autoDetect=%b autoImmersive=%b -> standard=%b",
            file.path,
            file.type,
            detectedMode,
            effectiveMode,
            settings.vrAutoDetectFormat,
            settings.vrAutoImmersive,
            shouldUseStandard,
        )
        return shouldUseStandard
    }

    private fun createStandardPlayerIntent(
        resourceId: Long,
        fileIndex: Int,
        filePath: String,
    ): Intent = Intent(activity, PlayerActivity::class.java).apply {
        putExtra("resourceId", resourceId)
        putExtra("initialIndex", fileIndex)
        putExtra("skipAvailabilityCheck", skipAvailabilityCheck)
        putExtra("initialFilePath", filePath)
    }

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
