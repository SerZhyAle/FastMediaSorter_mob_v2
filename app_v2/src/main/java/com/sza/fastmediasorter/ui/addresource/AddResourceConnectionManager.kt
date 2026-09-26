package com.sza.fastmediasorter.ui.addresource

import android.content.Intent
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.error.ErrorSeverity
import com.sza.fastmediasorter.core.util.PermissionHelper
import com.sza.fastmediasorter.data.cloud.CloudProvider
import com.sza.fastmediasorter.data.cloud.CloudResult
import com.sza.fastmediasorter.data.cloud.DropboxClient
import com.sza.fastmediasorter.data.cloud.GoogleDriveBrowserAuthManager
import com.sza.fastmediasorter.data.cloud.OneDriveRestClient
import com.sza.fastmediasorter.data.cloud.UnifiedCloudAuthManager
import com.sza.fastmediasorter.domain.identity.GoogleIdentityRepository
import com.sza.fastmediasorter.domain.identity.PrimaryGoogleAccountState
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.stats.StatsEvent
import com.sza.fastmediasorter.domain.stats.StatsSink
import com.sza.fastmediasorter.ui.common.permissions.permissionRationale
import com.sza.fastmediasorter.ui.dialog.DialogKeyboardDelegate
import com.sza.fastmediasorter.ui.dialog.ScrollableTextDialog
import com.sza.fastmediasorter.util.AppErrorNotifier
import com.sza.fastmediasorter.util.showBoundToHost
import com.sza.fastmediasorter.utils.collectOnLifecycle
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import timber.log.Timber

