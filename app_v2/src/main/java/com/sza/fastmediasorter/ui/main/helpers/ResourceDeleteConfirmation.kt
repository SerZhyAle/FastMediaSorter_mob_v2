package com.sza.fastmediasorter.ui.main.helpers

import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.util.showBoundToHost

/**
 * S1424: the one delete-resource confirmation, raised from wherever a resource can be deleted.
 *
 * Extracted from MainActivity, where it was private, because the launcher desktop deletes a resource
 * too and the owner ruled it has to ask the same question rather than a copy of it (strategic 6.2).
 * There is no precedent for deleting a resource outside the main window, so the wording had no
 * chance to drift yet - and a divergence in the wording of a delete confirmation is the worst kind.
 *
 * The caller supplies [onConfirm] rather than a resource, because the two hosts reach the delete
 * through different objects: the main window through its ViewModel, the desktop through the use case.
 */
object ResourceDeleteConfirmation {

    fun show(activity: AppCompatActivity, resourceName: String, onConfirm: () -> Unit) {
        // The dialog outlives the tap that asked for it, so a host already tearing down must not
        // attach a window - this guard came across from MainActivity with the rest.
        if (activity.isFinishing || activity.isDestroyed) return
        MaterialAlertDialogBuilder(
            activity,
            R.style.ThemeOverlay_FastMediaSorter_MaterialAlertDialog_Destructive,
        )
            .setTitle(R.string.delete_resource_title)
            .setMessage(activity.getString(R.string.delete_resource_message, resourceName))
            .setPositiveButton(R.string.delete) { _, _ -> onConfirm() }
            .setNegativeButton(android.R.string.cancel, null)
            .showBoundToHost(activity)
    }
}
