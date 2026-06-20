package com.sza.fastmediasorter.ui.settings.helpers

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RadioButton
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.utils.collectOnLifecycle
import com.google.android.material.snackbar.Snackbar
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.FragmentSettingsGeneralBinding
import com.sza.fastmediasorter.domain.model.FavoritesConflictStrategy
import com.sza.fastmediasorter.domain.model.FavoritesImportResult
import com.sza.fastmediasorter.domain.model.ResourceShareFormat
import com.sza.fastmediasorter.domain.usecase.RestoreFromGoogleDriveUseCase
import com.sza.fastmediasorter.ui.settings.BackupRestoreUiState
import com.sza.fastmediasorter.ui.settings.BackupRestoreViewModel
import com.sza.fastmediasorter.ui.settings.FavoritesExportUiState
import com.sza.fastmediasorter.ui.settings.FavoritesImportUiState
import com.sza.fastmediasorter.ui.settings.ResourceShareExportUiState
import com.sza.fastmediasorter.ui.settings.ResourceShareImportUiState
import timber.log.Timber
import java.io.File

/**
 * Hosts the App Data & Backups card logic: Google Drive backup/restore plus the favorites and
 * resource-share file export/import flows. The three SAF launchers are owned by the fragment
 * (they must be registered before STARTED) and passed in here.
 */
