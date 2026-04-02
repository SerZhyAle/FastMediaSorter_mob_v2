package com.sza.fastmediasorter.ui.player.helpers

import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.player.PlayerActivity
import com.sza.fastmediasorter.ui.player.PlayerViewModel
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Handles player error display and ViewModel event dispatching.
 * Extracted from PlayerActivity to reduce its size.
 */
class PlayerEventHandler(private val activity: PlayerActivity) {

    fun handleEvent(event: PlayerViewModel.PlayerEvent) {
        when (event) {
            is PlayerViewModel.PlayerEvent.ShowError ->
                showError(event.message)
            is PlayerViewModel.PlayerEvent.ShowMessage ->
                Toast.makeText(activity, event.message, Toast.LENGTH_SHORT).show()
            is PlayerViewModel.PlayerEvent.FileModified ->
                activity.lifecycleManager.trackModifiedFile(event.filePath)
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
        }
    }

    fun showError(message: String, throwable: Throwable? = null) {
        if (activity.isFinishing || activity.isDestroyed) {
            Timber.w("showError: Activity is finishing/destroyed, skipping error dialog")
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
                if (throwable != null) {
                    com.sza.fastmediasorter.ui.dialog.ErrorDialog.show(
                        context = activity,
                        title = activity.getString(R.string.error),
                        message = message,
                        details = throwable.stackTraceToString()
                    )
                } else {
                    com.sza.fastmediasorter.ui.dialog.ErrorDialog.show(
                        context = activity,
                        title = activity.getString(R.string.error),
                        message = message
                    )
                }
            } else {
                com.sza.fastmediasorter.util.ToastThrottler.showNetworkError(activity, message)
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
                .show()
        } catch (e: WindowManager.BadTokenException) {
            Timber.e(e, "PlayerEventHandler: showFileNotFound failed — bad window token")
        }
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
                    com.sza.fastmediasorter.ui.dialog.ErrorDialog.show(
                        context = activity,
                        title = activity.getString(R.string.error),
                        message = message,
                        actionButtonText = activity.getString(R.string.open_in_external_player),
                        onActionClick = { activity.shareManager.openInExternalPlayer(filePath) }
                    )
                } else {
                    val networkMessage = "$message\n\n${activity.getString(R.string.unsupported_format_network_hint)}"
                    com.sza.fastmediasorter.ui.dialog.ErrorDialog.show(
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
}
