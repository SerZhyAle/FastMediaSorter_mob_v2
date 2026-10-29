package com.sza.fastmediasorter.ui.streams.helpers

import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.di.UnitSystemEntryPoint
import com.sza.fastmediasorter.domain.model.BroadcastStartState
import com.sza.fastmediasorter.domain.model.Quantity
import com.sza.fastmediasorter.util.showBoundTo
import com.sza.fastmediasorter.utils.collectOnLifecycle
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow

/**
 * S4247: the "has not started yet" state of a row imported from a link handed out before its broadcast began
 * (LIVE-BROADCAST item S). The relay is asked for the start only while this dialog is shown; at the start the
 * person presses Play, or it plays at once when the setting says so - playing stays the person's action (CI).
 */
class StreamBroadcastWaitManager(
    private val activity: AppCompatActivity,
    private val watchStart: (streamId: String, endpoints: String?) -> Flow<BroadcastStartState>,
) {

    private var dialog: AlertDialog? = null
    private var watching: Job? = null

    fun show(streamId: String, endpoints: String?, playAtOnce: Boolean, onPlay: () -> Unit) {
        dismiss()
        // The dialog opens on the first state, so it never shows a message without the link's expiry.
        watching = activity.collectOnLifecycle(watchStart(streamId, endpoints)) { state ->
            render(state, playAtOnce, onPlay)
        }
    }

    fun dismiss() {
        dialog?.dismiss()
        forget()
    }

    private fun render(state: BroadcastStartState, playAtOnce: Boolean, onPlay: () -> Unit) {
        when (state) {
            is BroadcastStartState.Waiting -> showing(waitMessage(state.untilMs), onPlay)
            BroadcastStartState.Started -> if (playAtOnce) {
                dismiss()
                onPlay()
            } else {
                showing(activity.getString(R.string.stream_broadcast_wait_started), onPlay)
                    ?.getButton(AlertDialog.BUTTON_POSITIVE)
                    ?.apply {
                        isVisible = true
                        requestFocus()
                    }
            }
            BroadcastStartState.Expired -> {
                dismiss()
                Toast.makeText(activity, R.string.stream_broadcast_link_expired, Toast.LENGTH_LONG).show()
            }
        }
    }

    /** The open dialog with [message], built on first use; null when the screen can no longer show one. */
    private fun showing(message: String, onPlay: () -> Unit): AlertDialog? {
        dialog?.let { open ->
            open.setMessage(message)
            return open
        }
        val shown = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.stream_broadcast_wait_title)
            .setMessage(message)
            .setPositiveButton(R.string.stream_broadcast_wait_play) { _, _ -> onPlay() }
            .setNegativeButton(R.string.close, null)
            // The dismiss message is posted, so a replaced dialog's listener must not stop its successor's watch.
            .setOnDismissListener { closed -> if (dialog === closed) forget() }
            .showBoundTo(activity)
        shown?.getButton(AlertDialog.BUTTON_POSITIVE)?.isVisible = false
        shown?.getButton(AlertDialog.BUTTON_NEGATIVE)?.requestFocus()
        dialog = shown
        return shown
    }

    private fun waitMessage(untilMs: Long): String {
        val seam = EntryPointAccessors.fromApplication(activity.applicationContext, UnitSystemEntryPoint::class.java)
        return activity.getString(
            R.string.stream_broadcast_wait_message,
            seam.quantityFormatter().format(Quantity.DateTime(untilMs), seam.unitSystemProvider().value),
        )
    }

    private fun forget() {
        watching?.cancel()
        watching = null
        dialog = null
    }
}
