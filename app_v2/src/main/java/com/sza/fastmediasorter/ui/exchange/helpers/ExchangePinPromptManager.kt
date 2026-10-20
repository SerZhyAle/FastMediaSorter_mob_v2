package com.sza.fastmediasorter.ui.exchange.helpers

import android.view.LayoutInflater
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.DialogExchangePinBinding
import com.sza.fastmediasorter.domain.model.ExchangeMessage
import com.sza.fastmediasorter.ui.exchange.ExchangePinPhase
import com.sza.fastmediasorter.ui.exchange.ExchangePinPrompt
import com.sza.fastmediasorter.util.showBoundTo

/**
 * The PIN request of a protected resource (DEVICE-EXCHANGE 17 item AJ), opened over "From my devices". The view
 * model owns the sequence and every verdict; this class shows the phase it is given and hands the typed PIN on.
 * The field is cleared whenever the dialog goes, so a PIN never outlives the request that asked for it.
 */
class ExchangePinPromptManager(
    private val activity: AppCompatActivity,
    private val onSubmit: (String) -> Unit,
    private val onSkip: () -> Unit,
) {

    private var dialog: AlertDialog? = null
    private var binding: DialogExchangePinBinding? = null
    private var shownTicket: Int? = null

    fun render(prompt: ExchangePinPrompt) {
        when (prompt) {
            ExchangePinPrompt.None -> dismiss()
            is ExchangePinPrompt.Asking -> {
                if (dialog == null || shownTicket != prompt.ticket) show(prompt)
                apply(prompt.phase)
            }
        }
    }

    private fun show(prompt: ExchangePinPrompt.Asking) {
        dismiss()
        val view = DialogExchangePinBinding.inflate(LayoutInflater.from(activity))
        view.etExchangePin.doAfterTextChanged { view.tilExchangePin.error = null }
        view.etExchangePin.setOnEditorActionListener { _, actionId, _ ->
            (actionId == EditorInfo.IME_ACTION_DONE).also { if (it) submit() }
        }
        val shown = MaterialAlertDialogBuilder(activity)
            .setTitle(activity.getString(R.string.exchange_pin_title, prompt.resourceName))
            .setView(view.root)
            .setPositiveButton(if (prompt.renewing) R.string.save else R.string.exchange_directory_attach, null)
            .setNegativeButton(if (prompt.renewing) R.string.cancel else R.string.exchange_pin_skip, null)
            .setCancelable(false)
            .showBoundTo(activity) ?: return
        // Set after show(): a builder listener would dismiss the dialog before the owner has answered.
        shown.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { submit() }
        shown.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener { onSkip() }
        shown.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        view.etExchangePin.requestFocus()
        dialog = shown
        binding = view
        shownTicket = prompt.ticket
    }

    private fun apply(phase: ExchangePinPhase) {
        val view = binding ?: return
        val busy = phase == ExchangePinPhase.CHECKING
        view.etExchangePin.isEnabled = !busy
        dialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = !busy
        view.tilExchangePin.error =
            if (phase == ExchangePinPhase.REFUSED) activity.getString(R.string.exchange_pin_refused) else null
        val status = when (phase) {
            ExchangePinPhase.CHECKING -> activity.getString(R.string.exchange_pin_checking)
            ExchangePinPhase.UNREACHABLE ->
                ExchangeMessageText.twoParts(activity, ExchangeMessage.UNREACHABLE_DEVICE)
            ExchangePinPhase.FAILED -> activity.getString(R.string.exchange_pin_failed)
            else -> null
        }
        view.textExchangePinStatus.isVisible = status != null
        view.textExchangePinStatus.text = status
        if (phase == ExchangePinPhase.REFUSED || phase == ExchangePinPhase.UNREACHABLE) {
            view.etExchangePin.requestFocus()
            view.etExchangePin.selectAll()
        }
    }

    private fun submit() {
        val view = binding ?: return
        val pin = view.etExchangePin.text?.toString().orEmpty()
        if (pin.isEmpty()) {
            view.tilExchangePin.error = activity.getString(R.string.exchange_pin_empty)
        } else {
            onSubmit(pin)
        }
    }

    private fun dismiss() {
        binding?.etExchangePin?.text?.clear()
        dialog?.dismiss()
        dialog = null
        binding = null
        shownTicket = null
    }
}
