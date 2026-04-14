package com.sza.fastmediasorter.ui.settings.fragments

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.FragmentSettingsBackupRestoreBinding
import com.sza.fastmediasorter.domain.model.FavoritesConflictStrategy
import com.sza.fastmediasorter.domain.model.FavoritesImportResult
import com.sza.fastmediasorter.domain.model.FavoritesImportStatus
import com.sza.fastmediasorter.domain.usecase.RestoreFromGoogleDriveUseCase
import com.sza.fastmediasorter.ui.settings.BackupRestoreUiState
import com.sza.fastmediasorter.ui.settings.BackupRestoreViewModel
import com.sza.fastmediasorter.ui.settings.FavoritesExportUiState
import com.sza.fastmediasorter.ui.settings.FavoritesImportUiState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File

@AndroidEntryPoint
class BackupRestoreFragment : Fragment() {

    private var _binding: FragmentSettingsBackupRestoreBinding? = null
    private val binding get() = _binding!!

    private val viewModel: BackupRestoreViewModel by viewModels()

    private val importFileLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.previewFavoritesImport(uri)
        }
    }

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Timber.d("Sign-in activity result: resultCode=${result.resultCode}")
        var apiErrorCode: Int? = null
        val account = try {
            GoogleSignIn.getSignedInAccountFromIntent(result.data).getResult(com.google.android.gms.common.api.ApiException::class.java)
        } catch (e: com.google.android.gms.common.api.ApiException) {
            apiErrorCode = e.statusCode
            Timber.w("Sign-in failed (code=${e.statusCode}), trying last signed-in account")
            GoogleSignIn.getLastSignedInAccount(requireContext())
        } catch (e: Exception) {
            Timber.e(e, "Unexpected error parsing Google sign-in result")
            GoogleSignIn.getLastSignedInAccount(requireContext())
        }
        viewModel.handleSignInResult(account, apiErrorCode)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBackupRestoreBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupButtons()
        observeState()
        observeFavoritesState()
        updateAccountInfo()
    }

    private fun setupButtons() {
        binding.btnBackup.setOnClickListener {
            viewModel.startBackup()
        }
        binding.btnRestore.setOnClickListener {
            viewModel.startRestore()
        }
        binding.iconHelpBackupInfo.setOnClickListener {
            com.sza.fastmediasorter.ui.dialog.TooltipDialog.show(
                requireContext(),
                R.string.tooltip_backup_info_title,
                R.string.tooltip_backup_info_message
            )
        }
        binding.btnExportFavorites.setOnClickListener {
            viewModel.exportFavorites()
        }
        binding.btnImportFavorites.setOnClickListener {
            try {
                importFileLauncher.launch(arrayOf("application/json"))
            } catch (e: android.content.ActivityNotFoundException) {
                Timber.w(e, "BackupRestoreFragment: file picker not available")
                com.google.android.material.snackbar.Snackbar.make(
                    binding.root,
                    R.string.save_logs_not_supported,
                    com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    handleState(state)
                }
            }
        }
    }

    private fun observeFavoritesState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.exportFavState.collect { handleExportFavState(it) }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.importFavState.collect { handleImportFavState(it) }
            }
        }
    }

    private fun handleExportFavState(state: FavoritesExportUiState) {
        when (state) {
            is FavoritesExportUiState.Idle -> {
                binding.progressExportFav.visibility = View.GONE
                binding.btnExportFavorites.isEnabled = true
            }
            is FavoritesExportUiState.Loading -> {
                binding.progressExportFav.visibility = View.VISIBLE
                binding.btnExportFavorites.isEnabled = false
            }
            is FavoritesExportUiState.Success -> {
                binding.progressExportFav.visibility = View.GONE
                binding.btnExportFavorites.isEnabled = true
                showExportSuccessDialog(state.result.filePath, state.result.totalExported, state.result.fileSizeBytes)
                viewModel.resetExportFavState()
            }
            is FavoritesExportUiState.Error -> {
                binding.progressExportFav.visibility = View.GONE
                binding.btnExportFavorites.isEnabled = true
                showSnackbar(state.message)
                viewModel.resetExportFavState()
            }
        }
    }

    private fun handleImportFavState(state: FavoritesImportUiState) {
        when (state) {
            is FavoritesImportUiState.Idle -> {
                binding.progressImportFav.visibility = View.GONE
                binding.btnImportFavorites.isEnabled = true
            }
            is FavoritesImportUiState.LoadingPreview -> {
                binding.progressImportFav.visibility = View.VISIBLE
                binding.btnImportFavorites.isEnabled = false
            }
            is FavoritesImportUiState.Preview -> {
                binding.progressImportFav.visibility = View.GONE
                binding.btnImportFavorites.isEnabled = true
                showImportPreviewDialog(state.preview.sourceDevice, state.preview.totalInFile, state.uri)
            }
            is FavoritesImportUiState.Importing -> {
                binding.progressImportFav.visibility = View.VISIBLE
                binding.btnImportFavorites.isEnabled = false
            }
            is FavoritesImportUiState.Success -> {
                binding.progressImportFav.visibility = View.GONE
                binding.btnImportFavorites.isEnabled = true
                showImportResultDialog(state.result)
                viewModel.resetImportFavState()
            }
            is FavoritesImportUiState.Error -> {
                binding.progressImportFav.visibility = View.GONE
                binding.btnImportFavorites.isEnabled = true
                showSnackbar(state.message)
                viewModel.resetImportFavState()
            }
        }
    }

    private fun showExportSuccessDialog(filePath: String, count: Int, sizeBytes: Long) {
        val sizeKb = (sizeBytes / 1024).coerceAtLeast(1)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.export_fav_success_title)
            .setMessage(getString(R.string.export_fav_success_message))
            .setPositiveButton(R.string.share) { _, _ -> shareFile(filePath) }
            .setNegativeButton(android.R.string.ok, null)
            .show()
    }

    private fun showImportPreviewDialog(sourceDevice: String, count: Int, uri: Uri) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_import_favorites_preview, null)
        val radioSkip = dialogView.findViewById<RadioButton>(R.id.radioSkipDuplicates)
        val radioOverwrite = dialogView.findViewById<RadioButton>(R.id.radioOverwriteDuplicates)
        radioSkip.isChecked = true

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.import_fav_preview_title)
            .setMessage(getString(R.string.import_fav_preview_message))
            .setView(dialogView)
            .setPositiveButton(R.string.import_fav_action) { _, _ ->
                val strategy = if (radioSkip.isChecked) FavoritesConflictStrategy.SKIP else FavoritesConflictStrategy.OVERWRITE
                viewModel.confirmFavoritesImport(uri, strategy)
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                viewModel.resetImportFavState()
            }
            .setOnCancelListener { viewModel.resetImportFavState() }
            .show()
    }

    private fun showImportResultDialog(result: FavoritesImportResult) {
        val added = result.imported
        val skipped = result.skipped
        val failed = result.failed
        val unresolved = result.unresolved
        val message = buildString {
            append(getString(R.string.import_fav_result_added, added)).append("\n")
            append(getString(R.string.import_fav_result_skipped, skipped)).append("\n")
            if (unresolved > 0) append(getString(R.string.import_fav_result_unresolved, unresolved)).append("\n")
            if (failed > 0) append(getString(R.string.import_fav_result_failed, failed))
        }.trimEnd()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.import_fav_success_title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun shareFile(filePath: String) {
        try {
            val file = File(filePath)
            val uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.provider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.share)))
        } catch (e: Exception) {
            Timber.e(e, "Failed to share export file")
            showSnackbar(getString(R.string.export_fav_share_failed))
        }
    }

    private fun handleState(state: BackupRestoreUiState) {
        // Reset visibility
        binding.progressBackup.visibility = View.GONE
        binding.progressRestore.visibility = View.GONE
        binding.btnBackup.isEnabled = true
        binding.btnRestore.isEnabled = true

        when (state) {
            is BackupRestoreUiState.Idle -> { /* default state */ }

            is BackupRestoreUiState.Authenticating -> {
                binding.btnBackup.isEnabled = false
                binding.btnRestore.isEnabled = false
            }

            is BackupRestoreUiState.NeedsSignIn -> {
                launchSignIn()
            }

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
                showSnackbar(getString(R.string.backup_success, state.resourceCount, state.favoritesCount, state.accountEmail))
                updateAccountInfo()
                viewModel.resetState()
            }

            is BackupRestoreUiState.BackupInfoReady -> {
                showRestoreConfirmDialog(state.info)
            }

            is BackupRestoreUiState.RestoreSuccess -> {
                showRestoreSuccessMessage(state.result)
                viewModel.resetState()
            }

            is BackupRestoreUiState.Error -> {
                showSnackbar(state.message)
                viewModel.resetState()
            }
        }
    }

    private fun launchSignIn() {
        try {
            val intent = viewModel.getSignInIntent()
            signInLauncher.launch(intent)
        } catch (e: Exception) {
            Timber.e(e, "Failed to launch Google sign-in")
            showSnackbar(getString(R.string.backup_failed, e.message ?: "Unknown error"))
        }
    }

    private fun showRestoreConfirmDialog(info: RestoreFromGoogleDriveUseCase.BackupInfo) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.restore_confirm_title)
            .setMessage(getString(R.string.restore_confirm_message, info.createdAt, info.deviceModel, info.resourceCount, info.favoritesCount))
            .setPositiveButton(R.string.restore_from_google_drive) { _, _ ->
                viewModel.confirmRestore()
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                viewModel.resetState()
            }
            .setOnCancelListener {
                viewModel.resetState()
            }
            .show()
    }

    private fun showRestoreSuccessMessage(result: RestoreFromGoogleDriveUseCase.RestoreResult) {
        showSnackbar(getString(R.string.restore_success, result.resourcesAdded, result.resourcesSkipped, result.favoritesAdded, result.favoritesSkipped))
        if (result.resourcesNeedingAuth > 0) {
            Snackbar.make(binding.root, getString(R.string.restore_needs_auth), Snackbar.LENGTH_LONG).show()
        }
    }

    private fun updateAccountInfo() {
        val email = viewModel.getAccountEmail()
        if (email != null) {
            binding.layoutBackupInfo.visibility = View.VISIBLE
            binding.tvBackupAccount.text = getString(R.string.backup_account, email)
        } else {
            binding.layoutBackupInfo.visibility = View.GONE
        }
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
