@file:Suppress("DEPRECATION")

package com.sza.fastmediasorter.ui.addresource

import android.app.AlertDialog
import android.content.Intent
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.utils.collectOnLifecycle
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.cloud.CloudProvider
import com.sza.fastmediasorter.data.cloud.CloudResult
import com.sza.fastmediasorter.data.cloud.DropboxClient
import com.sza.fastmediasorter.data.cloud.OneDriveRestClient
import com.sza.fastmediasorter.data.cloud.UnifiedCloudAuthManager
import com.sza.fastmediasorter.databinding.ActivityAddResourceBinding
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.ui.common.DialogUtils
import com.sza.fastmediasorter.ui.dialog.ErrorDialog
import kotlinx.coroutines.launch
import timber.log.Timber

internal class AddResourceConnectionManager(
    private val activity: AddResourceActivity,
    private val binding: ActivityAddResourceBinding,
    private val viewModel: AddResourceViewModel,
    private val unifiedAuthManager: UnifiedCloudAuthManager,
    private val dropboxClient: dagger.Lazy<DropboxClient>,
    private val oneDriveClient: dagger.Lazy<OneDriveRestClient>
) {

    private var googleDriveAccount: GoogleSignInAccount? = null

    fun observeAuthEvents() {
        activity.collectOnLifecycle(unifiedAuthManager.authEvents) { event ->
            when (event) {
                is UnifiedCloudAuthManager.AuthEvent.Success -> {
                    Toast.makeText(
                        activity,
                        activity.getString(R.string.connected_as, event.accountEmail),
                        Toast.LENGTH_SHORT
                    ).show()
                    when (event.provider) {
                        CloudProvider.GOOGLE_DRIVE -> navigateToGoogleDriveFolderPicker(event.accountEmail)
                        CloudProvider.DROPBOX -> navigateToDropboxFolderPicker(event.accountEmail)
                        CloudProvider.ONEDRIVE -> navigateToOneDriveFolderPicker(event.accountEmail)
                    }
                }
                is UnifiedCloudAuthManager.AuthEvent.Error -> {
                    val titleRes = when (event.provider) {
                        CloudProvider.GOOGLE_DRIVE -> R.string.google_drive_authentication_failed
                        CloudProvider.DROPBOX -> R.string.dropbox_authentication_failed
                        CloudProvider.ONEDRIVE -> R.string.onedrive_authentication_failed
                    }
                    showDetailedErrorDialog(titleRes, event.message)
                }
            }
        }
    }

    fun handleResume() {
        activity.lifecycleScope.launch {
            unifiedAuthManager.handleResume()
        }
    }

    // ========== Cloud Status ==========

    fun updateCloudStorageStatus() {
        googleDriveAccount = GoogleSignIn.getLastSignedInAccount(activity)
        binding.tvGoogleDriveStatus.isVisible = true
        binding.tvGoogleDriveStatus.text = if (googleDriveAccount != null) {
            activity.getString(R.string.connected_as, googleDriveAccount?.email ?: "")
        } else {
            activity.getString(R.string.not_connected)
        }

        activity.lifecycleScope.launch {
            try {
                val restored = dropboxClient.get().tryRestoreFromStorage()
                binding.tvDropboxStatus.text = if (restored) {
                    val testResult = dropboxClient.get().testConnection()
                    if (testResult is CloudResult.Success) {
                        val email = dropboxClient.get().getAccountEmail() ?: "Unknown"
                        Timber.d("Dropbox connection restored: $email")
                        activity.getString(R.string.connected_as, email)
                    } else {
                        activity.getString(R.string.not_connected)
                    }
                } else {
                    activity.getString(R.string.not_connected)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to restore Dropbox connection")
                binding.tvDropboxStatus.text = activity.getString(R.string.not_connected)
            }
        }

        activity.lifecycleScope.launch {
            try {
                binding.tvOneDriveStatus.text = if (oneDriveClient.get().isAuthenticated()) {
                    val testResult = oneDriveClient.get().testConnection()
                    if (testResult is CloudResult.Success) {
                        val email = oneDriveClient.get().getAccountEmail() ?: "Unknown"
                        Timber.d("OneDrive connected: $email")
                        activity.getString(R.string.connected_as, email)
                    } else {
                        activity.getString(R.string.not_connected)
                    }
                } else {
                    activity.getString(R.string.not_connected)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to check OneDrive connection")
                binding.tvOneDriveStatus.text = activity.getString(R.string.not_connected)
            }
        }
    }

    // ========== Google Drive ==========

    fun startGoogleDriveAuth() {
        val account = GoogleSignIn.getLastSignedInAccount(activity)
        if (account != null) showGoogleDriveSignedInOptions(account)
        else unifiedAuthManager.startInteractiveSignIn(activity, CloudProvider.GOOGLE_DRIVE)
    }

    fun handleGoogleSignInResult(data: Intent?) {
        try {
            val account = GoogleSignIn.getSignedInAccountFromIntent(data).getResult(ApiException::class.java)
            googleDriveAccount = account
            updateCloudStorageStatus()
            Toast.makeText(
                activity,
                activity.getString(R.string.google_drive_signed_in, account.email ?: ""),
                Toast.LENGTH_SHORT
            ).show()
            navigateToGoogleDriveFolderPicker(account.email)
        } catch (e: ApiException) {
            Timber.e(e, "Google Sign-In failed: ${e.statusCode}")
            val errorMessage = when (e.statusCode) {
                10 -> "Google Drive authentication setup required. Check SHA-1 fingerprint in Google Cloud Console."
                12 -> "Google Sign-In cancelled"
                7 -> "Network error. Check your internet connection."
                else -> activity.getString(R.string.google_drive_authentication_failed)
            }
            Toast.makeText(activity, errorMessage, Toast.LENGTH_LONG).show()
        }
    }

    private fun showGoogleDriveSignedInOptions(account: GoogleSignInAccount) {
        AlertDialog.Builder(activity)
            .setTitle(R.string.google_drive)
            .setMessage(R.string.msg_already_authenticated)
            .setPositiveButton(R.string.google_drive_select_folder) { _, _ ->
                navigateToGoogleDriveFolderPicker(account.email ?: account.displayName)
            }
            .setNeutralButton(android.R.string.cancel, null)
            .show()
    }

    private fun signOutGoogleDrive() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).requestEmail().build()
        GoogleSignIn.getClient(activity, gso).signOut().addOnCompleteListener {
            googleDriveAccount = null
            updateCloudStorageStatus()
            Toast.makeText(activity, activity.getString(R.string.google_drive_signed_out), Toast.LENGTH_SHORT).show()
        }
    }

    private fun navigateToGoogleDriveFolderPicker(accountEmail: String? = null) {
        val intent = Intent(activity, com.sza.fastmediasorter.ui.cloudfolders.GoogleDriveFolderPickerActivity::class.java)
            .apply { accountEmail?.let { putExtra("extra_account_email", it) } }
        activity.startActivity(intent)
    }

    // ========== Dropbox ==========

    fun startDropboxAuth() {
        activity.lifecycleScope.launch {
            try {
                val testResult = dropboxClient.get().testConnection()
                if (testResult is CloudResult.Success) {
                    showDropboxSignedInOptions(dropboxClient.get().getAccountEmail())
                } else {
                    unifiedAuthManager.startInteractiveSignIn(activity, CloudProvider.DROPBOX)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to check Dropbox authentication")
                showDetailedErrorDialog(R.string.dropbox_authentication_failed, e.message)
            }
        }
    }

    private fun showDropboxSignedInOptions(accountEmail: String? = null) {
        AlertDialog.Builder(activity)
            .setTitle(R.string.dropbox)
            .setMessage(R.string.msg_already_authenticated)
            .setPositiveButton(R.string.dropbox_select_folder) { _, _ -> navigateToDropboxFolderPicker(accountEmail) }
            .setNegativeButton(R.string.dropbox_sign_out) { _, _ -> signOutDropbox() }
            .setNeutralButton(android.R.string.cancel, null)
            .show()
    }

    private fun signOutDropbox() {
        activity.lifecycleScope.launch {
            dropboxClient.get().signOut()
            Toast.makeText(activity, activity.getString(R.string.dropbox_signed_out), Toast.LENGTH_SHORT).show()
        }
    }

    private fun navigateToDropboxFolderPicker(accountEmail: String? = null) {
        val intent = Intent(activity, com.sza.fastmediasorter.ui.cloudfolders.DropboxFolderPickerActivity::class.java)
            .apply { accountEmail?.let { putExtra("extra_account_email", it) } }
        activity.startActivity(intent)
    }

    // ========== OneDrive ==========

    fun startOneDriveAuth() {
        activity.lifecycleScope.launch {
            try {
                val testResult = oneDriveClient.get().testConnection()
                if (testResult is CloudResult.Success) {
                    showOneDriveSignedInOptions(oneDriveClient.get().getAccountEmail())
                } else {
                    unifiedAuthManager.startInteractiveSignIn(activity, CloudProvider.ONEDRIVE)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to check OneDrive authentication")
                Toast.makeText(
                    activity,
                    activity.getString(R.string.onedrive_authentication_failed) + ": ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun showOneDriveSignedInOptions(accountEmail: String? = null) {
        AlertDialog.Builder(activity)
            .setTitle(R.string.onedrive)
            .setMessage(R.string.msg_already_authenticated)
            .setPositiveButton(R.string.onedrive_select_folder) { _, _ -> navigateToOneDriveFolderPicker(accountEmail) }
            .setNegativeButton(R.string.onedrive_sign_out) { _, _ -> signOutOneDrive() }
            .setNeutralButton(android.R.string.cancel, null)
            .show()
    }

    private fun signOutOneDrive() {
        activity.lifecycleScope.launch {
            oneDriveClient.get().signOut()
            Toast.makeText(activity, activity.getString(R.string.onedrive_signed_out), Toast.LENGTH_SHORT).show()
        }
    }

    private fun navigateToOneDriveFolderPicker(accountEmail: String? = null) {
        val intent = Intent(activity, com.sza.fastmediasorter.ui.cloudfolders.OneDriveFolderPickerActivity::class.java)
            .apply { accountEmail?.let { putExtra("extra_account_email", it) } }
        activity.startActivity(intent)
    }

    // ========== Account Picker ==========

    fun showAccountPicker(providerName: String, accounts: List<String>) {
        val options = accounts.toMutableList().also { it.add(activity.getString(R.string.add_new_account)) }
        val titleRes = when (providerName) {
            CloudProvider.GOOGLE_DRIVE.name -> R.string.google_drive
            CloudProvider.ONEDRIVE.name -> R.string.onedrive
            CloudProvider.DROPBOX.name -> R.string.dropbox
            else -> R.string.cloud_storage
        }
        AlertDialog.Builder(activity)
            .setTitle(activity.getString(titleRes))
            .setItems(options.toTypedArray()) { _, which ->
                if (which == options.size - 1) {
                    val provider = when (providerName) {
                        CloudProvider.GOOGLE_DRIVE.name -> CloudProvider.GOOGLE_DRIVE
                        CloudProvider.ONEDRIVE.name -> CloudProvider.ONEDRIVE
                        CloudProvider.DROPBOX.name -> CloudProvider.DROPBOX
                        else -> return@setItems
                    }
                    unifiedAuthManager.startInteractiveSignIn(activity, provider)
                } else {
                    when (providerName) {
                        CloudProvider.GOOGLE_DRIVE.name -> navigateToGoogleDriveFolderPicker(options[which])
                        CloudProvider.ONEDRIVE.name -> navigateToOneDriveFolderPicker(options[which])
                        CloudProvider.DROPBOX.name -> navigateToDropboxFolderPicker(options[which])
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ========== SMB / SFTP Connection Testing ==========

    fun testSmbConnection() {
        val server = binding.etSmbServer.text.toString().trim().replace(',', '.')
        if (!binding.etSmbServer.isValid()) {
            Toast.makeText(activity, activity.getString(R.string.invalid_server_address), Toast.LENGTH_SHORT).show()
            binding.etSmbServer.requestFocus()
            return
        }
        if (server.isEmpty()) {
            Toast.makeText(activity, activity.getString(R.string.server_address_required), Toast.LENGTH_SHORT).show()
            return
        }
        viewModel.testSmbConnection(
            server,
            binding.etSmbShareName.text.toString().trim(),
            binding.etSmbUsername.text.toString().trim(),
            binding.etSmbPassword.text.toString().trim(),
            binding.etSmbDomain.text.toString().trim(),
            binding.etSmbPort.text.toString().trim().toIntOrNull() ?: 445
        )
    }

    fun testSftpConnection() {
        val protocolType = getSelectedProtocol()
        val host = binding.etSftpHost.text.toString().trim()
        if (!binding.etSftpHost.isValid()) {
            Toast.makeText(activity, activity.getString(R.string.invalid_host_address), Toast.LENGTH_SHORT).show()
            binding.etSftpHost.requestFocus()
            return
        }
        if (host.isEmpty()) {
            Toast.makeText(activity, activity.getString(R.string.host_required), Toast.LENGTH_SHORT).show()
            return
        }
        val defaultPort = if (protocolType == ResourceType.SFTP) 22 else 21
        val port = binding.etSftpPort.text.toString().trim().toIntOrNull() ?: defaultPort
        val username = binding.etSftpUsername.text.toString().trim()

        if (protocolType == ResourceType.SFTP && binding.rbSftpSshKey.isChecked) {
            val privateKey = binding.etSftpPrivateKey.text.toString().trim()
            if (privateKey.isEmpty()) {
                Toast.makeText(activity, activity.getString(R.string.ssh_key_required), Toast.LENGTH_SHORT).show()
                return
            }
            viewModel.testSftpConnectionWithKey(
                host, port, username, privateKey,
                binding.etSftpKeyPassphrase.text.toString().trim().ifEmpty { null }
            )
        } else {
            viewModel.testSftpFtpConnection(protocolType, host, port, username,
                binding.etSftpPassword.text.toString().trim())
        }
    }

    private fun getSelectedProtocol(): ResourceType = when (binding.rgProtocol.checkedRadioButtonId) {
        binding.rbSftp.id -> ResourceType.SFTP
        binding.rbFtp.id -> ResourceType.FTP
        else -> ResourceType.SFTP
    }

    // ========== Dialog Helpers ==========

    fun showSharePickerDialog(server: String, shares: List<String>) {
        AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.msg_select_share, server))
            .setItems(shares.toTypedArray()) { _, which -> binding.etSmbShareName.setText(shares[which]) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    fun showError(message: String) {
        activity.lifecycleScope.launch {
            val settings = viewModel.getSettings()
            if (settings.showDetailedErrors) {
                DialogUtils.showScrollableDialog(
                    activity,
                    activity.getString(R.string.error),
                    message,
                    activity.getString(android.R.string.ok)
                )
            } else {
                Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    fun showTestResultDialog(message: String, isSuccess: Boolean) {
        val title = if (isSuccess) activity.getString(R.string.connection_test_success_title)
                    else activity.getString(R.string.connection_test_failed_title)
        DialogUtils.showScrollableDialog(activity, title, message, activity.getString(android.R.string.ok))
    }

    fun showRememberFileListHelpDialog() {
        AlertDialog.Builder(activity)
            .setTitle(R.string.remember_file_list_help_title)
            .setMessage(R.string.remember_file_list_help_message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    fun showDetailedErrorDialog(titleRes: Int, details: String?) {
        ErrorDialog.show(
            context = activity,
            title = activity.getString(titleRes),
            message = details?.takeIf { it.isNotBlank() } ?: activity.getString(R.string.error_unknown)
        )
    }
}
