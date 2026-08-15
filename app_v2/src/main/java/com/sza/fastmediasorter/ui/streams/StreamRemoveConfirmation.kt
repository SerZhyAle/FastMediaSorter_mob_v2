package com.sza.fastmediasorter.ui.streams

import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.dialog.DialogKeyboardDelegate
import com.sza.fastmediasorter.util.showBoundToHost
import timber.log.Timber

/**
 * S1424: the one remove-channel confirmation, raised from wherever a channel can be removed.
 *
 * Extracted from StreamsActivity, where it was private, because the launcher desktop removes a
 * channel too and the owner ruled a destructive action asks the same question rather than a copy of
 * it (strategic 6.2).
 *
 * The caller supplies [onConfirm] rather than the channel, because the two hosts reach the removal
 * through different objects: the streams screen through its ViewModel, the desktop through the use
 * case and the frame store.
 */
object StreamRemoveConfirmation {

    fun show(activity: AppCompatActivity, title: String, onConfirm: () -> Unit) {
        // The dialog outlives the tap that asked for it, so a host already tearing down must not
        // attach a window.
        if (activity.isFinishing || activity.isDestroyed) return
        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.streams_remove)
            .setMessage(title)
            .setPositiveButton(R.string.streams_remove) { _, _ -> onConfirm() }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        DialogKeyboardDelegate.applyTo(dialog) {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.performClick()
        }
        dialog.showBoundToHost(activity)
    }
}
