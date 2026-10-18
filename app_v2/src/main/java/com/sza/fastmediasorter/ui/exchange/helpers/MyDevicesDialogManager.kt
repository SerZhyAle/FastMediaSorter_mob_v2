package com.sza.fastmediasorter.ui.exchange.helpers

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.clipboard.copyTextToClipboard
import com.sza.fastmediasorter.databinding.DialogExchangeEditAddressBinding
import com.sza.fastmediasorter.databinding.DialogExchangeServerDevicesBinding
import com.sza.fastmediasorter.databinding.DialogExchangeShareServerBinding
import com.sza.fastmediasorter.databinding.DialogExchangeSignInBinding
import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry
import com.sza.fastmediasorter.domain.model.ExchangeConnectionString
import com.sza.fastmediasorter.domain.model.ExchangeConnectionStringResult
import com.sza.fastmediasorter.domain.model.ExchangeMessage
import com.sza.fastmediasorter.ui.companionimport.qr.QrCodeEncoder
import com.sza.fastmediasorter.ui.exchange.ExchangeDevicesState
import com.sza.fastmediasorter.ui.exchange.ExchangeSignInRequest
import com.sza.fastmediasorter.ui.exchange.ExchangeSignInState
import com.sza.fastmediasorter.ui.exchange.MyDevicesMessage
import com.sza.fastmediasorter.ui.exchange.MyDevicesViewModel
import com.sza.fastmediasorter.util.showBoundTo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Every dialog of the "My devices" screen: sign-in with the certificate check of amendment D, the device list
 * with revoke and the pairing code, the address edit, removal and passing the server on. The view model owns
 * every decision; this class only shows its states and forwards what the user chose.
 */
