package com.sza.fastmediasorter.ui.exchange.helpers

import android.content.ClipboardManager
import android.content.Context
import android.view.inputmethod.EditorInfo
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.DialogExchangeSignInBinding
import com.sza.fastmediasorter.domain.model.ExchangeConnectionString
import com.sza.fastmediasorter.domain.model.ExchangeConnectionStringResult
import com.sza.fastmediasorter.domain.model.ExchangeEndpoint
import com.sza.fastmediasorter.ui.exchange.ExchangeIntakeOutcome
import com.sza.fastmediasorter.ui.exchange.MyDevicesViewModel

/**
 * S4155: the intake half of the quick sign-in dialog - the paste field, the three actions and the summary of the
 * server a string named. It fills address and login, never the password, and applies nothing by itself: the
 * user still presses "Sign in". The string's fingerprint travels only as the pin of the address it came with,
 * so editing the address drops it.
 *
 * S4223: it also owns the form's mode. An invitation string, or the "invitation password" row, turns sign-in into
 * registration (19 items AQ and AR): the pairing-code row hides, the password is asked twice, and
 * [onRegisterModeChanged] lets the dialog relabel its button. The invitation lives only in [filled].
 */
class ExchangeSignInIntakeManager(
    private val activity: AppCompatActivity,
    private val binding: DialogExchangeSignInBinding,
    private val viewModel: MyDevicesViewModel,
    private val launchers: ExchangeIntakeLaunchers,
    private val status: ExchangeFailurePanel,
    private val onRegisterModeChanged: (Boolean) -> Unit = {},
) {

    private var filled: ExchangeConnectionString? = null

    /** The one-time invitation of the filled invitation string, null for any other string. */
    val invitation: String? get() = filled?.invitation

    /** True when the user asked to register with the server's shared invitation password. */
    val usesInvitationPassword: Boolean
        get() = invitation == null && binding.checkExchangeSignInUseInvitationPassword.isChecked

    val registers: Boolean get() = invitation != null || usesInvitationPassword

    fun bind() {
        binding.checkExchangeSignInUseCode.setOnCheckedChangeListener { code ->
            if (code) binding.checkExchangeSignInUseInvitationPassword.setCheckedSilently(false)
            renderMode()
        }
        binding.checkExchangeSignInUseInvitationPassword.setOnCheckedChangeListener { shared ->
            if (shared) binding.checkExchangeSignInUseCode.setCheckedSilently(false)
            renderMode()
        }
        binding.btnExchangeSignInPaste.setOnClickListener { applyText(clipboardText()) }
        binding.btnExchangeSignInScan.setOnClickListener { launchers.scanQr() }
        binding.btnExchangeSignInFile.setOnClickListener { launchers.chooseFile() }
        binding.etExchangeSignInIntake.setOnEditorActionListener { view, actionId, _ ->
            if (actionId != EditorInfo.IME_ACTION_GO) return@setOnEditorActionListener false
            applyText(view.text?.toString().orEmpty())
            true
        }
    }

    fun applyText(text: String) = show(viewModel.intakeText(text))

    fun applyFile(result: ExchangeConnectionStringResult) = show(viewModel.intakeFile(result))

    /** The pin the filled string carries for [endpoint]; null once the user edited the address to another one. */
    fun pinFor(endpoint: ExchangeEndpoint): String? = filled?.takeIf { it.endpoint == endpoint }?.fingerprint

    private fun show(outcome: ExchangeIntakeOutcome) {
        when (outcome) {
            is ExchangeIntakeOutcome.Filled -> fill(outcome.value)
            is ExchangeIntakeOutcome.Refused -> refuse(outcome.text)
        }
    }

    private fun fill(value: ExchangeConnectionString) {
        filled = value
        binding.etExchangeSignInAddress.setText(value.endpoint.toString())
        value.loginHint?.let { binding.etExchangeSignInLogin.setText(it) }
        binding.etExchangeSignInSecret.text = null
        binding.etExchangeSignInIntake.text = null
        val server = value.name?.let { "$it (${value.endpoint})" } ?: value.endpoint.toString()
        binding.textExchangeSignInServer.text = activity.getString(R.string.exchange_signin_server_summary, server)
        binding.textExchangeSignInPin.text = value.fingerprint?.let {
            activity.getString(R.string.exchange_signin_fingerprint_present, it)
        } ?: activity.getString(R.string.exchange_signin_fingerprint_absent)
        binding.textExchangeSignInServer.isVisible = true
        binding.textExchangeSignInPin.isVisible = true
        status.hide()
        renderMode()
        // An invitation names no account yet: the login is the person's choice, filled from `u` when present.
        val focus = if (value.isInvitation && value.loginHint == null) {
            binding.etExchangeSignInLogin
        } else {
            binding.etExchangeSignInSecret
        }
        focus.requestFocus()
    }

    private fun refuse(@StringRes text: Int) {
        filled = null
        binding.textExchangeSignInServer.isVisible = false
        binding.textExchangeSignInPin.isVisible = false
        status.showProgress(activity.getString(text))
        renderMode()
    }

    private fun renderMode() {
        val fromString = invitation != null
        val code = !fromString && binding.checkExchangeSignInUseCode.isChecked
        binding.textExchangeSignInInvitation.isVisible = fromString
        binding.checkExchangeSignInUseCode.isVisible = !fromString
        binding.checkExchangeSignInUseInvitationPassword.isVisible = !fromString
        binding.tilExchangeSignInInvitationPassword.isVisible = usesInvitationPassword
        binding.tilExchangeSignInPasswordRepeat.isVisible = registers
        binding.tilExchangeSignInSecret.hint = activity.getString(
            if (code) R.string.exchange_devices_signin_pairing_code else R.string.exchange_devices_signin_password
        )
        onRegisterModeChanged(registers)
    }

    private fun clipboardText(): String {
        val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        return clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(activity)?.toString()
            .orEmpty()
    }
}
