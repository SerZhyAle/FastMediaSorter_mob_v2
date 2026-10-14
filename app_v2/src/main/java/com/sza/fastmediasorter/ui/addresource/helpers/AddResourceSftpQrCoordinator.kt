package com.sza.fastmediasorter.ui.addresource.helpers

import android.widget.Toast
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.HostPort
import com.sza.fastmediasorter.domain.model.IntakeTextDispatch
import com.sza.fastmediasorter.domain.model.SftpPairingPayload
import com.sza.fastmediasorter.domain.model.SftpTunnelAddress
import com.sza.fastmediasorter.domain.usecase.intake.DispatchIntakeTextUseCase
import com.sza.fastmediasorter.ui.addresource.AddResourceActivity
import com.sza.fastmediasorter.ui.exchange.ExchangeSignInActivity
import timber.log.Timber

/**
 * Turns a scanned embedded-server pairing code into a filled SFTP form ("Resources -> Add Resource ->
 * My Device Server"). The same camera scan also reads companion configs; a code without the
 * [SftpPairingPayload.PREFIX] or [SftpPairingPayload.PREFIX_V2] is left to that path. A v2 code fills
 * the form from its direct fields and hands its tunnel candidate to the view model, which saves it as an
 * alternate path of the resource (contract ANYWHERE-ACCESS).
 *
 * The host-key fingerprint goes into the form's fingerprint field, which the save path already turns
 * into a pin (`PinnedHostKeyRepository`), so the first connection refuses any machine but the one that
 * showed the code. Nothing is saved here: the user reviews the form and saves it.
 */
class AddResourceSftpQrCoordinator(
    private val activity: AddResourceActivity,
    private val dispatchIntakeText: DispatchIntakeTextUseCase,
    private val rememberTunnel: (HostPort?) -> Unit,
) {

    /** True when [payload] was handled by this coordinator or opened an exchange flow. */
    fun handle(payload: String): Boolean {
        return when (val dispatch = dispatchIntakeText.dispatch(payload)) {
            is IntakeTextDispatch.ShareCode -> {
                val pairing = dispatch.payload
                Timber.d("S4094: pairing code scanned")
                rememberTunnel(SftpTunnelAddress.fromPairing(pairing))
                apply(pairing)
                Toast.makeText(activity, R.string.add_resource_sftp_pairing_filled, Toast.LENGTH_LONG).show()
                true
            }
            is IntakeTextDispatch.ExchangeString -> {
                val intent = ExchangeSignInActivity.createIntent(activity, payload)
                activity.startActivity(intent)
                true
            }
            is IntakeTextDispatch.NewerExchangeMajor -> {
                val intent = ExchangeSignInActivity.createIntent(activity, payload)
                activity.startActivity(intent)
                true
            }
            is IntakeTextDispatch.CompanionConfig -> false
            is IntakeTextDispatch.Unrecognized -> {
                if (payload.trim().startsWith("FMSSFTP")) {
                    Toast.makeText(activity, R.string.add_resource_sftp_pairing_invalid, Toast.LENGTH_LONG).show()
                    true
                } else {
                    false
                }
            }
        }
    }

    private fun apply(pairing: SftpPairingPayload) {
        activity.showSftpFolderOptions()
        val form = activity.forms.sftp
        val host = pairing.hosts.first()
        form.rbSftp.isChecked = true
        form.etSftpHost.setText(host)
        form.etSftpPort.setText(pairing.port.toString())
        form.etSftpUsername.setText(pairing.username)
        val password = pairing.password
        if (password != null) {
            form.rbSftpPassword.isChecked = true
            form.etSftpPassword.setText(password)
        } else {
            form.rbSftpSshKey.isChecked = true
        }
        form.etSftpHostKeyFingerprint.setText(pairing.hostKeyFingerprint)
        if (form.etSftpResourceName.text.isNullOrBlank()) {
            form.etSftpResourceName.setText(activity.getString(R.string.add_resource_sftp_pairing_name, host))
        }
    }
}
