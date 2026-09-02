package com.sza.fastmediasorter.ui.player

import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.dialog.DialogKeyboardDelegate

/**
 * S2151: the calm answer to a media file that did not load for a recognised network reason, raised in
 * place of the red critical error that used to describe an unreachable server and a corrupt local file
 * with the same sentence.
 *
 * [gone] chooses the wording and nothing else: a silent server is temporary and worth retrying, while a
 * server that answered "no such file" is final. [offerRemoveFavorite] is a separate flag rather than
 * [gone] itself, because the removal action is only honest when the file is final AND actually in the
 * favorites list - the host's toggle would otherwise ADD a gone file to favorites on a list where it
 * was never saved.
 *
 * Actions are lambdas rather than the file, following [com.sza.fastmediasorter.ui.streams.StreamUnavailableDialog]:
 * dismissal means different things to different hosts.
 */
object MediaUnavailableDialog {

    /** Returns the shown dialog, or null when the host was already tearing down, for hosts that track it. */
    fun show(
        activity: AppCompatActivity,
        fileName: String,
        gone: Boolean,
        offerRemoveFavorite: Boolean,
        onRetry: () -> Unit,
        onRemoveFromFavorites: () -> Unit,
        onDismiss: () -> Unit,
    ): AlertDialog? {
        // The dialog outlives the failure that asked for it, so a host already tearing down must not
        // attach a window.
        if (activity.isFinishing || activity.isDestroyed) return null
        val titleRes = if (gone) R.string.media_gone_title else R.string.media_unavailable_title
        val messageRes = if (gone) R.string.media_gone_message else R.string.media_unavailable_message
        val builder = MaterialAlertDialogBuilder(activity)
            .setTitle(titleRes)
            .setMessage(activity.getString(messageRes, fileName))
            .setPositiveButton(R.string.retry) { _, _ -> onRetry() }
            .setNegativeButton(android.R.string.cancel) { _, _ -> onDismiss() }
            .setOnCancelListener { onDismiss() }
        if (offerRemoveFavorite) {
            builder.setNeutralButton(R.string.streams_remove_from_favorites) { _, _ -> onRemoveFromFavorites() }
        }
        val dialog = builder.create()
        DialogKeyboardDelegate.applyTo(dialog) {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.performClick()
        }
        dialog.show()
        return dialog
    }
}
