package com.sza.fastmediasorter.ui.addresource.helpers

import android.widget.Toast
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.SftpPairingPayload
import com.sza.fastmediasorter.ui.addresource.AddResourceActivity
import timber.log.Timber

/**
 * Turns a scanned embedded-server pairing code into a filled SFTP form ("Resources -> Add Resource ->
 * My Device Server"). The same camera scan also reads companion configs; a code without the
 * [SftpPairingPayload.PREFIX] is left to that path.
 *
 * The host-key fingerprint goes into the form's fingerprint field, which the save path already turns
 * into a pin (`PinnedHostKeyRepository`), so the first connection refuses any machine but the one that
 * showed the code. Nothing is saved here: the user reviews the form and saves it.
 */
class AddResourceSftpQrCoordinator(private val activity: AddResourceActivity) {

    /** True when [payload] was a pairing code - applied, or refused as damaged - and needs no other handler. */
    fun handle(payload: String): Boolean {
        Timber.d("S3041: scanned code reached the pairing check")
        if (!SftpPairingPayload.isPairingPayload(payload)) return false
        val pairing = SftpPairingPayload.decode(payload)
        val message = if (pairing == null) {
            R.string.add_resource_sftp_pairing_invalid
        } else {
            apply(pairing)
            R.string.add_resource_sftp_pairing_filled
        }
        Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
        return true
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