internal class AddResourceConnectionManager(
    private val activity: AddResourceActivity,
    private val viewModel: AddResourceViewModel,
    private val unifiedAuthManager: UnifiedCloudAuthManager,
    private val dropboxClient: dagger.Lazy<DropboxClient>,
    private val oneDriveClient: dagger.Lazy<OneDriveRestClient>
) {

    // S1519: lazy ViewStub-backed form bindings owned by the activity (inflate on first access).
    private val smbForm get() = activity.forms.smb
    private val sftpForm get() = activity.forms.sftp
    private val cloudForm get() = activity.forms.cloud

    private val identityRepository: GoogleIdentityRepository by lazy {
        EntryPointAccessors.fromApplication(
            activity.applicationContext,
            AddResourceIdentityEntryPoint::class.java
        ).identityRepository()
    }

    private val browserAuthManager: GoogleDriveBrowserAuthManager by lazy {
        EntryPointAccessors.fromApplication(
            activity.applicationContext,
            AddResourceIdentityEntryPoint::class.java
        ).browserAuthManager()
    }

    // S0473: accessed via the same EntryPoint pattern as the auth managers above so the cloud-
    // connected stat can be recorded without threading StatsSink through every construction site.
    private val statsSink: StatsSink by lazy {
        EntryPointAccessors.fromApplication(
            activity.applicationContext,
            AddResourceIdentityEntryPoint::class.java
        ).statsSink()
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface AddResourceIdentityEntryPoint {
        fun identityRepository(): GoogleIdentityRepository
        fun browserAuthManager(): GoogleDriveBrowserAuthManager
        fun statsSink(): StatsSink
    }

    fun observeAuthEvents() {
        activity.collectOnLifecycle(unifiedAuthManager.authEvents) { event ->
            when (event) {
                is UnifiedCloudAuthManager.AuthEvent.Success -> {
                    // S0473: a cloud source connected successfully.
                    statsSink.record(StatsEvent.SourceConnected())
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
                    val message = if (
                        event.provider == CloudProvider.GOOGLE_DRIVE && event.message.isBlank()
                    ) {
                        activity.getString(R.string.s0294_google_drive_browser_auth_failed_message)
                    } else {
                        event.message
                    }
                    showDetailedErrorDialog(titleRes, message)
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
        Timber.d("S3735: cloud status with cancellation rethrow")
        // Google Drive can now be backed either by the primary identity-domain account or by a
        // browser-authenticated Quest/XR account stored for Drive-specific reuse.
        val boundEmail = (identityRepository.state.value as? PrimaryGoogleAccountState.Bound)?.account?.email
            ?: browserAuthManager.peekStoredAccountEmail()
        cloudForm.tvGoogleDriveStatus.isVisible = true
        cloudForm.tvGoogleDriveStatus.text = if (boundEmail != null) {
            activity.getString(R.string.connected_as, boundEmail)
        } else {
            activity.getString(R.string.not_connected)
        }

        activity.lifecycleScope.launch {
            try {
                val restored = dropboxClient.get().tryRestoreFromStorage()
                cloudForm.tvDropboxStatus.text = if (restored) {
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to restore Dropbox connection")
                cloudForm.tvDropboxStatus.text = activity.getString(R.string.not_connected)
            }
        }

        activity.lifecycleScope.launch {
            try {
                cloudForm.tvOneDriveStatus.text = if (oneDriveClient.get().isAuthenticated()) {
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to check OneDrive connection")
                cloudForm.tvOneDriveStatus.text = activity.getString(R.string.not_connected)
            }
        }
    }

    // ========== Google Drive ==========

    private fun navigateToGoogleDriveFolderPicker(accountEmail: String? = null) {
        val intent = Intent(
            activity,
            com.sza.fastmediasorter.ui.cloudfolders.GoogleDriveFolderPickerActivity::class.java
        )
            .apply { accountEmail?.let { putExtra("extra_account_email", it) } }
        activity.startActivity(intent)
    }

    // ========== Dropbox ==========

    private fun navigateToDropboxFolderPicker(accountEmail: String? = null) {
        val intent = Intent(activity, com.sza.fastmediasorter.ui.cloudfolders.DropboxFolderPickerActivity::class.java)
            .apply { accountEmail?.let { putExtra("extra_account_email", it) } }
        activity.startActivity(intent)
    }

    // ========== OneDrive ==========

    private fun navigateToOneDriveFolderPicker(accountEmail: String? = null) {
        val intent = Intent(activity, com.sza.fastmediasorter.ui.cloudfolders.OneDriveFolderPickerActivity::class.java)
            .apply { accountEmail?.let { putExtra("extra_account_email", it) } }
        activity.startActivity(intent)
    }

    // ========== Account Picker ==========

    private fun providerFromName(providerName: String): CloudProvider? = when (providerName) {
        CloudProvider.GOOGLE_DRIVE.name -> CloudProvider.GOOGLE_DRIVE
        CloudProvider.ONEDRIVE.name -> CloudProvider.ONEDRIVE
        CloudProvider.DROPBOX.name -> CloudProvider.DROPBOX
        else -> null
    }

    fun showAccountPicker(providerName: String, accounts: List<String>) {
        // No stored accounts yet -> the picker would contain only "Add new account".
        // Skip the redundant one-item dialog and start interactive sign-in directly
        // (the auth flow itself drives the native account picker / browser OAuth per platform).
        if (accounts.isEmpty()) {
            providerFromName(providerName)?.let { unifiedAuthManager.startInteractiveSignIn(activity, it) }
            return
        }
        val options = accounts.toMutableList().also { it.add(activity.getString(R.string.add_new_account)) }
        val titleRes = when (providerName) {
            CloudProvider.GOOGLE_DRIVE.name -> R.string.google_drive
            CloudProvider.ONEDRIVE.name -> R.string.onedrive
            CloudProvider.DROPBOX.name -> R.string.dropbox
            else -> R.string.cloud_storage
        }
        val accountDialog = MaterialAlertDialogBuilder(activity)
            .setTitle(activity.getString(titleRes))
            .setItems(options.toTypedArray()) { _, which ->
                if (which == options.size - 1) {
                    val provider = providerFromName(providerName) ?: return@setItems
                    unifiedAuthManager.startInteractiveSignIn(activity, provider)
                } else {
                    when (providerName) {
                        CloudProvider.GOOGLE_DRIVE.name -> navigateToGoogleDriveFolderPicker(options[which])
                        CloudProvider.ONEDRIVE.name -> navigateToOneDriveFolderPicker(options[which])
                        CloudProvider.DROPBOX.name -> navigateToDropboxFolderPicker(options[which])
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
        DialogKeyboardDelegate.applyTo(accountDialog) {}
        accountDialog.showBoundToHost(activity)
    }

    // ========== SMB / SFTP Connection Testing ==========

    fun testSmbConnection() {
        val server = smbForm.etSmbServer.text.toString().trim().replace(',', '.')
        if (!smbForm.etSmbServer.isValid()) {
            AppErrorNotifier.show(activity, activity.getString(R.string.invalid_server_address), ErrorSeverity.CRITICAL)
            smbForm.etSmbServer.requestFocus()
            return
        }
        if (server.isEmpty()) {
            AppErrorNotifier.show(
                activity,
                activity.getString(R.string.server_address_required),
                ErrorSeverity.CRITICAL
            )
            return
        }
        viewModel.testSmbConnection(
            server,
            smbForm.etSmbShareName.text.toString().trim(),
            smbForm.etSmbUsername.text.toString().trim(),
            smbForm.etSmbPassword.text.toString().trim(),
            smbForm.etSmbDomain.text.toString().trim(),
            smbForm.etSmbPort.text.toString().trim().toIntOrNull() ?: 445
        )
    }

    fun testSftpConnection() {
        val protocolType = getSelectedProtocol()
        val host = sftpForm.etSftpHost.text.toString().trim()
        if (!sftpForm.etSftpHost.isValid()) {
            AppErrorNotifier.show(activity, activity.getString(R.string.invalid_host_address), ErrorSeverity.CRITICAL)
            sftpForm.etSftpHost.requestFocus()
            return
        }
        if (host.isEmpty()) {
            AppErrorNotifier.show(activity, activity.getString(R.string.host_required), ErrorSeverity.CRITICAL)
            return
        }
        val defaultPort = if (protocolType == ResourceType.SFTP) 22 else 21
        val port = sftpForm.etSftpPort.text.toString().trim().toIntOrNull() ?: defaultPort
        val username = sftpForm.etSftpUsername.text.toString().trim()
        val expectedFingerprint = sftpForm.etSftpHostKeyFingerprint.text.toString().trim().ifEmpty { null }

        if (protocolType == ResourceType.SFTP && sftpForm.rbSftpSshKey.isChecked) {
            val privateKey = sftpForm.etSftpPrivateKey.text.toString().trim()
            if (privateKey.isEmpty()) {
                AppErrorNotifier.show(activity, activity.getString(R.string.ssh_key_required), ErrorSeverity.CRITICAL)
                return
            }
            viewModel.testSftpConnectionWithKey(
                host,
                port,
                username,
                privateKey,
                sftpForm.etSftpKeyPassphrase.text.toString().trim().ifEmpty { null },
                expectedFingerprint
            )
        } else {
            viewModel.testSftpFtpConnection(
                protocolType,
                host,
                port,
                username,
                sftpForm.etSftpPassword.text.toString().trim(),
                expectedFingerprint
            )
        }
    }

    private fun getSelectedProtocol(): ResourceType = when (sftpForm.rgProtocol.checkedRadioButtonId) {
        sftpForm.rbSftp.id -> ResourceType.SFTP
        sftpForm.rbFtp.id -> ResourceType.FTP
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
            displayItems.addAll(uniqueManual.map { "\u2713 $it" }) // ✓ prefix for previously-used entries
        }
        displayItems.addAll(shares)
        displayItems.add(activity.getString(R.string.smb_enter_share_manually))

        // Map display index → actual share name (null = "enter manually" option)
        val resolvedNames: List<String?> = buildList {
            uniqueManual.forEach { add(it) }
            shares.forEach { add(it) }
            add(null) // "enter manually" sentinel
        }

        val sharePickerDialog = MaterialAlertDialogBuilder(activity)
            .setTitle(activity.getString(R.string.msg_select_share, server))
            .setItems(displayItems.toTypedArray()) { _, which ->
                val resolved = resolvedNames[which]
                if (resolved == null) {
                    // User tapped "Enter manually" - show input dialog
                    showManualShareInputDialog(server)
                } else {
                    smbForm.etSmbShareName.setText(resolved)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
        DialogKeyboardDelegate.applyTo(sharePickerDialog) {}
        sharePickerDialog.showBoundToHost(activity)
    }

    fun showNoSharesFoundDialog() {
        val noSharesDialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.smb_no_shares_found_title)
            .setMessage(R.string.msg_no_shares_found)
            .setNegativeButton(R.string.cancel, null)
            .create()
        DialogKeyboardDelegate.applyTo(noSharesDialog) {}
        noSharesDialog.showBoundToHost(activity)
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
        val port = smbForm.etSmbPort.text?.toString()?.toIntOrNull() ?: 445

        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(activity.getString(R.string.smb_manual_share_dialog_title))
            .setView(editText)
            .setPositiveButton(R.string.ok, null) // set below to prevent auto-dismiss on invalid input
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val input = editText.text?.toString()?.trim().orEmpty()
                // Client-side validation: SMB share names - letters, digits, spaces, hyphens, underscores; 1-80 chars
                val valid = input.isNotBlank() && input.length <= 80 &&
                    input.matches(Regex("[A-Za-z0-9][A-Za-z0-9 _\\-]{0,79}"))
                if (!valid) {
                    editText.error = activity.getString(R.string.smb_share_name_invalid)
                    return@setOnClickListener
                }
                smbForm.etSmbShareName.setText(input)
                // Persist to history immediately - user confirmed intent; connection may still fail.
                viewModel.rememberManualShareName(server, port, input)
                dialog.dismiss()
            }
        }
        dialog.showBoundToHost(activity)
        // Request focus and show keyboard
        editText.requestFocus()
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
    }

    fun showError(message: String) {
        activity.lifecycleScope.launch {
            val settings = viewModel.getSettings()
            if (settings.showDetailedErrors) {
                ScrollableTextDialog.show(
                    context = activity,
                    title = activity.getString(R.string.error),
                    message = message,
                    showSave = false
                )
            } else {
                AppErrorNotifier.show(activity, message, ErrorSeverity.CRITICAL)
            }
        }
    }

    fun showTestResultDialog(message: String, isSuccess: Boolean, presentedFingerprint: String? = null) {
        val title = if (isSuccess) {
            activity.getString(R.string.connection_test_success_title)
        } else {
            activity.getString(R.string.connection_test_failed_title)
        }
        val currentFingerprint = sftpForm.etSftpHostKeyFingerprint.text?.toString()?.trim().orEmpty()
        val canOfferPin = isSuccess && !presentedFingerprint.isNullOrBlank() && currentFingerprint.isEmpty()

        val fullMessage = if (canOfferPin) {
            "$message\n\n${activity.getString(R.string.sftp_host_key_presented_format, presentedFingerprint)}"
        } else {
            message
        }

        if (canOfferPin) {
            ScrollableTextDialog.show(
                context = activity,
                title = title,
                message = fullMessage,
                showSave = false,
                actionButtonText = activity.getString(R.string.sftp_pin_host_key),
                onActionClick = {
                    sftpForm.headerSftpServerVerification.setExpanded(true, notify = false)
                    sftpForm.etSftpHostKeyFingerprint.setText(presentedFingerprint)
                    Toast.makeText(
                        activity,
                        activity.getString(R.string.sftp_host_key_pinned_toast),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )
        } else {
            ScrollableTextDialog.show(context = activity, title = title, message = fullMessage, showSave = false)
        }
    }

    fun showLocalNetworkPermissionRationale() {
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.permissions_required_title)
            .setMessage(activity.permissionRationale(PermissionHelper.LOCAL_NETWORK_PERMISSION))
            .setPositiveButton(R.string.local_network_permission_open_settings) { _, _ ->
                if (PermissionHelper.isLocalNetworkRuntimePermissionExpected()) {
                    PermissionHelper.requestLocalNetworkPermission(activity)
                } else {
                    PermissionHelper.routeToLocalNetworkSettings(activity)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .showBoundToHost(activity)
    }

    fun showDetailedErrorDialog(titleRes: Int, details: String?) {
        // S0294: `details` may be either a curated human-facing message (e.g. the
        // `s0294_google_drive_browser_*` strings the unified auth manager hands us when GMS-less
        // browser sign-in fails) or a raw provider exception. Curated strings should reach the
        // user verbatim so the cause is actionable; raw exception text would leak technical
        // jargon, so the contract here is: callers pass `null` (or blank) when they only have a
        // technical message, and the dialog falls back to the friendly default. Callers that
        // built a localised message pass it through and it is rendered as-is.
        val message = if (!details.isNullOrBlank()) {
            details
        } else {
            activity.getString(R.string.friendly_copy_error_auth_failed)
        }
        ScrollableTextDialog.show(
            context = activity,
            title = activity.getString(titleRes),
            message = message
        )
    }
}
