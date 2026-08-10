package com.sza.fastmediasorter.ui.browse.managers

import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.cloud.CloudProvider
import com.sza.fastmediasorter.ui.dialog.ScrollableTextDialog
import com.sza.fastmediasorter.util.showBoundToHost

internal class BrowseFeedbackDialogManager(
    private val activity: AppCompatActivity,
    private val callbacks: BrowseDialogHelper.DialogCallbacks
) {
    fun showErrorDialog(message: String, details: String?) {
        val dialogBuilder = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.error_title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)

        if (!details.isNullOrBlank()) {
            dialogBuilder.setNeutralButton(R.string.show_details) { _, _ ->
                showErrorDetailsDialog(details)
            }
        }

        val dialog = dialogBuilder.showBoundToHost(activity)
        dialog?.let { com.sza.fastmediasorter.core.ui.DialogAccessibilityHelper.applyInitialFocus(it) }
    }

    fun showCloudAuthenticationDialog(
        provider: CloudProvider,
        resourceName: String,
        onRemoveResource: () -> Unit = {}
    ) {
        val providerName = when (provider) {
            CloudProvider.GOOGLE_DRIVE -> activity.getString(R.string.google_drive)
            CloudProvider.DROPBOX -> activity.getString(R.string.dropbox)
            CloudProvider.ONEDRIVE -> activity.getString(R.string.onedrive)
        }

        MaterialAlertDialogBuilder(activity)
            .setTitle(activity.getString(R.string.authentication_required))
            .setMessage(activity.getString(R.string.cloud_auth_dialog_message, resourceName, providerName))
            .setPositiveButton(activity.getString(R.string.sign_in_now)) { _, _ ->
                callbacks.onCloudSignInRequested(provider)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(activity.getString(R.string.remove_resource)) { _, _ ->
                onRemoveResource()
            }
            .showBoundToHost(activity)
    }

    private fun showErrorDetailsDialog(details: String) {
        ScrollableTextDialog.show(
            context = activity,
            title = activity.getString(R.string.error_details_title),
            message = details
        )
    }
}
