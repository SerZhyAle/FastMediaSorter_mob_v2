package com.sza.fastmediasorter.ui.streams

import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.dialog.DialogKeyboardDelegate

/**
 * S1509: the one terminal "this channel did not play" dialog, raised from both hosts that can reach it -
 * the fullscreen player (video / RTSP) and the streams screen (inline radio).
 *
 * It used to exist as two independent copies, and both offered to remove the channel whatever the cause
 * of the failure was. [offline] is the distinction neither copy could make: with no network on the
 * device the failure says nothing about the channel, so the removal button is not attached at all.
 *
 * The caller supplies the three actions rather than the channel, because the hosts reach retry and
 * removal through different objects (ViewModel here, player glue there) and disagree on what dismissing
 * means - the player leaves the screen, the list stays where it is.
 *
 * S4444: [showEnded] is the same question for a broadcast its owner ended - a variant here, not a second surface.
 */
object StreamUnavailableDialog {

    /** Returns the shown dialog, or null when the host was already tearing down, for hosts that track it. */
    fun show(
        activity: AppCompatActivity,
        channelTitle: String,
        offline: Boolean,
        onRetry: () -> Unit,
        onRemove: () -> Unit,
        onDismiss: () -> Unit,
    ): AlertDialog? {
        // The dialog outlives the failure that asked for it, so a host already tearing down must not
        // attach a window.
        if (activity.isFinishing || activity.isDestroyed) return null
        val titleRes =
            if (offline) R.string.streams_unavailable_offline_title else R.string.streams_unavailable_title
        val messageRes =
            if (offline) R.string.streams_unavailable_offline_message else R.string.streams_unavailable_message
        val builder = MaterialAlertDialogBuilder(activity)
            .setTitle(titleRes)
            .setMessage(activity.getString(messageRes, channelTitle))
            .setPositiveButton(R.string.retry) { _, _ -> onRetry() }
            .setNegativeButton(R.string.cancel) { _, _ -> onDismiss() }
            .setOnCancelListener { onDismiss() }
        if (!offline) {
            // Removal is offered only for a failure that is the channel's own: during an outage every
            // channel fails at once, and deleting one is a destructive answer to the wrong question.
            builder.setNeutralButton(R.string.streams_remove) { _, _ -> onRemove() }
        }
        return present(builder.create())
    }

    /**
     * S4444, LIVE-BROADCAST item T: the broadcast this channel plays was ended by its owner. Nothing failed, so the
     * dialog says so and offers the explicit reconnect the contract asks for instead of retrying on its own.
     * [removable] is false for a broadcast listed by the exchange directory, which is no saved row.
     */
    fun showEnded(
        activity: AppCompatActivity,
        channelTitle: String,
        removable: Boolean,
        onReconnect: () -> Unit,
        onRemove: () -> Unit,
        onDismiss: () -> Unit,
    ): AlertDialog? {
        if (activity.isFinishing || activity.isDestroyed) return null
        val builder = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.stream_broadcast_ended_title)
            .setMessage(activity.getString(R.string.stream_broadcast_ended_message, channelTitle))
            .setPositiveButton(R.string.stream_broadcast_reconnect) { _, _ -> onReconnect() }
            .setNegativeButton(R.string.close) { _, _ -> onDismiss() }
            .setOnCancelListener { onDismiss() }
        if (removable) builder.setNeutralButton(R.string.streams_remove) { _, _ -> onRemove() }
        return present(builder.create())
    }

    private fun present(dialog: AlertDialog): AlertDialog {
        DialogKeyboardDelegate.applyTo(dialog) {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.performClick()
        }
        dialog.show()
        return dialog
    }
}
