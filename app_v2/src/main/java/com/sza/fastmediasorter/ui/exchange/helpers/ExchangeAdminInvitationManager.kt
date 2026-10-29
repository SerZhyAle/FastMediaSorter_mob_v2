package com.sza.fastmediasorter.ui.exchange.helpers

import android.content.Intent
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.clipboard.copyTextToClipboard
import com.sza.fastmediasorter.databinding.DialogExchangeAdminAccountBinding
import com.sza.fastmediasorter.databinding.DialogExchangeAdminInvitationBinding
import com.sza.fastmediasorter.domain.model.ExchangeAdminCreatedInvitation
import com.sza.fastmediasorter.domain.model.ExchangeAdminKinds
import com.sza.fastmediasorter.domain.model.ExchangeConnectionStringCodec
import com.sza.fastmediasorter.ui.companionimport.qr.QrCodeEncoder
import com.sza.fastmediasorter.ui.exchange.ExchangeServerAdminViewModel
import com.sza.fastmediasorter.util.applicationScope
import com.sza.fastmediasorter.util.showBoundTo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.IOException

/**
 * The invitation of the administration screen: the create form, and the one window that shows a created
 * invitation with its code, connection string and QR (strategic goal 4). Nothing of the invitation outlives the
 * window: the manager holds no reply, and the file variant lives only in the cache directory that
 * [clearSharedFiles] removes when the screen opens and when it dies.
 */
class ExchangeAdminInvitationManager(
    private val activity: AppCompatActivity,
    private val viewModel: ExchangeServerAdminViewModel,
) {

    fun showCreateDialog() {
        val binding = DialogExchangeAdminAccountBinding.inflate(LayoutInflater.from(activity))
        binding.tilExchangeAdminLogin.hint = activity.getString(R.string.exchange_admin_expected_login_label)
        binding.tilExchangeAdminPassword.isVisible = false
        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.exchange_admin_create_invitation)
            .setView(binding.root)
            .setPositiveButton(R.string.exchange_admin_create_invitation, null)
            .setNegativeButton(R.string.cancel, null)
            .showBoundTo(activity) ?: return
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val expected = binding.etExchangeAdminLogin.text?.toString()?.trim().orEmpty().takeIf { it.isNotEmpty() }
            if (expected != null && !ExchangeConnectionStringCodec.isLogin(expected)) {
                binding.tilExchangeAdminLogin.error = activity.getString(R.string.exchange_admin_reply_bad_login)
                return@setOnClickListener
            }
            val kind = if (binding.rbExchangeAdminKindGuest.isChecked) {
                ExchangeAdminKinds.GUEST
            } else {
                ExchangeAdminKinds.MEMBER
            }
            viewModel.createInvitation(kind, expected)
            dialog.dismiss()
        }
    }

    /** The one showing of a created invitation; closing it forgets the code and the string. */
    fun show(created: ExchangeAdminCreatedInvitation) {
        val binding = DialogExchangeAdminInvitationBinding.inflate(LayoutInflater.from(activity))
        binding.textExchangeAdminInvitationCode.text =
            activity.getString(R.string.exchange_admin_invitation_code, created.code)
        binding.textExchangeAdminInvitationString.text = created.connectionString
        binding.btnExchangeAdminInvitationCopy.setOnClickListener {
            activity.copyTextToClipboard(
                activity.getString(R.string.exchange_admin_qr_description),
                created.connectionString,
            )
        }
        binding.btnExchangeAdminInvitationSend.setOnClickListener { sendAsText(created) }
        binding.btnExchangeAdminInvitationSendFile.setOnClickListener { sendAsFile(created) }
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.exchange_admin_section_invitations)
            .setView(binding.root)
            .setPositiveButton(R.string.close, null)
            .showBoundTo(activity)
        activity.lifecycleScope.launch {
            val sizePx = activity.resources.getDimensionPixelSize(R.dimen.sftp_server_qr_size)
            val bitmap = withContext(Dispatchers.Default) { QrCodeEncoder.encode(created.connectionString, sizePx) }
            binding.imageExchangeAdminInvitationQr.setImageBitmap(bitmap)
        }
    }

    /**
     * Removes the directory of shared invitation files; called when the screen opens and when it dies.
     * The delete runs on the application scope (IO): the activity scope is already gone on the second call,
     * and neither call may do file I/O on the main thread. Only the application context is captured, so a
     * delete still running does not hold the dying activity.
     */
    fun clearSharedFiles() {
        val appContext = activity.applicationContext
        appContext.applicationScope().launch {
            File(appContext.cacheDir, SHARED_DIR).deleteRecursively()
        }
    }

    private fun sendAsText(created: ExchangeAdminCreatedInvitation) {
        val intent = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, created.connectionString)
        activity.startActivity(Intent.createChooser(intent, activity.getString(R.string.exchange_admin_send_text)))
    }

    private fun sendAsFile(created: ExchangeAdminCreatedInvitation) {
        val host = viewModel.serverHost?.takeUnless { it.isEmpty() } ?: FALLBACK_HOST
        val file = File(File(activity.cacheDir, SHARED_DIR), "$host.${ExchangeConnectionStringCodec.FILE_EXTENSION}")
        activity.lifecycleScope.launch {
            val written = withContext(Dispatchers.Default) {
                try {
                    file.parentFile?.mkdirs()
                    file.writeText(created.connectionString)
                    file
                } catch (io: IOException) {
                    Timber.w(io, "Exchange admin invitation file write failed")
                    null
                }
            } ?: return@launch
            val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", written)
            val intent = Intent(Intent.ACTION_SEND)
                .setType("application/octet-stream")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            activity.startActivity(
                Intent.createChooser(intent, activity.getString(R.string.exchange_admin_send_file)),
            )
        }
    }

    private companion object {
        const val SHARED_DIR = "exchange-admin-invite"
        const val FALLBACK_HOST = "server"
    }
}
