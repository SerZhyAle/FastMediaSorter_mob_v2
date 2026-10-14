package com.sza.fastmediasorter.ui.exchange.helpers

import android.text.format.Formatter
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.DialogExchangeClipboardTransferBinding
import com.sza.fastmediasorter.ui.exchange.ExchangeClipboardUiState
import com.sza.fastmediasorter.ui.exchange.MyDevicesActivity
import com.sza.fastmediasorter.util.showBoundTo

/**
 * The one dialog of the clipboard window (strategic S4226 §5.1 pillar D): progress with "Hide" and "Cancel"
 * while a transfer runs, then its outcome with "OK". Leaving the dialog by back or an outside tap is "Hide":
 * only "Cancel" stops a transfer, which goes on with its notification once the window is gone.
 */
class ExchangeClipboardDialogManager(
    private val activity: AppCompatActivity,
    private val sending: Boolean,
    private val onHide: () -> Unit,
    private val onCancel: () -> Unit,
    private val onDone: () -> Unit,
) {

    private var dialog: AlertDialog? = null
    private var running: DialogExchangeClipboardTransferBinding? = null
    private var shown: ExchangeClipboardUiState? = null

    fun render(state: ExchangeClipboardUiState) {
        when (state) {
            // The window must keep input focus until the clipboard is read, so nothing covers it yet.
            ExchangeClipboardUiState.Checking -> Unit
            is ExchangeClipboardUiState.Running -> showRunning(state)
            ExchangeClipboardUiState.NoEntry ->
                showOutcome(state, activity.getString(R.string.exchange_clipboard_no_entry), openDevices = true)
            is ExchangeClipboardUiState.LocalRefusal ->
                showOutcome(state, activity.getString(state.message), openDevices = false)
            is ExchangeClipboardUiState.Finished -> {
                val text = listOfNotNull(state.headline, state.detail).joinToString("\n\n")
                showOutcome(state, text, openDevices = false)
            }
        }
    }

    fun dismiss() {
        dialog?.dismiss()
        dialog = null
        running = null
        shown = null
    }

    private fun showRunning(state: ExchangeClipboardUiState.Running) {
        val binding = running ?: openRunning()
        val action = activity.getString(
            if (sending) R.string.exchange_clipboard_sending else R.string.exchange_clipboard_getting
        )
        val done = Formatter.formatShortFileSize(activity, state.done)
        binding.exchangeClipboardMessage.text = state.total
            ?.let { "$action\n$done / ${Formatter.formatShortFileSize(activity, it)}" }
            ?: "$action\n$done"
        val total = state.total
        if (total != null) {
            binding.exchangeClipboardProgress.isIndeterminate = false
            binding.exchangeClipboardProgress.setProgressCompat((state.done * PERCENT / total).toInt(), true)
        }
    }

    private fun openRunning(): DialogExchangeClipboardTransferBinding {
        dismiss()
        val binding = DialogExchangeClipboardTransferBinding.inflate(activity.layoutInflater)
        dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(title())
            .setView(binding.root)
            .setPositiveButton(R.string.exchange_clipboard_hide) { _, _ -> onHide() }
            .setNegativeButton(R.string.cancel) { _, _ -> onCancel() }
            .setOnCancelListener { onHide() }
            .showBoundTo(activity)
        running = binding
        return binding
    }

    private fun showOutcome(state: ExchangeClipboardUiState, text: String, openDevices: Boolean) {
        if (state == shown) return
        dismiss()
        shown = state
        dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(title())
            .setMessage(text)
            .setPositiveButton(R.string.ok) { _, _ ->
                if (openDevices) MyDevicesActivity.start(activity)
                onDone()
            }
            .setOnCancelListener { onDone() }
            .showBoundTo(activity)
    }

    private fun title(): Int =
        if (sending) R.string.exchange_clipboard_send_label else R.string.exchange_clipboard_get_label

    private companion object {
        const val PERCENT = 100
    }
}
