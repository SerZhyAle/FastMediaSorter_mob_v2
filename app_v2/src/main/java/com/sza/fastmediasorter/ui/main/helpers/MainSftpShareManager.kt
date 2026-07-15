package com.sza.fastmediasorter.ui.main.helpers

import android.app.Activity
import android.view.LayoutInflater
import android.widget.TextView
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.utils.SftpHostReachabilityClassifier
import com.sza.fastmediasorter.utils.SftpPathUtils

/**
 * S0984: builds the "share SFTP access" confirmation dialog (plaintext-password warning, optional
 * password omission, honest private-network reachability note) and reports the user's choice.
 * S1039: the same dialog offers two transports - a `.fmscfg` file ([ShareMethod.FILE], positive
 * button) or an on-screen QR ([ShareMethod.QR], neutral button); the password checkbox and the
 * private-network warning apply to both. Kept out of [com.sza.fastmediasorter.ui.main.MainActivity]
 * to respect its LOC ceiling.
 */
class MainSftpShareManager(private val activity: Activity) {

    /** How the caller should transport the access config chosen in the dialog. */
    enum class ShareMethod { FILE, QR }

    /** [onConfirm] receives whether to embed the password and which [ShareMethod] the user picked. */
    fun show(resource: MediaResource, onConfirm: (includePassword: Boolean, method: ShareMethod) -> Unit) {
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_share_sftp_access, null, false)
        val omitPassword = view.findViewById<MaterialCheckBox>(R.id.checkboxOmitPassword)
        val privateWarning = view.findViewById<TextView>(R.id.textPrivateNetworkWarning)

        val host = SftpPathUtils.parseSftpPath(resource.path)?.host
        if (host != null &&
            SftpHostReachabilityClassifier.classify(host) == SftpHostReachabilityClassifier.Reachability.PRIVATE_LAN
        ) {
            privateWarning.visibility = TextView.VISIBLE
        }

        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.sftp_share_export_title)
            .setView(view)
            .setPositiveButton(R.string.sftp_share_export_action) { _, _ ->
                onConfirm(!omitPassword.isChecked, ShareMethod.FILE)
            }
            .setNeutralButton(R.string.sftp_share_show_qr_action) { _, _ ->
                onConfirm(!omitPassword.isChecked, ShareMethod.QR)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
