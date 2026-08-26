package com.sza.fastmediasorter.ui.player.helpers

import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.error.ErrorSeverity
import com.sza.fastmediasorter.domain.mutation.Mutation
import com.sza.fastmediasorter.ui.player.PlayerActivity
import com.sza.fastmediasorter.ui.player.PlayerViewModel
import com.sza.fastmediasorter.ui.streams.StreamUnavailableDialog
import com.sza.fastmediasorter.util.AppErrorNotifier
import com.sza.fastmediasorter.util.showBoundTo
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID

/**
 * Handles player error display and ViewModel event dispatching.
 * Extracted from PlayerActivity to reduce its size.
 */
class PlayerEventHandler(private val activity: PlayerActivity) {

    // Track any open error dialog so we can dismiss it if the Activity is destroyed
    // while the dialog is still showing (prevents WindowLeaked).
    private var activeDialog: androidx.appcompat.app.AlertDialog? = null

    /** Must be called from PlayerActivity.onDestroy() to clean up open dialogs. */
    fun onDestroy() {
        activeDialog?.dismiss()
        activeDialog = null
    }

    fun handleEvent(event: PlayerViewModel.PlayerEvent) {
        when (event) {
            is PlayerViewModel.PlayerEvent.ShowError ->
                showError(event.message)
            is PlayerViewModel.PlayerEvent.ShowMessage ->
                Toast.makeText(activity, event.message, Toast.LENGTH_SHORT).show()
            is PlayerViewModel.PlayerEvent.FileModified -> {
                // S0242 Phase 02: FileModified is fired only after a successful delete
                // from PlayerDeleteUndoCoordinator - journal it as Mutation.Delete so the
                // Browse Reconciler picks it up on its next onResume.
                val resourceId = activity.lifecycleManager.currentResourceId()
                val resourceType = activity.lifecycleManager.currentResourceType()
                if (resourceId != null && resourceType != null) {
                    activity.lifecycleManager.recordMutation(
                        Mutation.Delete(
                            resourceId = resourceId,
                            canonicalPath = activity.lifecycleManager
                                .pathNormalizer()
                                .canonical(event.filePath, resourceType),
                            opId = UUID.randomUUID().toString(),
                            timestampMs = System.currentTimeMillis(),
                        )
                    )
                }
            }
            is PlayerViewModel.PlayerEvent.ShowUndoSnackbar ->
                activity.undoOperationManager.showUndoSnackbar(event.operation)
            is PlayerViewModel.PlayerEvent.CloudAuthRequired ->
                showCloudAuthenticationError(event.provider)
            is PlayerViewModel.PlayerEvent.ShowMissingFileInfo -> {
                Toast.makeText(
                    activity,
                    activity.getString(R.string.file_not_found_playing_from_start, event.fileName),
                    Toast.LENGTH_LONG
                ).show()
            }
            PlayerViewModel.PlayerEvent.FinishActivity -> activity.finish()
            is PlayerViewModel.PlayerEvent.CastStateChanged -> {
                val msg = if (event.isCasting && event.deviceName != null) {
                    activity.getString(com.sza.fastmediasorter.R.string.cast_connected, event.deviceName)
                } else if (!event.isCasting) {
                    activity.getString(com.sza.fastmediasorter.R.string.cast_disconnected)
                } else null
                if (msg != null) Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
            }
            is PlayerViewModel.PlayerEvent.ShowVrInstallCta -> {
                showVrInstallCtaDialog()
            }
            PlayerViewModel.PlayerEvent.StopPlayback -> {
                activity._videoPlayerManager?.getPlayer()?.pause()
                activity.audioServiceController?.player?.pause()
                Toast.makeText(activity, R.string.playback_order_stopped, Toast.LENGTH_SHORT).show()
            }
            // S0162: rotation sensor toggled from player button
            is PlayerViewModel.PlayerEvent.RotationSensorToggled -> {
                activity.onRotationSensorToggled(event.sensorEnabled)
            }
            // S0581: a list stream failed to play - offer retry / remove-from-list.
            is PlayerViewModel.PlayerEvent.ShowStreamUnavailable -> {
                showStreamUnavailable(event.source, event.offline)
            }
        }
    }

    fun showError(message: String, throwable: Throwable? = null) {
        val isInPip = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N &&
            activity.isInPictureInPictureMode
        if (activity.isFinishing || activity.isDestroyed || isInPip) {
            Timber.w("showError: Activity is finishing/destroyed/in PiP, skipping error dialog")
            return
        }

        // Handle Android 10+ RecoverableSecurityException for delete operations
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q &&
            throwable is android.app.RecoverableSecurityException
        ) {
            try {
                Timber.i("showError: Launching delete permission request for user")
                val intentSender = throwable.userAction.actionIntent.intentSender
                activity.deletePermissionLauncher.launch(
                    IntentSenderRequest.Builder(intentSender).build()
                )
                return
            } catch (e: Exception) {
                Timber.e(e, "showError: Failed to launch delete permission request")
                // Fall through to show error message
            }
        }

