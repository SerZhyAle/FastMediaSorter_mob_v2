package com.sza.fastmediasorter.ui.settings.helpers

import android.content.Intent
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.utils.collectOnLifecycle
import com.google.android.material.snackbar.Snackbar
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.FragmentSettingsGeneralBinding
import com.sza.fastmediasorter.domain.usecase.RestoreFromGoogleDriveUseCase
import com.sza.fastmediasorter.ui.settings.BackupRestoreUiState
import com.sza.fastmediasorter.ui.settings.BackupRestoreViewModel
import timber.log.Timber

class GeneralSettingsBackupHelper(
    private val binding: FragmentSettingsGeneralBinding,
    private val fragment: Fragment,
    private val backupViewModel: BackupRestoreViewModel,
    private val signInLauncher: ActivityResultLauncher<Intent>,
) {
    fun setupWearCompanionButton() {
        if (!BuildConfig.SUPPORT_WEAR_COMPANION) {
            binding.btnWearCompanion?.visibility = View.GONE
            binding.dividerWearCompanion?.visibility = View.GONE
            return
        }
        binding.btnWearCompanion?.setOnClickListener {
            if (fragment.childFragmentManager.findFragmentByTag("wear_sync") == null) {
                com.sza.fastmediasorter.ui.settings.fragments.WearSyncSettingsFragment()
                    .show(fragment.childFragmentManager, "wear_sync")
            }
        }
    }

    fun setupBackupButtons() {
        binding.btnBackup.setOnClickListener { backupViewModel.startBackup() }
        binding.btnRestore.setOnClickListener { backupViewModel.startRestore() }
        binding.iconHelpBackupInfo.setOnClickListener {
            com.sza.fastmediasorter.ui.dialog.TooltipDialog.show(
                fragment.requireContext(),
                R.string.tooltip_backup_info_title,
                R.string.tooltip_backup_info_message
            )
        }
    }

    fun observeBackupState() {
        fragment.viewLifecycleOwner.collectOnLifecycle(backupViewModel.uiState) { state -> handleBackupState(state) }
    }

    fun updateBackupAccountInfo() {
        val email = backupViewModel.getAccountEmail()
        if (email != null) {
            binding.layoutBackupInfo.visibility = View.VISIBLE
            binding.tvBackupAccount.text = fragment.getString(R.string.backup_account, email)
        } else {
            binding.layoutBackupInfo.visibility = View.GONE
        }
    }

    private fun handleBackupState(state: BackupRestoreUiState) {
        binding.progressBackup.visibility = View.GONE
        binding.progressRestore.visibility = View.GONE
        binding.btnBackup.isEnabled = true
        binding.btnRestore.isEnabled = true

        when (state) {
            is BackupRestoreUiState.Idle -> {}
            is BackupRestoreUiState.Authenticating -> {
                binding.btnBackup.isEnabled = false
                binding.btnRestore.isEnabled = false
            }
            is BackupRestoreUiState.NeedsSignIn -> launchBackupSignIn()
            is BackupRestoreUiState.BackingUp -> {
                binding.progressBackup.visibility = View.VISIBLE
                binding.btnBackup.isEnabled = false
                binding.btnRestore.isEnabled = false
            }
            is BackupRestoreUiState.FetchingInfo -> {
                binding.progressRestore.visibility = View.VISIBLE
                binding.btnBackup.isEnabled = false
                binding.btnRestore.isEnabled = false
            }
            is BackupRestoreUiState.Restoring -> {
                binding.progressRestore.visibility = View.VISIBLE
                binding.btnBackup.isEnabled = false
                binding.btnRestore.isEnabled = false
            }
            is BackupRestoreUiState.BackupSuccess -> {
                showBackupSnackbar(fragment.getString(R.string.backup_success, state.resourceCount, state.favoritesCount, state.accountEmail))
                updateBackupAccountInfo()
                backupViewModel.resetState()
            }
            is BackupRestoreUiState.BackupInfoReady -> showRestoreConfirmDialog(state.info)
            is BackupRestoreUiState.RestoreSuccess -> {
                showRestoreSuccessMessage(state.result)
                backupViewModel.resetState()
            }
            is BackupRestoreUiState.Error -> {
                showBackupSnackbar(state.message)
                backupViewModel.resetState()
            }
        }
    }

    private fun launchBackupSignIn() {
        try {
            val intent = backupViewModel.getSignInIntent()
            signInLauncher.launch(intent)
        } catch (e: Exception) {
            Timber.e(e, "Failed to launch Google sign-in")
            showBackupSnackbar(fragment.getString(R.string.backup_failed, e.message ?: "Unknown error"))
        }
    }

    private fun showRestoreConfirmDialog(info: RestoreFromGoogleDriveUseCase.BackupInfo) {
        MaterialAlertDialogBuilder(fragment.requireContext())
            .setTitle(R.string.restore_confirm_title)
            .setMessage(fragment.getString(R.string.restore_confirm_message, info.createdAt, info.deviceModel, info.resourceCount, info.favoritesCount))
            .setPositiveButton(R.string.restore_from_google_drive) { _, _ -> backupViewModel.confirmRestore() }
            .setNegativeButton(android.R.string.cancel) { _, _ -> backupViewModel.resetState() }
            .setOnCancelListener { backupViewModel.resetState() }
            .show()
    }

    private fun showRestoreSuccessMessage(result: RestoreFromGoogleDriveUseCase.RestoreResult) {
        showBackupSnackbar(fragment.getString(R.string.restore_success, result.resourcesAdded, result.resourcesSkipped, result.favoritesAdded, result.favoritesSkipped))
        if (result.resourcesNeedingAuth > 0) {
            Snackbar.make(binding.root, fragment.getString(R.string.restore_needs_auth), Snackbar.LENGTH_LONG).show()
        }
    }

    private fun showBackupSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }
}