class MyDevicesDialogManager(
    private val activity: AppCompatActivity,
    private val viewModel: MyDevicesViewModel,
    private val launchers: ExchangeIntakeLaunchers,
) {

    private var signInDialog: AlertDialog? = null
    private var signInPanel: ExchangeFailurePanel? = null
    private var intake: ExchangeSignInIntakeManager? = null
    private var lastSignInState: ExchangeSignInState? = null
    private var devicesDialog: AlertDialog? = null
    private var devicesBinding: DialogExchangeServerDevicesBinding? = null
    private var devicesAdapter: ExchangeDeviceAdapter? = null
    private var devicesPanel: ExchangeFailurePanel? = null

    fun resolve(message: MyDevicesMessage): String = resolve(activity, message)

    fun showSignIn(
        prefill: ExchangeConnectionString? = null,
        onDismiss: (() -> Unit)? = null,
    ) {
        viewModel.resetSignIn()
        val binding = DialogExchangeSignInBinding.inflate(LayoutInflater.from(activity))
        val panel = ExchangeFailurePanel(binding.exchangeFailurePanel, activity)
        val intakeManager = ExchangeSignInIntakeManager(activity, binding, viewModel, launchers, panel) { registers ->
            signInDialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.setText(
                if (registers) R.string.exchange_signin_register_submit else R.string.exchange_devices_signin_submit
            )
        }.also { it.bind() }
        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.exchange_devices_signin_title)
            .setView(binding.root)
            .setPositiveButton(R.string.exchange_devices_signin_submit, null)
            .setNegativeButton(R.string.cancel, null)
            .setOnDismissListener {
                signInDialog = null
                signInPanel = null
                intake = null
                lastSignInState = null
                viewModel.resetSignIn()
                onDismiss?.invoke()
            }
            .showBoundTo(activity) ?: return
        // Set after show(): a builder listener would dismiss the dialog before the server has answered.
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { submitSignIn(binding) }
        signInDialog = dialog
        signInPanel = panel
        intake = intakeManager
        prefill?.let { intakeManager.applyText(it.encode()) }
    }

    /** A QR or an outside string arrives while no form is open: the form opens already filled. */
    fun applyIntakeText(text: String) {
        if (intake == null) showSignIn()
        intake?.applyText(text)
    }

    fun applyIntakeFile(result: ExchangeConnectionStringResult) {
        if (intake == null) showSignIn()
        intake?.applyFile(result)
    }

    fun renderSignIn(state: ExchangeSignInState) {
        val panel = signInPanel ?: return
        // A collector restarted by the lifecycle replays the same state; its trust dialog is already up.
        if (state === lastSignInState) return
        lastSignInState = state
        val busy = state is ExchangeSignInState.InProgress
        signInDialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = !busy
        when (state) {
            ExchangeSignInState.InProgress -> panel.showProgress(
                activity.getString(
                    if (intake?.registers == true) {
                        R.string.exchange_signin_register_progress
                    } else {
                        R.string.exchange_devices_signin_progress
                    }
                )
            )
            is ExchangeSignInState.Failed -> panel.showFailure(state.diagnosis)
            else -> panel.hide()
        }
        when (state) {
            is ExchangeSignInState.NeedsTrust -> showTrust(state)
            is ExchangeSignInState.Done -> {
                signInDialog?.dismiss()
                val done = if (state.registered) {
                    R.string.exchange_signin_register_done
                } else {
                    R.string.exchange_devices_signin_done
                }
                toast(activity.getString(done, state.endpoint.toString()))
            }
            else -> Unit
        }
    }

    private fun submitSignIn(binding: DialogExchangeSignInBinding) {
        val endpoint = viewModel.parseAddress(binding.etExchangeSignInAddress.text?.toString().orEmpty())
        val login = binding.etExchangeSignInLogin.text?.toString()?.trim().orEmpty()
        val secret = binding.etExchangeSignInSecret.text?.toString().orEmpty()
        binding.tilExchangeSignInAddress.error =
            if (endpoint == null) activity.getString(R.string.exchange_devices_signin_address_invalid) else null
        val loginValid = viewModel.isValidLogin(login)
        binding.tilExchangeSignInLogin.error =
            if (loginValid) null else activity.getString(R.string.exchange_devices_signin_login_invalid)
        binding.tilExchangeSignInSecret.error =
            if (secret.isEmpty()) activity.getString(R.string.exchange_devices_signin_secret_empty) else null
        val registration = registrationFields(binding, secret) ?: return
        if (endpoint == null || !loginValid || secret.isEmpty()) return
        viewModel.signIn(
            ExchangeSignInRequest(
                endpoint,
                login,
                secret,
                pairingCode = !registration.registers && binding.checkExchangeSignInUseCode.isChecked,
                invitation = registration.invitation,
                invitationPassword = registration.invitationPassword,
            ),
            acceptedPin = intake?.pinFor(endpoint),
        )
    }

    private class Registration(val registers: Boolean, val invitation: String?, val invitationPassword: String?)

    // A forgotten password has no recovery (19 item AT), so registration asks for it twice. Null = a field error.
    private fun registrationFields(binding: DialogExchangeSignInBinding, secret: String): Registration? {
        val intakeManager = intake?.takeIf { it.registers } ?: return Registration(false, null, null)
        val repeat = binding.etExchangeSignInPasswordRepeat.text?.toString().orEmpty()
        val mismatch = repeat != secret
        binding.tilExchangeSignInPasswordRepeat.error =
            if (mismatch) activity.getString(R.string.exchange_signin_password_mismatch) else null
        val shared = binding.etExchangeSignInInvitationPassword.text?.toString().orEmpty()
            .takeIf { intakeManager.usesInvitationPassword }
        val sharedMissing = shared?.isEmpty() == true
        binding.tilExchangeSignInInvitationPassword.error =
            if (sharedMissing) activity.getString(R.string.exchange_signin_invitation_password_empty) else null
        return Registration(true, intakeManager.invitation, shared).takeUnless { mismatch || sharedMissing }
    }

    private fun showTrust(state: ExchangeSignInState.NeedsTrust) {
        val message = state.pinned?.let { pinned ->
            ExchangeMessageText.twoParts(activity, ExchangeMessage.CERTIFICATE_CHANGED) + "\n\n" +
                activity.getString(R.string.exchange_failure_certificate_fingerprints, pinned, state.presented)
        } ?: activity.getString(R.string.exchange_devices_cert_message, state.presented)
        // A changed certificate is the dangerous case: its dialog opens on the safe button (S4051 owner rule).
        val theme = state.pinned?.let { R.style.ThemeOverlay_FastMediaSorter_MaterialAlertDialog_Destructive } ?: 0
        MaterialAlertDialogBuilder(activity, theme)
            .setTitle(R.string.exchange_devices_cert_title)
            .setMessage(message)
            .setPositiveButton(R.string.exchange_devices_cert_accept) { _, _ ->
                viewModel.signIn(state.request, acceptedPin = state.presented)
            }
            .setNegativeButton(R.string.cancel) { _, _ -> viewModel.resetSignIn() }
            .setOnCancelListener { viewModel.resetSignIn() }
            .showBoundTo(activity)
    }

    fun showDevices(entry: DeviceExchangeAccountEntry) {
        val binding = DialogExchangeServerDevicesBinding.inflate(LayoutInflater.from(activity))
        binding.rvExchangeDevices.layoutManager = LinearLayoutManager(activity)
        binding.rvExchangeDevices.adapter = ExchangeDeviceAdapter(entry.deviceId) { device ->
            confirmRevoke(device.deviceName) { viewModel.revokeDevice(entry, device) }
        }.also { devicesAdapter = it }
        binding.btnExchangeDevicesPairing.setOnClickListener { viewModel.issuePairingCode(entry) }
        devicesDialog = MaterialAlertDialogBuilder(activity)
            .setTitle(ExchangeServerCardAdapter.address(entry))
            .setView(binding.root)
            .setNegativeButton(R.string.close, null)
            .setOnDismissListener {
                devicesDialog = null
                devicesBinding = null
                devicesAdapter = null
                devicesPanel = null
                viewModel.closeDevices()
            }
            .showBoundTo(activity)
        devicesBinding = binding
        devicesPanel = ExchangeFailurePanel(binding.exchangeFailurePanel, activity).also {
            it.showProgress(activity.getString(R.string.exchange_devices_list_loading))
        }
        viewModel.openDevices(entry)
        Timber.d("S4348: devices dialog shown")
    }

    fun renderDevices(state: ExchangeDevicesState) {
        val binding = devicesBinding ?: return
        val panel = devicesPanel ?: return
        Timber.d("S4348: devices dialog render")
        when (state) {
            ExchangeDevicesState.Closed, ExchangeDevicesState.Loading ->
                panel.showProgress(activity.getString(R.string.exchange_devices_list_loading))
            is ExchangeDevicesState.Failed -> panel.showFailure(state.diagnosis)
            is ExchangeDevicesState.Loaded -> if (state.devices.isEmpty()) {
                panel.showProgress(activity.getString(R.string.exchange_devices_list_empty))
            } else {
                panel.hide()
            }
        }
        if (state is ExchangeDevicesState.Loaded) {
            devicesAdapter?.submit(state.devices, state.notices)
        }
    }

    fun renderPairingCode(message: MyDevicesMessage?) {
        val binding = devicesBinding ?: return
        binding.textExchangeDevicesPairing.text = message?.let(::resolve)
        binding.textExchangeDevicesPairing.isVisible = message != null
    }

    private fun confirmRevoke(name: String, onConfirm: () -> Unit) {
        MaterialAlertDialogBuilder(activity, R.style.ThemeOverlay_FastMediaSorter_MaterialAlertDialog_Destructive)
            .setTitle(activity.getString(R.string.exchange_devices_revoke_confirm_title, name))
            .setMessage(R.string.exchange_devices_revoke_confirm_message)
            .setPositiveButton(R.string.exchange_devices_revoke) { _, _ -> onConfirm() }
            .setNegativeButton(R.string.cancel, null)
            .showBoundTo(activity)
    }

    fun showEditAddress(entry: DeviceExchangeAccountEntry) {
        val binding = DialogExchangeEditAddressBinding.inflate(LayoutInflater.from(activity))
        binding.etExchangeEditAddress.setText(ExchangeServerCardAdapter.address(entry))
        viewModel.announcedEndpoint(entry)?.let { announced ->
            binding.textExchangeEditAnnounced.text =
                activity.getString(R.string.exchange_devices_edit_announced, announced.toString())
            binding.textExchangeEditAnnounced.isVisible = true
            binding.btnExchangeEditUseAnnounced.isVisible = true
            binding.btnExchangeEditUseAnnounced.setOnClickListener {
                binding.etExchangeEditAddress.setText(announced.toString())
            }
        }
        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.exchange_devices_edit_title)
            .setView(binding.root)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)
            .showBoundTo(activity) ?: return
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val endpoint = viewModel.parseAddress(binding.etExchangeEditAddress.text?.toString().orEmpty())
            if (endpoint == null) {
                binding.tilExchangeEditAddress.error =
                    activity.getString(R.string.exchange_devices_signin_address_invalid)
            } else {
                viewModel.changeAddress(entry, endpoint)
                dialog.dismiss()
            }
        }
    }

    fun confirmRemove(entry: DeviceExchangeAccountEntry) {
        MaterialAlertDialogBuilder(activity, R.style.ThemeOverlay_FastMediaSorter_MaterialAlertDialog_Destructive)
            .setTitle(
                activity.getString(R.string.exchange_devices_remove_title, ExchangeServerCardAdapter.address(entry))
            )
            .setMessage(R.string.exchange_devices_remove_message)
            .setPositiveButton(R.string.exchange_devices_menu_sign_out) { _, _ -> viewModel.remove(entry) }
            .setNegativeButton(R.string.cancel, null)
            .showBoundTo(activity)
    }

    fun showShare(entry: DeviceExchangeAccountEntry) {
        val text = viewModel.connectionString(entry)
        if (text == null) {
            toast(activity.getString(R.string.exchange_devices_share_unavailable))
            return
        }
        val binding = DialogExchangeShareServerBinding.inflate(LayoutInflater.from(activity))
        binding.textExchangeShareString.text = text
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.exchange_devices_menu_share)
            .setView(binding.root)
            .setPositiveButton(R.string.exchange_devices_share_send) { _, _ -> send(text) }
            .setNeutralButton(R.string.copy) { _, _ ->
                activity.copyTextToClipboard(activity.getString(R.string.exchange_devices_qr_description), text)
            }
            .setNegativeButton(R.string.close, null)
            .showBoundTo(activity)
        activity.lifecycleScope.launch {
            val sizePx = activity.resources.getDimensionPixelSize(R.dimen.sftp_server_qr_size)
            val bitmap = withContext(Dispatchers.Default) { QrCodeEncoder.encode(text, sizePx) }
            binding.imageExchangeShareQr.setImageBitmap(bitmap)
        }
    }

    private fun send(text: String) {
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        activity.startActivity(Intent.createChooser(intent, activity.getString(R.string.exchange_devices_menu_share)))
    }

    private fun toast(text: String) {
        Toast.makeText(activity, text, Toast.LENGTH_LONG).show()
    }

    companion object {
        fun resolve(context: Context, message: MyDevicesMessage): String {
            val args = message.args.map { arg -> if (arg is MyDevicesMessage) resolve(context, arg) else arg }

            // Resources.getString takes varargs; the message carries at most two arguments, so the copy is free.
            @Suppress("SpreadOperator")
            val text = context.getString(message.text, *args.toTypedArray())
            return text
        }
    }
}
