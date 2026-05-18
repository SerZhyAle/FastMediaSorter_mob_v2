package com.sza.fastmediasorter.ui.addresource

import android.app.AlertDialog
import android.content.Intent
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.utils.collectOnLifecycle
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.util.PermissionHelper
import com.sza.fastmediasorter.data.cloud.CloudProvider
import com.sza.fastmediasorter.data.cloud.CloudResult
import com.sza.fastmediasorter.data.cloud.DropboxClient
import com.sza.fastmediasorter.data.cloud.OneDriveRestClient
import com.sza.fastmediasorter.data.cloud.UnifiedCloudAuthManager
import com.sza.fastmediasorter.databinding.ActivityAddResourceBinding
import com.sza.fastmediasorter.domain.identity.GoogleIdentityRepository
import com.sza.fastmediasorter.domain.identity.PrimaryGoogleAccountState
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.core.error.ErrorSeverity
import com.sza.fastmediasorter.ui.common.DialogUtils
import com.sza.fastmediasorter.ui.dialog.ErrorDialog
import com.sza.fastmediasorter.util.AppErrorNotifier
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
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

    /** S0200 Phase 04b: email of the currently bound primary Google account via identity domain. */
    private var googleDriveAccountEmail: String? = null

    private val identityRepository: GoogleIdentityRepository by lazy {
        EntryPointAccessors.fromApplication(
            activity.applicationContext,
            AddResourceIdentityEntryPoint::class.java
        ).identityRepository()
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface AddResourceIdentityEntryPoint {
        fun identityRepository(): GoogleIdentityRepository
    }

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
        // S0200 Phase 04b: source primary account state from the identity domain.
        val boundEmail = (identityRepository.state.value as? PrimaryGoogleAccountState.Bound)?.account?.email
        googleDriveAccountEmail = boundEmail
        binding.tvGoogleDriveStatus.isVisible = true
        binding.tvGoogleDriveStatus.text = if (boundEmail != null) {
            activity.getString(R.string.connected_as, boundEmail)
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
        val boundEmail = (identityRepository.state.value as? PrimaryGoogleAccountState.Bound)?.account?.email
        if (boundEmail != null) showGoogleDriveSignedInOptions(boundEmail)
        else unifiedAuthManager.startInteractiveSignIn(activity, CloudProvider.GOOGLE_DRIVE)
    }

    private fun showGoogleDriveSignedInOptions(accountEmail: String) {
        AlertDialog.Builder(activity)
            .setTitle(R.string.google_drive)
            .setMessage(R.string.msg_already_authenticated)
            .setPositiveButton(R.string.google_drive_select_folder) { _, _ ->
                navigateToGoogleDriveFolderPicker(accountEmail)
            }
            .setNeutralButton(android.R.string.cancel, null)
            .show()
    }

    @Suppress("unused")
    private fun signOutGoogleDrive() {
        // S0200 Phase 04b: primary-account sign-out goes through the identity domain.
        activity.lifecycleScope.launch {
            try {
                identityRepository.signOutPrimary()
                googleDriveAccountEmail = null
                updateCloudStorageStatus()
                Toast.makeText(activity, activity.getString(R.string.google_drive_signed_out), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Timber.e(e, "Failed to sign out from Google Drive (identity domain)")
            }
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
                AppErrorNotifier.show(
                    activity,
                    activity.getString(R.string.onedrive_authentication_failed),
                    ErrorSeverity.CRITICAL
                )
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
            AppErrorNotifier.show(activity, activity.getString(R.string.invalid_server_address), ErrorSeverity.CRITICAL)
            binding.etSmbServer.requestFocus()
            return
        }
        if (server.isEmpty()) {
            AppErrorNotifier.show(activity, activity.getString(R.string.server_address_required), ErrorSeverity.CRITICAL)
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
            AppErrorNotifier.show(activity, activity.getString(R.string.invalid_host_address), ErrorSeverity.CRITICAL)
            binding.etSftpHost.requestFocus()
            return
        }
        if (host.isEmpty()) {
            AppErrorNotifier.show(activity, activity.getString(R.string.host_required), ErrorSeverity.CRITICAL)
            return
        }
        val defaultPort = if (protocolType == ResourceType.SFTP) 22 else 21
        val port = binding.etSftpPort.text.toString().trim().toIntOrNull() ?: defaultPort
        val username = binding.etSftpUsername.text.toString().trim()

        if (protocolType == ResourceType.SFTP && binding.rbSftpSshKey.isChecked) {
            val privateKey = binding.etSftpPrivateKey.text.toString().trim()
            if (privateKey.isEmpty()) {
                AppErrorNotifier.show(activity, activity.getString(R.string.ssh_key_required), ErrorSeverity.CRITICAL)
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

    fun showSharePickerDialog(server: String, shares: List<String>, manualShares: List<String> = emptyList()) {
        // Build a combined item list:
        //   [previously used / manual]  - deduplicated against auto-discovered
        //   [auto-discovered]
        //   "+ Enter share name manually.."
        val autoSet = shares.map { it.lowercase() }.toSet()
        val uniqueManual = manualShares.filter { it.lowercase() !in autoSet }

        val displayItems = mutableListOf<String>()
        if (uniqueManual.isNotEmpty()) {
            // Section header (non-selectable appearance via a prefix marker)
            displayItems.addAll(uniqueManual.map { "\u2713 $it" })  // ✓ prefix for previously-used entries
        }
        displayItems.addAll(shares)
        displayItems.add(activity.getString(R.string.smb_enter_share_manually))

        // Map display index → actual share name (null = "enter manually" option)
        val resolvedNames: List<String?> = buildList {
            uniqueManual.forEach { add(it) }
            shares.forEach { add(it) }
            add(null)  // "enter manually" sentinel
        }

        AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.msg_select_share, server))
            .setItems(displayItems.toTypedArray()) { _, which ->
                val resolved = resolvedNames[which]
                if (resolved == null) {
                    // User tapped "Enter manually" - show input dialog
                    showManualShareInputDialog(server)
                } else {
                    binding.etSmbShareName.setText(resolved)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * S0064: Shows a validated EditText dialog for manual SMB share name entry.
     * On confirm, fills the share name field and persists the name to history.
     */
    private fun showManualShareInputDialog(server: String) {
        val editText = android.widget.EditText(activity).apply {
            hint = activity.getString(R.string.smb_manual_share_hint)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setSingleLine(true)
            // Accessibility: min touch target height handled by dialog padding; contentDescription set for TalkBack
            contentDescription = activity.getString(R.string.smb_manual_share_dialog_title)
            val padPx = (12 * activity.resources.displayMetrics.density).toInt()
            setPadding(padPx, padPx, padPx, padPx)
        }
        val port = binding.etSmbPort.text?.toString()?.toIntOrNull() ?: 445

        val dialog = AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.smb_manual_share_dialog_title))
            .setView(editText)
            .setPositiveButton(android.R.string.ok, null) // set below to prevent auto-dismiss on invalid input
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val input = editText.text?.toString()?.trim().orEmpty()
                // Client-side validation: SMB share names - letters, digits, spaces, hyphens, underscores; 1–80 chars
                val valid = input.isNotBlank() && input.length <= 80 &&
                    input.matches(Regex("[A-Za-z0-9][A-Za-z0-9 _\\-]{0,79}"))
                if (!valid) {
                    editText.error = activity.getString(R.string.smb_share_name_invalid)
                    return@setOnClickListener
                }
                binding.etSmbShareName.setText(input)
                // Persist to history immediately - user confirmed intent; connection may still fail.
                viewModel.rememberManualShareName(server, port, input)
                dialog.dismiss()
            }
        }
        dialog.show()
        // Request focus and show keyboard
        editText.requestFocus()
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
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
                AppErrorNotifier.show(activity, message, ErrorSeverity.CRITICAL)
            }
        }
    }

    fun showTestResultDialog(message: String, isSuccess: Boolean) {
        val title = if (isSuccess) activity.getString(R.string.connection_test_success_title)
                    else activity.getString(R.string.connection_test_failed_title)
        DialogUtils.showScrollableDialog(activity, title, message, activity.getString(android.R.string.ok))
    }

    fun showLocalNetworkPermissionRationale() {
        AlertDialog.Builder(activity)
            .setTitle(R.string.local_network_permission_rationale_title)
            .setMessage(R.string.local_network_permission_rationale_message)
            .setPositiveButton(R.string.local_network_permission_open_settings) { _, _ ->
                if (PermissionHelper.isLocalNetworkRuntimePermissionExpected()) {
                    PermissionHelper.requestLocalNetworkPermission(activity)
                } else {
                    PermissionHelper.routeToLocalNetworkSettings(activity)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
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
            // Auth providers often return technical exception text here; keep the dialog human-facing.
            message = activity.getString(R.string.friendly_copy_error_auth_failed)
        )
    }
}