        activity.lifecycleScope.launch {
            val settings = activity.viewModel.getSettings()
            val isSlideshowActive = activity.viewModel.state.value.isSlideShowActive &&
                !activity.viewModel.state.value.isPaused

            if (settings.showDetailedErrors && !isSlideshowActive) {
                if (activity.isFinishing || activity.isDestroyed) {
                    Timber.w("showError: Activity finished during settings load, skipping dialog")
                    return@launch
                }
                // S0118: keep the richer dialog surface, but do not expose raw stack traces to users.
                activeDialog = com.sza.fastmediasorter.ui.dialog.ScrollableTextDialog.show(
                    context = activity,
                    title = activity.getString(R.string.error),
                    message = message
                )
            } else {
                AppErrorNotifier.show(
                    activity = activity,
                    message = message,
                    severity = ErrorSeverity.CRITICAL
                )
            }
        }
    }

    fun showFileNotFound(fileName: String) {
        if (activity.isFinishing || activity.isDestroyed) return
        try {
            AlertDialog.Builder(activity)
                .setTitle(R.string.file_not_found_title)
                .setMessage(activity.getString(R.string.player_file_not_found_message, fileName))
                .setPositiveButton(android.R.string.ok, null)
                .showBoundTo(activity)
        } catch (e: WindowManager.BadTokenException) {
            Timber.e(e, "PlayerEventHandler: showFileNotFound failed - bad window token")
        }
    }

    /**
     * S0581: a video/RTSP stream from the list did not respond. Offer to retry the same URL or
     * remove the dead stream from the local list (then leave the player, since there is nothing to
     * show). Cancel simply closes the player.
     *
     * S1509: [offline] suppresses the remove action - the dialog now lives in [StreamUnavailableDialog]
     * so this host and the streams screen ask the same question rather than two copies of it.
     */
    private fun showStreamUnavailable(
        source: com.sza.fastmediasorter.data.local.db.StreamSourceEntity,
        offline: Boolean
    ) {
        val isInPip = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N &&
            activity.isInPictureInPictureMode
        if (isInPip) {
            Timber.w("showStreamUnavailable: in PiP mode - suppressing dialog, pausing player")
            activity._videoPlayerManager?.pause()
            return
        }
        activeDialog?.dismiss()
        activeDialog = StreamUnavailableDialog.show(
            activity = activity,
            channelTitle = source.title,
            offline = offline,
            onRetry = { activity.playVideo(source.url) },
            onRemove = {
                activity.viewModel.removeStreamSource(source)
                activity.finish()
            },
            onDismiss = { activity.finish() },
        )
    }

    fun showCloudAuthenticationError(providerName: String? = null) {
        activity.dialogHelper.showCloudAuthError(providerName) {
            when (providerName?.lowercase()) {
                "dropbox" -> activity.cloudAuthManager.launchDropboxSignIn()
                "google drive", "google_drive" -> activity.cloudAuthManager.launchGoogleSignIn()
                "onedrive" -> activity.cloudAuthManager.launchOneDriveSignIn()
                else -> Timber.w("Unknown provider for auth request: $providerName")
            }
        }
    }

    fun showUnsupportedFormatError(message: String, filePath: String, isLocalFile: Boolean) {
        if (activity.isFinishing || activity.isDestroyed) {
            Timber.w("showUnsupportedFormatError: Activity is finishing/destroyed, skipping dialog")
            return
        }

        activity.lifecycleScope.launch {
            val settings = activity.viewModel.getSettings()
            val isSlideshowActive = activity.viewModel.state.value.isSlideShowActive &&
                !activity.viewModel.state.value.isPaused

            if (settings.showDetailedErrors && !isSlideshowActive) {
                if (activity.isFinishing || activity.isDestroyed) {
                    Timber.w("showUnsupportedFormatError: Activity finished during settings load, skipping dialog")
                    return@launch
                }
                if (isLocalFile) {
                    activeDialog = com.sza.fastmediasorter.ui.dialog.ScrollableTextDialog.show(
                        context = activity,
                        title = activity.getString(R.string.error),
                        message = message,
                        actionButtonText = activity.getString(R.string.open_in_external_player),
                        onActionClick = { activity.shareManager.openInExternalPlayer(filePath) }
                    )
                } else {
                    val networkMessage = "$message\n\n${activity.getString(R.string.unsupported_format_network_hint)}"
                    activeDialog = com.sza.fastmediasorter.ui.dialog.ScrollableTextDialog.show(
                        context = activity,
                        title = activity.getString(R.string.error),
                        message = networkMessage
                    )
                }
            } else {
                val toastMessage = if (isLocalFile) {
                    activity.getString(R.string.unsupported_format_use_external_player)
                } else {
                    activity.getString(R.string.unsupported_format_copy_to_local)
                }
                Toast.makeText(activity, toastMessage, Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Show VR install CTA dialog when 3D content is detected in standard flavor.
     *
     * S0232 - Play Store launch is intentionally disabled. The previous target package
     * `com.sza.fastmediasorter.vr` no longer exists (after S0232 the `vr` flavor and
     * `noLegal` share `applicationId = com.sza.fastmediasorter` with standard; S0250
     * 2026-05-19 archived the former `vrUnlicensed` flavor entirely), and the VR
     * edition is not published yet anyway. Quest VR builds, when published, will land on
     * Meta Horizon Store rather than Play Store - Play Store is not the correct target.
     *
     * TODO(S0232 §3 "vr re-suffix on Store publication"): wire the positive button to the
     * actual store listing once the VR edition is published. At that point the action
     * must dispatch to Meta Horizon Store (Quest) or Play Store (Android XR) based on
     * the runtime device profile, not a hard-coded Play Store URL.
     */
    private fun showVrInstallCtaDialog() {
        if (activity.isFinishing || activity.isDestroyed) return

        try {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.vr_install_cta_title)
                .setMessage(R.string.vr_install_cta_message)
                // S0232: positive button removed - store launch is dead until VR edition ships.
                .setNegativeButton(R.string.vr_install_cta_dismiss) { dialog, _ ->
                    dialog.dismiss()
                }
                .setCancelable(true)
                .show()
        } catch (e: android.view.WindowManager.BadTokenException) {
            Timber.e(e, "showVrInstallCtaDialog: bad window token")
        }
    }
}