class GeneralSettingsBackupHelper(
    private val binding: FragmentSettingsGeneralBinding,
    private val fragment: Fragment,
    private val backupViewModel: BackupRestoreViewModel,
    private val mediaCapabilities: MediaCapabilities,
    private val importFavoritesLauncher: ActivityResultLauncher<Array<String>>,
    private val exportResourcesLauncher: ActivityResultLauncher<String>,
    private val importResourcesLauncher: ActivityResultLauncher<Array<String>>,
) {
    fun setupWearCompanionButton() {
        if (!mediaCapabilities.supportsWearCompanion) {
            binding.containerWearCompanion?.visibility = View.GONE
            return
        }
        binding.btnWearCompanion?.setOnClickListener {
            if (fragment.childFragmentManager.findFragmentByTag("wear_sync") == null) {
                com.sza.fastmediasorter.ui.settings.fragments.WearSyncSettingsFragment()
                    .show(fragment.childFragmentManager, "wear_sync")
            }
        }
        binding.iconHelpWearCompanion?.setOnClickListener {
            com.sza.fastmediasorter.ui.dialog.TooltipDialog.show(
                fragment.requireContext(),
                R.string.tooltip_wear_companion_title,
                R.string.tooltip_wear_companion_message
            )
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

    /** S0200 Phase 04c: Credential Manager sign-in via the ViewModel's [BackupRestoreViewModel.startSignIn]. */
    private fun launchBackupSignIn() {
        try {
            backupViewModel.startSignIn(fragment.requireActivity())
        } catch (e: Exception) {
            Timber.e(e, "Failed to launch Google sign-in")
            showBackupSnackbar(fragment.getString(R.string.backup_failed))
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

    // ── Favorites + resource-share export/import (S0491 resurrection) ─────────────

    fun setupExportImportButtons() {
        binding.btnExportFavorites.setOnClickListener { backupViewModel.exportFavorites() }
        binding.btnImportFavorites.setOnClickListener {
            try {
                importFavoritesLauncher.launch(arrayOf("application/json"))
            } catch (e: ActivityNotFoundException) {
                Timber.w(e, "Favorites import: file picker not available")
                showBackupSnackbar(fragment.getString(R.string.save_logs_not_supported))
            }
        }
        binding.btnExportResources.setOnClickListener {
            MaterialAlertDialogBuilder(fragment.requireContext())
                .setTitle(R.string.resource_share_export_title)
                .setMessage(R.string.resource_share_credentials_warning)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    try {
                        exportResourcesLauncher.launch("fms_resources.${ResourceShareFormat.EXTENSION}")
                    } catch (e: ActivityNotFoundException) {
                        Timber.w(e, "Resource export: document creator not available")
                        showBackupSnackbar(fragment.getString(R.string.resource_share_export_failed))
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
        binding.btnImportResources.setOnClickListener {
            try {
                importResourcesLauncher.launch(
                    arrayOf(ResourceShareFormat.MIME_TYPE, "application/xml", "text/xml", "*/*")
                )
            } catch (e: ActivityNotFoundException) {
                Timber.w(e, "Resource import: file picker not available")
                showBackupSnackbar(fragment.getString(R.string.save_logs_not_supported))
            }
        }
    }

    fun observeExportImportState() {
        fragment.viewLifecycleOwner.collectOnLifecycle(backupViewModel.exportFavState) { handleExportFavState(it) }
        fragment.viewLifecycleOwner.collectOnLifecycle(backupViewModel.importFavState) { handleImportFavState(it) }
        fragment.viewLifecycleOwner.collectOnLifecycle(backupViewModel.exportResState) { handleExportResState(it) }
        fragment.viewLifecycleOwner.collectOnLifecycle(backupViewModel.importResState) { handleImportResState(it) }
    }

    private fun handleExportFavState(state: FavoritesExportUiState) {
        binding.progressExportFav.visibility = if (state is FavoritesExportUiState.Loading) View.VISIBLE else View.GONE
        binding.btnExportFavorites.isEnabled = state !is FavoritesExportUiState.Loading
        when (state) {
            is FavoritesExportUiState.Success -> {
                showExportFavSuccessDialog(state.result.filePath)
                backupViewModel.resetExportFavState()
            }
            is FavoritesExportUiState.Error -> {
                showBackupSnackbar(state.message)
                backupViewModel.resetExportFavState()
            }
            else -> Unit
        }
    }

    private fun handleImportFavState(state: FavoritesImportUiState) {
        binding.progressImportFav.visibility =
            if (state is FavoritesImportUiState.LoadingPreview || state is FavoritesImportUiState.Importing) View.VISIBLE else View.GONE
        binding.btnImportFavorites.isEnabled =
            state !is FavoritesImportUiState.LoadingPreview && state !is FavoritesImportUiState.Importing
        when (state) {
            is FavoritesImportUiState.Preview -> showImportFavPreviewDialog(state.uri)
            is FavoritesImportUiState.Success -> {
                showImportFavResultDialog(state.result)
                backupViewModel.resetImportFavState()
            }
            is FavoritesImportUiState.Error -> {
                showBackupSnackbar(state.message)
                backupViewModel.resetImportFavState()
            }
            else -> Unit
        }
    }

    private fun handleExportResState(state: ResourceShareExportUiState) {
        binding.progressExportResources.visibility = if (state is ResourceShareExportUiState.Loading) View.VISIBLE else View.GONE
        binding.btnExportResources.isEnabled = state !is ResourceShareExportUiState.Loading
        when (state) {
            is ResourceShareExportUiState.Success -> {
                MaterialAlertDialogBuilder(fragment.requireContext())
                    .setTitle(R.string.resource_share_export_title)
                    .setMessage(fragment.getString(R.string.resource_share_export_success, state.exported, state.skipped))
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
                backupViewModel.resetExportResState()
            }
            is ResourceShareExportUiState.Error -> {
                showBackupSnackbar(state.message)
                backupViewModel.resetExportResState()
            }
            else -> Unit
        }
    }

    private fun handleImportResState(state: ResourceShareImportUiState) {
        binding.progressImportResources.visibility =
            if (state is ResourceShareImportUiState.LoadingPreview || state is ResourceShareImportUiState.Importing) View.VISIBLE else View.GONE
        binding.btnImportResources.isEnabled =
            state !is ResourceShareImportUiState.LoadingPreview && state !is ResourceShareImportUiState.Importing
        when (state) {
            is ResourceShareImportUiState.Preview -> showResourceImportPreviewDialog(state)
            is ResourceShareImportUiState.Success -> {
                MaterialAlertDialogBuilder(fragment.requireContext())
                    .setTitle(R.string.resource_share_import_title)
                    .setMessage(fragment.getString(R.string.resource_share_import_success, state.created, state.updated, state.skipped))
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
                backupViewModel.resetImportResState()
            }
            is ResourceShareImportUiState.Error -> {
                showBackupSnackbar(state.message)
                backupViewModel.resetImportResState()
            }
            else -> Unit
        }
    }

    private fun showExportFavSuccessDialog(filePath: String) {
        MaterialAlertDialogBuilder(fragment.requireContext())
            .setTitle(R.string.export_fav_success_title)
            .setMessage(R.string.export_fav_success_message)
            .setPositiveButton(R.string.share) { _, _ -> shareFavoritesFile(filePath) }
            .setNegativeButton(android.R.string.ok, null)
            .show()
    }

    private fun showImportFavPreviewDialog(uri: Uri) {
        val dialogView = fragment.layoutInflater.inflate(R.layout.dialog_import_favorites_preview, null)
        val radioSkip = dialogView.findViewById<RadioButton>(R.id.radioSkipDuplicates)
        radioSkip.isChecked = true
        MaterialAlertDialogBuilder(fragment.requireContext())
            .setTitle(R.string.import_fav_preview_title)
            .setMessage(R.string.import_fav_preview_message)
            .setView(dialogView)
            .setPositiveButton(R.string.import_fav_action) { _, _ ->
                val strategy = if (radioSkip.isChecked) FavoritesConflictStrategy.SKIP else FavoritesConflictStrategy.OVERWRITE
                backupViewModel.confirmFavoritesImport(uri, strategy)
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> backupViewModel.resetImportFavState() }
            .setOnCancelListener { backupViewModel.resetImportFavState() }
            .show()
    }

    private fun showImportFavResultDialog(result: FavoritesImportResult) {
        val message = buildString {
            append(fragment.getString(R.string.import_fav_result_added, result.imported)).append("\n")
            append(fragment.getString(R.string.import_fav_result_skipped, result.skipped))
            if (result.unresolved > 0) append("\n").append(fragment.getString(R.string.import_fav_result_unresolved, result.unresolved))
            if (result.failed > 0) append("\n").append(fragment.getString(R.string.import_fav_result_failed, result.failed))
        }
        MaterialAlertDialogBuilder(fragment.requireContext())
            .setTitle(R.string.import_fav_success_title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun showResourceImportPreviewDialog(state: ResourceShareImportUiState.Preview) {
        val message = buildString {
            append(fragment.getString(R.string.resource_share_import_preview_message, state.toCreate, state.toUpdate))
            if (state.containsCredentials) {
                append("\n\n").append(fragment.getString(R.string.resource_share_credentials_warning))
            }
        }
        MaterialAlertDialogBuilder(fragment.requireContext())
            .setTitle(R.string.resource_share_import_title)
            .setMessage(message)
            .setPositiveButton(R.string.resource_share_import_action) { _, _ -> backupViewModel.confirmResourceImport(state.uri) }
            .setNegativeButton(android.R.string.cancel) { _, _ -> backupViewModel.resetImportResState() }
            .setOnCancelListener { backupViewModel.resetImportResState() }
            .show()
    }

    private fun shareFavoritesFile(filePath: String) {
        try {
            val context = fragment.requireContext()
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", File(filePath))
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            fragment.startActivity(Intent.createChooser(intent, fragment.getString(R.string.share)))
        } catch (e: Exception) {
            Timber.e(e, "Failed to share favorites export file")
            showBackupSnackbar(fragment.getString(R.string.export_fav_share_failed))
        }
    }
}
